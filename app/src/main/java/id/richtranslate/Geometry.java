package id.richtranslate;

import java.util.List;

final class Geometry {
  static boolean contains(List<float[]> polygon, float x, float y) {
    boolean inside = false;
    for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
      float[] a = polygon.get(i), b = polygon.get(j);
      // Include edges, so text touching the lasso is not arbitrarily dropped.
      double cross = (x - a[0]) * (b[1] - a[1]) - (y - a[1]) * (b[0] - a[0]);
      if (Math.abs(cross) < 0.001
          && x >= Math.min(a[0], b[0])
          && x <= Math.max(a[0], b[0])
          && y >= Math.min(a[1], b[1])
          && y <= Math.max(a[1], b[1])) return true;
      if ((a[1] > y) != (b[1] > y) && x < (b[0] - a[0]) * (y - a[1]) / (b[1] - a[1]) + a[0])
        inside = !inside;
    }
    return inside;
  }
}
