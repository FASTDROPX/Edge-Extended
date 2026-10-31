package dev.edgewave;

import static org.junit.Assert.*;

import android.app.Application;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import java.io.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowSettings;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "ru-rRU-w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WhiteStageTest {
  @Before
  public void reset() {
    WaveService.active = WaveService.requested = false;
  }

  @Test
  public void defaultsMatchReferenceAndPreserveSavedChoices() {
    SharedPreferences p =
        RuntimeEnvironment.getApplication().getSharedPreferences("default-test", 0);
    AppDefaults.apply(p);
    assertEquals(2, p.getInt("enterEffect", -1));
    assertEquals(2, p.getInt("exitEffect", -1));
    assertEquals(450, p.getInt("transitionMs", -1));
    assertEquals(1, p.getInt("bottomMode", -1));
    assertEquals(0, p.getInt("bottomOffset", -1));
    p.edit().putInt("enterEffect", 4).putInt("bottomMode", 0).apply();
    AppDefaults.apply(p);
    assertEquals(4, p.getInt("enterEffect", -1));
    assertEquals(0, p.getInt("bottomMode", -1));
  }

  @Test
  public void whiteWindowContainsOnlyCenteredSwitchAndHidesSystemBars() throws Exception {
    WhiteStageActivity activity = Robolectric.buildActivity(WhiteStageActivity.class).setup().get();
    assertEquals(1, activity.root.getChildCount());
    assertSame(activity.control, activity.root.getChildAt(0));
    assertEquals(Color.WHITE, ((ColorDrawable) activity.root.getBackground()).getColor());
    assertFalse(activity.control.isChecked());
    assertTrue(activity.control.getText() == null || activity.control.getText().length() == 0);
    int flags = activity.getWindow().getDecorView().getSystemUiVisibility();
    assertTrue((flags & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0);
    assertTrue((flags & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0);
    activity.root.measure(
        View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY));
    activity.root.layout(0, 0, 720, 1600);
    assertEquals(360, (activity.control.getLeft() + activity.control.getRight()) / 2);
    assertEquals(800, (activity.control.getTop() + activity.control.getBottom()) / 2);
    Bitmap bitmap = Bitmap.createBitmap(720, 1600, Bitmap.Config.ARGB_8888);
    activity.root.draw(new Canvas(bitmap));
    assertEquals(Color.WHITE, bitmap.getPixel(0, 0));
    assertEquals(Color.WHITE, bitmap.getPixel(719, 1599));
    File folder = new File("build/white-stage-checks");
    folder.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(folder, "white-stage.png"))) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
  }

  @Test
  public void switchStartsAndStopsRealVisualization() {
    ShadowSettings.setCanDrawOverlays(true);
    Application app = RuntimeEnvironment.getApplication();
    Shadows.shadowOf(app)
        .grantPermissions(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.POST_NOTIFICATIONS);
    WhiteStageActivity activity = Robolectric.buildActivity(WhiteStageActivity.class).setup().get();
    activity.control.setChecked(true);
    Intent start = Shadows.shadowOf(app).getNextStartedService();
    assertNotNull(start);
    assertEquals(WaveService.class.getName(), start.getComponent().getClassName());
    assertNull(start.getAction());
    activity.control.setChecked(false);
    Intent stop = Shadows.shadowOf(app).getNextStartedService();
    assertEquals("stop", stop.getAction());
  }

  @Test
  public void shutdownFadesExistingEffectsWithoutNewSpawns() {
    SharedPreferences p =
        RuntimeEnvironment.getApplication().getSharedPreferences("shutdown-test", 0);
    p.edit().putBoolean("fx_particles_top", true).apply();
    EffectsEngine e = new EffectsEngine(p);
    e.spawn(2, 1000, 1, new float[48]);
    int count = e.items.size();
    e.beginShutdown(1000, 450);
    e.update(new float[48], 1100, false, new float[48]);
    assertEquals(count, e.items.size());
    assertTrue(e.visibility(1200) > 0 && e.visibility(1200) < 1);
    assertEquals(0, e.visibility(1450), 0);
    e.prune(1450);
    assertTrue(e.items.isEmpty());
    e.cancelShutdown();
    assertEquals(1, e.visibility(1500), 0);
  }
}
