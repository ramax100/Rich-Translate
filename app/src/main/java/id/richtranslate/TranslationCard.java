package id.richtranslate;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;

/** Transparent local overlay with opaque white bands behind individual lines. No card/footer. */
final class TranslationCard extends View {
  final TextPlacement.Result placement;
  final String translatedText;
  final List<RectF> bands = new ArrayList<>();
  private final Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final int slop;
  private float downY, lastY, offset;
  private boolean dragging;

  TranslationCard(Context context, String value, TextPlacement.Result placement) {
    super(context);
    this.placement = placement;
    this.translatedText = value;
    white.setColor(Color.WHITE);
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
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    // Erase only the old text strips, not the entire bounding rectangle between lines.
    for (Rect line : placement.sourceLines) canvas.drawRect(line, white);
    canvas.save();
    canvas.translate(0, -offset);
    for (RectF band : bands) canvas.drawRoundRect(band, placement.pad, placement.pad, white);
    canvas.translate(placement.textX, placement.textY);
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
    offset = Math.max(0, Math.min(placement.contentHeight - getHeight(), next));
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
