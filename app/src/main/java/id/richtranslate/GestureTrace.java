package id.richtranslate;

import java.util.ArrayList;
import java.util.List;

/** Bounded primitive storage. No per-move object allocation. */
final class GestureTrace {
  static final int LIMIT = 768;
  final float[] x = new float[LIMIT], y = new float[LIMIT];
  int size, compactions;

  void clear() {
    size = 0;
    compactions = 0;
  }

  boolean add(float px, float py, float minimumDistance) {
    if (size > 0) {
      float dx = px - x[size - 1], dy = py - y[size - 1];
      if (dx * dx + dy * dy < minimumDistance * minimumDistance) return false;
    }
    if (size == LIMIT) {
      int next = 1;
      for (int i = 2; i < size; i += 2) {
        x[next] = x[i];
        y[next] = y[i];
        next++;
      }
      x[next] = x[size - 1];
      y[next] = y[size - 1];
      size = next + 1;
      compactions++;
    }
    x[size] = px;
    y[size] = py;
    size++;
    return true;
  }

  List<float[]> screenPoints(float originX, float originY) {
    List<float[]> points = new ArrayList<>(size);
    for (int i = 0; i < size; i++) points.add(new float[] {x[i] + originX, y[i] + originY});
    return points;
  }
}
