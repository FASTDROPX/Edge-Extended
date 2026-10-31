package dev.edgewave;

import java.util.Arrays;

/** Six-channel, uniformly resampled tempogram. Analysed once a second, only until tempo locks. */
final class TempoEstimator {
  private static final int CHANNELS = 6, CAPACITY = 640, MAX_LAG = 68, STEP = 20;
  private final float[][] history = new float[CHANNELS][CAPACITY],
      work = new float[CHANNELS][CAPACITY],
      acf = new float[CHANNELS][MAX_LAG + 1];
  private final float[] previousBands = new float[48],
      lastFeatures = new float[CHANNELS],
      features = new float[CHANNELS];
  private int count, head, stable;
  private long previousTime = -1, nextSample, lastAnalysis = -10000;
  private float lastCandidate;
  float bpm, confidence;
  boolean ready;

  void add(float[] bands, long now) {
    Arrays.fill(features, 0);
    for (int i = 0; i < 48; i++) {
      int group = i / 16;
      float value = Math.max(0, bands[i]);
      features[group] += value / 16;
      features[3 + group] += Math.max(0, value - previousBands[i]) / 16;
      previousBands[i] = value;
    }
    if (previousTime < 0) {
      previousTime = now;
      nextSample = now;
      System.arraycopy(features, 0, lastFeatures, 0, CHANNELS);
    }
    if (now - previousTime > 1500) {
      clear();
      previousTime = now;
      nextSample = now;
      System.arraycopy(features, 0, lastFeatures, 0, CHANNELS);
    }
    while (nextSample <= now) {
      float mix =
          now == previousTime
              ? 1
              : Math.max(
                  0, Math.min(1, (nextSample - previousTime) / (float) (now - previousTime)));
      for (int ch = 0; ch < CHANNELS; ch++)
        history[ch][head] = lastFeatures[ch] + (features[ch] - lastFeatures[ch]) * mix;
      head = (head + 1) % CAPACITY;
      count = Math.min(CAPACITY, count + 1);
      nextSample += STEP;
    }
    previousTime = now;
    System.arraycopy(features, 0, lastFeatures, 0, CHANNELS);
    if (count >= 300 && now - lastAnalysis >= 1000) {
      lastAnalysis = now;
      analyse();
    }
  }

  private void analyse() {
    for (int ch = 0; ch < CHANNELS; ch++) {
      float mean = 0;
      for (int i = 0; i < count; i++) {
        float value = history[ch][(head - count + i + CAPACITY) % CAPACITY];
        work[ch][i] = value;
        mean += value / count;
      }
      float variance = 0;
      for (int i = 0; i < count; i++) {
        work[ch][i] -= mean;
        variance += work[ch][i] * work[ch][i];
      }
      Arrays.fill(acf[ch], 0);
      if (variance / count < 1e-9f) continue;
      for (int lag = 2; lag <= MAX_LAG; lag++) {
        float xy = 0, xx = 0, yy = 0;
        for (int i = lag; i < count; i++) {
          float a = work[ch][i], b = work[ch][i - lag];
          xy += a * b;
          xx += a * a;
          yy += b * b;
        }
        acf[ch][lag] = xx * yy > 1e-16f ? xy / (float) Math.sqrt(xx * yy) : 0;
      }
    }
    float best = -1, bestLag = 0, bestRaw = 0, bestContrast = 0;
    for (int tempo = 60; tempo <= 200; tempo++) {
      float lag = 60000f / (STEP * tempo),
          raw = correlation(lag),
          contrast = raw - Math.max(correlation(lag * .8f), correlation(lag * 1.2f));
      if (raw < .52f || contrast < .055f) continue;
      float score =
          raw + contrast * .18f - correlation(lag / 2) * .45f - correlation(lag / 3) * .2f;
      if (score > best) {
        best = score;
        bestLag = lag;
        bestRaw = raw;
        bestContrast = contrast;
      }
    }
    if (bestLag == 0) {
      confidence = 0;
      stable = 0;
      return;
    }
    int peak = Math.max(3, Math.min(MAX_LAG - 1, Math.round(bestLag)));
    float a = correlation(peak - 1),
        b = correlation(peak),
        c = correlation(peak + 1),
        denominator = a - 2 * b + c;
    float adjustment = Math.abs(denominator) > .000001f ? .5f * (a - c) / denominator : 0;
    adjustment = Math.max(-.5f, Math.min(.5f, adjustment));
    float candidate = 60000f / (STEP * (peak + adjustment));
    candidate = Math.max(60, Math.min(200, candidate));
    confidence = Math.max(0, Math.min(1, bestRaw * .7f + Math.min(1, bestContrast / .45f) * .3f));
    stable =
        lastCandidate > 0
                && Math.abs(candidate - lastCandidate) < Math.max(3, lastCandidate * .025f)
            ? stable + 1
            : 1;
    lastCandidate = candidate;
    bpm = bpm == 0 ? candidate : bpm + (candidate - bpm) * .5f;
    ready = stable >= 3 && confidence > .76f;
  }

  private float correlation(float lag) {
    if (lag < 2 || lag > MAX_LAG) return 0;
    int lo = (int) lag, hi = Math.min(MAX_LAG, lo + 1);
    float mix = lag - lo, first = 0, second = 0, third = 0;
    for (int ch = 0; ch < CHANNELS; ch++) {
      float value = Math.max(0, acf[ch][lo] + (acf[ch][hi] - acf[ch][lo]) * mix);
      if (value > first) {
        third = second;
        second = first;
        first = value;
      } else if (value > second) {
        third = second;
        second = value;
      } else if (value > third) third = value;
    }
    float weight = .6f + (second > 0 ? .3f : 0) + (third > 0 ? .1f : 0);
    return (first * .6f + second * .3f + third * .1f) / weight;
  }

  void clear() {
    for (float[] channel : history) Arrays.fill(channel, 0);
    Arrays.fill(previousBands, 0);
    Arrays.fill(lastFeatures, 0);
    previousTime = -1;
    nextSample = 0;
    lastAnalysis = -10000;
    count = head = stable = 0;
    lastCandidate = bpm = confidence = 0;
    ready = false;
  }
}
