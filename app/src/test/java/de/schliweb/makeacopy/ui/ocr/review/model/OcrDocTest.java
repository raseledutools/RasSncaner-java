package de.schliweb.makeacopy.ui.ocr.review.model;

import static org.junit.Assert.*;

import org.junit.Test;

/** JVM unit tests for {@link OcrDoc} data model. */
public class OcrDocTest {

  @Test
  public void defaultDoc_hasSchemaOne() {
    OcrDoc doc = new OcrDoc();
    assertEquals(1, doc.schema);
  }

  @Test
  public void defaultDoc_hasEmptyLists() {
    OcrDoc doc = new OcrDoc();
    assertNotNull(doc.words);
    assertTrue(doc.words.isEmpty());
    assertNotNull(doc.lines);
    assertTrue(doc.lines.isEmpty());
    assertNotNull(doc.blocks);
    assertTrue(doc.blocks.isEmpty());
  }

  @Test
  public void defaultDoc_imageSizeZero() {
    OcrDoc doc = new OcrDoc();
    assertNotNull(doc.imageSize);
    assertEquals(0, doc.imageSize.w);
    assertEquals(0, doc.imageSize.h);
  }

  @Test
  public void imageSize_paramConstructor() {
    OcrDoc.ImageSize size = new OcrDoc.ImageSize(1920, 1080);
    assertEquals(1920, size.w);
    assertEquals(1080, size.h);
  }

  @Test
  public void word_defaultValues() {
    OcrDoc.Word w = new OcrDoc.Word();
    assertEquals(0, w.id);
    assertNull(w.t);
    assertNotNull(w.b);
    assertEquals(4, w.b.length);
    assertEquals(0f, w.c, 0.001f);
    assertFalse(w.e);
    assertEquals(0, w.l);
    assertEquals(0, w.k);
    assertNull(w.lang);
  }

  @Test
  public void word_setFields() {
    OcrDoc.Word w = new OcrDoc.Word();
    w.id = 42;
    w.t = "hello";
    w.b[0] = 10;
    w.b[1] = 20;
    w.b[2] = 100;
    w.b[3] = 30;
    w.c = 0.95f;
    w.e = true;
    w.l = 1;
    w.k = 2;
    w.lang = "eng";

    assertEquals(42, w.id);
    assertEquals("hello", w.t);
    assertEquals(10, w.b[0]);
    assertEquals(0.95f, w.c, 0.001f);
    assertTrue(w.e);
    assertEquals("eng", w.lang);
  }

  @Test
  public void line_defaultValues() {
    OcrDoc.Line line = new OcrDoc.Line();
    assertEquals(0, line.id);
    assertNull(line.w);
    assertNotNull(line.b);
    assertEquals(4, line.b.length);
  }

  @Test
  public void block_defaultValues() {
    OcrDoc.Block block = new OcrDoc.Block();
    assertEquals(0, block.id);
    assertNull(block.l);
    assertNotNull(block.b);
    assertEquals(4, block.b.length);
  }

  @Test
  public void addWordsToDoc() {
    OcrDoc doc = new OcrDoc();
    OcrDoc.Word w1 = new OcrDoc.Word();
    w1.id = 1;
    w1.t = "first";
    OcrDoc.Word w2 = new OcrDoc.Word();
    w2.id = 2;
    w2.t = "second";
    doc.words.add(w1);
    doc.words.add(w2);
    assertEquals(2, doc.words.size());
    assertEquals("first", doc.words.get(0).t);
    assertEquals("second", doc.words.get(1).t);
  }
}
