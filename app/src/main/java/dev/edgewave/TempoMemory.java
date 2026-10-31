package dev.edgewave;

import android.content.SharedPreferences;

final class TempoMemory {
  final SharedPreferences prefs;
  String key = "";
  boolean saved;

  TempoMemory(SharedPreferences p) {
    prefs = p;
  }

  void select(String track, BeatTracker tracker) {
    if (track == null || track.isEmpty() || track.equals(key)) return;
    save(tracker);
    key = track;
    saved = false;
    tracker.reset();
    float stored = prefs.getFloat("tempo_v2_" + key, 0);
    if (stored >= 60 && stored <= 200) {
      tracker.restore(stored);
      saved = true;
    }
  }

  void save(BeatTracker tracker) {
    if (!saved && !key.isEmpty() && tracker.locked) {
      prefs.edit().putFloat("tempo_v2_" + key, tracker.bpm).apply();
      saved = true;
    }
  }

  void recalculate(BeatTracker tracker) {
    if (!key.isEmpty()) prefs.edit().remove("tempo_v2_" + key).apply();
    saved = false;
    tracker.reset();
  }
}
