package dev.edgewave;

import android.graphics.Path;

/** Smooth shoulder transitions and a flat vertical middle, normalized to the user's dimensions. */
final class DentShape {
  static void trace(Path path, float d, float length, float center) {
    path.reset();
    path.moveTo(0, center - length);
    path.cubicTo(0, center - length * .66f, d, center - length * .66f, d, center - length * .28f);
    path.lineTo(d, center + length * .28f);
    path.cubicTo(d, center + length * .66f, 0, center + length * .66f, 0, center + length);
    path.close();
  }
}
