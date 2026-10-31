package dev.edgewave;

final class WaveSmoothing {
  private static final float[] LIVE = new float[5], CLASSIC = new float[9];

  static {
    for (int i = -2; i <= 2; i++) LIVE[i + 2] = (float) Math.exp(-i * i / 2.2);
    for (int i = -4; i <= 4; i++) CLASSIC[i + 4] = (float) Math.exp(-i * i / 7.0);
  }

  static void prepare(float[] input, float[] out, boolean classic) {
    float[] weights = classic ? CLASSIC : LIVE;
    int radius = weights.length / 2;
    for (int i = 0; i < input.length; i++) {
      float sum = 0, total = 0;
      for (int k = -radius; k <= radius; k++) {
        float w = weights[k + radius];
        sum += input[Math.max(0, Math.min(input.length - 1, i + k))] * w;
        total += w;
      }
      out[i] = sum / total;
    }
  }

  static float at(float[] data, float along) {
    float x = Math.max(0, Math.min(1, along)) * (data.length - 1);
    int a = (int) x, b = Math.min(data.length - 1, a + 1);
    return data[a] + (data[b] - data[a]) * (x - a);
  }
}
