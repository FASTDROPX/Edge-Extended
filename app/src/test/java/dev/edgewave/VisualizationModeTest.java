package dev.edgewave;

import static org.junit.Assert.*;

import android.content.*;
import android.view.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "ru-rRU")
public class VisualizationModeTest {
  @Test
  public void defaultIsCurrentModeAndSavedOldModeSurvivesDefaults() {
    SharedPreferences p =
        RuntimeEnvironment.getApplication().getSharedPreferences("mode-default54", 0);
    AppDefaults.apply(p);
    assertEquals(1, p.getInt("effectMode", -1));
    p.edit().putInt("effectMode", 0).apply();
    AppDefaults.apply(p);
    assertTrue(new EffectsEngine(p).legacyMode());
  }

  @Test
  public void oldModeChoosesVariedEnabledEffectsWithoutPatternEvents() {
    SharedPreferences p = RuntimeEnvironment.getApplication().getSharedPreferences("old-mode54", 0);
    p.edit().putInt("effectMode", 0).putBoolean("fx_combos", false).apply();
    EffectsEngine e = new EffectsEngine(p);
    e.testMask = (1 << EffectsEngine.COUNT) - 1;
    Set<Integer> seen = new HashSet<>();
    for (int t = 1000; t < 26000; t += 20) {
      float[] bands = new float[48];
      float pulse = .25f * (float) Math.exp(-((t - 1000) % 500) / 40f);
      Arrays.fill(bands, .004f + pulse);
      e.offerAudioFrame(bands, t);
      e.update(bands, t, true, new float[48]);
      for (EffectsEngine.Fx f : e.items) seen.add(f.kind);
    }
    assertTrue(e.pending.isEmpty());
    assertTrue(e.patternStatus.startsWith("Старый"));
    assertTrue("legacy scheduler did not vary effects " + seen, seen.size() >= 5);
  }

  @Test
  public void liveModeSwitchDropsOldAnalysisButKeepsActiveAnimation() {
    SharedPreferences p =
        RuntimeEnvironment.getApplication().getSharedPreferences("mode-switch54", 0);
    EffectsEngine e = new EffectsEngine(p);
    e.testMask = (1 << 8) | 1;
    e.externalAnalysis = true;
    e.spawn(8, 900, .8f, new float[48]);
    EffectsEngine.Fx original = e.items.get(0);
    e.waitingUpper = 0;
    e.pending.offer(new TrackPatterns.Event(1000, TrackPatterns.Type.ROLL, 0, 4, 1, 1, .5f));
    p.edit().putInt("effectMode", 0).apply();
    e.update(new float[48], 1000, true, new float[48]);
    assertSame(original, e.items.get(0));
    assertTrue(e.pending.isEmpty());
    assertEquals(-1, e.waitingUpper);
    p.edit().putInt("effectMode", 1).apply();
    e.pending.offer(new TrackPatterns.Event(1010, TrackPatterns.Type.ROLL, 0, 4, 1, 1, .5f));
    e.update(new float[48], 1020, true, new float[48]);
    assertTrue(e.pending.isEmpty());
    assertSame(original, e.items.get(0));
  }

  @Test
  public void materialSelectorSavesChoice() {
    MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
    a.navigation.setSelectedItemId(MainActivity.NAV_EFFECTS);
    android.widget.Button field = a.content.findViewWithTag("effectMode");
    assertNotNull(field);
    assertTrue(field.getText().toString().contains("БЕТА"));
    field.performClick();
    androidx.appcompat.app.AlertDialog dialog =
        (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
    assertEquals(2, dialog.getListView().getCount());
    dialog.getListView().performItemClick(null, 0, 0);
    assertEquals(0, a.prefs.getInt("effectMode", -1));
    a.showPage(MainActivity.NAV_EFFECTS);
    android.widget.Button selected = a.content.findViewWithTag("effectMode");
    assertTrue(selected.getText().toString().contains("По битам"));
  }
}
