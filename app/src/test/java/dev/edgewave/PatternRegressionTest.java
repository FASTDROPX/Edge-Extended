package dev.edgewave;

import static org.junit.Assert.*;

import org.junit.Test;

public class PatternRegressionTest {
  @Test
  public void bottomPulseAndTrailAreNotFallbacksForSustainedTonesOrOrdinaryKicks() {
    int all = (1 << EffectsEngine.COUNT) - 1;
    for (TrackPatterns.Type type : TrackPatterns.Type.values()) {
      if (type == TrackPatterns.Type.DROP) continue;
      int chosen =
          MusicalDirector.choose(new TrackPatterns.Event(0, type, 0, 1, .8f, 1, .5f), all, true);
      assertEquals(0, chosen & (1 << 5));
      if (type != TrackPatterns.Type.ROLL && type != TrackPatterns.Type.SNARE)
        assertEquals(0, chosen & 1);
    }
    assertEquals(
        0,
        MusicalDirector.choose(
            new TrackPatterns.Event(0, TrackPatterns.Type.TONAL, 0, 1, 1, 1, .5f), 1, true));
  }

  @Test
  public void acceleratingRealPcmKeepsMostHits() {
    short[] pcm = DemoSounds.render(0, 140, .8f);
    PcmSpectrum fft = new PcmSpectrum();
    TrackPatterns patterns = new TrackPatterns();
    int hits = 0;
    for (int ms = 2500; ms < 5000; ms += 10) {
      TrackPatterns.Event e = patterns.analyze(fft.analyze(pcm, ms * 24, 24000), ms, 140);
      if (e != null && (e.type == TrackPatterns.Type.ROLL || e.type == TrackPatterns.Type.SNARE))
        hits++;
    }
    assertTrue("accelerated section lost hits: " + hits, hits >= 21);
    assertTrue("extra triggers: " + hits, hits <= 28);
  }

  @Test
  public void independentHighBandAttacksSurviveLoudSteadyBass() {
    TrackPatterns p = new TrackPatterns();
    int found = 0;
    for (int t = 0; t < 2400; t += 10) {
      float[] b = new float[48];
      for (int i = 0; i < 16; i++) b[i] = .8f;
      b[24] = .3f;
      for (int i = 26; i < 46; i++) b[i] = .03f * (float) Math.exp(-(t % 120) / 18f);
      TrackPatterns.Event e = p.analyze(b, t, 125);
      if (t >= 120
          && e != null
          && (e.type == TrackPatterns.Type.SNARE || e.type == TrackPatterns.Type.ROLL)) found++;
    }
    assertTrue("snare lost under bass: " + found, found >= 17);
  }

  @Test
  public void changingBassContinuesToProduceGlideEvents() {
    short[] pcm = DemoSounds.render(2, 140, .8f);
    PcmSpectrum fft = new PcmSpectrum();
    TrackPatterns patterns = new TrackPatterns();
    int glides = 0;
    long previous = -1, maxGap = 0;
    for (int ms = 0; ms < 8000; ms += 15) {
      TrackPatterns.Event e = patterns.analyze(fft.analyze(pcm, ms * 24, 24000), ms, 140);
      if (e != null && e.type == TrackPatterns.Type.GLIDE) {
        glides++;
        if (previous >= 0) maxGap = Math.max(maxGap, ms - previous);
        previous = ms;
      }
    }
    assertTrue(glides >= 25);
    assertTrue("large unexplained gaps: " + maxGap, maxGap < 1000);
  }
}
