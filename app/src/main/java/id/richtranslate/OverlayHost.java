package id.richtranslate;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.List;

/** One stable WindowManager surface from screenshot delivery through processing and results. */
// Positions come from physical screenshot coordinates, including under RTL locales.
@android.annotation.SuppressLint("RtlHardcoded")
final class OverlayHost extends FrameLayout {
  final FrameLayout resultLayer;
  final TextView closeButton;
  private boolean committed;

  OverlayHost(Context context, int screenWidth, int screenHeight, Runnable close) {
    super(context);
    setBackgroundColor(Color.TRANSPARENT);
    setFitsSystemWindows(false);
    setPadding(0, 0, 0, 0);
    setFocusable(false);
    // While reading results, touches outside strips must not accidentally click the game.
    setClickable(true);
    resultLayer = new FrameLayout(context);
    addView(resultLayer, new LayoutParams(-1, -1));
    closeButton = Ui.bold(context, "×", 26, Ui.INK);
    closeButton.setGravity(Gravity.CENTER);
    closeButton.setBackground(Ui.shape(Color.WHITE, 100, context));
    closeButton.setContentDescription("Tutup terjemahan dan kembali ke floating ball");
    int size = Math.min(Math.min(screenWidth, screenHeight), Ui.dp(context, 44));
    LayoutParams p = new LayoutParams(size, size, Gravity.TOP | Gravity.LEFT);
    p.leftMargin = Math.max(0, screenWidth - size - Ui.dp(context, 12));
    p.topMargin = Math.max(0, Math.min(screenHeight - size, Ui.dp(context, 32)));
    addView(closeButton, p);
    closeButton.setOnClickListener(v -> close.run());
  }

  /** Children are prepared first, then attached within one UI traversal. Never cancels pre-draw. */
  void commit(List<TranslationCard> strips, View attribution, Rect badgeBounds) {
    if (committed) return;
    for (TranslationCard strip : strips) {
      Rect b = strip.placement.bounds;
      LayoutParams p = new LayoutParams(b.width(), b.height(), Gravity.TOP | Gravity.LEFT);
      p.leftMargin = b.left;
      p.topMargin = b.top;
      resultLayer.addView(strip, p);
    }
    if (attribution != null) {
      LayoutParams p =
          new LayoutParams(badgeBounds.width(), badgeBounds.height(), Gravity.TOP | Gravity.LEFT);
      p.leftMargin = badgeBounds.left;
      p.topMargin = badgeBounds.top;
      resultLayer.addView(attribution, p);
    }
    committed = true;
    resultLayer.requestLayout();
  }
}
