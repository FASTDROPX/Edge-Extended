package dev.edgewave;

import static org.junit.Assert.*;

import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.slider.Slider;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "en-rUS-w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ReleaseLocalizationTest {
  int count(View v, Class<?> type) {
    int n = type.isInstance(v) ? 1 : 0;
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) n += count(g.getChildAt(i), type);
    }
    return n;
  }

  void sliders(View v) {
    if (v instanceof Slider) {
      assertEquals(0, ((Slider) v).getTrackStopIndicatorSize());
      assertFalse(((Slider) v).isTickVisible());
    }
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) sliders(g.getChildAt(i));
    }
  }

  void save(MainActivity a, String name) throws Exception {
    View v = a.getWindow().getDecorView();
    v.measure(
        View.MeasureSpec.makeMeasureSpec(720, 1073741824),
        View.MeasureSpec.makeMeasureSpec(1600, 1073741824));
    v.layout(0, 0, 720, 1600);
    Bitmap b = Bitmap.createBitmap(720, 1600, Bitmap.Config.ARGB_8888);
    v.draw(new Canvas(b));
    File d = new File("build/release-checks");
    d.mkdirs();
    try (FileOutputStream f = new FileOutputStream(new File(d, name + ".png"))) {
      b.compress(Bitmap.CompressFormat.PNG, 100, f);
    }
  }

  @Test
  public void releaseHomeHasNoPreviewOrStatusTileAndGithubOpensRepository() throws Exception {
    MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
    assertEquals("Edge Extended", a.toolbar.getTitle().toString());
    assertEquals(0, count(a.content, WaveView.class));
    assertEquals(0, count(a.content, Chip.class));
    assertFalse(a.power.isChecked());
    assertFalse(a.prefs.getBoolean("volume", true));
    assertFalse(a.prefs.getBoolean("fx_combos", true));
    for (String k : EffectsEngine.KEYS) assertFalse(k, a.prefs.getBoolean(k, true));
    a.openGitHub();
    Intent i = Shadows.shadowOf(a).getNextStartedActivity();
    assertEquals("https://github.com/FASTDROPX/Edge-Extended", i.getDataString());
    save(a, "home-en");
    a.navigation.setSelectedItemId(MainActivity.NAV_WAVE);
    assertEquals(0, count(a.content, WaveView.class));
    sliders(a.content);
    a.navigation.setSelectedItemId(MainActivity.NAV_EFFECTS);
    sliders(a.content);
    save(a, "effects-en");
  }

  @Test
  public void translationsCoverVisibleNavigationAndEffectNames() {
    Context c = RuntimeEnvironment.getApplication();
    String[] tags = {"en", "ru", "zh-Hans", "fr", "de"},
        homes = {"Home", "Главная", "首页", "Accueil", "Start"};
    for (int n = 0; n < tags.length; n++) {
      Configuration cfg = new Configuration(c.getResources().getConfiguration());
      cfg.setLocale(Locale.forLanguageTag(tags[n]));
      Context localized = c.createConfigurationContext(cfg);
      assertEquals(tags[n], homes[n], localized.getString(R.string.home));
      assertEquals(10, localized.getResources().getStringArray(R.array.effect_names).length);
      assertFalse(localized.getString(R.string.volume_keys_description).isEmpty());
      assertFalse(localized.getString(R.string.about_message).isEmpty());
    }
  }

  @Test
  public void languageMenuContainsEverySupportedLanguage() {
    MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
    a.languageDialog();
    AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
    assertEquals(6, d.getListView().getCount());
    assertEquals("English", d.getListView().getAdapter().getItem(2));
    assertEquals("简体中文", d.getListView().getAdapter().getItem(3));
    assertEquals("Français", d.getListView().getAdapter().getItem(4));
    assertEquals("Deutsch", d.getListView().getAdapter().getItem(5));
  }

  @Test
  public void defaultsDoNotOverwriteExplicitUserChoices() {
    SharedPreferences p =
        RuntimeEnvironment.getApplication().getSharedPreferences("release-defaults", 0);
    p.edit().putBoolean("volume", true).putBoolean("fx_line", true).apply();
    AppDefaults.apply(p);
    assertTrue(p.getBoolean("volume", false));
    assertTrue(p.getBoolean("fx_line", false));
    assertFalse(p.getBoolean("fx_combos", true));
  }

  @Test
  @Config(qualifiers = "zh-rCN-w360dp-h800dp-xhdpi")
  public void chineseUiRenders() throws Exception {
    MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
    save(a, "home-zh");
    a.navigation.setSelectedItemId(MainActivity.NAV_EFFECTS);
    save(a, "effects-zh");
  }

  @Test
  @Config(qualifiers = "de-rDE-w360dp-h800dp-night-xhdpi")
  public void germanNightUiRenders() throws Exception {
    MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
    save(a, "home-de-night");
    a.navigation.setSelectedItemId(MainActivity.NAV_EFFECTS);
    save(a, "effects-de-night");
  }

  @Test
  @Config(qualifiers = "en-rUS-w360dp-h800dp-night-xhdpi")
  public void englishNightUiRenders() throws Exception {
    MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
    save(a, "home-en-night");
    a.navigation.setSelectedItemId(MainActivity.NAV_EFFECTS);
    save(a, "effects-en-night");
  }

  @Test
  @Config(qualifiers = "fr-rFR-w360dp-h800dp-xhdpi")
  public void frenchUiRenders() throws Exception {
    MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
    save(a, "home-fr");
  }
}
