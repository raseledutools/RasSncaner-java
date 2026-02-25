package de.schliweb.makeacopy.ui.ocr;

import static org.junit.Assert.*;

import android.graphics.RectF;
import de.schliweb.makeacopy.utils.RecognizedWord;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * JVM unit tests for {@link OCRViewModel.OcrUiState} immutable with* builders,
 * getEffectiveText/Words, and {@link OCRViewModel.Event}.
 */
public class OcrUiStateTest {

  private static RectF rect(float l, float t, float r, float b) {
    RectF rf = new RectF();
    rf.left = l;
    rf.top = t;
    rf.right = r;
    rf.bottom = b;
    return rf;
  }

  private static OCRViewModel.OcrUiState base() {
    return new OCRViewModel.OcrUiState(
        false, false, "eng", "", new ArrayList<>(), null, null, null, null, null, false);
  }

  // ---- withProcessing ----

  @Test
  public void withProcessing_setsFlag() {
    OCRViewModel.OcrUiState s = base().withProcessing(true);
    assertTrue(s.processing());
    assertFalse(s.imageProcessed());
  }

  // ---- withImageProcessed ----

  @Test
  public void withImageProcessed_setsFlag() {
    OCRViewModel.OcrUiState s = base().withImageProcessed(true);
    assertTrue(s.imageProcessed());
  }

  // ---- withText ----

  @Test
  public void withText_updatesText() {
    OCRViewModel.OcrUiState s = base().withText("hello");
    assertEquals("hello", s.ocrText());
  }

  // ---- withWords ----

  @Test
  public void withWords_updatesWords() {
    List<RecognizedWord> words =
        Arrays.asList(new RecognizedWord("a", rect(0, 0, 10, 10), 90f));
    OCRViewModel.OcrUiState s = base().withWords(words);
    assertEquals(1, s.words().size());
  }

  // ---- withLanguage ----

  @Test
  public void withLanguage_updatesLanguage() {
    OCRViewModel.OcrUiState s = base().withLanguage("deu");
    assertEquals("deu", s.language());
  }

  // ---- withDuration ----

  @Test
  public void withDuration_setsDuration() {
    OCRViewModel.OcrUiState s = base().withDuration(1234L);
    assertEquals(Long.valueOf(1234L), s.durationMs());
  }

  // ---- withMeanConfidence ----

  @Test
  public void withMeanConfidence_setsConfidence() {
    OCRViewModel.OcrUiState s = base().withMeanConfidence(85);
    assertEquals(Integer.valueOf(85), s.meanConfidence());
  }

  // ---- withTransform ----

  @Test
  public void withTransform_setsTransform() {
    OCRViewModel.OcrTransform tx = new OCRViewModel.OcrTransform(100, 200, 300, 400, 3f, 2f, 0, 0);
    OCRViewModel.OcrUiState s = base().withTransform(tx);
    assertNotNull(s.transform());
    assertEquals(300, s.transform().dstW());
    assertEquals(400, s.transform().dstH());
  }

  // ---- withReviewedText ----

  @Test
  public void withReviewedText_setsReviewedText() {
    OCRViewModel.OcrUiState s = base().withReviewedText("reviewed");
    assertEquals("reviewed", s.reviewedText());
  }

  // ---- withReviewedWords ----

  @Test
  public void withReviewedWords_setsReviewedWords() {
    List<RecognizedWord> rw =
        Arrays.asList(new RecognizedWord("b", rect(0, 0, 5, 5), 80f));
    OCRViewModel.OcrUiState s = base().withReviewedWords(rw);
    assertEquals(1, s.reviewedWords().size());
  }

  // ---- withHasReviewEdits ----

  @Test
  public void withHasReviewEdits_setsFlag() {
    OCRViewModel.OcrUiState s = base().withHasReviewEdits(true);
    assertTrue(s.hasReviewEdits());
  }

  // ---- getEffectiveText ----

  @Test
  public void getEffectiveText_returnsOcrText_whenNoReview() {
    OCRViewModel.OcrUiState s = base().withText("ocr");
    assertEquals("ocr", s.getEffectiveText());
  }

  @Test
  public void getEffectiveText_returnsReviewedText_whenReviewed() {
    OCRViewModel.OcrUiState s =
        base().withText("ocr").withReviewedText("reviewed").withHasReviewEdits(true);
    assertEquals("reviewed", s.getEffectiveText());
  }

  @Test
  public void getEffectiveText_returnsOcrText_whenReviewedButNotEdited() {
    OCRViewModel.OcrUiState s = base().withText("ocr").withReviewedText("reviewed");
    assertEquals("ocr", s.getEffectiveText());
  }

  // ---- getEffectiveWords ----

  @Test
  public void getEffectiveWords_returnsOcrWords_whenNoReview() {
    List<RecognizedWord> words =
        Arrays.asList(new RecognizedWord("a", rect(0, 0, 10, 10), 90f));
    OCRViewModel.OcrUiState s = base().withWords(words);
    assertEquals(words, s.getEffectiveWords());
  }

  @Test
  public void getEffectiveWords_returnsReviewedWords_whenReviewed() {
    List<RecognizedWord> ocrWords =
        Arrays.asList(new RecognizedWord("a", rect(0, 0, 10, 10), 90f));
    List<RecognizedWord> revWords =
        Arrays.asList(new RecognizedWord("b", rect(0, 0, 5, 5), 80f));
    OCRViewModel.OcrUiState s =
        base().withWords(ocrWords).withReviewedWords(revWords).withHasReviewEdits(true);
    assertEquals(revWords, s.getEffectiveWords());
  }

  // ---- chaining ----

  @Test
  public void chaining_preservesAllFields() {
    OCRViewModel.OcrUiState s =
        base().withProcessing(true).withLanguage("deu").withText("test").withDuration(500L);
    assertTrue(s.processing());
    assertEquals("deu", s.language());
    assertEquals("test", s.ocrText());
    assertEquals(Long.valueOf(500L), s.durationMs());
  }

  // ---- Event ----

  @Test
  public void event_getContentIfNotHandled_returnsOnce() {
    OCRViewModel.Event<String> event = new OCRViewModel.Event<>("data");
    assertEquals("data", event.getContentIfNotHandled());
    assertNull(event.getContentIfNotHandled());
  }

  @Test
  public void event_peek_alwaysReturns() {
    OCRViewModel.Event<String> event = new OCRViewModel.Event<>("data");
    assertEquals("data", event.peek());
    event.getContentIfNotHandled(); // consume
    assertEquals("data", event.peek()); // still returns
  }

  // ---- OcrTransform record ----

  @Test
  public void ocrTransform_fieldsAccessible() {
    OCRViewModel.OcrTransform tx = new OCRViewModel.OcrTransform(10, 20, 30, 40, 3f, 2f, 5, 6);
    assertEquals(10, tx.srcW());
    assertEquals(20, tx.srcH());
    assertEquals(30, tx.dstW());
    assertEquals(40, tx.dstH());
    assertEquals(3f, tx.scaleX(), 0.01f);
    assertEquals(2f, tx.scaleY(), 0.01f);
    assertEquals(5, tx.offsetX());
    assertEquals(6, tx.offsetY());
  }
}
