package dev.edgewave;

/** Slow gain adaptation preserves short transients; attack and release are independent. */
final class SpectrumMotion {
  final float[] levels = new float[48];
  private final float[] average = new float[48];
  private float reference = .18f;
  float beat;

  void update(float[] raw, float dt, float sensitivity) {
    float peak = 0;
    for (float value : raw) peak = Math.max(peak, value);
    reference +=
        (peak - reference) * (1 - (float) Math.exp(-dt * (peak > reference ? 1.4f : .35f)));
    float gain = Math.min(10, sensitivity * .65f / Math.max(.09f, reference));
    float flux = 0;
    float attack = 1 - (float) Math.exp(-dt * 42),
        release = 1 - (float) Math.exp(-dt * 10),
        follow = 1 - (float) Math.exp(-dt * 2.4f);
    for (int i = 0; i < levels.length; i++) {
      float signal = raw[i] < .004f ? 0 : 1 - (float) Math.exp(-raw[i] * gain);
      float rise = Math.max(0, signal - average[i]);
      average[i] += (signal - average[i]) * follow;
      float target = Math.min(1.3f, signal * .85f + rise * 1.8f);
      levels[i] += (target - levels[i]) * (target > levels[i] ? attack : release);
      if (i < 16) flux += rise / 16;
    }
    beat = Math.max(Math.min(1, flux * 2.5f), beat * (float) Math.exp(-dt * 8));
  }
}
