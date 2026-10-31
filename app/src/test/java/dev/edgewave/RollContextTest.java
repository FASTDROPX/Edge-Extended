package dev.edgewave;

import static org.junit.Assert.*;

import org.junit.Test;

public class RollContextTest {
  @Test
  public void unrelatedNoiseDoesNotAccumulateIntoBuildup() {
    RollContext c = new RollContext();
    for (int t : new int[] {0, 1000, 2070, 3000, 4100, 5070}) c.observe(t);
    assertFalse(c.active(5100));
    assertEquals(1, c.hits);
    assertFalse(c.release(5200, true, true));
  }

  @Test
  public void consistentAccelerationArmsButRequiresBassAndBroadAttack() {
    RollContext c = new RollContext();
    for (int t : new int[] {0, 180, 340, 480, 600}) c.observe(t);
    assertTrue(c.active(650));
    assertTrue(c.release(750, true, true));
    assertFalse(c.release(750, false, true));
    assertFalse(c.release(750, true, false));
    assertFalse(c.release(2000, true, true));
  }

  @Test
  public void oneMissingHitCanBeTolerated() {
    RollContext c = new RollContext();
    for (int t : new int[] {0, 120, 240, 480, 600}) c.observe(t);
    assertTrue(c.active(650));
  }

  @Test
  public void loudCandidateIsNotPermissionForBottomPulse() {
    TrackPatterns.Event candidate =
        new TrackPatterns.Event(0, TrackPatterns.Type.DROP, 0, 1, 1, 1, .5f, false, false);
    assertEquals(0, MusicalDirector.choose(candidate, 1023, true) & (1 << 5));
    TrackPatterns.Event confirmed =
        new TrackPatterns.Event(0, TrackPatterns.Type.DROP, 0, 1, .6f, 1, .5f, true, false);
    assertEquals(1 << 5, MusicalDirector.choose(confirmed, 1023, false));
  }

  @Test
  public void realPcmSnareRollAloneNeverPermitsBottomAccent() {
    assertEquals(0, bottomAccents(0));
  }

  @Test
  public void realPcmQuietToBassTransitionCanConfirmBottomAccent() {
    assertTrue(bottomAccents(3) > 0);
  }

  int bottomAccents(int preset) {
    short[] pcm = DemoSounds.render(preset, 140, .8f);
    PcmSpectrum fft = new PcmSpectrum();
    TrackPatterns analyzer = new TrackPatterns();
    int accents = 0;
    for (int ms = 0; ms < 8000; ms += 20) {
      TrackPatterns.Event e = analyzer.analyze(fft.analyze(pcm, ms * 24, DemoSounds.RATE), ms, 140);
      if (e != null && e.bottomAccent) accents++;
    }
    return accents;
  }
}
