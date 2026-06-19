/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.ocr.paddle.layout.reading;

import android.graphics.RectF;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class PaddleLayoutFallbackColumnDetector {
    private PaddleLayoutFallbackColumnDetector() {}

    static List<PaddleLayoutReadingBlock> cluster(List<PaddleLayoutReadingLine> lines, int pageWidth,
                                                  List<String> warnings) {
        List<PaddleLayoutReadingLine> sorted = new ArrayList<>(lines == null ? Collections.emptyList() : lines);
        sorted.sort(Comparator.comparingDouble(PaddleLayoutReadingLine::centerX)
                .thenComparingDouble(l -> l.box.top));
        List<List<PaddleLayoutReadingLine>> columns = splitByStableGutter(sorted, pageWidth);
        if (columns.isEmpty()) columns = centerCluster(sorted, pageWidth);
        columns.removeIf(List::isEmpty);
        columns.sort(Comparator.comparingDouble(PaddleLayoutFallbackColumnDetector::left));
        String confidence = confidence(columns, pageWidth);
        if (hasWordBoxReconstruction(warnings) && isUnstableOverlappingSplit(columns, pageWidth, confidence)) {
            warnings.add("RECONSTRUCTION_AWARE_COLUMN_SPLIT_REJECTED columns=" + columns.size()
                    + " overlap=" + Math.round(horizontalOverlap(columns))
                    + " gutter=" + Math.round(gutter(columns)));
            columns = new ArrayList<>();
            columns.add(sorted);
            confidence = confidence(columns, pageWidth);
        }
        if (columns.size() > 1) warnings.add(confidence + "_CONFIDENCE_COLUMN_SPLIT fallbackColumns=" + columns.size()
                + " gutter=" + Math.round(gutter(columns)));
        List<PaddleLayoutReadingBlock> out = new ArrayList<>();
        List<RectF> refinedBoxes = refinedColumnBoxes(columns);
        List<RectF> visualBoxes = visualColumnBoxes(columns, refinedBoxes);
        for (int i = 0; i < columns.size(); i++) {
            List<PaddleLayoutReadingLine> column = columns.get(i);
            column.sort(Comparator.comparingDouble((PaddleLayoutReadingLine l) -> l.box.top)
                    .thenComparingDouble(l -> l.box.left));
            PaddleLayoutDiagnosticRole role = sidebarRole(column, columns, pageWidth, i);
            if (role == PaddleLayoutDiagnosticRole.SIDEBAR_CANDIDATE) warnings.add("SIDEBAR_HEURISTIC_USED column=" + i);
            out.add(new PaddleLayoutReadingBlock(0, i, null, column, false, role,
                    confidence.toLowerCase(Locale.ROOT), refinedBoxes.get(i), visualBoxes.get(i)));
        }
        addTransitionWarnings(out, pageWidth, warnings);
        return out;
    }

    private static List<List<PaddleLayoutReadingLine>> splitByStableGutter(List<PaddleLayoutReadingLine> lines,
                                                                           int pageWidth) {
        List<List<PaddleLayoutReadingLine>> out = new ArrayList<>();
        if (lines.size() < 4) return out;
        List<Float> anchors = new ArrayList<>();
        for (PaddleLayoutReadingLine line : lines) anchors.add(line.box.left);
        anchors.sort(Float::compare);
        List<Float> boundaries = new ArrayList<>();
        for (int i = 1; i < anchors.size(); i++) {
            float gap = anchors.get(i) - anchors.get(i - 1);
            int leftCount = i;
            int rightCount = anchors.size() - i;
            if (leftCount >= 2 && rightCount >= 2 && gap >= pageWidth * 0.11f) {
                boundaries.add((anchors.get(i - 1) + anchors.get(i)) * 0.5f);
            }
        }
        if (boundaries.isEmpty()) return out;
        for (int i = 0; i <= boundaries.size(); i++) out.add(new ArrayList<>());
        for (PaddleLayoutReadingLine line : lines) {
            int column = 0;
            while (column < boundaries.size() && line.box.left >= boundaries.get(column)) column++;
            out.get(column).add(line);
        }
        out.removeIf(List::isEmpty);
        return out.size() > 1 ? out : new ArrayList<>();
    }

    private static List<List<PaddleLayoutReadingLine>> centerCluster(List<PaddleLayoutReadingLine> sorted,
                                                                     int pageWidth) {
        List<List<PaddleLayoutReadingLine>> columns = new ArrayList<>();
        for (PaddleLayoutReadingLine line : sorted) {
            List<PaddleLayoutReadingLine> best = null;
            float bestDistance = Float.MAX_VALUE;
            for (List<PaddleLayoutReadingLine> column : columns) {
                float distance = Math.abs(centerX(column) - line.centerX());
                float threshold = Math.max(pageWidth * 0.10f, Math.max(width(column), line.box.width()) * 0.28f);
                if (distance < threshold && distance < bestDistance) {
                    best = column;
                    bestDistance = distance;
                }
            }
            if (best == null) {
                best = new ArrayList<>();
                columns.add(best);
            }
            best.add(line);
        }
        return columns;
    }

    private static String confidence(List<List<PaddleLayoutReadingLine>> columns, int pageWidth) {
        if (columns.size() < 2) return "single";
        float gutter = gutter(columns);
        float overlap = horizontalOverlap(columns);
        float variance = 0f;
        for (List<PaddleLayoutReadingLine> column : columns) variance += leftVariance(column);
        variance /= Math.max(1, columns.size());
        if (isStableMultiColumnSplit(columns, pageWidth, gutter, overlap, variance)) return "MEDIUM";
        if (gutter > pageWidth * 0.12f && overlap < pageWidth * 0.02f && variance < pageWidth * 0.08f) return "HIGH";
        if (gutter > pageWidth * 0.06f && overlap < pageWidth * 0.08f) return "MEDIUM";
        return "LOW";
    }

    private static boolean isUnstableOverlappingSplit(List<List<PaddleLayoutReadingLine>> columns, int pageWidth,
                                                      String confidence) {
        return columns.size() > 1
                && "LOW".equals(confidence)
                && horizontalOverlap(columns) > pageWidth * 0.15f;
    }

    private static boolean hasWordBoxReconstruction(List<String> warnings) {
        if (warnings == null) return false;
        for (String warning : warnings) {
            if (warning != null && warning.startsWith("WORD_BOX_LINE_RECONSTRUCTION activated")) return true;
        }
        return false;
    }

    private static boolean isStableMultiColumnSplit(List<List<PaddleLayoutReadingLine>> columns, int pageWidth,
                                                    float gutter, float overlap, float variance) {
        if (columns.size() < 3) return false;
        if (gutter < pageWidth * 0.035f || overlap > pageWidth * 0.02f || variance > pageWidth * 0.02f) return false;
        int minLines = Integer.MAX_VALUE;
        int maxLines = 0;
        float minHeight = Float.MAX_VALUE;
        float maxHeight = 0f;
        for (List<PaddleLayoutReadingLine> column : columns) {
            minLines = Math.min(minLines, column.size());
            maxLines = Math.max(maxLines, column.size());
            float height = bounds(column).height();
            minHeight = Math.min(minHeight, height);
            maxHeight = Math.max(maxHeight, height);
        }
        if (minLines < 4 || maxLines > minLines * 2) return false;
        return minHeight > 0f && maxHeight <= minHeight * 1.35f;
    }

    private static float gutter(List<List<PaddleLayoutReadingLine>> columns) {
        float min = Float.MAX_VALUE;
        for (int i = 1; i < columns.size(); i++) min = Math.min(min, left(columns.get(i)) - right(columns.get(i - 1)));
        return min == Float.MAX_VALUE ? 0f : min;
    }

    private static float horizontalOverlap(List<List<PaddleLayoutReadingLine>> columns) {
        float max = 0f;
        for (int i = 1; i < columns.size(); i++) max = Math.max(max, right(columns.get(i - 1)) - left(columns.get(i)));
        return Math.max(0f, max);
    }

    private static float leftVariance(List<PaddleLayoutReadingLine> lines) {
        if (lines.size() < 2) return 0f;
        float mean = 0f;
        for (PaddleLayoutReadingLine line : lines) mean += line.box.left;
        mean /= lines.size();
        float variance = 0f;
        for (PaddleLayoutReadingLine line : lines) variance += Math.abs(line.box.left - mean);
        return variance / lines.size();
    }

    private static void addTransitionWarnings(List<PaddleLayoutReadingBlock> blocks, int pageWidth,
                                              List<String> warnings) {
        for (int i = 1; i < blocks.size(); i++) {
            PaddleLayoutReadingBlock previous = blocks.get(i - 1);
            PaddleLayoutReadingBlock current = blocks.get(i);
            if (previous.columnIndex != current.columnIndex && !isStableColumnMajorSwitch(blocks, i)
                    && current.box.top < previous.box.bottom - pageWidth * 0.04f) {
                warnings.add("CROSS_COLUMN_JUMP_WARNING from=" + previous.columnIndex + " to=" + current.columnIndex);
            }
        }
    }

    private static boolean isStableColumnMajorSwitch(List<PaddleLayoutReadingBlock> blocks, int currentIndex) {
        PaddleLayoutReadingBlock previous = blocks.get(currentIndex - 1);
        PaddleLayoutReadingBlock current = blocks.get(currentIndex);
        if (current.columnIndex <= previous.columnIndex) return false;
        if (current.box.left < previous.box.left) return false;
        for (int i = currentIndex + 1; i < blocks.size(); i++) {
            PaddleLayoutReadingBlock later = blocks.get(i);
            if (later.columnIndex < current.columnIndex) return false;
        }
        return true;
    }

    private static List<RectF> refinedColumnBoxes(List<List<PaddleLayoutReadingLine>> columns) {
        List<RectF> boxes = new ArrayList<>();
        for (List<PaddleLayoutReadingLine> column : columns) boxes.add(bounds(column));
        if (boxes.size() < 2) return boxes;
        for (int i = 0; i < boxes.size() - 1; i++) {
            RectF left = boxes.get(i);
            RectF right = boxes.get(i + 1);
            float boundary = (medianRight(columns.get(i)) + medianLeft(columns.get(i + 1))) * 0.5f;
            if (boundary > left.left && boundary < right.right) {
                left.right = Math.min(left.right, boundary);
                right.left = Math.max(right.left, boundary);
            }
        }
        for (RectF box : boxes) {
            if (box.right < box.left) box.right = box.left;
            if (box.bottom < box.top) box.bottom = box.top;
        }
        return boxes;
    }

    private static List<RectF> visualColumnBoxes(List<List<PaddleLayoutReadingLine>> columns, List<RectF> refinedBoxes) {
        List<RectF> boxes = new ArrayList<>();
        for (int i = 0; i < refinedBoxes.size(); i++) boxes.add(new RectF(refinedBoxes.get(i)));
        if (boxes.size() < 2) return boxes;
        for (int i = 0; i < boxes.size(); i++) {
            RectF robust = robustContentBox(columns.get(i), i, boxes.size());
            if (robust.width() <= 0f || robust.height() <= 0f) continue;
            RectF visual = boxes.get(i);
            visual.left = robust.left;
            visual.right = robust.right;
            visual.top = Math.min(visual.top, robust.top);
            visual.bottom = Math.max(visual.bottom, robust.bottom);
        }
        return boxes;
    }

    private static RectF robustContentBox(List<PaddleLayoutReadingLine> lines, int columnIndex, int columnCount) {
        RectF full = bounds(lines);
        if (columnCount >= 3) return full;
        if (lines == null || lines.size() < 3) return full;
        List<Float> lefts = new ArrayList<>();
        List<Float> rights = new ArrayList<>();
        for (PaddleLayoutReadingLine line : lines) {
            lefts.add(line.box.left);
            rights.add(line.box.right);
        }
        lefts.sort(Float::compare);
        rights.sort(Float::compare);
        float visualLeft = columnIndex == 0 ? full.left : percentile(lefts, 0.25f);
        float visualRight = columnIndex == columnCount - 1 ? full.right : percentile(rights, 0.25f);
        return new RectF(visualLeft, full.top, visualRight, full.bottom);
    }

    private static float percentile(List<Float> values, float fraction) {
        if (values.isEmpty()) return 0f;
        int index = Math.round((values.size() - 1) * fraction);
        return values.get(Math.max(0, Math.min(values.size() - 1, index)));
    }

    private static RectF bounds(List<PaddleLayoutReadingLine> lines) {
        RectF out = new RectF();
        boolean first = true;
        for (PaddleLayoutReadingLine line : lines) {
            if (first) {
                out.set(line.box);
                first = false;
            } else {
                out.union(line.box);
            }
        }
        return out;
    }

    private static float medianLeft(List<PaddleLayoutReadingLine> lines) {
        List<Float> values = new ArrayList<>();
        for (PaddleLayoutReadingLine line : lines) values.add(line.box.left);
        values.sort(Float::compare);
        return values.isEmpty() ? 0f : values.get(values.size() / 2);
    }

    private static float medianRight(List<PaddleLayoutReadingLine> lines) {
        List<Float> values = new ArrayList<>();
        for (PaddleLayoutReadingLine line : lines) values.add(line.box.right);
        values.sort(Float::compare);
        return values.isEmpty() ? 0f : values.get(values.size() / 2);
    }

    private static PaddleLayoutDiagnosticRole sidebarRole(List<PaddleLayoutReadingLine> column,
                                                         List<List<PaddleLayoutReadingLine>> columns,
                                                         int pageWidth, int index) {
        if (columns.size() < 2) return PaddleLayoutDiagnosticRole.UNASSIGNED_COLUMN;
        float w = width(column);
        float max = 0f;
        for (List<PaddleLayoutReadingLine> c : columns) max = Math.max(max, width(c));
        boolean edge = left(column) > pageWidth * 0.55f || right(column) < pageWidth * 0.45f;
        boolean narrow = w < max * 0.72f;
        return edge && narrow && index > 0 ? PaddleLayoutDiagnosticRole.SIDEBAR_CANDIDATE
                : PaddleLayoutDiagnosticRole.UNASSIGNED_COLUMN;
    }

    private static float centerX(List<PaddleLayoutReadingLine> lines) {
        return (left(lines) + right(lines)) * 0.5f;
    }

    private static float width(List<PaddleLayoutReadingLine> lines) {
        return Math.max(1f, right(lines) - left(lines));
    }

    private static float left(List<PaddleLayoutReadingLine> lines) {
        float value = Float.MAX_VALUE;
        for (PaddleLayoutReadingLine line : lines) value = Math.min(value, line.box.left);
        return value == Float.MAX_VALUE ? 0f : value;
    }

    private static float right(List<PaddleLayoutReadingLine> lines) {
        float value = 0f;
        for (PaddleLayoutReadingLine line : lines) value = Math.max(value, line.box.right);
        return value;
    }
}