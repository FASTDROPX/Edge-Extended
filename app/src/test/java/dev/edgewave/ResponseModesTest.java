package dev.edgewave;

import static org.junit.Assert.*;

import java.util.Arrays;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "ru-rRU")
public class ResponseModesTest {
  @Test
  public void oldAndNewDecodersPreserveDifferentDynamicResponse() {
    byte[] fft = new byte[1024];
    fft[4] = 4;
    float[] classic = AudioSpectrum.decode(fft, 48000, true),
        live = AudioSpectrum.decode(fft, 48000, false);
    float oldPeak = 0, newPeak = 0;
    for (int i = 0; i < 48; i++) {
      oldPeak = Math.max(oldPeak, classic[i]);
      newPeak = Math.max(newPeak, live[i]);
    }
    assertTrue(oldPeak > .3f);
    assertEquals(4 / 128f, newPeak, .00001f);
    for (float f : AudioSpectrum.decode(new byte[1024], 48000, true)) assertEquals(0, f, 0);
  }

  @Test
  public void classicAndLiveHaveDifferentAmplitudeAndMovement() {
    float[] levels = new float[48];
    Arrays.fill(levels, .7f);
    float classic = ClassicWaveProfile.sampleLayer(levels, 0, 0, 0);
    float live = WaveProfile.sampleLayer(levels, 0, 0, 0, .8f);
    assertTrue(Math.abs(live - classic) > .15f);
  }

  @Test
  public void switchingModesKeepsMuteCompletelyHidden() {
    android.content.Context c = RuntimeEnvironment.getApplication();
    for (String mode : new String[] {"classic", "live", "hybrid"}) {
      c.getSharedPreferences("wave", 0).edit().putString("responseMode", mode).apply();
      WaveView v = new WaveView(c, false);
      v.muted = true;
      v.sounding = true;
      v.layout(0, 0, 320, 200);
      android.graphics.Bitmap b =
          android.graphics.Bitmap.createBitmap(320, 200, android.graphics.Bitmap.Config.ARGB_8888);
      v.draw(new android.graphics.Canvas(b));
      for (int y = 0; y < 200; y++)
        assertEquals(0, android.graphics.Color.alpha(b.getPixel(160, y)));
    }
  }
}
