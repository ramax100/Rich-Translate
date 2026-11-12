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

  private final Done callback;
  private final GestureTrace trace = new GestureTrace();
  private final Path stroke = new Path();
  private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG),
      text = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF label = new RectF();
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
    text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    setFocusable(false);
    setContentDescription("Lingkari teks, lalu lepas. Ketuk × untuk batal.");
    // Default hardware-accelerated window renderer. No software bitmap layer.
  }

  @Override
  protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    super.onSizeChanged(width, height, oldWidth, oldHeight);
    updateOrigin();
    float w = Math.min(Ui.dp(getContext(), 300), width - Ui.dp(getContext(), 24));
    label.set((width - w) / 2, Ui.dp(getContext(), 20), (width + w) / 2, Ui.dp(getContext(), 68));
  }

  private void updateOrigin() {
    getLocationOnScreen(origin);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    text.setColor(0xEFFFFFFF);
    text.setStyle(Paint.Style.FILL);
    canvas.drawRoundRect(label, Ui.dp(getContext(), 14), Ui.dp(getContext(), 14), text);
    text.setColor(Ui.INK);
    text.setTextSize(Ui.dp(getContext(), 13));
    canvas.drawText(
        "Lingkari teks • lepas jari",
        label.left + Ui.dp(getContext(), 14),
        label.top + Ui.dp(getContext(), 29),
        text);
    text.setTextSize(Ui.dp(getContext(), 24));
    canvas.drawText(
        "×", label.right - Ui.dp(getContext(), 31), label.top + Ui.dp(getContext(), 30), text);
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
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        updateOrigin();
        if (x > label.right - Ui.dp(getContext(), 48)
            && x < label.right
            && y > label.top
            && y < label.bottom) {
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
