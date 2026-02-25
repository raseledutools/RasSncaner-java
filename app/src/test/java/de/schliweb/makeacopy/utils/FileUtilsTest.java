package de.schliweb.makeacopy.utils;

import static org.junit.Assert.*;

import org.junit.Test;

public class FileUtilsTest {

  // --- firstUriFromJson ---

  @Test
  public void firstUriFromJson_null_returnsNull() {
    assertNull(FileUtils.firstUriFromJson(null));
  }

  @Test
  public void firstUriFromJson_empty_returnsNull() {
    assertNull(FileUtils.firstUriFromJson(""));
  }

  @Test
  public void firstUriFromJson_noQuotes_returnsNull() {
    assertNull(FileUtils.firstUriFromJson("[]"));
  }

  @Test
  public void firstUriFromJson_singleUri() {
    assertEquals(
        "content://com.example/doc/1",
        FileUtils.firstUriFromJson("[\"content://com.example/doc/1\"]"));
  }

  @Test
  public void firstUriFromJson_multipleUris_returnsFirst() {
    assertEquals(
        "file:///a.pdf",
        FileUtils.firstUriFromJson("[\"file:///a.pdf\", \"file:///b.pdf\"]"));
  }

  @Test
  public void firstUriFromJson_withEscapedQuotes() {
    // The escaped quote inside should be skipped
    String json = "[\"hello\"]";
    assertEquals("hello", FileUtils.firstUriFromJson(json));
  }

  @Test
  public void firstUriFromJson_noClosingQuote_returnsNull() {
    assertNull(FileUtils.firstUriFromJson("[\"unclosed"));
  }

  @Test
  public void firstUriFromJson_emptyString_returnsEmpty() {
    assertEquals("", FileUtils.firstUriFromJson("[\"\"]"));
  }

  @Test
  public void firstUriFromJson_objectWrapped_returnsFirstQuotedString() {
    // firstUriFromJson returns the first quoted string, which is the key "uri"
    assertEquals("uri", FileUtils.firstUriFromJson("{\"uri\": \"content://x\"}"));
  }
}
