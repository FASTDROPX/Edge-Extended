package dev.edgewave;

/**
 * Hann-window STFT frontend for the test player. Output matches the live 48 log-frequency bands.
 */
final class PcmSpectrum {
  static final int SIZE = 2048;
  private final float[] re = new float[SIZE], im = new float[SIZE], window = new float[SIZE];

  PcmSpectrum() {
    for (int i = 0; i < SIZE; i++)
      window[i] = .5f - .5f * (float) Math.cos(2 * Math.PI * i / (SIZE - 1));
  }

  float[] analyze(short[] pcm, int end, int rate) {
    for (int i = 0; i < SIZE; i++) {
      int index = end - SIZE + i;
      re[i] = (index >= 0 && index < pcm.length ? pcm[index] / 32768f : 0) * window[i];
      im[i] = 0;
    }
    for (int i = 1, j = 0; i < SIZE; i++) {
      int bit = SIZE >> 1;
      for (; (j & bit) != 0; bit >>= 1) j ^= bit;
      j ^= bit;
      if (i < j) {
        float v = re[i];
        re[i] = re[j];
        re[j] = v;
      }
    }
    for (int len = 2; len <= SIZE; len <<= 1) {
      float wr = (float) Math.cos(-2 * Math.PI / len), wi = (float) Math.sin(-2 * Math.PI / len);
      for (int start = 0; start < SIZE; start += len) {
        float ar = 1, ai = 0;
        for (int k = 0; k < len / 2; k++) {
          int a = start + k, b = a + len / 2;
          float br = re[b] * ar - im[b] * ai, bi = re[b] * ai + im[b] * ar;
          re[b] = re[a] - br;
          im[b] = im[a] - bi;
          re[a] += br;
          im[a] += bi;
          float next = ar * wr - ai * wi;
          ai = ar * wi + ai * wr;
          ar = next;
        }
      }
    }
    float[] bands = new float[48];
    for (int b = 0; b < 48; b++) {
      int lo = Math.max(1, (int) (45 * Math.pow(16000.0 / 45, b / 48.0) * SIZE / rate)),
          hi =
              Math.min(
                  SIZE / 2 - 1,
                  Math.max(lo, (int) (45 * Math.pow(16000.0 / 45, (b + 1) / 48.0) * SIZE / rate)));
      float peak = 0;
      for (int k = lo; k <= hi; k++)
        peak = Math.max(peak, (float) Math.hypot(re[k], im[k]) * 4 / SIZE);
      bands[b] = Math.min(1, peak);
    }
    return bands;
  }
}
