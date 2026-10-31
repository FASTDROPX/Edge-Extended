package dev.edgewave;

import static org.junit.Assert.*;

import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.*;
import com.google.android.material.card.MaterialCardView;
import java.io.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "ru-rRU-w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CompactInterfaceTest {
  MainActivity activity() {
    android.content.Context c = RuntimeEnvironment.getApplication();
    ((android.media.AudioManager) c.getSystemService(Context.AUDIO_SERVICE))
        .setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 5, 0);
    return Robolectric.buildActivity(MainActivity.class).setup().get();
  }

  int count(View v, Class<?> type) {
    int n = type.isInstance(v) ? 1 : 0;
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) n += count(g.getChildAt(i), type);
    }
    return n;
  }

  void save(View v, String name, int height) throws Exception {
    v.measure(
        View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    v.layout(0, 0, 720, height);
    Bitmap b = Bitmap.createBitmap(720, height, Bitmap.Config.ARGB_8888);
    v.draw(new Canvas(b));
    File folder = new File("build/compact-checks");
    folder.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(folder, name + ".png"))) {
      b.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
  }

  @Test
  public void pagesAreCompactAndSelectionUsesMaterialDialog() throws Exception {
    MainActivity a = activity();
    int[] ids = {
      MainActivity.NAV_HOME, MainActivity.NAV_WAVE, MainActivity.NAV_KEYS, MainActivity.NAV_EFFECTS
    };
    String[] labels = {"home-day", "wave-day", "keys-day", "effects-day"};
    for (int i = 0; i < ids.length; i++) {
      a.navigation.setSelectedItemId(ids[i]);
      save(a.getWindow().getDecorView(), labels[i], 1600);
    }
    assertTrue(count(a.content, MaterialCardView.class) <= 7);
    Button mode = a.content.findViewWithTag("effectMode");
    assertTrue(mode.getText().toString().contains("БЕТА"));
    mode.performClick();
    AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
    assertEquals(2, dialog.getListView().getCount());
    assertTrue(dialog.getListView().getAdapter().getItem(1).toString().contains("БЕТА"));
    save(dialog.getWindow().getDecorView(), "choice-day", 1000);
    dialog.dismiss();
    a.effectDialog(9);
    AlertDialog details = (AlertDialog) ShadowDialog.getLatestDialog();
    Button heights = details.getWindow().getDecorView().findViewWithTag("duetHeights");
    assertNotNull(heights);
    heights.performClick();
    AlertDialog choices = (AlertDialog) ShadowDialog.getLatestDialog();
    choices.getListView().performItemClick(null, 0, 0);
    assertEquals(0, a.prefs.getInt("duetHeights", -1));
  }

  @Test
  @Config(qualifiers = "ru-rRU-w360dp-h800dp-night-xhdpi")
  public void nightThemeHasReadablePagesDialogsAndSystemBars() throws Exception {
    MainActivity a = activity();
    assertTrue(AppAppearance.dark(a));
    int surface = a.color(com.google.android.material.R.attr.colorSurface),
        text = a.color(com.google.android.material.R.attr.colorOnSurface);
    assertTrue(androidx.core.graphics.ColorUtils.calculateLuminance(surface) < .1);
    assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(text, surface) > 7);
    int[] ids = {
      MainActivity.NAV_HOME, MainActivity.NAV_WAVE, MainActivity.NAV_KEYS, MainActivity.NAV_EFFECTS
    };
    String[] labels = {"home-night", "wave-night", "keys-night", "effects-night"};
    for (int i = 0; i < ids.length; i++) {
      a.navigation.setSelectedItemId(ids[i]);
      save(a.getWindow().getDecorView(), labels[i], 1600);
    }
    a.content.findViewWithTag("effectMode").performClick();
    AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
    save(dialog.getWindow().getDecorView(), "choice-night", 1000);
    int flags = a.getWindow().getDecorView().getSystemUiVisibility();
    assertEquals(
        0,
        flags & (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));
  }

  @Test
  public void themeChoicePersistsAndSelectsRealNightMode() {
    MainActivity a = activity();
    a.themeDialog();
    AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
    dialog.getListView().performItemClick(null, 2, 0);
    assertEquals(2, a.prefs.getInt("themeMode", -1));
    assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode());
    a.prefs.edit().putInt("themeMode", 0).apply();
    AppAppearance.apply(a);
  }

  @Test
  public void laboratoryIsNoLongerAnActivity() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    try {
      c.getPackageManager()
          .getActivityInfo(new ComponentName(c, "dev.edgewave.AudioLabActivity"), 0);
      fail("audio laboratory is still exposed");
    } catch (PackageManager.NameNotFoundException expected) {
    }
  }

  @Test
  public void duetAlternatesHeightsAndCanUseFixedArrangement() {
    Context c = RuntimeEnvironment.getApplication();
    SharedPreferences p = c.getSharedPreferences("duet-height60", 0);
    EffectsEngine e = new EffectsEngine(p);
    for (int i = 0; i < 6; i++) {
      e.items.clear();
      e.spawn(9, i * 2000, .8f, new float[48]);
      EffectsEngine.Fx f = e.items.get(0);
      assertEquals(i % 2 == 1, f.duetReverse);
      assertEquals(
          5,
          Math.abs(MusicEffectsPainter.duetHeight(f, 0) - MusicEffectsPainter.duetHeight(f, 1)),
          0);
    }
    p.edit().putInt("duetHeights", 0).apply();
    for (int i = 0; i < 3; i++) {
      e.items.clear();
      e.spawn(9, 20000 + i * 2000, .8f, new float[48]);
      assertFalse(e.items.get(0).duetReverse);
    }
  }
}
