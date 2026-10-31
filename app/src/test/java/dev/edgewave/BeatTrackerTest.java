package dev.edgewave;

import static org.junit.Assert.*;

import org.junit.Test;

public class BeatTrackerTest {
  BeatTracker train(int bpm, boolean misses) {
    BeatTracker tracker = new BeatTracker();
    int period = Math.round(60000f / bpm);
    for (int t = 0; t < 18000; t += 20) {
      float[] bands = new float[48];
      int cycle = t / period;
      float hit = misses && cycle % 7 == 6 ? 0 : (float) Math.exp(-(t % period) / 45f);
      for (int i = 0; i < 48; i++) bands[i] = .008f + hit * .42f * (float) Math.exp(-i / 18f);
      tracker.update(bands, t, true);
    }
    return tracker;
  }

  @Test
  public void detects120And90Bpm() {
    BeatTracker a = train(120, false), b = train(90, false);
    assertEquals(120, a.bpm, 3);
    assertEquals(90, b.bpm, 3);
    assertTrue(a.confidence > .7f);
  }

  @Test
  public void occasionalMissingKickDoesNotDoublePeriod() {
    assertEquals(120, train(120, true).bpm, 4);
  }

  @Test
  public void silenceAndSteadyToneNeverInventTempo() {
    BeatTracker tracker = new BeatTracker();
    for (int t = 0; t < 10000; t += 20) assertNull(tracker.update(new float[48], t, true));
    assertEquals(0, tracker.bpm, 0);
    float[] constant = new float[48];
    java.util.Arrays.fill(constant, .1f);
    for (int t = 10000; t < 15000; t += 20) tracker.update(constant, t, true);
    assertEquals(0, tracker.bpm, 0);
  }

  @Test
  public void retainsLockedTempoAcrossSilence() {
    BeatTracker tracker = train(120, false);
    for (int t = 18000; t < 23000; t += 20) tracker.update(new float[48], t, false);
    assertEquals(120, tracker.bpm, 3);
    assertTrue(tracker.locked);
  }

  @Test
  public void establishedTempoIsNotRecomputedDuringTheTrack() {
    BeatTracker tracker = train(120, false);
    assertTrue(tracker.locked);
    float locked = tracker.bpm;
    for (int t = 18000; t < 36000; t += 20) {
      float[] bands = new float[48];
      float kick = (float) Math.exp(-((t - 18000) % 667) / 45f);
      java.util.Arrays.fill(bands, kick * .2f + .008f);
      tracker.update(bands, t, true);
    }
    assertEquals(locked, tracker.bpm, 0);
    tracker.suspend();
    assertEquals(locked, tracker.bpm, 0);
    tracker.reset();
    assertFalse(tracker.locked);
    assertEquals(0, tracker.bpm, 0);
  }

  @Test
  public void detectsSmoothMidrangePulsationWithoutDrums() {
    BeatTracker tracker = new BeatTracker();
    for (int t = 0; t < 22000; t += 17) {
      float[] bands = new float[48];
      float energy = .08f + .05f * (float) Math.sin(2 * Math.PI * t / 625);
      for (int i = 16; i < 32; i++) bands[i] = energy;
      tracker.update(bands, t, true);
    }
    assertTrue(tracker.locked);
    assertEquals(96, tracker.bpm, 3);
  }

  @Test
  public void detectsAlternatingNotesWithConstantTotalEnergy() {
    BeatTracker tracker = new BeatTracker();
    for (int t = 0; t < 22000; t += 23) {
      float[] bands = new float[48];
      int note = (t / 500) % 4;
      for (int i = 16; i < 32; i++) bands[i] = .01f;
      bands[17 + note * 3] = .5f;
      tracker.update(bands, t, true);
    }
    assertTrue(tracker.locked);
    assertEquals(120, tracker.bpm, 4);
  }
}
