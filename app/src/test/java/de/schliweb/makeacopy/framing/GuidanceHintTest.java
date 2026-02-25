package de.schliweb.makeacopy.framing;

import static org.junit.Assert.*;

import org.junit.Test;

/** Unit tests for the {@link GuidanceHint} enum. */
public class GuidanceHintTest {

  @Test
  public void allValues_present() {
    GuidanceHint[] values = GuidanceHint.values();
    assertEquals(17, values.length);
  }

  @Test
  public void valueOf_directional() {
    assertNotNull(GuidanceHint.valueOf("MOVE_LEFT"));
    assertNotNull(GuidanceHint.valueOf("MOVE_RIGHT"));
    assertNotNull(GuidanceHint.valueOf("MOVE_UP"));
    assertNotNull(GuidanceHint.valueOf("MOVE_DOWN"));
    assertNotNull(GuidanceHint.valueOf("MOVE_CLOSER"));
    assertNotNull(GuidanceHint.valueOf("MOVE_BACK"));
  }

  @Test
  public void valueOf_tilt() {
    assertNotNull(GuidanceHint.valueOf("TILT_LEFT"));
    assertNotNull(GuidanceHint.valueOf("TILT_RIGHT"));
    assertNotNull(GuidanceHint.valueOf("TILT_FORWARD"));
    assertNotNull(GuidanceHint.valueOf("TILT_BACK"));
  }

  @Test
  public void valueOf_status() {
    assertNotNull(GuidanceHint.valueOf("OK"));
    assertNotNull(GuidanceHint.valueOf("NO_DOCUMENT_DETECTED"));
    assertNotNull(GuidanceHint.valueOf("HOLD_STILL"));
    assertNotNull(GuidanceHint.valueOf("READY_ENTER"));
    assertNotNull(GuidanceHint.valueOf("TOO_FAR"));
  }

  @Test
  public void valueOf_orientation() {
    assertNotNull(GuidanceHint.valueOf("ORIENTATION_PORTRAIT_TIP"));
    assertNotNull(GuidanceHint.valueOf("ORIENTATION_LANDSCAPE_TIP"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void valueOf_invalid_throws() {
    GuidanceHint.valueOf("NONEXISTENT");
  }
}
