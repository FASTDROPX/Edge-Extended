package dev.edgewave;

public class AudioSpectrumCheck {
  static int peak(int bin) {
    byte[] fft = new byte[1024];
    fft[2 * bin] = 100;
    float[] bands = AudioSpectrum.decode(fft, 48000);
    int best = 0;
    for (int i = 0; i < bands.length; i++) {
      if (!Float.isFinite(bands[i]) || bands[i] < 0 || bands[i] > 1)
        throw new AssertionError("invalid band");
      if (bands[i] > bands[best]) best = i;
    }
    return best;
  }

  public static void main(String[] args) {
    for (float f : AudioSpectrum.decode(new byte[1024], 48000))
      if (f != 0) throw new AssertionError("silence");
    int bass = peak(2), treble = peak(170);
    if (bass > 12 || treble < 35) throw new AssertionError("frequency direction");
    System.out.println(
        "PASS Visualizer FFT: silence; finite bands; bass left="
            + bass
            + ", treble right="
            + treble);
  }
}
