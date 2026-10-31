package dev.edgewave;

import static org.junit.Assert.*;

import android.content.*;
import android.graphics.*;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "ru-rRU-w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class HybridAndMusicEffectsTest {
  @Test
  public void hybridKeepsReducedClassicFoundationAndLiveImpulse() {
    float[] classic = new float[48], silent = new float[48], live = new float[48];
    Arrays.fill(classic, .4f);
    Arrays.fill(live, .2f);
    for (int layer = 0; layer < 3; layer++) {
      float baseline = HybridWaveProfile.samplePrepared(classic, silent, .2f, 1, 3, layer, 0);
      float rest = .08f + layer * .22f;
      assertEquals(
          rest * .8f + (ClassicWaveProfile.samplePrepared(classic, .2f, 1, layer) - rest) * 1.25f,
          baseline,
          .00001f);
      float before = HybridWaveProfile.samplePrepared(classic, live, .2f, 1, 3, layer, 0),
          after = HybridWaveProfile.samplePrepared(classic, live, .2f, 1, 3, layer, .6f);
      float impulse =
          WaveProfile.samplePrepared(live, .2f, 3, layer, .6f)
              - WaveProfile.samplePrepared(live, .2f, 3, layer, 0);
      assertEquals(impulse * .7f, after - before, .00001f);
    }
  }

  @Test
  public void allTenEffectsParticipateInSchedulingAndCombinations() {
    assertEquals(10, EffectsEngine.COUNT);
    EffectScheduler scheduler = new EffectScheduler();
    Set<Integer> seen = new HashSet<>();
    for (int i = 1; i < 300; i++) {
      int kind =
          scheduler.choose(
              (1 << EffectsEngine.COUNT) - 1,
              new BeatTracker.Beat(i * 500, .8f, i % 6 == 0, i),
              120);
      if (kind >= 0) seen.add(kind);
    }
    for (int i = 0; i < EffectsEngine.COUNT; i++)
      assertTrue("missing effect " + i, seen.contains(i));
    for (int a = 0; a < EffectsEngine.COUNT; a++)
      for (int b = 0; b < EffectsEngine.COUNT; b++)
        if (a != b) {
          Random random = new Random(2);
          boolean found = false;
          for (int i = 0; i < 100; i++)
            found |= ComboPlanner.partner(a, (1 << a) | (1 << b), true, random) == b;
          assertEquals(ComboPlanner.compatible(a, b), found);
        }
  }

  @Test
  public void newEffectSwitchesUseTheirOwnKeysAndSpawnTheirRenderers() {
    SharedPreferences p = RuntimeEnvironment.getApplication().getSharedPreferences("music-fx", 0);
    for (int kind = 8; kind < EffectsEngine.COUNT; kind++) {
      p.edit().clear().putBoolean(EffectsEngine.KEYS[kind], true).apply();
      EffectsEngine engine = new EffectsEngine(p);
      assertEquals(1 << kind, engine.mask());
      engine.spawn(kind, 1000, .8f, new float[48]);
      assertEquals(kind, engine.items.get(0).kind);
      assertTrue(engine.items.size() < 4);
    }
  }

  @Test
  public void hybridIsSelectableAndPreviewIsNotFlat() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    c.getSharedPreferences("wave", 0).edit().putString("responseMode", "hybrid").apply();
    MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
    activity.navigation.setSelectedItemId(MainActivity.NAV_WAVE);
    android.widget.Button choice = activity.content.findViewWithTag("responseMode");
    assertTrue(choice.getText().toString().contains("Классическая живая"));
    WaveView wave = new WaveView(c, true);
    wave.appear = 1;
    wave.layout(0, 0, 720, 280);
    Arrays.fill(wave.levels, .6f);
    Arrays.fill(wave.classicLevels, .4f);
    Bitmap bitmap = Bitmap.createBitmap(720, 280, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    canvas.drawColor(Color.WHITE);
    wave.draw(canvas);
    assertEquals(Color.BLACK, bitmap.getPixel(360, 5));
    File folder = new File("build/hybrid-checks");
    folder.mkdirs();
    try (FileOutputStream output = new FileOutputStream(new File(folder, "hybrid.png"))) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
    }
  }

  List<MaterialAutoCompleteTextView> find(android.view.View root) {
    List<MaterialAutoCompleteTextView> fields = new ArrayList<>();
    if (root instanceof MaterialAutoCompleteTextView)
      fields.add((MaterialAutoCompleteTextView) root);
    if (root instanceof android.view.ViewGroup) {
      android.view.ViewGroup group = (android.view.ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++) fields.addAll(find(group.getChildAt(i)));
    }
    return fields;
  }

  @Test
  public void duetKeepsBassAndTrebleMeasurements() {
    SharedPreferences p = RuntimeEnvironment.getApplication().getSharedPreferences("energies", 0);
    p.edit().putBoolean("fx_duet", true).apply();
    EffectsEngine engine = new EffectsEngine(p);
    engine.forcedKind = 9;
    float[] bands = new float[48];
    for (int i = 0; i < 16; i++) bands[i] = .6f;
    for (int i = 32; i < 48; i++) bands[i] = .03f;
    engine.update(bands, 1000, true, new float[48]);
    EffectsEngine.Fx duet = engine.items.get(0);
    assertTrue(duet.low > duet.high);
    assertTrue(duet.pitch >= 0 && duet.pitch <= 1);
  }
}
