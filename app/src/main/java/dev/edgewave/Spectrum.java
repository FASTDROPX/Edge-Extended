package dev.edgewave;

/** Hann-windowed radix-2 FFT; log-spaced bands from bass at left to treble at right. */
final class Spectrum {
  static float[] analyze(short[] input, int count) {
    int n = 2048;
    double[] re = new double[n], im = new double[n];
    for (int i = 0; i < Math.min(n, count); i++)
      re[i] = input[i] / 32768.0 * (.5 - .5 * Math.cos(2 * Math.PI * i / (n - 1)));
    for (int i = 1, j = 0; i < n; i++) {
      int bit = n >> 1;
      for (; (j & bit) != 0; bit >>= 1) j ^= bit;
      j ^= bit;
      if (i < j) {
        double t = re[i];
        re[i] = re[j];
        re[j] = t;
      }
    }
    for (int len = 2; len <= n; len <<= 1) {
      double ang = -2 * Math.PI / len;
      for (int i = 0; i < n; i += len) {
        double wr = 1, wi = 0;
        for (int j = 0; j < len / 2; j++) {
          int u = i + j, v = u + len / 2;
          double vr = re[v] * wr - im[v] * wi, vi = re[v] * wi + im[v] * wr;
          re[v] = re[u] - vr;
          im[v] = im[u] - vi;
          re[u] += vr;
          im[u] += vi;
          double nw = wr * Math.cos(ang) - wi * Math.sin(ang);
          wi = wr * Math.sin(ang) + wi * Math.cos(ang);
          wr = nw;
        }
      }
    }
    float[] out = new float[48];
    for (int b = 0; b < 48; b++) {
      int lo = Math.max(1, (int) (45 * Math.pow(16000.0 / 45, b / 48.0) * n / 44100));
      int hi =
          Math.min(
              n / 2 - 1,
              Math.max(lo + 1, (int) (45 * Math.pow(16000.0 / 45, (b + 1) / 48.0) * n / 44100)));
      double peak = 0;
      for (int k = lo; k <= hi; k++) peak = Math.max(peak, Math.hypot(re[k], im[k]) * 4 / n);
      out[b] = (float) Math.max(0, Math.min(1, (20 * Math.log10(peak + 1e-8) + 70) / 70));
    }
    return out;
  }
}
