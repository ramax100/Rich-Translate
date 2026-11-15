package id.richtranslate;

import android.graphics.Rect;
import android.view.WindowManager;
import java.util.Arrays;
import java.util.List;

/** No OCR/node access: only capture scheduling, area bounds and touchability policy. */
final class AutoCapturePolicy {
  static final long SCAN_DELAY_MS = 1200,
      HIDE_SETTLE_MS = 110,
      HIDE_DEADLINE_MS = 650,
      JOB_TIMEOUT_MS = 15000;

  static boolean useWindowCapture(int sdk, int windowId) {
    return sdk >= 34 && windowId >= 0;
  }

  static boolean windowSizeMatches(
      int screenshotWidth, int screenshotHeight, int displayWidth, int displayHeight) {
    return screenshotWidth == displayWidth && screenshotHeight == displayHeight;
  }

  static int flags(int base, boolean automatic) {
    return automatic ? base | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE : base;
  }

  static List<float[]> rectangle(List<float[]> points, int width, int height) {
    if (points == null || points.size() < 3 || width < 1 || height < 1)
      throw new IllegalArgumentException("Area dialog tidak valid");
    float left = width, top = height, right = 0, bottom = 0;
    for (float[] p : points) {
      if (p == null || p.length < 2 || !Float.isFinite(p[0]) || !Float.isFinite(p[1]))
        throw new IllegalArgumentException("Area dialog tidak valid");
      left = Math.min(left, p[0]);
      top = Math.min(top, p[1]);
      right = Math.max(right, p[0]);
      bottom = Math.max(bottom, p[1]);
    }
    left = Math.max(0, left);
    top = Math.max(0, top);
    right = Math.min(width, right);
    bottom = Math.min(height, bottom);
    if (right - left < 12 || bottom - top < 12)
      throw new IllegalArgumentException(
          "Lingkari seluruh kotak dialog, bukan titik atau garis kecil.");
    return Arrays.asList(
        new float[] {left, top},
        new float[] {right, top},
        new float[] {right, bottom},
        new float[] {left, bottom});
  }

  static boolean controlOverlaps(List<float[]> area, Rect control) {
    return Rect.intersects(
        new Rect(
            (int) area.get(0)[0],
            (int) area.get(0)[1],
            (int) Math.ceil(area.get(2)[0]),
            (int) Math.ceil(area.get(2)[1])),
        control);
  }
}
