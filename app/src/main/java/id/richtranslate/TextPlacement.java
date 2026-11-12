package id.richtranslate;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import java.util.ArrayList;
import java.util.List;

/** Local, source-sized typesetting. Never forces a 240dp card or merges separate OCR blocks. */
final class TextPlacement {
  static final class Result {
    final Rect bounds;
    final List<Rect> sourceLines;
    final StaticLayout layout;
    final float sizePx, sizeSp, textX, textY;
    final int pad, halo, contentHeight;
    final boolean scrolls;

    Result(
        Context context,
        Rect bounds,
        List<Rect> lines,
        StaticLayout layout,
        float x,
        float y,
        int pad,
        int halo,
        int contentHeight) {
      this.bounds = bounds;
      this.sourceLines = lines;
      this.layout = layout;
      this.sizePx = layout.getPaint().getTextSize();
      this.sizeSp = sizePx / context.getResources().getDisplayMetrics().scaledDensity;
      this.textX = x;
      this.textY = y;
      this.pad = pad;
      this.halo = halo;
      this.contentHeight = contentHeight;
      this.scrolls = contentHeight > bounds.height();
    }
  }

  static TextPaint paint(float px) {
    TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    paint.setTextSize(Math.max(1, px));
    paint.setColor(android.graphics.Color.rgb(20, 20, 20));
    paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    return paint;
  }

  /** OCR boxes contain visible glyphs, not font ems. Calibrate against the actual word. */
  static float estimateFontPx(String word, float visibleHeight) {
    TextPaint paint = paint(100);
    Rect glyphs = new Rect();
    if (word != null && !word.isEmpty()) paint.getTextBounds(word, 0, word.length(), glyphs);
    float ratio = glyphs.height() > 0 ? glyphs.height() / 100f : .74f;
    return Math.max(1, visibleHeight) / Math.max(.4f, Math.min(1.2f, ratio));
  }

  static StaticLayout layout(String value, int width, float sizePx, float spacing) {
    return StaticLayout.Builder.obtain(value, 0, value.length(), paint(sizePx), Math.max(1, width))
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
        .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
        .setLineSpacing(0, spacing)
        .build();
  }

  private static float glyphTop(StaticLayout layout) {
    String value = layout.getText().toString();
    int end = layout.getLineVisibleEnd(0);
    Rect glyphs = new Rect();
    layout.getPaint().getTextBounds(value, 0, end, glyphs);
    return layout.getLineBaseline(0) + (glyphs.isEmpty() ? layout.getPaint().ascent() : glyphs.top);
  }

  private static float glyphBottom(StaticLayout layout) {
    int last = layout.getLineCount() - 1;
    String value = layout.getText().toString();
    Rect glyphs = new Rect();
    layout
        .getPaint()
        .getTextBounds(value, layout.getLineStart(last), layout.getLineVisibleEnd(last), glyphs);
    return layout.getLineBaseline(last)
        + (glyphs.isEmpty() ? layout.getPaint().descent() : glyphs.bottom);
  }

  static Result place(
      Context context,
      String value,
      Rect source,
      List<Rect> sourceLines,
      int screenWidth,
      int screenHeight,
      float sourceFontPx,
      int availableBottom) {
    screenWidth = Math.max(1, screenWidth);
    screenHeight = Math.max(1, screenHeight);
    int pad = Math.max(1, Ui.dp(context, 2));
    int halo = Math.max(1, Ui.dp(context, 2)), outerPad = pad + halo;
    Rect anchor = new Rect(source);
    if (!anchor.intersect(0, 0, screenWidth, screenHeight)) anchor.set(0, 0, 1, 1);
    int left = Math.max(0, anchor.left - outerPad), top = Math.max(0, anchor.top - outerPad);
    int right = Math.min(screenWidth, anchor.right + outerPad);
    // Glyphs keep the source origin. Background gets 2dp padding + a 2dp feather allowance.
    int textWidth = Math.max(1, Math.min(anchor.width(), screenWidth - anchor.left));
    float nominal =
        Float.isFinite(sourceFontPx) && sourceFontPx > 0
            ? sourceFontPx
            : estimateFontPx("Ag", anchor.height());
    nominal = Math.max(1, Math.min(screenHeight * 2f, nominal));
    // Keep the source line spacing when there are multiple OCR lines.
    float spacing = 1f;
    if (sourceLines.size() > 1) {
      List<Rect> sorted = new ArrayList<>(sourceLines);
      sorted.sort((a, b) -> Integer.compare(a.top, b.top));
      List<Float> steps = new ArrayList<>();
      for (int i = 1; i < sorted.size(); i++) {
        int step = sorted.get(i).top - sorted.get(i - 1).top;
        if (step > 0) steps.add((float) step);
      }
      if (!steps.isEmpty()) {
        java.util.Collections.sort(steps);
        android.graphics.Paint.FontMetrics fm = paint(nominal).getFontMetrics();
        spacing =
            Math.max(.85f, Math.min(1.5f, steps.get(steps.size() / 2) / (fm.descent - fm.ascent)));
      }
    }
    StaticLayout measured = layout(value, textWidth, nominal, spacing);
    float visibleHeight = glyphBottom(measured) - glyphTop(measured);
    // A modest fit is allowed, but don't make translations microscopic to force a fit.
    if (visibleHeight > anchor.height() + 1) {
      float low = nominal * .78f, high = nominal;
      for (int i = 0; i < 12; i++) {
        float mid = (low + high) / 2;
        StaticLayout candidate = layout(value, textWidth, mid, spacing);
        if (glyphBottom(candidate) - glyphTop(candidate) <= anchor.height() + 1) low = mid;
        else high = mid;
      }
      measured = layout(value, textWidth, low, spacing);
    }
    float textX = anchor.left - left, textY = anchor.top - top - glyphTop(measured);
    int contentHeight =
        Math.max(
            anchor.bottom - top + outerPad,
            (int) Math.ceil(textY + glyphBottom(measured)) + outerPad);
    // Even without a neighbouring OCR block, stay local instead of growing to a full-screen panel.
    int localBottom =
        anchor.top
            + (int) Math.ceil(Math.max(anchor.height() * 2f, anchor.height() + nominal * 1.4f));
    int limit =
        Math.max(anchor.bottom, Math.min(Math.min(screenHeight, availableBottom), localBottom));
    int height = Math.max(1, Math.min(contentHeight, Math.min(screenHeight - top, limit - top)));
    List<Rect> masks = new ArrayList<>();
    for (Rect line : sourceLines) {
      Rect r = new Rect(line);
      r.inset(-pad, -pad);
      r.offset(-left, -top);
      masks.add(r);
    }
    if (masks.isEmpty()) {
      Rect r = new Rect(anchor);
      r.inset(-pad, -pad);
      r.offset(-left, -top);
      masks.add(r);
    }
    return new Result(
        context,
        new Rect(left, top, right, top + height),
        masks,
        measured,
        textX,
        textY,
        pad,
        halo,
        contentHeight);
  }
}
