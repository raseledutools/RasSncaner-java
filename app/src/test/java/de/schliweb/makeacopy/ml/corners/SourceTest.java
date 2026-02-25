package de.schliweb.makeacopy.ml.corners;

import static org.junit.Assert.*;

import org.junit.Test;

/** Unit tests for the {@link Source} enum. */
public class SourceTest {

  @Test
  public void allValues_present() {
    Source[] values = Source.values();
    assertEquals(3, values.length);
    assertNotNull(Source.valueOf("DOCQUAD"));
    assertNotNull(Source.valueOf("OPENCV"));
    assertNotNull(Source.valueOf("FALLBACK"));
  }

  @Test
  public void ordinal_order() {
    assertEquals(0, Source.DOCQUAD.ordinal());
    assertEquals(1, Source.OPENCV.ordinal());
    assertEquals(2, Source.FALLBACK.ordinal());
  }

  @Test(expected = IllegalArgumentException.class)
  public void valueOf_invalid_throws() {
    Source.valueOf("INVALID");
  }
}
