package id.richtranslate;

import static org.junit.Assert.*;

import org.junit.Test;

public class AutoTextTrackerTest {
  @Test
  public void waitsForTwoMatchingFrames() {
    AutoTextTracker t = new AutoTextTracker();
    assertFalse(t.observe("Hello").translate);
    assertTrue(t.observe("Hello").translate);
  }

  @Test
  public void typewriterRestartsStability() {
    AutoTextTracker t = new AutoTextTracker();
    for (String s : new String[] {"H", "He", "Hello"}) assertFalse(t.observe(s).translate);
    assertTrue(t.observe("Hello").translate);
  }

  @Test
  public void ignoresUnchangedDisplayedText() {
    AutoTextTracker t = new AutoTextTracker();
    t.observe("Hello");
    t.observe("Hello");
    assertTrue(t.markDisplayed("Hello"));
    assertFalse(t.observe("Hello").translate);
    assertFalse(t.observe("Hello").clearOld);
  }

  @Test
  public void clearsOldSceneAndStabilizesNewScene() {
    AutoTextTracker t = new AutoTextTracker();
    t.observe("One");
    t.observe("One");
    t.markDisplayed("One");
    AutoTextTracker.Decision d = t.observe("Two");
    assertFalse(d.clearOld);
    assertFalse(d.translate);
    AutoTextTracker.Decision stable = t.observe("Two");
    assertTrue(stable.translate);
    assertTrue(stable.clearOld);
  }

  @Test
  public void emptyRequiresTwoScansBeforeClear() {
    AutoTextTracker t = new AutoTextTracker();
    t.observe("One");
    t.observe("One");
    t.markDisplayed("One");
    assertFalse(t.observe("").clearOld);
    assertTrue(t.observe("").clearOld);
    assertFalse(t.observe("").translate);
  }

  @Test
  public void returningDialogueCanBeTranslatedAgain() {
    AutoTextTracker t = new AutoTextTracker();
    t.observe("One");
    t.observe("One");
    t.markDisplayed("One");
    t.observe("");
    t.observe("");
    assertFalse(t.observe("One").translate);
    assertTrue(t.observe("One").translate);
  }

  @Test
  public void normalizationIgnoresCaseAndWhitespace() {
    assertEquals("hello world", AutoTextTracker.normalize("  HELLO\n\u00a0World  "));
  }

  @Test
  public void retainsPunctuationAndLanguage() {
    assertNotEquals(AutoTextTracker.normalize("No!"), AutoTextTracker.normalize("No?"));
  }

  @Test
  public void cannotMarkUnstableOrWrongText() {
    AutoTextTracker t = new AutoTextTracker();
    t.observe("One");
    assertFalse(t.markDisplayed("One"));
    t.observe("One");
    assertFalse(t.markDisplayed("Two"));
    assertTrue(t.markDisplayed("One"));
  }

  @Test
  public void resetDropsPreviousSession() {
    AutoTextTracker t = new AutoTextTracker();
    t.observe("One");
    t.observe("One");
    t.markDisplayed("One");
    t.reset();
    assertFalse(t.observe("One").translate);
    assertTrue(t.observe("One").translate);
  }

  @Test
  public void oneNoisyFrameNeverRemovesUnchangedResult() {
    AutoTextTracker t = new AutoTextTracker();
    t.observe("Hello");
    t.observe("Hello");
    t.markDisplayed("Hello");
    assertFalse(t.observe("He1lo").clearOld);
    assertFalse(t.observe("Hello").translate);
    assertFalse(t.observe("Hello").clearOld);
  }

  @Test
  public void separatedMisreadsCannotAccumulateAsTwoStableFrames() {
    AutoTextTracker t = new AutoTextTracker();
    t.observe("Hello");
    t.observe("Hello");
    t.markDisplayed("Hello");
    t.observe("He1lo");
    t.observe("Hello");
    AutoTextTracker.Decision d = t.observe("He1lo");
    assertFalse(d.clearOld);
    assertFalse(d.translate);
  }

  @Test
  public void oneBlankFrameThenOriginalKeepsShownText() {
    AutoTextTracker t = new AutoTextTracker();
    t.observe("Hello");
    t.observe("Hello");
    t.markDisplayed("Hello");
    assertFalse(t.observe("").clearOld);
    assertFalse(t.observe("Hello").translate);
    assertFalse(t.observe("").clearOld);
  }
}
