package dev.edgewave;

import android.content.SharedPreferences;

final class AppDefaults {
  static void apply(SharedPreferences p) {
    SharedPreferences.Editor e = p.edit();
    boolean changed = false;
    String[] keys = {
      "enterEffect", "exitEffect", "transitionMs", "bottomMode", "bottomOffset", "effectMode"
    };
    int[] values = {2, 2, 450, 1, 0, 1};
    for (int i = 0; i < keys.length; i++)
      if (!p.contains(keys[i])) {
        e.putInt(keys[i], values[i]);
        changed = true;
      }
    for (String key :
        new String[] {
          "volume",
          "fx_combos",
          "fx_line",
          "fx_trace",
          "fx_particles_top",
          "fx_particles_side",
          "fx_side",
          "fx_bottom",
          "fx_particles_bottom",
          "fx_frame",
          "fx_strings",
          "fx_duet"
        })
      if (!p.contains(key)) {
        e.putBoolean(key, false);
        changed = true;
      }
    if (changed) e.apply();
  }
}
