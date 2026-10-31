package dev.edgewave;

import static org.junit.Assert.*;

import android.content.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "ru-rRU")
public class TempoMemoryTest {
  @Test
  public void tempoIsSavedAndRestoredPerTrack() {
    SharedPreferences p = RuntimeEnvironment.getApplication().getSharedPreferences("tempo-test", 0);
    TempoMemory memory = new TempoMemory(p);
    BeatTracker tracker = new BeatTracker();
    memory.select("song-a", tracker);
    tracker.restore(123);
    memory.save(tracker);
    memory.select("song-b", tracker);
    assertFalse(tracker.locked);
    tracker.restore(92);
    memory.save(tracker);
    memory.select("song-a", tracker);
    assertTrue(tracker.locked);
    assertEquals(123, tracker.bpm, 0);
    memory.recalculate(tracker);
    assertFalse(tracker.locked);
    assertFalse(p.contains("tempo_v2_song-a"));
    memory.select("song-b", tracker);
    assertEquals(92, tracker.bpm, 0);
  }

  @Test
  public void cacheSurvivesEngineRecreationAndSameTrackDoesNotReset() {
    SharedPreferences p =
        RuntimeEnvironment.getApplication().getSharedPreferences("tempo-restart", 0);
    BeatTracker tracker = new BeatTracker();
    TempoMemory first = new TempoMemory(p);
    first.select("track", tracker);
    tracker.restore(130);
    first.save(tracker);
    TempoMemory second = new TempoMemory(p);
    BeatTracker restored = new BeatTracker();
    second.select("track", restored);
    second.select("track", restored);
    assertEquals(130, restored.bpm, 0);
    assertTrue(restored.locked);
  }
}
