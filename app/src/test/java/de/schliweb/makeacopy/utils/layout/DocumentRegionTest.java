package de.schliweb.makeacopy.utils.layout;

import static org.junit.Assert.*;

import android.graphics.Rect;
import org.junit.Test;

/**
 * Unit tests for DocumentRegion class. Note: Tests that require Android Rect methods (equals,
 * contains, width, height, intersects) are skipped in JVM unit tests due to Android framework
 * mocking limitations. These should be tested in instrumented tests instead.
 */
public class DocumentRegionTest {

  @Test
  public void constructor_withBoundsAndType_createsRegion() {
    Rect bounds = new Rect(0, 0, 100, 200);
    DocumentRegion region = new DocumentRegion(bounds, DocumentRegion.Type.BODY);

    // Check bounds reference (not equals due to Android mocking)
    assertNotNull(region.getBounds());
    assertEquals(DocumentRegion.Type.BODY, region.getType());
    assertEquals(1.0f, region.getConfidence(), 0.001f);
    assertEquals(0, region.getReadingOrder());
    assertFalse(region.hasChildren());
  }

  @Test
  public void constructor_withFullParams_createsRegion() {
    Rect bounds = new Rect(10, 20, 110, 220);
    DocumentRegion region = new DocumentRegion(bounds, DocumentRegion.Type.HEADER, 0.85f, 5);

    assertNotNull(region.getBounds());
    assertEquals(DocumentRegion.Type.HEADER, region.getType());
    assertEquals(0.85f, region.getConfidence(), 0.001f);
    assertEquals(5, region.getReadingOrder());
  }

  @Test
  public void getArea_withNullBounds_returnsZero() {
    DocumentRegion region = new DocumentRegion(null, DocumentRegion.Type.BODY);

    assertEquals(0, region.getArea());
  }

  @Test
  public void addChild_addsChildAndSetsParent() {
    Rect parentBounds = new Rect(0, 0, 500, 800);
    Rect childBounds = new Rect(10, 10, 100, 100);
    DocumentRegion parent = new DocumentRegion(parentBounds, DocumentRegion.Type.BODY);
    DocumentRegion child = new DocumentRegion(childBounds, DocumentRegion.Type.COLUMN);

    parent.addChild(child);

    assertTrue(parent.hasChildren());
    assertEquals(1, parent.getChildren().size());
    assertEquals(parent, child.getParent());
  }

  @Test
  public void getOptimalPsm_returnsCorrectValues() {
    assertEquals(6, new DocumentRegion(null, DocumentRegion.Type.HEADER).getOptimalPsm());
    assertEquals(3, new DocumentRegion(null, DocumentRegion.Type.BODY).getOptimalPsm());
    assertEquals(7, new DocumentRegion(null, DocumentRegion.Type.FOOTER).getOptimalPsm());
    assertEquals(11, new DocumentRegion(null, DocumentRegion.Type.TABLE).getOptimalPsm());
    assertEquals(7, new DocumentRegion(null, DocumentRegion.Type.CAPTION).getOptimalPsm());
    assertEquals(4, new DocumentRegion(null, DocumentRegion.Type.COLUMN).getOptimalPsm());
    assertEquals(3, new DocumentRegion(null, DocumentRegion.Type.UNKNOWN).getOptimalPsm());
  }

  @Test
  public void toString_containsType() {
    DocumentRegion region = new DocumentRegion(null, DocumentRegion.Type.HEADER);

    String str = region.toString();
    assertTrue(str.contains("HEADER"));
  }

  @Test
  public void settersAndGetters_workCorrectly() {
    DocumentRegion region = new DocumentRegion(null, DocumentRegion.Type.BODY);

    region.setConfidence(0.75f);
    assertEquals(0.75f, region.getConfidence(), 0.001f);

    region.setReadingOrder(10);
    assertEquals(10, region.getReadingOrder());

    region.setColumnIndex(2);
    assertEquals(2, region.getColumnIndex());

    region.setRowIndex(3);
    assertEquals(3, region.getRowIndex());

    region.setLanguage("deu");
    assertEquals("deu", region.getLanguage());
  }

  @Test
  public void hasChildren_withNoChildren_returnsFalse() {
    DocumentRegion region = new DocumentRegion(null, DocumentRegion.Type.BODY);
    assertFalse(region.hasChildren());
  }

  @Test
  public void hasChildren_withChildren_returnsTrue() {
    DocumentRegion parent = new DocumentRegion(null, DocumentRegion.Type.BODY);
    DocumentRegion child = new DocumentRegion(null, DocumentRegion.Type.COLUMN);

    parent.addChild(child);

    assertTrue(parent.hasChildren());
  }
}
