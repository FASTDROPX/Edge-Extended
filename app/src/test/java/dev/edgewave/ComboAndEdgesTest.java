package dev.edgewave;

import static org.junit.Assert.*;

import android.content.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "ru-rRU")
public class ComboAndEdgesTest {
  @Test
  public void pairsAlternateWithSingleEffects() {
    Random random = new Random(57);
    int solos = 0, pairs = 0;
    for (int i = 0; i < 100; i++) {
      int second = ComboPlanner.partner(3, (1 << 3) | (1 << 4), true, random);
      if (second < 0) solos++;
      else {
        assertEquals(4, second);
        pairs++;
      }
    }
    assertTrue(solos > 20 && pairs > 20);
  }

  @Test
  public void everyCompatiblePairIsPossibleAndForbiddenPairNeverOccurs() {
    for (int a = 0; a < 8; a++)
      for (int b = 0; b < 8; b++)
        if (a != b) {
          Random random = new Random(91);
          boolean seen = false;
          for (int i = 0; i < 100; i++) {
            int second = ComboPlanner.partner(a, (1 << a) | (1 << b), true, random);
            assertTrue(second == -1 || second == b);
            seen |= second == b;
          }
          assertEquals(ComboPlanner.compatible(a, b), seen);
        }
  }

  @Test
  public void disabledCombinationsAlwaysRemainSolo() {
    for (int i = 0; i < 8; i++)
      assertEquals(-1, ComboPlanner.partner(i, 255, false, new Random(i)));
  }

  @Test
  public void forbiddenSideEffectsCannotOverlapEvenAcrossDifferentBeats() {
    SharedPreferences p = RuntimeEnvironment.getApplication().getSharedPreferences("conflicts", 0);
    EffectsEngine e = new EffectsEngine(p);
    e.spawn(1, 1000, .8f, new float[48]);
    e.spawn(4, 1100, .8f, new float[48]);
    assertEquals(1, e.items.size());
    assertEquals(0, e.compatibleMask(1 << 4));
    e.items.clear();
    e.spawn(4, 2000, .8f, new float[48]);
    e.spawn(1, 2100, .8f, new float[48]);
    assertEquals(1, e.items.size());
    assertEquals(0, e.compatibleMask(1 << 1));
  }

  @Test
  public void allFourEdgeDirectionPatternsAreSupported() {
    assertFalse(EdgeSweep.upward(.1f, 0));
    assertFalse(EdgeSweep.upward(.1f, 1));
    assertTrue(EdgeSweep.upward(.35f, 0));
    assertTrue(EdgeSweep.upward(.35f, 1));
    assertFalse(EdgeSweep.upward(.6f, 0));
    assertTrue(EdgeSweep.upward(.6f, 1));
    assertTrue(EdgeSweep.upward(.85f, 0));
    assertFalse(EdgeSweep.upward(.85f, 1));
  }

  @Test
  public void edgesAreHalfAsDeepAndLeaveThroughTopOrBottom() {
    assertEquals((4 + .8f * 7) / 2, EdgeSweep.depth(.8f, 1), .00001f);
    float h = 1000, l = EdgeSweep.halfLength(h);
    assertEquals(h, EdgeSweep.center(h, 1, false) - l, .0001f);
    assertEquals(0, EdgeSweep.center(h, 1, true) + l, .0001f);
    assertTrue(EdgeSweep.center(h, .6f, false) > EdgeSweep.center(h, .3f, false));
    assertTrue(EdgeSweep.center(h, .6f, true) < EdgeSweep.center(h, .3f, true));
  }
}
