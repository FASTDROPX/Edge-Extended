package dev.edgewave;

import static org.junit.Assert.*;

import org.junit.Test;

public class DentMotionTest {
  @Test
  public void staysFullyIndentedForEntireHold() {
    DentMotion m = new DentMotion();
    m.press(100);
    assertEquals(0, m.value(100), 0);
    assertEquals(1, m.value(260), 0);
    assertEquals(1, m.value(60100), 0);
    assertFalse(m.finished(60100));
  }

  @Test
  public void releaseRetractsSmoothlyThenFinishes() {
    DentMotion m = new DentMotion();
    m.press(100);
    m.release(1000);
    assertEquals(1, m.value(1000), 0);
    assertTrue(m.value(1120) > 0 && m.value(1120) < 1);
    assertEquals(0, m.value(1240), 0);
    assertTrue(m.finished(1240));
  }

  @Test
  public void repeatDownDoesNotRestartAnimation() {
    DentMotion m = new DentMotion();
    m.press(100);
    float amount = m.value(180);
    m.press(180);
    assertEquals(amount, m.value(180), 0);
    assertEquals(1, m.value(260), 0);
  }

  @Test
  public void quickTapAndRepressAreContinuous() {
    DentMotion m = new DentMotion();
    m.press(100);
    float partial = m.value(150);
    m.release(150);
    assertEquals(partial, m.value(150), 0);
    float returning = m.value(200);
    m.press(200);
    assertEquals(returning, m.value(200), 0);
    assertEquals(1, m.value(360), 0);
  }
}
