package id.richtranslate;

import static org.junit.Assert.*;

import org.junit.Test;

public class GestureTraceTest {
  @Test
  public void nearSamplesAreDiscarded() {
    GestureTrace t = new GestureTrace();
    assertTrue(t.add(0, 0, 3));
    assertFalse(t.add(1, 1, 3));
    assertEquals(1, t.size);
  }

  @Test
  public void storageStaysBoundedForLongGesture() {
    GestureTrace t = new GestureTrace();
    for (int i = 0; i < 100000; i++) t.add(i, i, 0);
    assertTrue(t.size <= GestureTrace.LIMIT);
    assertTrue(t.compactions > 0);
    assertEquals(99999, t.x[t.size - 1], .01f);
  }

  @Test
  public void firstPointSurvivesCompaction() {
    GestureTrace t = new GestureTrace();
    for (int i = 0; i < 10000; i++) t.add(i, i, 0);
    assertEquals(0, t.x[0], .01f);
  }

  @Test
  public void screenOffsetAppliedOnce() {
    GestureTrace t = new GestureTrace();
    t.add(10, 20, 0);
    float[] point = t.screenPoints(5, 7).get(0);
    assertArrayEquals(new float[] {15, 27}, point, .001f);
  }

  @Test
  public void clearResetsGesture() {
    GestureTrace t = new GestureTrace();
    for (int i = 0; i < 2000; i++) t.add(i, i, 0);
    t.clear();
    assertEquals(0, t.size);
    assertEquals(0, t.compactions);
  }
}
