package dev.edgewave;

import java.util.Random;

/** Audio only. No effect IDs, visual timeline or analyzer access. */
final class DemoSounds {
  private static final double[] NOTES = {440, 554.365, 659.255, 830.61, 587.33};
  static final int RATE = 24000, SECONDS = 20;
  static final String[] NAMES = {
    "Дроби и ускорение",
    "Мелодические атаки",
    "Переливы баса",
    "Затишье → плотный фрагмент",
    "Смешанный рисунок"
  };

  static short[] render(int preset, float bpm, float gain) {
    short[] pcm = new short[RATE * SECONDS];
    Random noise = new Random(1207 + preset);
    double phase = 0;
    float beat = 60 / bpm;
    for (int i = 0; i < pcm.length; i++) {
      double t = i / (double) RATE;
      int section = preset == 4 ? (int) (t / 5) % 4 : preset;
      double v = 0;
      if (section == 0) {
        double spacing = beat / (t % 5 < 2.5 ? 2 : 4), age = t % spacing;
        v =
            (noise.nextDouble() * 2 - 1) * Math.exp(-age / 0.025) * .48
                + Math.sin(2 * Math.PI * 185 * age) * Math.exp(-age / .04) * .19;
      } else if (section == 1) {
        double age = t % (beat / 2);
        int note = (int) (t / (beat / 2)) % 5;
        double frequency = NOTES[note];
        v =
            (Math.sin(2 * Math.PI * frequency * t)
                    + .28 * Math.sin(4 * Math.PI * frequency * t)
                    + .13 * Math.sin(6 * Math.PI * frequency * t))
                * .28
                * Math.exp(-age / .12)
                * Math.min(1, age / .004);
      } else if (section == 2) {
        double frequency =
            110 * Math.pow(2, 1.7 * (.5 + .5 * Math.sin(2 * Math.PI * t / (beat * 2))));
        phase += 2 * Math.PI * frequency / RATE;
        v = .24 * Math.sin(phase) + .12 * Math.sin(phase * 3) + .08 * Math.sin(phase * 5);
      } else {
        double cycle = t % 5;
        if (cycle < 1.8) v = .0015 * Math.sin(2 * Math.PI * 220 * t);
        else {
          double age = (cycle - 1.8) % beat, roll = (cycle - 1.8) % (beat / 2);
          v =
              .43
                      * Math.sin(2 * Math.PI * (65 + 55 * Math.exp(-age * 25)) * age)
                      * Math.exp(-age / .1)
                  + .32 * (noise.nextDouble() * 2 - 1) * Math.exp(-roll / .065)
                  + .19 * Math.sin(2 * Math.PI * 660 * t) * Math.exp(-roll / .17);
        }
      }
      double edge = Math.min(1, Math.min(t / .008, (SECONDS - t) / .025));
      pcm[i] = (short) (Math.tanh(v * gain) * edge * 29000);
    }
    return pcm;
  }
}
