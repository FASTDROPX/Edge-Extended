package dev.edgewave;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class EffectsLogicTest {
  @Test
  public void transitionsHaveExactVisibleAndHiddenEndpoints() {
    for (int mode = 0; mode < 5; mode++)
      for (int x = 0; x <= 10; x++) {
        assertEquals(0, TransitionShape.y(mode, x / 10f, 90, 120, 0, 2), 0);
        assertEquals(90, TransitionShape.y(mode, x / 10f, 90, 120, 1, 2), 0);
        float middle = TransitionShape.y(mode, x / 10f, 90, 120, .5f, 2);
        assertTrue(Float.isFinite(middle) && middle >= 0 && middle <= 90);
      }
  }

  @Test
  public void schedulerChoosesOneEnabledAccentWithVarietyAndRest() {
    EffectScheduler scheduler = new EffectScheduler();
    Set<Integer> seen = new HashSet<>();
    int prior = -1, rests = 0;
    for (int i = 1; i <= 80; i++) {
      int choice = scheduler.choose(255, new BeatTracker.Beat(i * 500, .8f, i % 8 == 0, i), 120);
      if (choice < 0) {
        rests++;
        continue;
      }
      assertTrue(choice >= 0 && choice < 8);
      assertNotEquals(prior, choice);
      prior = choice;
      seen.add(choice);
    }
    assertTrue(seen.size() >= 6);
    assertTrue(rests > 0);
  }

  @Test
  public void epicEffectsNeverRunOnOrdinaryBeat() {
    EffectScheduler scheduler = new EffectScheduler();
    for (int i = 1; i < 40; i++)
      assertEquals(
          -1,
          scheduler.choose((1 << 1) | (1 << 7), new BeatTracker.Beat(i * 500, .5f, false, i), 120));
  }

  @Test
  public void disabledEffectsAndRapidDuplicateOnsetsAreIgnored() {
    EffectScheduler scheduler = new EffectScheduler();
    assertEquals(-1, scheduler.choose(0, new BeatTracker.Beat(1000, 1, true, 1)));
    assertEquals(2, scheduler.choose(1 << 2, new BeatTracker.Beat(1500, 1, false, 1)));
    assertEquals(-1, scheduler.choose(1 << 2, new BeatTracker.Beat(1520, 1, false, 2)));
  }
}
