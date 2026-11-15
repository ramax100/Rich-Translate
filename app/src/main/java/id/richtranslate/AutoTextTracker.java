package id.richtranslate;

import java.text.Normalizer;
import java.util.Locale;

/** Text-only stability gate. Stores no screenshots and writes no dialogue history. */
final class AutoTextTracker {
  static final class Decision {
    final boolean translate, clearOld;

    Decision(boolean translate, boolean clearOld) {
      this.translate = translate;
      this.clearOld = clearOld;
    }
  }

  private String candidate = "", shown = "";
  private int repetitions, emptyFrames;

  static String normalize(String value) {
    return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
        .replaceAll("[\\p{Z}\\s]+", " ")
        .trim()
        .toLowerCase(Locale.ROOT);
  }

  Decision observe(String value) {
    String text = normalize(value);
    if (text.isEmpty()) {
      candidate = "";
      repetitions = 0;
      emptyFrames = Math.min(2, emptyFrames + 1);
      boolean clear = emptyFrames >= 2 && !shown.isEmpty();
      if (clear) shown = "";
      return new Decision(false, clear);
    }
    emptyFrames = 0;
    if (text.equals(shown)) return new Decision(false, false);
    boolean clear = !shown.isEmpty();
    if (clear) shown = "";
    if (text.equals(candidate)) repetitions = Math.min(2, repetitions + 1);
    else {
      candidate = text;
      repetitions = 1;
    }
    return new Decision(repetitions >= 2, clear);
  }

  boolean markDisplayed(String value) {
    String text = normalize(value);
    if (text.isEmpty() || !text.equals(candidate) || repetitions < 2) return false;
    shown = text;
    return true;
  }

  void reset() {
    candidate = shown = "";
    repetitions = emptyFrames = 0;
  }
}
