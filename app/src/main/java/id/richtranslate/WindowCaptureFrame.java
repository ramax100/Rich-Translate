package id.richtranslate;

import android.graphics.*;
import java.util.List;

/** Map an API34 window buffer to physical display coordinates without capturing our overlay. */
final class WindowCaptureFrame {
  static Rect resolve(
      int bufferWidth, int bufferHeight, Rect window, int displayWidth, int displayHeight) {
    if (bufferWidth < 1 || bufferHeight < 1 || displayWidth < 1 || displayHeight < 1) return null;
    if (bufferWidth == displayWidth && bufferHeight == displayHeight)
      return new Rect(0, 0, displayWidth, displayHeight);
    if (window == null
        || window.isEmpty()
        || window.left < 0
        || window.top < 0
        || window.right > displayWidth
        || window.bottom > displayHeight) return null;
    // Accept full-resolution or proportionally scaled window images, never guess an inset.
    long cross =
        Math.abs((long) bufferWidth * window.height() - (long) bufferHeight * window.width());
    long pixels = (long) bufferWidth * window.height();
    if (cross > Math.max(2, Math.round(pixels * .002))) return null;
    return new Rect(window);
  }

  static boolean containsSelection(Rect frame, List<float[]> area) {
    if (frame == null || frame.isEmpty() || area == null || area.isEmpty()) return false;
    for (float[] p : area)
      if (p == null
          || p.length < 2
          || !Float.isFinite(p[0])
          || !Float.isFinite(p[1])
          || p[0] < frame.left
          || p[0] > frame.right
          || p[1] < frame.top
          || p[1] > frame.bottom) return false;
    return true;
  }

  /** Returns source when it already matches. Caller retains ownership of source otherwise. */
  static Bitmap compose(Bitmap source, Rect frame, int displayWidth, int displayHeight) {
    if (source == null || frame == null || displayWidth < 1 || displayHeight < 1)
      throw new IllegalArgumentException("Frame screenshot tidak valid");
    float scale = Math.min(1f, 1920f / Math.max(displayWidth, displayHeight));
    int w = Math.max(1, Math.round(displayWidth * scale)),
        h = Math.max(1, Math.round(displayHeight * scale));
    if (frame.equals(new Rect(0, 0, displayWidth, displayHeight))
        && source.getWidth() == w
        && source.getHeight() == h) return source;
    Bitmap result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    try {
      Canvas canvas = new Canvas(result);
      canvas.drawColor(Color.WHITE);
      float sx = (float) w / displayWidth, sy = (float) h / displayHeight;
      canvas.drawBitmap(
          source,
          null,
          new RectF(frame.left * sx, frame.top * sy, frame.right * sx, frame.bottom * sy),
          new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
      return result;
    } catch (RuntimeException | OutOfMemoryError e) {
      result.recycle();
      throw e;
    }
  }
}
