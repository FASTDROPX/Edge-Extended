package dev.edgewave;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class TrackPatternsTest {
  float[] snare(float value) {
    float[] b = new float[48];
    for (int i = 18; i < 45; i++) b[i] = value * (.8f + .2f * (i % 3) / 2);
    return b;
  }

  List<TrackPatterns.Event> roll(float gain) {
    TrackPatterns p = new TrackPatterns();
    List<TrackPatterns.Event> events = new ArrayList<>();
    for (int t = 0; t < 2400; t += 10) {
      float env = (float) Math.exp(-(t % 120) / 17f);
      TrackPatterns.Event e = p.analyze(snare(gain * env), t, 125);
      if (e != null) events.add(e);
    }
    return events;
  }

  @Test
  public void fastIdenticalHitsStayInSameVoiceAndEachGetAnEvent() {
    List<TrackPatterns.Event> events = roll(.25f);
    assertTrue(events.size() >= 18);
    int voice = events.get(0).voice, rolls = 0;
    for (TrackPatterns.Event e : events) {
      assertEquals(voice, e.voice);
      if (e.type == TrackPatterns.Type.ROLL) {
        rolls++;
        assertTrue(e.subdivision >= 3);
      }
    }
    assertTrue(rolls >= 16);
  }

  @Test
  public void soundRoleSurvivesLargeGainChange() {
    List<TrackPatterns.Event> loud = roll(.5f), quiet = roll(.06f);
    assertTrue(quiet.size() >= 17);
    for (int i = 2; i < Math.min(loud.size(), quiet.size()); i++)
      assertEquals(loud.get(i).type, quiet.get(i).type);
  }

  @Test
  public void gradualGainAndSteadyToneDoNotInventDrumRoll() {
    TrackPatterns p = new TrackPatterns();
    int events = 0;
    for (int t = 0; t < 4000; t += 20) {
      float[] b = snare(.15f * (1 - t / 8000f));
      TrackPatterns.Event e = p.analyze(b, t, 120);
      if (e != null) {
        events++;
        assertNotEquals(TrackPatterns.Type.ROLL, e.type);
      }
    }
    assertTrue(events <= 1);
  }

  @Test
  public void sameLevelDifferentSpectraChooseDifferentRoles() {
    TrackPatterns a = new TrackPatterns(), b = new TrackPatterns();
    float[] tonal = new float[48];
    tonal[27] = .4f;
    TrackPatterns.Event lead = a.analyze(tonal, 0, 120), noise = b.analyze(snare(.4f / 24), 0, 120);
    assertEquals(TrackPatterns.Type.TONAL, lead.type);
    assertEquals(TrackPatterns.Type.SNARE, noise.type);
  }

  @Test
  public void dropRequiresSpectrumAndQuietContext() {
    TrackPatterns p = new TrackPatterns();
    for (int t = 0; t < 1000; t += 20) p.analyze(new float[48], t, 140);
    float[] drop = snare(.12f);
    for (int i = 0; i < 14; i++) drop[i] = .12f;
    assertEquals(TrackPatterns.Type.DROP, p.analyze(drop, 1000, 140).type);
    TrackPatterns q = new TrackPatterns();
    for (int t = 0; t < 1000; t += 20) q.analyze(new float[48], t, 140);
    float[] tone = new float[48];
    tone[20] = 1;
    assertNotEquals(TrackPatterns.Type.DROP, q.analyze(tone, 1000, 140).type);
  }

  @Test
  public void acousticDirectorIsConsistentAndRespectsDropExclusions() {
    int all = (1 << 10) - 1;
    for (int voice = 0; voice < 10; voice++) {
      TrackPatterns.Event e =
          new TrackPatterns.Event(0, TrackPatterns.Type.ROLL, voice, 4, .8f, 1, .6f);
      assertEquals(1, MusicalDirector.choose(e, all, true));
    }
    int drop =
        MusicalDirector.choose(
            new TrackPatterns.Event(0, TrackPatterns.Type.DROP, 0, 1, 1, 1, .5f), all, true);
    assertEquals(0, drop & ((1 << 1) | (1 << 4)));
    assertEquals(0, drop & (1 << 9));
    assertTrue((drop & (1 << 8)) != 0);
    assertEquals(
        1,
        Integer.bitCount(
            MusicalDirector.choose(
                new TrackPatterns.Event(0, TrackPatterns.Type.DROP, 0, 1, 1, 1, .5f), all, false)));
  }

  @Test
  public void actualPcmDemosAreInterpretedWithoutAnyTimelineOfEffects() {
    TrackPatterns.Type[] expected = {
      TrackPatterns.Type.ROLL,
      TrackPatterns.Type.TONAL,
      TrackPatterns.Type.GLIDE,
      TrackPatterns.Type.DROP
    };
    for (int preset = 0; preset < 4; preset++) {
      short[] audio = DemoSounds.render(preset, 140, .8f);
      PcmSpectrum spectrum = new PcmSpectrum();
      TrackPatterns analyzer = new TrackPatterns();
      int count = 0;
      for (int ms = 0; ms < 8000; ms += 20) {
        TrackPatterns.Event e =
            analyzer.analyze(spectrum.analyze(audio, ms * 24, DemoSounds.RATE), ms, 140);
        if (e != null && e.type == expected[preset]) count++;
      }
      assertTrue("missing acoustic pattern in PCM preset " + preset, count > 0);
    }
  }
}
