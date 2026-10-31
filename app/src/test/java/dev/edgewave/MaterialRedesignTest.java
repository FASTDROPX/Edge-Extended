package dev.edgewave;

import static org.junit.Assert.*;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.slider.Slider;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "ru-rRU-w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MaterialRedesignTest {
  <T> List<T> find(View view, Class<T> type) {
    List<T> result = new ArrayList<>();
    if (type.isInstance(view)) result.add(type.cast(view));
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++)
        result.addAll(find(group.getChildAt(i), type));
    }
    return result;
  }

  void save(View view, String name) throws Exception {
    view.measure(
        View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY));
    view.layout(0, 0, 720, 1600);
    Bitmap image = Bitmap.createBitmap(720, 1600, Bitmap.Config.ARGB_8888);
    view.draw(new Canvas(image));
    File folder = new File("build/material-checks");
    folder.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(folder, name + ".png"))) {
      image.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
  }

  @Test
  public void allPagesUseMaterialNavigationSlidersAndDropdowns() throws Exception {
    ((android.media.AudioManager)
            RuntimeEnvironment.getApplication().getSystemService(Context.AUDIO_SERVICE))
        .setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 5, 0);
    MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
    assertTrue(activity.navigation instanceof BottomNavigationView);
    assertNotNull(activity.toolbar);
    assertTrue(activity.content.getChildCount() > 0);
    int[] pages = {
      MainActivity.NAV_HOME, MainActivity.NAV_WAVE, MainActivity.NAV_KEYS, MainActivity.NAV_EFFECTS
    };
    String[] names = {"home", "wave", "keys", "effects"};
    for (int i = 0; i < pages.length; i++) {
      activity.navigation.setSelectedItemId(pages[i]);
      assertEquals(pages[i], activity.page);
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500));
      assertTrue(find(activity.content, Spinner.class).isEmpty());
      if (i == 1 || i == 2) assertTrue(find(activity.content, Slider.class).size() >= 4);
      if (i == 3) {
        assertNotNull(activity.content.findViewWithTag("effectMode"));
        assertNotNull(activity.content.findViewWithTag("enterEffect"));
        assertNotNull(activity.content.findViewWithTag("exitEffect"));
        assertNotNull(activity.content.findViewWithTag("bottomMode"));
      }
      save(activity.getWindow().getDecorView(), names[i]);
    }
  }

  @Test
  public void applicationDialogsUseMaterialAppCompat() {
    MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
    activity.aboutDialog();
    android.app.Dialog dialog = ShadowDialog.getLatestDialog();
    assertNotNull(dialog);
    assertTrue(dialog instanceof androidx.appcompat.app.AlertDialog);
    assertTrue(dialog.isShowing());
  }

  @Test
  public void iconHasThreeCalmLayersAboveHalfAndBlackBackground() throws Exception {
    AdaptiveIconDrawable icon =
        (AdaptiveIconDrawable)
            RuntimeEnvironment.getApplication().getDrawable(R.mipmap.ic_launcher);
    Bitmap image = Bitmap.createBitmap(216, 216, Bitmap.Config.ARGB_8888);
    icon.setBounds(0, 0, 216, 216);
    icon.draw(new Canvas(image));
    assertEquals(Color.BLACK, image.getPixel(108, 150));
    Set<Integer> tones = new HashSet<>();
    int last = 0;
    for (int y = 0; y < 216; y++) {
      int pixel = image.getPixel(108, y);
      if (Color.alpha(pixel) == 255 && Color.red(pixel) > 20) {
        tones.add(pixel);
        last = y;
      }
    }
    assertTrue(last > 70 && last < 108);
    assertTrue(tones.contains(Color.rgb(244, 244, 244)));
    assertTrue(tones.contains(Color.rgb(157, 157, 157)));
    assertTrue(tones.contains(Color.rgb(83, 83, 83)));
    assertNotNull(icon.getMonochrome());
    File folder = new File("build/material-checks");
    folder.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(folder, "icon.png"))) {
      image.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
  }

  @Test
  public void monochromeIconKeepsLowerPartTransparent() throws Exception {
    Drawable monochrome =
        RuntimeEnvironment.getApplication().getDrawable(R.drawable.ic_launcher_monochrome);
    Bitmap mask = Bitmap.createBitmap(216, 216, Bitmap.Config.ARGB_8888);
    monochrome.setBounds(0, 0, 216, 216);
    monochrome.draw(new Canvas(mask));
    for (int y = 108; y < 216; y++)
      for (int x = 0; x < 216; x++) assertEquals(0, Color.alpha(mask.getPixel(x, y)));
    Bitmap themed = Bitmap.createBitmap(216, 216, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(themed);
    Paint paint = new Paint(3);
    paint.setColor(0xffd7eaf1);
    canvas.drawRoundRect(new RectF(0, 0, 216, 216), 48, 48, paint);
    monochrome.setTint(0xff243c46);
    canvas.save();
    Path clip = new Path();
    clip.addRoundRect(new RectF(0, 0, 216, 216), 48, 48, Path.Direction.CW);
    canvas.clipPath(clip);
    canvas.scale(1.5f, 1.5f, 108, 108);
    monochrome.draw(canvas);
    canvas.restore();
    File folder = new File("build/material-checks");
    folder.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(folder, "icon-themed.png"))) {
      themed.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
  }

  @Test
  public void frameEchoIsThreeShallowLayersWithSoftEndpoints() {
    for (int layer = 0; layer < 3; layer++) {
      assertEquals(0, ContourEcho.alpha(0, layer));
      assertEquals(0, ContourEcho.alpha(1, layer));
      assertTrue(ContourEcho.alpha(.5f, layer) > 0);
      assertTrue(ContourEcho.alpha(.5f, layer) < 150);
      assertTrue(ContourEcho.inset(1, layer, 1, 1) <= 22.01f);
    }
    float a = ContourEcho.inset(ContourEcho.progress(.5f, 0), 0, .8f, 1),
        b = ContourEcho.inset(ContourEcho.progress(.5f, 1), 1, .8f, 1),
        c = ContourEcho.inset(ContourEcho.progress(.5f, 2), 2, .8f, 1);
    assertTrue(b - a > 1);
    assertTrue(c - b > 1);
  }

  @Test
  public void navigationDoesNotApplySystemInsetsTwice() {
    MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
    int before = activity.navigation.getPaddingBottom();
    WindowInsets insets =
        new WindowInsets.Builder()
            .setInsets(WindowInsets.Type.systemBars(), android.graphics.Insets.of(0, 30, 0, 48))
            .build();
    activity.rootPanel.dispatchApplyWindowInsets(insets);
    assertEquals(30, activity.rootPanel.getPaddingTop());
    assertEquals(48, activity.rootPanel.getPaddingBottom());
    assertEquals(before, activity.navigation.getPaddingBottom());
  }
}
