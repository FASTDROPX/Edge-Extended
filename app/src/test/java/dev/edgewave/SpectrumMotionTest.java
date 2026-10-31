package dev.edgewave;

import static org.junit.Assert.*;

import java.util.Arrays;
import org.junit.Test;

public class SpectrumMotionTest {
  @Test
  public void drumAttackMovesWithinTwoFrames() {
    SpectrumMotion m = new SpectrumMotion();
    float[] quiet = new float[48];
    Arrays.fill(quiet, .025f);
    for (int i = 0; i < 120; i++) m.update(quiet, 1 / 60f, 1.6f);
    float before = m.levels[4];
    float[] loud = new float[48];
    Arrays.fill(loud, .8f);
    m.update(loud, 1 / 60f, 1.6f);
    m.update(loud, 1 / 60f, 1.6f);
    assertTrue("attack too weak", m.levels[4] - before > .35f);
    assertTrue("bass transient missing", m.beat > .4f);
  }

  @Test
  public void silenceDecaysToRestWithoutInventedSignal() {
    SpectrumMotion m = new SpectrumMotion();
    float[] loud = new float[48];
    Arrays.fill(loud, .8f);
    m.update(loud, .05f, 1.6f);
    for (int i = 0; i < 180; i++) m.update(new float[48], 1 / 60f, 1.6f);
    for (float x : m.levels) assertTrue(x < .0001f);
    assertTrue(m.beat < .0001f);
  }

  @Test
  public void frequencyMovementDoesNotLeakToOppositeEnd() {
    SpectrumMotion m = new SpectrumMotion();
    float[] bass = new float[48];
    bass[3] = .7f;
    m.update(bass, .03f, 1.6f);
    assertTrue(m.levels[3] > .1f);
    assertEquals(0, m.levels[45], 0);
  }

  @Test
  public void sensitivityIncreasesMotion() {
    float[] data = new float[48];
    Arrays.fill(data, .05f);
    SpectrumMotion low = new SpectrumMotion(), high = new SpectrumMotion();
    low.update(data, .05f, .5f);
    high.update(data, .05f, 3f);
    assertTrue(high.levels[10] > low.levels[10] * 1.4f);
  }
}
