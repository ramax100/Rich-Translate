package id.richtranslate;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.*;

final class Ui {
  static final int BG = 0xFFFCF8F9,
      CARD = Color.WHITE,
      INK = 0xFF202333,
      MUTED = 0xFF74798D,
      ACCENT = 0xFF831526,
      TINT = 0xFFFCECEF,
      LINE = 0xFFF1E4E7,
      SELECTION_RED = 0xFFE53935;

  static int dp(Context c, float value) {
    return Math.round(value * c.getResources().getDisplayMetrics().density);
  }

  static GradientDrawable shape(int color, int radius, Context c) {
    GradientDrawable g = new GradientDrawable();
    g.setColor(color);
    g.setCornerRadius(dp(c, radius));
    return g;
  }

  static TextView text(Context c, String value, int size, int color) {
    TextView t = new TextView(c);
    t.setText(value);
    t.setTextColor(color);
    t.setTextSize(size);
    t.setLineSpacing(0, 1.12f);
    t.setIncludeFontPadding(false);
    return t;
  }

  static TextView bold(Context c, String value, int size, int color) {
    TextView t = text(c, value, size, color);
    t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    return t;
  }

  static Button button(Context c, String value) {
    Button b = new Button(c);
    b.setText(value);
    b.setTextColor(Color.WHITE);
    b.setTextSize(15);
    b.setAllCaps(false);
    b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    b.setBackground(shape(ACCENT, 16, c));
    return b;
  }

  static LinearLayout column(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }
}
