package de.schliweb.makeacopy.utils.layout;

import android.graphics.Rect;
import android.util.Log;
import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects columns in multi-column document layouts using vertical projection analysis.
 * This class analyzes binary document images to identify column boundaries
 * for proper reading order reconstruction.
 */
public class ColumnDetector {

    private static final String TAG = "ColumnDetector";

    /**
     * Minimum width of a valley (gap between columns) as fraction of image width.
     * Valleys narrower than this are ignored.
     */
    private static final double MIN_VALLEY_WIDTH_RATIO = 0.02;

    /**
     * Minimum column width as fraction of image width.
     * Columns narrower than this are merged with neighbors.
     */
    private static final double MIN_COLUMN_WIDTH_RATIO = 0.1;

    /**
     * Threshold for valley detection as fraction of maximum projection value.
     * Lower values detect more valleys.
     */
    private static final double VALLEY_THRESHOLD_RATIO = 0.1;

    /**
     * Margin to exclude from analysis (as fraction of image width).
     * Helps ignore page borders and binding areas.
     */
    private static final double MARGIN_RATIO = 0.02;

    /**
     * Detects columns in a binary document image.
     *
     * @param binaryImage binary (black text on white background) Mat image
     * @return list of DocumentRegion objects representing detected columns,
     * or a single region covering the entire image if no columns detected
     */
    public List<DocumentRegion> detectColumns(Mat binaryImage) {
        if (binaryImage == null || binaryImage.empty()) {
            Log.w(TAG, "detectColumns: empty or null image");
            return createSingleColumnResult(0, 0);
        }

        int width = binaryImage.cols();
        int height = binaryImage.rows();

        if (width < 100 || height < 100) {
            Log.d(TAG, "detectColumns: image too small for column detection");
            return createSingleColumnResult(width, height);
        }

        try {
            // 1. Compute vertical projection (count black pixels per column)
            int[] projection = computeVerticalProjection(binaryImage);

            // 2. Smooth the projection to reduce noise
            int[] smoothed = smoothProjection(projection, Math.max(5, width / 100));

            // 3. Find valleys (gaps between columns)
            List<int[]> valleys = findValleys(smoothed, width);

            // 4. Create column regions from valleys
            List<DocumentRegion> columns = createColumnsFromValleys(valleys, width, height);

            // 5. Filter and validate columns
            columns = filterColumns(columns, width, height);

            Log.d(TAG, "detectColumns: detected " + columns.size() + " column(s)");
            return columns;

        } catch (Exception e) {
            Log.e(TAG, "detectColumns: error during detection", e);
            return createSingleColumnResult(width, height);
        }
    }

    /**
     * Computes the vertical projection of a binary image.
     * For each x-coordinate, counts the number of black (foreground) pixels.
     *
     * @param binary binary image (0 = black/text, 255 = white/background)
     * @return array of pixel counts per column
     */
    private int[] computeVerticalProjection(Mat binary) {
        int width = binary.cols();
        int height = binary.rows();
        int[] projection = new int[width];

        // For efficiency, process row by row
        byte[] rowData = new byte[width];

        for (int y = 0; y < height; y++) {
            binary.get(y, 0, rowData);
            for (int x = 0; x < width; x++) {
                // In binary image: 0 = black (text), 255 = white (background)
                // Count black pixels (text)
                if ((rowData[x] & 0xFF) == 0) {
                    projection[x]++;
                }
            }
        }

        return projection;
    }

    /**
     * Smooths the projection array using a moving average filter.
     *
     * @param projection original projection values
     * @param windowSize size of the smoothing window
     * @return smoothed projection array
     */
    private int[] smoothProjection(int[] projection, int windowSize) {
        if (windowSize < 1) windowSize = 1;
        int[] smoothed = new int[projection.length];
        int halfWindow = windowSize / 2;

        for (int i = 0; i < projection.length; i++) {
            int sum = 0;
            int count = 0;
            for (int j = Math.max(0, i - halfWindow); j <= Math.min(projection.length - 1, i + halfWindow); j++) {
                sum += projection[j];
                count++;
            }
            smoothed[i] = sum / count;
        }

        return smoothed;
    }

    /**
     * Finds valleys (low points) in the projection that indicate column boundaries.
     *
     * @param projection smoothed projection values
     * @param imageWidth width of the image
     * @return list of valley ranges as [start, end] pairs
     */
    private List<int[]> findValleys(int[] projection, int imageWidth) {
        List<int[]> valleys = new ArrayList<>();

        // Find maximum projection value for threshold calculation
        int maxProjection = 0;
        for (int value : projection) {
            if (value > maxProjection) {
                maxProjection = value;
            }
        }

        if (maxProjection == 0) {
            return valleys; // Empty image
        }

        int threshold = (int) (maxProjection * VALLEY_THRESHOLD_RATIO);
        int minValleyWidth = (int) (imageWidth * MIN_VALLEY_WIDTH_RATIO);
        int margin = (int) (imageWidth * MARGIN_RATIO);

        boolean inValley = false;
        int valleyStart = 0;

        for (int i = margin; i < projection.length - margin; i++) {
            if (projection[i] < threshold) {
                if (!inValley) {
                    inValley = true;
                    valleyStart = i;
                }
            } else {
                if (inValley) {
                    inValley = false;
                    int valleyEnd = i;
                    int valleyWidth = valleyEnd - valleyStart;

                    // Only accept valleys wider than minimum
                    if (valleyWidth >= minValleyWidth) {
                        valleys.add(new int[]{valleyStart, valleyEnd});
                    }
                }
            }
        }

        // Handle valley extending to end of image
        if (inValley) {
            int valleyEnd = projection.length - margin;
            int valleyWidth = valleyEnd - valleyStart;
            if (valleyWidth >= minValleyWidth) {
                valleys.add(new int[]{valleyStart, valleyEnd});
            }
        }

        return valleys;
    }

    /**
     * Creates column regions from detected valleys.
     *
     * @param valleys list of valley ranges
     * @param width   image width
     * @param height  image height
     * @return list of column regions
     */
    private List<DocumentRegion> createColumnsFromValleys(List<int[]> valleys, int width, int height) {
        List<DocumentRegion> columns = new ArrayList<>();

        if (valleys.isEmpty()) {
            // No valleys found - single column document
            return createSingleColumnResult(width, height);
        }

        int columnStart = 0;
        int columnIndex = 0;

        for (int[] valley : valleys) {
            int valleyCenter = (valley[0] + valley[1]) / 2;

            // Create column from columnStart to valley center
            if (valleyCenter > columnStart) {
                Rect bounds = new Rect(columnStart, 0, valleyCenter, height);
                DocumentRegion column = new DocumentRegion(bounds, DocumentRegion.Type.COLUMN);
                column.setColumnIndex(columnIndex);
                column.setReadingOrder(columnIndex);
                columns.add(column);
                columnIndex++;
            }

            columnStart = valleyCenter;
        }

        // Create final column from last valley to end
        if (columnStart < width) {
            Rect bounds = new Rect(columnStart, 0, width, height);
            DocumentRegion column = new DocumentRegion(bounds, DocumentRegion.Type.COLUMN);
            column.setColumnIndex(columnIndex);
            column.setReadingOrder(columnIndex);
            columns.add(column);
        }

        return columns;
    }

    /**
     * Filters columns to remove invalid or too-narrow columns.
     *
     * @param columns detected columns
     * @param width   image width
     * @param height  image height
     * @return filtered list of valid columns
     */
    private List<DocumentRegion> filterColumns(List<DocumentRegion> columns, int width, int height) {
        if (columns.size() <= 1) {
            return columns;
        }

        int minColumnWidth = (int) (width * MIN_COLUMN_WIDTH_RATIO);
        List<DocumentRegion> filtered = new ArrayList<>();

        for (DocumentRegion column : columns) {
            if (column.getBounds().width() >= minColumnWidth) {
                filtered.add(column);
            } else {
                Log.d(TAG, "filterColumns: removing narrow column: " + column.getBounds().width() + "px");
            }
        }

        // If all columns were filtered, return single column
        if (filtered.isEmpty()) {
            return createSingleColumnResult(width, height);
        }

        // Re-index columns after filtering
        for (int i = 0; i < filtered.size(); i++) {
            filtered.get(i).setColumnIndex(i);
            filtered.get(i).setReadingOrder(i);
        }

        return filtered;
    }

    /**
     * Creates a single-column result covering the entire image.
     *
     * @param width  image width
     * @param height image height
     * @return list containing a single column region
     */
    private List<DocumentRegion> createSingleColumnResult(int width, int height) {
        List<DocumentRegion> result = new ArrayList<>();
        if (width > 0 && height > 0) {
            Rect bounds = new Rect(0, 0, width, height);
            DocumentRegion column = new DocumentRegion(bounds, DocumentRegion.Type.COLUMN);
            column.setColumnIndex(0);
            column.setReadingOrder(0);
            result.add(column);
        }
        return result;
    }

    /**
     * Estimates the number of columns in a document without full detection.
     * Useful for quick classification of document type.
     *
     * @param binaryImage binary document image
     * @return estimated number of columns (1, 2, 3, or more)
     */
    public int estimateColumnCount(Mat binaryImage) {
        List<DocumentRegion> columns = detectColumns(binaryImage);
        return columns.size();
    }

    /**
     * Checks if the document appears to be multi-column.
     *
     * @param binaryImage binary document image
     * @return true if document has 2 or more columns
     */
    public boolean isMultiColumn(Mat binaryImage) {
        return estimateColumnCount(binaryImage) > 1;
    }
}
