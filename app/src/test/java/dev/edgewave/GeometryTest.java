package dev.edgewave;

import static org.junit.Assert.*;

import org.junit.Test;

public class GeometryTest {
  @Test
  public void navigationIsSubtractedExactlyOnce() {
    assertEquals(1520, ScreenGeometry.bottom(1520, 0, 1600, 80, false, 0));
    assertEquals(1520, ScreenGeometry.bottom(1600, 0, 1600, 80, false, 0));
    assertEquals(1440, ScreenGeometry.bottom(1440, 80, 1600, 80, false, 0));
  }

  @Test
  public void physicalEdgeAndManualCorrectionWork() {
    assertEquals(1600, ScreenGeometry.bottom(1600, 0, 1600, 80, true, 0));
    assertEquals(1504, ScreenGeometry.bottom(1600, 0, 1600, 80, false, 16));
  }
}
