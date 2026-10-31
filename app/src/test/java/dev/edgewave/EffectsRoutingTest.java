package dev.edgewave;

import static org.junit.Assert.*;

import android.content.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "ru-rRU-w360dp-h800dp-xhdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class EffectsRoutingTest {
  @Test
  public void eachRollHitAddsNewInstanceWithoutRestartingOldOne() {
    SharedPreferences p = RuntimeEnvironment.getApplication().getSharedPreferences("roll50", 0);
    p.edit().putBoolean("fx_line", true).apply();
    EffectsEngine engine = new EffectsEngine(p);
    float[] shape = new float[48];
    Arrays.fill(shape, 70);
    for (int t = 0; t <= 360; t += 10) {
      float[] bands = new float[48];
      for (int i = 18; i < 45; i++) bands[i] = .2f * (float) Math.exp(-(t % 120) / 17f);
      engine.offerAudioFrame(bands, t);
      engine.update(bands, t, true, shape);
    }
    assertTrue(engine.items.size() >= 3);
    Set<Long> ids = new HashSet<>();
    Set<Long> births = new HashSet<>();
    for (EffectsEngine.Fx f : engine.items) {
      assertEquals(0, f.kind);
      ids.add(f.id);
      births.add(f.born);
    }
    assertEquals(engine.items.size(), ids.size());
    assertEquals(engine.items.size(), births.size());
    assertEquals(0, engine.items.get(0).born);
  }

  @Test
  public void dropBurstIncludesEveryCompatibleSelectedFamilyBeforeParticlesFillBudget() {
    SharedPreferences p =
        RuntimeEnvironment.getApplication().getSharedPreferences("drop-burst50", 0);
    p.edit().putBoolean("fx_combos", true).apply();
    EffectsEngine engine = new EffectsEngine(p);
    engine.testMask = (1 << EffectsEngine.COUNT) - 1;
    engine.externalAnalysis = true;
    engine.pending.offer(new TrackPatterns.Event(1000, TrackPatterns.Type.DROP, 0, 1, 1, 1, .5f));
    engine.update(new float[48], 1000, true, new float[48]);
    Set<Integer> seen = new HashSet<>();
    for (EffectsEngine.Fx f : engine.items) seen.add(f.kind);
    assertEquals(new HashSet<>(Arrays.asList(2, 3, 5, 6, 7, 8)), seen);
    assertTrue(engine.items.size() <= 80);
  }

  @Test
  public void repeatedTonalAndGlideEventsCannotStackIdenticalGestures() {
    for (int kind : new int[] {8, 9}) {
      SharedPreferences p =
          RuntimeEnvironment.getApplication().getSharedPreferences("gesture51-" + kind, 0);
      EffectsEngine e = new EffectsEngine(p);
      e.testMask = 1 << kind;
      e.externalAnalysis = true;
      for (int now = 1000; now < 4000; now += 100) {
        e.pending.offer(
            new TrackPatterns.Event(
                now,
                kind == 8 ? TrackPatterns.Type.TONAL : TrackPatterns.Type.GLIDE,
                0,
                1,
                .8f,
                1,
                .5f));
        e.update(new float[48], now, true, new float[48]);
        int count = 0;
        for (EffectsEngine.Fx f : e.items) if (f.kind == kind) count++;
        assertTrue("stacked gesture " + kind, count <= 1);
      }
    }
  }

  @Test
  public void quietActivePlaybackStaysVisibleButMuteStillHides() {
    android.content.Context c = RuntimeEnvironment.getApplication();
    WaveView wave = new WaveView(c, false);
    wave.playbackActive = true;
    wave.appear = 1;
    wave.layout(0, 0, 720, 1600);
    wave.last = System.nanoTime() - 50000000;
    android.graphics.Bitmap b =
        android.graphics.Bitmap.createBitmap(720, 1600, android.graphics.Bitmap.Config.ARGB_8888);
    wave.draw(new android.graphics.Canvas(b));
    assertEquals(1, wave.appear, .001f);
    assertEquals(android.graphics.Color.BLACK, b.getPixel(20, 20));
    wave.muted = true;
    wave.last = System.nanoTime() - 50000000;
    wave.draw(new android.graphics.Canvas(b));
    assertTrue(wave.appear < .9f);
  }

  @Test
  public void bottomPulseRequiresStrongRareDrop() {
    EffectsEngine e =
        new EffectsEngine(
            RuntimeEnvironment.getApplication().getSharedPreferences("rare-bottom51", 0));
    e.testMask = 1 << 5;
    e.externalAnalysis = true;
    for (int time : new int[] {1000, 2000, 14000}) {
      e.pending.offer(new TrackPatterns.Event(time, TrackPatterns.Type.DROP, 0, 1, 1, 1, .5f));
      e.update(new float[48], time, true, new float[48]);
      assertEquals(time == 2000 ? 0 : 1, e.items.size());
    }
  }

  @Test
  public void sidesCycleOnActuallySpawnedGesturesNotOnUnrelatedEvents() {
    EffectsEngine e =
        new EffectsEngine(RuntimeEnvironment.getApplication().getSharedPreferences("sides52", 0));
    e.testMask = 1 << 4;
    e.externalAnalysis = true;
    for (int i = 0; i < 9; i++) {
      int now = 1000 + i * 400;
      e.pending.offer(
          new TrackPatterns.Event(now - 10, TrackPatterns.Type.GLIDE, 0, 1, .7f, 1, .1f));
      e.pending.offer(new TrackPatterns.Event(now, TrackPatterns.Type.KICK, 0, 1, .7f, 1, .1f));
      e.update(new float[48], now, true, new float[48]);
      assertEquals(i % 3, e.items.get(e.items.size() - 1).side);
    }
  }

  @Test
  public void allUpperFamiliesExcludeEachOtherButRollCopiesRemainPossible() {
    EffectsEngine e =
        new EffectsEngine(RuntimeEnvironment.getApplication().getSharedPreferences("upper52", 0));
    for (int a : new int[] {0, 8, 9})
      for (int b : new int[] {0, 8, 9})
        if (a != b) {
          e.items.clear();
          e.spawn(a, 0, .8f, new float[48]);
          e.spawn(b, 100, .8f, new float[48]);
          assertEquals(1, e.items.size());
        }
    e.items.clear();
    e.spawn(0, 0, .8f, new float[48]);
    e.spawn(0, 100, .8f, new float[48]);
    assertEquals(2, e.items.size());
  }

  @Test
  public void upperGestureFinishesBeforeQueuedFamilyStarts() {
    EffectsEngine e =
        new EffectsEngine(
            RuntimeEnvironment.getApplication().getSharedPreferences("queue-upper53", 0));
    e.testMask = (1 << 8) | 1;
    e.externalAnalysis = true;
    e.spawn(8, 900, .8f, new float[48]);
    EffectsEngine.Fx original = e.items.get(0);
    e.pending.offer(new TrackPatterns.Event(1000, TrackPatterns.Type.SNARE, 0, 1, .8f, 1, .5f));
    e.update(new float[48], 1000, true, new float[48]);
    assertSame(original, e.items.get(0));
    assertEquals(8, e.items.get(0).kind);
    assertEquals(0, e.waitingUpper);
    e.pending.offer(new TrackPatterns.Event(1680, TrackPatterns.Type.ROLL, 0, 4, .8f, 1, .5f));
    e.update(new float[48], 1700, true, new float[48]);
    for (EffectsEngine.Fx f : e.items) assertEquals(0, f.kind);
    assertFalse(e.items.contains(original));
  }

  @Test
  public void oldQueuedUpperEventCannotFireLongAfterItsBeat() {
    EffectsEngine e =
        new EffectsEngine(
            RuntimeEnvironment.getApplication().getSharedPreferences("stale-upper53", 0));
    e.testMask = (1 << 9) | 1;
    e.externalAnalysis = true;
    e.spawn(9, 900, .8f, new float[48]);
    e.pending.offer(new TrackPatterns.Event(1000, TrackPatterns.Type.SNARE, 0, 1, .8f, 1, .5f));
    e.update(new float[48], 1000, true, new float[48]);
    e.update(new float[48], 2500, true, new float[48]);
    assertTrue(e.items.isEmpty());
    assertEquals(-1, e.waitingUpper);
  }

  @Test
  public void sideParticleBurstsAreSmallAndPoolIsBounded() {
    EffectsEngine e =
        new EffectsEngine(
            RuntimeEnvironment.getApplication().getSharedPreferences("side-pool53", 0));
    e.spawn(3, 1000, 1, new float[48]);
    assertEquals(10, e.items.size());
    for (int i = 0; i < 20; i++) e.spawn(3, 1000, 1, new float[48]);
    assertEquals(18, e.items.size());
  }

  @Test
  public void audioQueueIsBoundedAndTrackChangeClearsOldEvents() {
    SharedPreferences p = RuntimeEnvironment.getApplication().getSharedPreferences("queue50", 0);
    EffectsEngine engine = new EffectsEngine(p);
    for (int t = 0; t < 20000; t += 10) {
      float[] bands = new float[48];
      for (int i = 18; i < 45; i++) bands[i] = .2f * (float) Math.exp(-(t % 120) / 17f);
      engine.offerAudioFrame(bands, t);
    }
    assertTrue(engine.pending.size() <= 32);
    engine.useTrack("new-track");
    assertTrue(engine.pending.isEmpty());
  }
}
