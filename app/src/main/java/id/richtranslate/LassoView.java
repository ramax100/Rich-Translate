package id.richtranslate;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.List;

/** Transparent live selector: never paints a screenshot or a full-screen dim layer. */
final class LassoView extends View {
  interface Done {
    void selected(List<float[]> screenPoints);

    void cancel();
  }

  static final int HINT_WIDTH_DP = 196, HINT_HEIGHT_DP = 30, HINT_ALPHA = 200, CANCEL_TOUCH_DP = 44;
  private static final String HINT = "Lingkari teks • lepas";
  private final Done callback;
  private final GestureTrace trace = new GestureTrace();
  private final Path stroke = new Path();
  private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG),
      text = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF label = new RectF(), cancelBounds = new RectF();
  private final Paint.FontMetrics metrics = new Paint.FontMetrics();
  private boolean cancelling;
  private final int[] origin = new int[2];
  private final float spacing, strokeWidth;

  LassoView(Context context, Done callback) {
    super(context);
    this.callback = callback;
    spacing = Ui.dp(context, 3);
    strokeWidth = Ui.dp(context, 2);
    line.setColor(Ui.SELECTION_RED);
    line.setStyle(Paint.Style.STROKE);
    line.setStrokeWidth(strokeWidth);
    line.setStrokeCap(Paint.Cap.ROUND);
    line.setStrokeJoin(Paint.Join.ROUND);
    text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    setFocusable(false);
    setContentDescription("Lingkari teks, lalu lepas. Ketuk × untuk batal.");
    // Default hardware-accelerated window renderer. No software bitmap layer.
  }

  @Override
  protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    super.onSizeChanged(width, height, oldWidth, oldHeight);
    updateOrigin();
    float w =
        Math.max(0, Math.min(Ui.dp(getContext(), HINT_WIDTH_DP), width - Ui.dp(getContext(), 24)));
    float h = Math.min(height, Ui.dp(getContext(), HINT_HEIGHT_DP));
    float top = Math.max(0, Math.min(Ui.dp(getContext(), 20), height - h));
    label.set((width - w) / 2, top, (width + w) / 2, top + h);
    float touchW = Math.min(width, Ui.dp(getContext(), CANCEL_TOUCH_DP));
    float touchH = Math.min(height, Ui.dp(getContext(), CANCEL_TOUCH_DP));
    float cx = label.right - Ui.dp(getContext(), 16), cy = label.centerY();
    float left = Math.max(0, Math.min(width - touchW, cx - touchW / 2));
    float touchTop = Math.max(0, Math.min(height - touchH, cy - touchH / 2));
    cancelBounds.set(left, touchTop, left + touchW, touchTop + touchH);
  }

  private void updateOrigin() {
    getLocationOnScreen(origin);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    text.setColor(Color.argb(HINT_ALPHA, 255, 255, 255));
    text.setStyle(Paint.Style.FILL);
    canvas.drawRoundRect(label, Ui.dp(getContext(), 10), Ui.dp(getContext(), 10), text);
    text.setColor(0xDE202333);
    text.setTextSize(Ui.dp(getContext(), 11));
    text.getFontMetrics(metrics);
    float baseline = label.centerY() - (metrics.ascent + metrics.descent) / 2;
    int save = canvas.save();
    canvas.clipRect(
        label.left + Ui.dp(getContext(), 10),
        label.top,
        label.right - Ui.dp(getContext(), 34),
        label.bottom);
    canvas.drawText(HINT, label.left + Ui.dp(getContext(), 10), baseline, text);
    canvas.restoreToCount(save);
    text.setTextSize(Ui.dp(getContext(), 18));
    text.getFontMetrics(metrics);
    canvas.drawText(
        "×",
        label.right - Ui.dp(getContext(), 16) - text.measureText("×") / 2,
        label.centerY() - (metrics.ascent + metrics.descent) / 2,
        text);
    canvas.drawPath(stroke, line);
  }

  private void append(float x, float y, float distance) {
    int compactions = trace.compactions;
    if (!trace.add(x, y, distance)) return;
    if (trace.compactions != compactions) {
      stroke.reset();
      stroke.moveTo(trace.x[0], trace.y[0]);
      for (int i = 1; i < trace.size; i++) stroke.lineTo(trace.x[i], trace.y[i]);
    } else if (trace.size == 1) stroke.moveTo(x, y);
    else stroke.lineTo(x, y);
  }

  @Override
  public boolean performClick() {
    super.performClick();
    return true;
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    float x = event.getX(), y = event.getY();
    if (cancelling && event.getActionMasked() != MotionEvent.ACTION_DOWN) {
      if (event.getActionMasked() == MotionEvent.ACTION_UP
          || event.getActionMasked() == MotionEvent.ACTION_CANCEL) cancelling = false;
      return true;
    }
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        updateOrigin();
        cancelling = false;
        if (cancelBounds.contains(x, y)) {
          cancelling = true;
          performClick();
          callback.cancel();
          return true;
        }
        trace.clear();
        stroke.reset();
        append(x, y, 0);
        postInvalidateOnAnimation();
        return true;
      case MotionEvent.ACTION_MOVE:
        // Only the current sample matters visually; historical samples multiply work
        // on devices that batch high-frequency input. The final polygon stays bounded.
        append(x, y, spacing);
        postInvalidateOnAnimation();
        return true;
      case MotionEvent.ACTION_UP:
        append(x, y, 0);
        performClick();
        if (trace.size < 3) callback.cancel();
        else callback.selected(trace.screenPoints(origin[0], origin[1]));
        return true;
      case MotionEvent.ACTION_CANCEL:
        callback.cancel();
        return true;
      default:
        return true;
    }
  }

  @Override
  public boolean onKeyDown(int key, KeyEvent event) {
    if (key == KeyEvent.KEYCODE_BACK) {
      callback.cancel();
      return true;
    }
    return super.onKeyDown(key, event);
  }
}
