package id.richtranslate;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "w540dp-h260dp-xhdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class InlineLayoutTest {
  private final Context context = RuntimeEnvironment.getApplication();

  private TextPlacement.Result place(String text, Rect source, float px) {
    return TextPlacement.place(
        context, text, source, Collections.singletonList(source), 1080, 520, px, 520);
  }

  private TranslationCard card(String text, TextPlacement.Result result) {
    TranslationCard v = new TranslationCard(context, text, result);
    v.measure(
        View.MeasureSpec.makeMeasureSpec(result.bounds.width(), View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(result.bounds.height(), View.MeasureSpec.EXACTLY));
    v.layout(0, 0, result.bounds.width(), result.bounds.height());
    return v;
  }

  @Test
  public void shortNumberDoesNotBecomeLargeCard() {
    Rect source = new Rect(470, 140, 575, 180);
    TextPlacement.Result result = place("100", source, 48);
    assertTrue(result.bounds.width() <= source.width() + Ui.dp(context, 4));
    assertTrue(result.bounds.height() < source.height() + Ui.dp(context, 12));
  }

  @Test
  public void glyphOriginMatchesOriginalSource() {
    Rect source = new Rect(205, 100, 560, 137);
    TextPlacement.Result r = place("Kunci gerbang", source, 38);
    assertEquals(source.left, r.bounds.left + r.textX, .01);
    Rect glyphs = new Rect();
    String text = r.layout.getText().toString();
    r.layout.getPaint().getTextBounds(text, 0, r.layout.getLineVisibleEnd(0), glyphs);
    float top = r.bounds.top + r.textY + r.layout.getLineBaseline(0) + glyphs.top;
    assertEquals(source.top, top, .01);
  }

  @Test
  public void fontIsEstimatedFromVisibleGlyphHeight() {
    String word = "Gerbang";
    android.text.TextPaint paint = TextPlacement.paint(40);
    Rect glyphs = new Rect();
    paint.getTextBounds(word, 0, word.length(), glyphs);
    assertEquals(40, TextPlacement.estimateFontPx(word, glyphs.height()), 1.5);
  }

  @Test
  public void largeSourceProducesLargerTranslation() {
    TextPlacement.Result small = place("12", new Rect(20, 100, 100, 125), 30);
    TextPlacement.Result large = place("12", new Rect(200, 100, 400, 165), 70);
    assertTrue(large.sizePx > small.sizePx * 1.8);
  }

  @Test
  public void fittingTextKeepsSourceFontSize() {
    TextPlacement.Result r = place("Buka pintu", new Rect(100, 100, 500, 150), 40);
    assertEquals(40, r.sizePx, .01);
  }

  @Test
  public void tinySourceIsNotForcedToFourteenSp() {
    TextPlacement.Result r = place("12", new Rect(100, 100, 125, 112), 12);
    assertEquals(12, r.sizePx, .01);
    assertTrue(r.sizeSp < 14);
  }

  @Test
  public void longTranslationWrapsLocallyInsteadOfWidening() {
    Rect src = new Rect(100, 200, 280, 230);
    TextPlacement.Result r =
        place("Menyerang lawan dan meningkatkan kekuatan serangan selama dua giliran.", src, 28);
    assertTrue(r.layout.getLineCount() > 1);
    assertTrue(r.bounds.width() <= src.width() + Ui.dp(context, 4));
    assertTrue(r.sizePx >= 28 * .78 - .01);
    assertEquals(src.top - Ui.dp(context, 2), r.bounds.top);
  }

  @Test
  public void longOutputStillStaysNearSourceInsteadOfBecomingTallPanel() {
    Rect source = new Rect(100, 100, 300, 130);
    TextPlacement.Result r = place("Teks yang sangat panjang. ".repeat(100), source, 28);
    assertTrue(r.scrolls);
    assertTrue(r.bounds.height() < 100);
  }

  @Test
  public void nextBlockLimitsExpansionWithoutMerging() {
    Rect src = new Rect(100, 100, 280, 130);
    TextPlacement.Result r =
        TextPlacement.place(
            context,
            "Teks terjemahan yang sangat panjang. ".repeat(20),
            src,
            List.of(src),
            1080,
            520,
            28,
            180);
    assertTrue(r.scrolls);
    assertTrue(r.bounds.bottom <= 180);
  }

  @Test
  public void veryLongTextRetainedWithoutEllipsis() {
    String text = "Petualangan berlanjut. ".repeat(200);
    TextPlacement.Result r = place(text, new Rect(100, 300, 350, 330), 30);
    TranslationCard c = card(text, r);
    assertEquals(text, c.translatedText);
    assertEquals(text, r.layout.getText().toString());
    assertTrue(r.scrolls);
  }

  @Test
  public void backgroundIsWhiteAtSourceAndTranslationLine() {
    Rect src = new Rect(100, 100, 450, 135);
    TextPlacement.Result r = place("Buka pintu", src, 35);
    TranslationCard c = card("Buka pintu", r);
    Bitmap b = Bitmap.createBitmap(c.getWidth(), c.getHeight(), Bitmap.Config.ARGB_8888);
    c.draw(new Canvas(b));
    assertEquals(Color.WHITE, b.getPixel(r.pad, r.pad));
    assertEquals(Color.rgb(20, 20, 20), r.layout.getPaint().getColor());
    b.recycle();
  }

  @Test
  public void spaceBetweenLinesRemainsTransparent() {
    Rect src = new Rect(100, 100, 500, 220);
    List<Rect> lines = List.of(new Rect(100, 100, 500, 130), new Rect(100, 190, 300, 220));
    TextPlacement.Result r = TextPlacement.place(context, "Buka", src, lines, 1080, 520, 30, 520);
    TranslationCard c = card("Buka", r);
    Bitmap b = Bitmap.createBitmap(c.getWidth(), c.getHeight(), Bitmap.Config.ARGB_8888);
    c.draw(new Canvas(b));
    assertEquals(0, Color.alpha(b.getPixel(100, 70)));
    assertNull(c.getBackground());
    b.recycle();
  }

  @Test
  public void resultContainsNoButtonsOrBadgeFooter() {
    TextPlacement.Result r = place("Buka pintu", new Rect(100, 100, 500, 150), 30);
    TranslationCard c = card("Buka pintu", r);
    assertFalse(android.view.ViewGroup.class.isAssignableFrom(c.getClass()));
    assertEquals("Terjemahan: Buka pintu", c.getContentDescription());
  }

  @Test
  public void placementStaysInsideScreenForTinyAndEdgeCases() {
    for (int[] dims : new int[][] {{1, 1}, {120, 80}, {480, 100}, {1080, 520}}) {
      TextPlacement.Result r =
          TextPlacement.place(
              context,
              "Teks panjang. ".repeat(20),
              new Rect(400, 80, 900, 120),
              List.of(new Rect(400, 80, 900, 120)),
              dims[0],
              dims[1],
              28,
              dims[1]);
      assertTrue(r.bounds.left >= 0);
      assertTrue(r.bounds.top >= 0);
      assertTrue(r.bounds.right <= dims[0]);
      assertTrue(r.bounds.bottom <= dims[1]);
      assertTrue(r.bounds.width() > 0);
      assertTrue(r.bounds.height() > 0);
    }
  }

  @Test
  public void invalidFontEstimateFallsBackToSourceGeometry() {
    TextPlacement.Result r = place("Buka", new Rect(100, 100, 500, 150), Float.NaN);
    assertTrue(Float.isFinite(r.sizePx));
    assertTrue(r.sizePx > 0);
  }

  @Test
  public void fontScaleDoesNotChangePhysicalOverlaySize() {
    android.content.res.Configuration config =
        new android.content.res.Configuration(context.getResources().getConfiguration());
    config.fontScale = 2f;
    Context large = context.createConfigurationContext(config);
    Rect src = new Rect(100, 100, 500, 150);
    TextPlacement.Result a = place("Buka", src, 35),
        b = TextPlacement.place(large, "Buka", src, List.of(src), 1080, 520, 35, 520);
    assertEquals(a.sizePx, b.sizePx, .01);
    assertEquals(a.bounds, b.bounds);
  }

  @Test
  public void overflowSupportsAccessibleScrolling() {
    String value = "Baca hasil panjang. ".repeat(100);
    TextPlacement.Result r = place(value, new Rect(100, 400, 300, 430), 30);
    TranslationCard c = card(value, r);
    assertTrue(r.scrolls);
    assertTrue(
        c.performAccessibilityAction(
            android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null));
    assertTrue(
        c.performAccessibilityAction(
            android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null));
  }

  @Test
  public void overflowCanBeDragged() {
    String value = "Teks panjang ".repeat(100);
    TranslationCard c = card(value, place(value, new Rect(100, 400, 300, 430), 30));
    MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 30, 60, 0),
        move = MotionEvent.obtain(0, 20, MotionEvent.ACTION_MOVE, 30, 10, 0),
        up = MotionEvent.obtain(0, 40, MotionEvent.ACTION_UP, 30, 10, 0);
    assertTrue(c.onTouchEvent(down));
    assertTrue(c.onTouchEvent(move));
    assertTrue(c.onTouchEvent(up));
    down.recycle();
    move.recycle();
    up.recycle();
  }

  @Test
  public void renderIllustrativeGameOverlay() throws Exception {
    Bitmap bitmap = Bitmap.createBitmap(1080, 520, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    canvas.drawColor(Color.rgb(156, 133, 112));
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(Color.rgb(110, 95, 83));
    canvas.drawRoundRect(24, 65, 1056, 490, 18, 18, paint);
    paint.setColor(Color.rgb(106, 112, 133));
    canvas.drawRoundRect(90, 105, 350, 355, 16, 16, paint);
    paint.setColor(Color.rgb(222, 200, 84));
    canvas.drawCircle(225, 175, 23, paint);
    paint.setStrokeWidth(18);
    canvas.drawLine(225, 200, 220, 270, paint);
    canvas.drawLine(220, 250, 170, 315, paint);
    canvas.drawLine(220, 250, 275, 315, paint);
    paint.setTypeface(Typeface.DEFAULT);
    paint.setTextSize(18);
    paint.setColor(Color.WHITE);
    canvas.drawText("SIMULASI LAYOUT • bukan screenshot perangkat / hasil OCR", 24, 32, paint);
    List<Rect> used = new ArrayList<>();
    String[][] pairs = {
      {"Rikudo Item Destruction", "Penghancuran Item Rikudo"},
      {"Lv 80", "Lv 80"},
      {"100", "100"},
      {"700", "700"},
      {"12", "12"}
    };
    int[][] positions = {
      {180, 88, 32}, {405, 145, 52}, {470, 210, 46}, {470, 275, 46}, {470, 340, 42}
    };
    for (int i = 0; i < pairs.length; i++) {
      String original = pairs[i][0], translated = pairs[i][1];
      int x = positions[i][0], base = positions[i][1], size = positions[i][2];
      paint.setTextSize(size);
      paint.setColor(Color.WHITE);
      canvas.drawText(original, x, base, paint);
      Rect glyphs = new Rect();
      paint.getTextBounds(original, 0, original.length(), glyphs);
      glyphs.offset(x, base);
      used.add(glyphs);
      TextPlacement.Result r = place(translated, glyphs, size);
      TranslationCard card = card(translated, r);
      canvas.save();
      canvas.translate(r.bounds.left, r.bounds.top);
      card.draw(canvas);
      canvas.restore();
    }
    String text =
        "(Kenjutsu) Menyerang lawan dan meningkatkan kerusakan serangan pengguna serta mengurangi"
            + " kerusakan yang diterima sebesar 70% (2 giliran)";
    Rect source = new Rect(100, 385, 1000, 452);
    TextPlacement.Result r =
        TextPlacement.place(
            context,
            text,
            source,
            List.of(new Rect(100, 385, 1000, 414), new Rect(100, 423, 900, 452)),
            1080,
            520,
            30,
            500);
    TranslationCard card = card(text, r);
    canvas.save();
    canvas.translate(r.bounds.left, r.bounds.top);
    card.draw(canvas);
    canvas.restore();
    java.io.File out = new java.io.File("build/reports/ui/hasil-inline-simulasi.png");
    out.getParentFile().mkdirs();
    try (java.io.FileOutputStream stream = new java.io.FileOutputStream(out)) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
    }
    bitmap.recycle();
  }
}
