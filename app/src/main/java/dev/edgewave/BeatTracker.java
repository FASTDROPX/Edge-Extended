package dev.edgewave;

import java.util.Arrays;

/** Fast full-spectrum accents plus a separate six-channel periodicity estimator. */
final class BeatTracker {
  static final class Beat {
    final long time;
    final float strength;
    final boolean epic;
    final int index;

    Beat(long t, float s, boolean e, int i) {
      time = t;
      strength = s;
      epic = e;
      index = i;
    }
  }

  private final TempoEstimator tempo = new TempoEstimator();
  private final float[] previous = new float[48], silence = new float[48];
  private float mean = .004f,
      deviation = .004f,
      energyMean = .02f,
      energyDeviation = .005f,
      lastEnergy,
      slope;
  private long lastFrame = -1, lastBeat = -10000, lastSignal = -10000;
  private int count;
  volatile float bpm, confidence;
  boolean locked;

  Beat update(float[] bands, long now, boolean active) {
    if (lastFrame >= 0 && now - lastFrame > 3000) suspend();
    float dt = lastFrame < 0 ? .033f : Math.max(.005f, Math.min(.2f, (now - lastFrame) / 1000f));
    lastFrame = now;
    if (!locked) {
      tempo.add(active ? bands : silence, now);
      bpm = tempo.bpm;
      confidence = tempo.confidence;
      locked = tempo.ready;
    }
    if (!active) {
      Arrays.fill(previous, 0);
      slope = lastEnergy = 0;
      if (now - lastSignal > 3000 && !locked) {
        bpm = confidence = 0;
        tempo.clear();
      }
      return null;
    }
    float flux = 0, energy = 0;
    for (int i = 0; i < 48; i++) {
      float value = Math.max(0, bands[i]);
      float weight = i < 32 ? 1 : .9f;
      flux += Math.max(0, value - previous[i]) * weight / 48;
      energy += value / 48;
      previous[i] = value;
    }
    if (energy > .0005f) lastSignal = now;
    float threshold = Math.max(.0015f, mean * 1.5f + deviation * .6f);
    float nextSlope = slope + (energy - lastEnergy - slope) * (1 - (float) Math.exp(-dt * 12));
    boolean swellPeak =
        slope > 0
            && nextSlope <= 0
            && lastEnergy > energyMean + Math.max(.001f, energyDeviation * .35f);
    long minimumGap = (long) Math.max(220, bpm > 0 ? 60000 / bpm * .62f : 220);
    boolean hit = (flux > threshold || swellPeak) && energy > .001f && now - lastBeat >= minimumGap;
    float strength =
        Math.max(
            0,
            Math.min(
                1,
                Math.max(
                    flux / Math.max(.008f, threshold) * .32f,
                    swellPeak
                        ? (lastEnergy - energyMean) / Math.max(.003f, energyDeviation * 3)
                        : 0)));
    boolean epic =
        count >= 6 && energy > Math.max(.018f, energyMean * 1.4f) && flux > threshold * 1.65f;
    float blend = 1 - (float) Math.exp(-dt * 2);
    deviation += (Math.abs(flux - mean) - deviation) * blend;
    mean += (flux - mean) * blend;
    energyDeviation +=
        (Math.abs(energy - energyMean) - energyDeviation) * (1 - (float) Math.exp(-dt * 1.1f));
    energyMean += (energy - energyMean) * (1 - (float) Math.exp(-dt * .65f));
    lastEnergy = energy;
    slope = nextSlope;
    if (!hit) return null;
    lastBeat = now;
    count++;
    return new Beat(now, strength, epic, count);
  }

  void restore(float value) {
    reset();
    bpm = value;
    confidence = 1;
    locked = true;
  }

  void suspend() {
    Arrays.fill(previous, 0);
    lastFrame = -1;
    lastBeat = -10000;
    mean = deviation = .004f;
    energyMean = .02f;
    energyDeviation = .005f;
    slope = lastEnergy = 0;
    if (!locked) {
      bpm = confidence = 0;
      tempo.clear();
    }
  }

  void reset() {
    locked = false;
    Arrays.fill(previous, 0);
    tempo.clear();
    bpm = confidence = 0;
    mean = deviation = .004f;
    energyMean = .02f;
    energyDeviation = .005f;
    slope = lastEnergy = 0;
    lastBeat = -10000;
    lastFrame = -1;
    lastSignal = -10000;
    count = 0;
  }
}
