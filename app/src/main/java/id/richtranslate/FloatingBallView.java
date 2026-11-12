package id.richtranslate;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** A small dark-grey translucent circle, with a pale rim and no letter or shadow. */
final class FloatingBallView extends View {
  static final int VISIBLE_DP = 32, TOUCH_DP = 40;
  static final int FILL_COLOR = 0x66282828; // Neutral charcoal, 40 percent opacity.
  private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG),
      edge = new Paint(Paint.ANTI_ALIAS_FLAG);

  FloatingBallView(Context context) {
    super(context);
    fill.setColor(FILL_COLOR);
    edge.setColor(0xB3FFFFFF);
    edge.setStyle(Paint.Style.STROKE);
    edge.setStrokeWidth(Ui.dp(context, 1.5f));
    setClickable(true);
  }

  @Override
  public boolean performClick() {
    super.performClick();
    return true;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float radius =
        Math.min(Ui.dp(getContext(), VISIBLE_DP) / 2f, Math.min(getWidth(), getHeight()) / 2f)
            - edge.getStrokeWidth() / 2;
    canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, radius, fill);
    canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, radius, edge);
  }
}
