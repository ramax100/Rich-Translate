package id.richtranslate;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;

/** Soft translucent white bands. Only their edges are feathered; glyphs stay sharp. */
final class TranslationCard extends View {
  final TextPlacement.Result placement;
  final String translatedText;
  final List<RectF> bands = new ArrayList<>();
  static final int BACKGROUND_ALPHA = 232;
  private final Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint feather = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path sourceShape = new Path(),
      visibleShape = new Path(),
      backgroundShape = new Path();
  private final int slop;
  private float downY, lastY, offset;
  private boolean dragging;

  TranslationCard(Context context, String value, TextPlacement.Result placement) {
    super(context);
    this.placement = placement;
    this.translatedText = value;
    white.setColor(Color.argb(BACKGROUND_ALPHA, 255, 255, 255));
    feather.setColor(Color.WHITE);
    feather.setStyle(Paint.Style.STROKE);
    feather.setStrokeJoin(Paint.Join.ROUND);
    feather.setStrokeCap(Paint.Cap.ROUND);
    for (Rect line : placement.sourceLines)
      sourceShape.addRoundRect(
          new RectF(line), placement.pad * 2f, placement.pad * 2f, Path.Direction.CW);
    slop = ViewConfiguration.get(context).getScaledTouchSlop();
    setContentDescription("Terjemahan: " + value);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    setFocusable(false);
    for (int i = 0; i < placement.layout.getLineCount(); i++) {
      int start = placement.layout.getLineStart(i), end = placement.layout.getLineVisibleEnd(i);
      Rect glyphs = new Rect();
      placement.layout.getPaint().getTextBounds(value, start, end, glyphs);
      if (glyphs.isEmpty()) continue;
      float base = placement.textY + placement.layout.getLineBaseline(i);
      bands.add(
          new RectF(
              placement.textX + placement.layout.getLineLeft(i) - placement.pad,
              base + glyphs.top - placement.pad,
              placement.textX + placement.layout.getLineRight(i) + placement.pad,
              base + glyphs.bottom + placement.pad));
    }
    rebuildBackground();
  }

  /** Rebuild only when content scrolls, never on every draw or animation frame. */
  private void rebuildBackground() {
    visibleShape.reset();
    float radius = placement.pad * 2f;
    for (RectF band : bands) {
      if (band.bottom - offset < -placement.halo
          || band.top - offset > placement.bounds.height() + placement.halo) continue;
      visibleShape.addRoundRect(
          band.left,
          band.top - offset,
          band.right,
          band.bottom - offset,
          radius,
          radius,
          Path.Direction.CW);
    }
    // Union prevents source masks and translated strips being painted twice and turning opaque.
    if (!backgroundShape.op(sourceShape, visibleShape, Path.Op.UNION)) {
      backgroundShape.set(sourceShape);
      backgroundShape.addPath(visibleShape);
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    // A small static white halo softens the outline; this does not blur the game or the text.
    feather.setStrokeWidth(placement.halo * 2f);
    feather.setAlpha(5);
    canvas.drawPath(backgroundShape, feather);
    feather.setStrokeWidth(placement.halo * 1.25f);
    feather.setAlpha(12);
    canvas.drawPath(backgroundShape, feather);
    feather.setStrokeWidth(placement.halo * .5f);
    feather.setAlpha(24);
    canvas.drawPath(backgroundShape, feather);
    canvas.drawPath(backgroundShape, white);
    canvas.save();
    canvas.translate(placement.textX, placement.textY - offset);
    placement.layout.draw(canvas);
    canvas.restore();
  }

  @Override
  public boolean performClick() {
    super.performClick();
    return true;
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (!placement.scrolls) return false;
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        downY = lastY = event.getY();
        dragging = false;
        return true;
      case MotionEvent.ACTION_MOVE:
        float y = event.getY();
        if (Math.abs(y - downY) > slop) dragging = true;
        if (dragging) scrollResult(offset + lastY - y);
        lastY = y;
        return true;
      case MotionEvent.ACTION_UP:
        if (!dragging) performClick();
        return true;
      case MotionEvent.ACTION_CANCEL:
        dragging = false;
        return true;
      default:
        return true;
    }
  }

  private void scrollResult(float next) {
    float value = Math.max(0, Math.min(placement.contentHeight - getHeight(), next));
    if (value == offset) return;
    offset = value;
    rebuildBackground();
    postInvalidateOnAnimation();
  }

  @Override
  public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
    super.onInitializeAccessibilityNodeInfo(info);
    info.setScrollable(placement.scrolls);
    if (placement.scrolls) {
      info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
      info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }
  }

  @Override
  public boolean performAccessibilityAction(int action, android.os.Bundle arguments) {
    if (placement.scrolls
        && (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
      scrollResult(
          offset
              + (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? 1 : -1)
                  * getHeight()
                  * .8f);
      return true;
    }
    return super.performAccessibilityAction(action, arguments);
  }
}
