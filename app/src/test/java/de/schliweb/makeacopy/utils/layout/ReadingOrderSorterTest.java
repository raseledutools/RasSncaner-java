package de.schliweb.makeacopy.utils.layout;

import static org.junit.Assert.*;

import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Unit tests for ReadingOrderSorter class. Note: Tests that require Android Rect methods are
 * limited due to JVM mocking constraints.
 */
public class ReadingOrderSorterTest {

  @Test
  public void sort_emptyList_doesNotThrow() {
    List<DocumentRegion> regions = new ArrayList<>();
    ReadingOrderSorter.sort(regions);
    assertTrue(regions.isEmpty());
  }

  @Test
  public void sort_nullList_doesNotThrow() {
    ReadingOrderSorter.sort(null);
    // Should not throw
  }

  @Test
  public void sort_singleRegion_setsReadingOrderZero() {
    List<DocumentRegion> regions = new ArrayList<>();
    regions.add(new DocumentRegion(new Rect(0, 0, 100, 100), DocumentRegion.Type.BODY));

    ReadingOrderSorter.sort(regions);

    assertEquals(0, regions.get(0).getReadingOrder());
  }

  @Test
  public void sortTableCells_sortsRowMajorOrder() {
    List<DocumentRegion> cells = new ArrayList<>();

    // Create cells in random order
    DocumentRegion cell_1_1 = new DocumentRegion(null, DocumentRegion.Type.BODY);
    cell_1_1.setRowIndex(1);
    cell_1_1.setColumnIndex(1);

    DocumentRegion cell_0_0 = new DocumentRegion(null, DocumentRegion.Type.BODY);
    cell_0_0.setRowIndex(0);
    cell_0_0.setColumnIndex(0);

    DocumentRegion cell_0_1 = new DocumentRegion(null, DocumentRegion.Type.BODY);
    cell_0_1.setRowIndex(0);
    cell_0_1.setColumnIndex(1);

    DocumentRegion cell_1_0 = new DocumentRegion(null, DocumentRegion.Type.BODY);
    cell_1_0.setRowIndex(1);
    cell_1_0.setColumnIndex(0);

    cells.add(cell_1_1);
    cells.add(cell_0_0);
    cells.add(cell_0_1);
    cells.add(cell_1_0);

    ReadingOrderSorter.sortTableCells(cells, ReadingOrderSorter.TextDirection.LTR);

    // Expected order: (0,0), (0,1), (1,0), (1,1)
    assertEquals(0, cells.get(0).getRowIndex());
    assertEquals(0, cells.get(0).getColumnIndex());

    assertEquals(0, cells.get(1).getRowIndex());
    assertEquals(1, cells.get(1).getColumnIndex());

    assertEquals(1, cells.get(2).getRowIndex());
    assertEquals(0, cells.get(2).getColumnIndex());

    assertEquals(1, cells.get(3).getRowIndex());
    assertEquals(1, cells.get(3).getColumnIndex());
  }

  @Test
  public void sortTableCells_rtl_sortsRightToLeft() {
    List<DocumentRegion> cells = new ArrayList<>();

    DocumentRegion cell_0_0 = new DocumentRegion(null, DocumentRegion.Type.BODY);
    cell_0_0.setRowIndex(0);
    cell_0_0.setColumnIndex(0);

    DocumentRegion cell_0_1 = new DocumentRegion(null, DocumentRegion.Type.BODY);
    cell_0_1.setRowIndex(0);
    cell_0_1.setColumnIndex(1);

    cells.add(cell_0_0);
    cells.add(cell_0_1);

    ReadingOrderSorter.sortTableCells(cells, ReadingOrderSorter.TextDirection.RTL);

    // In RTL, column 1 should come before column 0
    assertEquals(1, cells.get(0).getColumnIndex());
    assertEquals(0, cells.get(1).getColumnIndex());
  }

  @Test
  public void getDirectionForLanguage_returnsCorrectDirection() {
    // LTR languages
    assertEquals(
        ReadingOrderSorter.TextDirection.LTR, ReadingOrderSorter.getDirectionForLanguage("eng"));
    assertEquals(
        ReadingOrderSorter.TextDirection.LTR, ReadingOrderSorter.getDirectionForLanguage("deu"));
    assertEquals(
        ReadingOrderSorter.TextDirection.LTR, ReadingOrderSorter.getDirectionForLanguage("fra"));

    // RTL languages
    assertEquals(
        ReadingOrderSorter.TextDirection.RTL, ReadingOrderSorter.getDirectionForLanguage("ara"));
    assertEquals(
        ReadingOrderSorter.TextDirection.RTL, ReadingOrderSorter.getDirectionForLanguage("heb"));
    assertEquals(
        ReadingOrderSorter.TextDirection.RTL, ReadingOrderSorter.getDirectionForLanguage("fa"));
    assertEquals(
        ReadingOrderSorter.TextDirection.RTL, ReadingOrderSorter.getDirectionForLanguage("ur"));

    // Null/empty defaults to LTR
    assertEquals(
        ReadingOrderSorter.TextDirection.LTR, ReadingOrderSorter.getDirectionForLanguage(null));
    assertEquals(
        ReadingOrderSorter.TextDirection.LTR, ReadingOrderSorter.getDirectionForLanguage(""));
  }

  @Test
  public void getDirectionForLanguage_shortCodes() {
    assertEquals(
        ReadingOrderSorter.TextDirection.RTL, ReadingOrderSorter.getDirectionForLanguage("ar"));
    assertEquals(
        ReadingOrderSorter.TextDirection.RTL, ReadingOrderSorter.getDirectionForLanguage("he"));
  }

  @Test
  public void sortTableCells_emptyList_doesNotThrow() {
    List<DocumentRegion> cells = new ArrayList<>();
    ReadingOrderSorter.sortTableCells(cells, ReadingOrderSorter.TextDirection.LTR);
    assertTrue(cells.isEmpty());
  }

  @Test
  public void sortTableCells_singleCell_setsReadingOrderZero() {
    List<DocumentRegion> cells = new ArrayList<>();
    DocumentRegion cell = new DocumentRegion(null, DocumentRegion.Type.BODY);
    cell.setRowIndex(0);
    cell.setColumnIndex(0);
    cells.add(cell);

    ReadingOrderSorter.sortTableCells(cells, ReadingOrderSorter.TextDirection.LTR);

    assertEquals(0, cells.get(0).getReadingOrder());
  }

  @Test
  public void sortByColumns_emptyList_doesNotThrow() {
    List<DocumentRegion> regions = new ArrayList<>();
    ReadingOrderSorter.sortByColumns(regions, ReadingOrderSorter.TextDirection.LTR);
    assertTrue(regions.isEmpty());
  }

  @Test
  public void sortHierarchical_emptyList_doesNotThrow() {
    List<DocumentRegion> regions = new ArrayList<>();
    ReadingOrderSorter.sortHierarchical(regions, ReadingOrderSorter.TextDirection.LTR);
    assertTrue(regions.isEmpty());
  }

  @Test
  public void sortRecursive_emptyList_doesNotThrow() {
    List<DocumentRegion> regions = new ArrayList<>();
    ReadingOrderSorter.sortRecursive(regions, ReadingOrderSorter.TextDirection.LTR);
    assertTrue(regions.isEmpty());
  }

  @Test
  public void sortRecursive_nullList_doesNotThrow() {
    ReadingOrderSorter.sortRecursive(null, ReadingOrderSorter.TextDirection.LTR);
    // Should not throw
  }
}
