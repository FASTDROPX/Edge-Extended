package dev.edgewave;

import static org.junit.Assert.*;

import android.content.Context;
import android.media.AudioManager;
import android.view.KeyEvent;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "ru-rRU")
public class VolumeKeysTest {
  final List<String> presses = new ArrayList<>();
  VolumeKeysService service;

  @Before
  public void setup() {
    service = new VolumeKeysService();
    VolumeKeysService.listener =
        (up, down) -> presses.add((up ? "+" : "-") + (down ? "down" : "up"));
  }

  @After
  public void cleanup() {
    VolumeKeysService.listener = null;
  }

  @Test
  public void minusAtZeroStillAnimatesAndDoesNotConsumeKey() {
    AudioManager a =
        (AudioManager) RuntimeEnvironment.getApplication().getSystemService(Context.AUDIO_SERVICE);
    a.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
    assertFalse(
        service.onKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN)));
    assertEquals(Collections.singletonList("-down"), presses);
  }

  @Test
  public void plusAtMaximumStillAnimatesAndDoesNotConsumeKey() {
    AudioManager a =
        (AudioManager) RuntimeEnvironment.getApplication().getSystemService(Context.AUDIO_SERVICE);
    a.setStreamVolume(
        AudioManager.STREAM_MUSIC, a.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
    assertFalse(service.onKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP)));
    assertEquals(Collections.singletonList("+down"), presses);
  }

  @Test
  public void releaseOtherKeysAndHeldRepeats() {
    service.onKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP));
    assertEquals(Collections.singletonList("+up"), presses);
    presses.clear();
    service.onKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A));
    assertTrue(presses.isEmpty());
    assertFalse(
        service.onKeyEvent(
            new KeyEvent(0, 100, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 3)));
    assertEquals(Collections.singletonList("+down"), presses);
  }

  @Test
  public void bothKeysForwardPressAndRelease() {
    for (int key : new int[] {KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN}) {
      service.onKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
      service.onKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
    }
    assertEquals(Arrays.asList("+down", "+up", "-down", "-up"), presses);
  }

  @Test
  public void interruptedServiceReleasesBothKeys() {
    service.onInterrupt();
    assertEquals(Arrays.asList("+up", "-up"), presses);
  }
}
