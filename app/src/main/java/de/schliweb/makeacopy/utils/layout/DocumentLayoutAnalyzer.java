package de.schliweb.makeacopy.utils.layout;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;
import lombok.Getter;
import lombok.Setter;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * Main class for document layout analysis.
 * Combines column detection, table detection, and region classification
 * to provide comprehensive document structure analysis for improved OCR.
 */
public class DocumentLayoutAnalyzer {

    private static final String TAG = "DocumentLayoutAnalyzer";

    /**
     * Header region detection: top percentage of page to consider as header.
     */
    private static final double HEADER_REGION_RATIO = 0.12;

    /**
     * Footer region detection: bottom percentage of page to consider as footer.
     */
    private static final double FOOTER_REGION_RATIO = 0.08;

    /**
     * Minimum content density to consider a region as containing text.
     */
    private static final double MIN_CONTENT_DENSITY = 0.01;

    /**
     * Maximum content density (above this is likely an image/figure).
     */
    private static final double MAX_TEXT_DENSITY = 0.5;

    private final ColumnDetector columnDetector;
    private final TableDetector tableDetector;

    @Getter
    @Setter
    private String language = "eng";

    @Getter
    @Setter
    private boolean detectTables = true;

    @Getter
    @Setter
    private boolean detectColumns = true;

    @Getter
    @Setter
    private boolean detectHeaderFooter = true;

    /**
     * Creates a new DocumentLayoutAnalyzer with default settings.
     */
    public DocumentLayoutAnalyzer() {
        this.columnDetector = new ColumnDetector();
        this.tableDetector = new TableDetector();
    }

    /**
     * Analyzes the layout of a document image.
     *
     * @param bitmap input document image
     * @return list of detected document regions sorted by reading order
     */
    public List<DocumentRegion> analyze(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            Log.w(TAG, "analyze: null or recycled bitmap");
            return new ArrayList<>();
        }

        List<DocumentRegion> regions = new ArrayList<>();
        Mat rgba = new Mat();
        Mat gray = new Mat();
        Mat binary = new Mat();

        try {
            // Convert bitmap to OpenCV Mat
            Utils.bitmapToMat(bitmap, rgba);
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY);

            // Binarize for analysis
            Imgproc.threshold(gray, binary, 0, 255, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU);

            int width = binary.cols();
            int height = binary.rows();

            // 1. Detect header and footer regions
            if (detectHeaderFooter) {
                DocumentRegion header = detectHeader(binary, width, height);
                if (header != null) {
                    regions.add(header);
                }

                DocumentRegion footer = detectFooter(binary, width, height);
                if (footer != null) {
                    regions.add(footer);
                }
            }

            // 2. Detect tables
            List<DocumentRegion> tables = new ArrayList<>();
            if (detectTables) {
                tables = tableDetector.detectTables(binary);
                regions.addAll(tables);
            }

            // 3. Detect columns in remaining area (excluding header, footer, tables)
            if (detectColumns) {
                List<DocumentRegion> columns = detectColumnsExcluding(binary, regions, width, height);
                regions.addAll(columns);
            }

            // 4. Detect figures/images
            List<DocumentRegion> figures = detectFigures(binary, regions, width, height);
            regions.addAll(figures);

            // 5. If no specific regions found, create a single body region
            if (regions.isEmpty()) {
                Rect bodyBounds = new Rect(0, 0, width, height);
                DocumentRegion body = new DocumentRegion(bodyBounds, DocumentRegion.Type.BODY);
                regions.add(body);
            }

            // 6. Sort regions by reading order
            ReadingOrderSorter.TextDirection direction =
                    ReadingOrderSorter.getDirectionForLanguage(language);
            ReadingOrderSorter.sortHierarchical(regions, direction);

            Log.d(TAG, "analyze: detected " + regions.size() + " region(s)");

        } catch (Exception e) {
            Log.e(TAG, "analyze: error during layout analysis", e);
        } finally {
            rgba.release();
            gray.release();
            binary.release();
        }

        return regions;
    }

    /**
     * Analyzes layout from an already binarized Mat image.
     *
     * @param binaryImage binary (black text on white) Mat image
     * @return list of detected document regions
     */
    public List<DocumentRegion> analyzeFromBinary(Mat binaryImage) {
        if (binaryImage == null || binaryImage.empty()) {
            Log.w(TAG, "analyzeFromBinary: null or empty image");
            return new ArrayList<>();
        }

        List<DocumentRegion> regions = new ArrayList<>();

        try {
            int width = binaryImage.cols();
            int height = binaryImage.rows();

            // 1. Detect header and footer
            if (detectHeaderFooter) {
                DocumentRegion header = detectHeader(binaryImage, width, height);
                if (header != null) {
                    regions.add(header);
                }

                DocumentRegion footer = detectFooter(binaryImage, width, height);
                if (footer != null) {
                    regions.add(footer);
                }
            }

            // 2. Detect tables
            List<DocumentRegion> tables = new ArrayList<>();
            if (detectTables) {
                tables = tableDetector.detectTables(binaryImage);
                regions.addAll(tables);
            }

            // 3. Detect columns
            if (detectColumns) {
                List<DocumentRegion> columns = detectColumnsExcluding(binaryImage, regions, width, height);
                regions.addAll(columns);
            }

            // 4. Detect figures
            List<DocumentRegion> figures = detectFigures(binaryImage, regions, width, height);
            regions.addAll(figures);

            // 5. Fallback to single body region
            if (regions.isEmpty()) {
                Rect bodyBounds = new Rect(0, 0, width, height);
                DocumentRegion body = new DocumentRegion(bodyBounds, DocumentRegion.Type.BODY);
                regions.add(body);
            }

            // 6. Sort by reading order
            ReadingOrderSorter.TextDirection direction =
                    ReadingOrderSorter.getDirectionForLanguage(language);
            ReadingOrderSorter.sortHierarchical(regions, direction);

        } catch (Exception e) {
            Log.e(TAG, "analyzeFromBinary: error", e);
        }

        return regions;
    }

    /**
     * Detects the header region at the top of the page.
     *
     * @param binary binary image
     * @param width  image width
     * @param height image height
     * @return header region or null if not detected
     */
    private DocumentRegion detectHeader(Mat binary, int width, int height) {
        int headerHeight = (int) (height * HEADER_REGION_RATIO);
        if (headerHeight < 20) return null;

        // Extract header region
        org.opencv.core.Rect headerRect = new org.opencv.core.Rect(0, 0, width, headerHeight);
        Mat headerRegion = binary.submat(headerRect);

        // Check if region has content
        double density = calculateContentDensity(headerRegion);
        headerRegion.release();

        if (density > MIN_CONTENT_DENSITY && density < MAX_TEXT_DENSITY) {
            Rect bounds = new Rect(0, 0, width, headerHeight);
            DocumentRegion header = new DocumentRegion(bounds, DocumentRegion.Type.HEADER);
            header.setConfidence((float) Math.min(1.0, density * 10));
            return header;
        }

        return null;
    }

    /**
     * Detects the footer region at the bottom of the page.
     *
     * @param binary binary image
     * @param width  image width
     * @param height image height
     * @return footer region or null if not detected
     */
    private DocumentRegion detectFooter(Mat binary, int width, int height) {
        int footerHeight = (int) (height * FOOTER_REGION_RATIO);
        if (footerHeight < 20) return null;

        int footerTop = height - footerHeight;

        // Extract footer region
        org.opencv.core.Rect footerRect = new org.opencv.core.Rect(0, footerTop, width, footerHeight);
        Mat footerRegion = binary.submat(footerRect);

        // Check if region has content
        double density = calculateContentDensity(footerRegion);
        footerRegion.release();

        if (density > MIN_CONTENT_DENSITY && density < MAX_TEXT_DENSITY) {
            Rect bounds = new Rect(0, footerTop, width, height);
            DocumentRegion footer = new DocumentRegion(bounds, DocumentRegion.Type.FOOTER);
            footer.setConfidence((float) Math.min(1.0, density * 10));
            return footer;
        }

        return null;
    }

    /**
     * Detects columns in the document, excluding already detected regions.
     *
     * @param binary          binary image
     * @param existingRegions already detected regions to exclude
     * @param width           image width
     * @param height          image height
     * @return list of column regions
     */
    private List<DocumentRegion> detectColumnsExcluding(Mat binary, List<DocumentRegion> existingRegions,
                                                        int width, int height) {
        // Calculate body area (excluding header and footer)
        int bodyTop = 0;
        int bodyBottom = height;

        for (DocumentRegion region : existingRegions) {
            if (region.getType() == DocumentRegion.Type.HEADER) {
                bodyTop = Math.max(bodyTop, region.getBounds().bottom);
            } else if (region.getType() == DocumentRegion.Type.FOOTER) {
                bodyBottom = Math.min(bodyBottom, region.getBounds().top);
            }
        }

        if (bodyBottom <= bodyTop) {
            return new ArrayList<>();
        }

        // Extract body region for column detection
        org.opencv.core.Rect bodyRect = new org.opencv.core.Rect(0, bodyTop, width, bodyBottom - bodyTop);
        Mat bodyRegion = binary.submat(bodyRect);

        // Detect columns
        List<DocumentRegion> columns = columnDetector.detectColumns(bodyRegion);
        bodyRegion.release();

        // Adjust column bounds to absolute coordinates
        for (DocumentRegion column : columns) {
            Rect bounds = column.getBounds();
            column.setBounds(new Rect(bounds.left, bounds.top + bodyTop,
                    bounds.right, bounds.bottom + bodyTop));
        }

        // Remove columns that overlap with tables
        List<DocumentRegion> filteredColumns = new ArrayList<>();
        for (DocumentRegion column : columns) {
            boolean overlapsTable = false;
            for (DocumentRegion existing : existingRegions) {
                if (existing.getType() == DocumentRegion.Type.TABLE &&
                        column.intersects(existing)) {
                    overlapsTable = true;
                    break;
                }
            }
            if (!overlapsTable) {
                filteredColumns.add(column);
            }
        }

        return filteredColumns;
    }

    /**
     * Detects figure/image regions in the document.
     *
     * @param binary          binary image
     * @param existingRegions already detected regions
     * @param width           image width
     * @param height          image height
     * @return list of figure regions
     */
    private List<DocumentRegion> detectFigures(Mat binary, List<DocumentRegion> existingRegions,
                                               int width, int height) {
        List<DocumentRegion> figures = new ArrayList<>();

        // Find large contours that might be figures
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(binary.clone(), contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        double minFigureArea = width * height * 0.02; // At least 2% of page
        double maxFigureArea = width * height * 0.5;  // At most 50% of page

        for (MatOfPoint contour : contours) {
            org.opencv.core.Rect boundingRect = Imgproc.boundingRect(contour);
            double area = boundingRect.width * boundingRect.height;

            if (area >= minFigureArea && area <= maxFigureArea) {
                // Check aspect ratio (figures are usually not too elongated)
                double aspectRatio = (double) boundingRect.width / boundingRect.height;
                if (aspectRatio > 0.2 && aspectRatio < 5.0) {
                    // Check content density (figures have high density)
                    Mat figureRegion = binary.submat(boundingRect);
                    double density = calculateContentDensity(figureRegion);
                    figureRegion.release();

                    if (density > MAX_TEXT_DENSITY) {
                        Rect bounds = new Rect(boundingRect.x, boundingRect.y,
                                boundingRect.x + boundingRect.width,
                                boundingRect.y + boundingRect.height);

                        // Check if overlaps with existing regions
                        DocumentRegion figure = new DocumentRegion(bounds, DocumentRegion.Type.FIGURE);
                        boolean overlaps = false;
                        for (DocumentRegion existing : existingRegions) {
                            if (figure.intersects(existing)) {
                                overlaps = true;
                                break;
                            }
                        }

                        if (!overlaps) {
                            figure.setConfidence((float) density);
                            figures.add(figure);
                        }
                    }
                }
            }
            contour.release();
        }
        hierarchy.release();

        return figures;
    }

    /**
     * Calculates the content density of a binary region.
     *
     * @param region binary image region
     * @return density value (0.0 to 1.0)
     */
    private double calculateContentDensity(Mat region) {
        if (region.empty()) return 0.0;

        int nonZero = Core.countNonZero(region);
        int total = region.rows() * region.cols();
        return (double) nonZero / total;
    }

    /**
     * Quick check if document has complex layout (multiple columns or tables).
     *
     * @param bitmap input document image
     * @return true if complex layout detected
     */
    public boolean hasComplexLayout(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return false;
        }

        Mat rgba = new Mat();
        Mat gray = new Mat();
        Mat binary = new Mat();

        try {
            Utils.bitmapToMat(bitmap, rgba);
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY);
            Imgproc.threshold(gray, binary, 0, 255, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU);

            // Check for multiple columns
            boolean multiColumn = columnDetector.isMultiColumn(binary);

            // Check for tables
            boolean hasTables = tableDetector.containsTables(binary);

            return multiColumn || hasTables;

        } catch (Exception e) {
            Log.e(TAG, "hasComplexLayout: error", e);
            return false;
        } finally {
            rgba.release();
            gray.release();
            binary.release();
        }
    }

    /**
     * Gets the estimated number of columns in the document.
     *
     * @param bitmap input document image
     * @return number of columns (1 for single column)
     */
    public int getColumnCount(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return 1;
        }

        Mat rgba = new Mat();
        Mat gray = new Mat();
        Mat binary = new Mat();

        try {
            Utils.bitmapToMat(bitmap, rgba);
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY);
            Imgproc.threshold(gray, binary, 0, 255, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU);

            return columnDetector.estimateColumnCount(binary);

        } catch (Exception e) {
            Log.e(TAG, "getColumnCount: error", e);
            return 1;
        } finally {
            rgba.release();
            gray.release();
            binary.release();
        }
    }

    /**
     * Performs full analysis and returns detailed results.
     *
     * @param bitmap input document image
     * @return analysis result with regions and metadata
     */
    public AnalysisResult analyzeWithMetadata(Bitmap bitmap) {
        List<DocumentRegion> regions = analyze(bitmap);

        int columnCount = 1;
        boolean hasTable = false;
        boolean hasHeader = false;
        boolean hasFooter = false;

        for (DocumentRegion region : regions) {
            switch (region.getType()) {
                case COLUMN:
                    columnCount = Math.max(columnCount, region.getColumnIndex() + 1);
                    break;
                case TABLE:
                    hasTable = true;
                    break;
                case HEADER:
                    hasHeader = true;
                    break;
                case FOOTER:
                    hasFooter = true;
                    break;
            }
        }

        ReadingOrderSorter.TextDirection direction =
                ReadingOrderSorter.getDirectionForLanguage(language);

        return new AnalysisResult(regions, columnCount, hasTable, hasHeader, hasFooter, direction);
    }

    /**
         * Result class containing analysis results with additional metadata.
         */
        public record AnalysisResult(List<DocumentRegion> regions, int columnCount, boolean hasTable, boolean hasHeader,
                                     boolean hasFooter, ReadingOrderSorter.TextDirection textDirection) {
    }
}
