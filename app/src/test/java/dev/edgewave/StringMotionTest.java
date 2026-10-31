package dev.edgewave;

import static org.junit.Assert.*;

import org.junit.Test;

public class StringMotionTest {
  EffectsEngine.Fx fx(float seed, float seed2) {
    return new EffectsEngine.Fx(1, 8, 0, 1500, .8f, 0, seed, seed2, new float[48]);
  }

  @Test
  public void bothDirectionsAndBothOrdersAreAvailable() {
    assertTrue(StringMotion.rightToLeft(fx(.1f, .1f)));
    assertFalse(StringMotion.rightToLeft(fx(.8f, .1f)));
    assertEquals(0, StringMotion.rank(fx(.1f, .1f), 0));
    assertEquals(2, StringMotion.rank(fx(.1f, .8f), 0));
  }

  @Test
  public void linesRevealInOrderWith45MillisecondStagger() {
    EffectsEngine.Fx f = fx(.8f, .1f);
    assertTrue(StringMotion.reveal(f, 0, .02f) > 0);
    assertEquals(0, StringMotion.reveal(f, 1, .02f), 0);
    assertEquals(0, StringMotion.reveal(f, 2, .02f), 0);
    assertTrue(StringMotion.reveal(f, 1, .04f) > 0);
    assertEquals(0, StringMotion.reveal(f, 2, .04f), 0);
    for (int i = 0; i < 3; i++) assertEquals(1, StringMotion.reveal(f, i, .4f), 0);
  }

  @Test
  public void allThreeSeparationPatternsAndMildDeformationAreBounded() {
    for (int mode = 0; mode < 3; mode++) {
      EffectsEngine.Fx f = fx(.7f, (mode + .1f) / 6);
      assertEquals(mode, StringMotion.mode(f));
      if (mode == 0) assertEquals(0, StringMotion.separation(f, 1, 1), 0);
      if (mode == 1) assertEquals(0, StringMotion.separation(f, 0, 1), 0);
      if (mode == 2) assertEquals(0, StringMotion.separation(f, 2, 1), 0);
      float top = StringMotion.separation(f, 0, 1),
          mid = StringMotion.separation(f, 1, 1),
          bottom = StringMotion.separation(f, 2, 1);
      assertTrue(top < mid && mid < bottom);
      if (mode == 0) assertEquals(mid - top, bottom - mid, .001f);
      if (mode == 1) assertTrue(mid - top > bottom - mid);
      if (mode == 2) assertTrue(mid - top < bottom - mid);
      for (int line = 0; line < 3; line++)
        for (int i = 0; i <= 100; i++) {
          float x = i / 100f;
          assertTrue(Math.abs(StringMotion.bend(f, x, line, .5f)) <= 1.3f);
          assertEquals(0, StringMotion.bend(f, x, line, 0), .00001f);
        }
    }
  }

  @Test
  public void bottomPulseHasBroadSmoothPeak() {
    assertEquals(0, EffectsPainter.bottomPulse(0), 0);
    assertEquals(0, EffectsPainter.bottomPulse(1), 0);
    assertTrue(EffectsPainter.bottomPulse(.012f) < EffectsPainter.bottomPulse(.025f));
    assertTrue(EffectsPainter.bottomPulse(.3f) > .5f);
    assertTrue(
        Math.abs(EffectsPainter.bottomPulse(.0249f) - EffectsPainter.bottomPulse(.0251f)) < .0001f);
  }
}
