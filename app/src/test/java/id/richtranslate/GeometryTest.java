package id.richtranslate;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class GeometryTest {
  private List<float[]> box() {
    return Arrays.asList(
        new float[] {0, 0}, new float[] {100, 0}, new float[] {100, 100}, new float[] {0, 100});
  }

  @Test
  public void inside() {
    assertTrue(Geometry.contains(box(), 50, 50));
  }

  @Test
  public void outside() {
    assertFalse(Geometry.contains(box(), 150, 50));
  }

  @Test
  public void boundary() {
    assertTrue(Geometry.contains(box(), 0, 50));
  }

  @Test
  public void concave() {
    List<float[]> p =
        Arrays.asList(
            new float[] {0, 0},
            new float[] {100, 0},
            new float[] {100, 20},
            new float[] {20, 20},
            new float[] {20, 100},
            new float[] {0, 100});
    assertTrue(Geometry.contains(p, 10, 90));
    assertFalse(Geometry.contains(p, 50, 50));
  }

  @Test
  public void repeatedPoint() {
    List<float[]> p =
        Arrays.asList(
            new float[] {0, 0},
            new float[] {0, 0},
            new float[] {100, 0},
            new float[] {100, 100},
            new float[] {0, 100});
    assertTrue(Geometry.contains(p, 30, 30));
    assertFalse(Geometry.contains(p, 101, 101));
  }
}
