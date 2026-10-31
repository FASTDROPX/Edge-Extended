package dev.edgewave;

public class SpectrumCheck {
  static int peak(double frequency) {
    short[] s = new short[2048];
    for (int i = 0; i < s.length; i++)
      s[i] = (short) (20000 * Math.sin(2 * Math.PI * frequency * i / 44100));
    float[] f = Spectrum.analyze(s, s.length);
    int best = 0;
    for (int i = 0; i < f.length; i++) {
      if (!Float.isFinite(f[i]) || f[i] < 0 || f[i] > 1) throw new AssertionError("invalid band");
      if (f[i] > f[best]) best = i;
    }
    return best;
  }

  public static void main(String[] args) {
    float[] silence = Spectrum.analyze(new short[2048], 2048);
    for (float x : silence) if (x != 0) throw new AssertionError("silence");
    int bass = peak(100), treble = peak(8000);
    if (bass > 12 || treble < 35 || bass >= treble) throw new AssertionError("frequency order");
    System.out.println(
        "PASS: silence, finite normalized bands, bass left ("
            + bass
            + "), treble right ("
            + treble
            + ")");
  }
}
