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

import de.schliweb.makeacopy.utils.ocr.OCRHelper;
import de.schliweb.makeacopy.utils.ocr.RecognizedWord;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.PaddleLayoutClass;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.PaddleLayoutRegion;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.PaddleLayoutResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PaddleLayoutReadingOrderResolver {
    public enum RegionSelectionMode {
        MAPPING_TEXTUAL_HINTS,
        RAW_ID_GEOMETRY_ONLY
    }

    private static final float MIN_REGION_CONFIDENCE = 0.20f;
    private static final float MIN_REGION_AREA_RATIO = 0.0004f;
    private static final Set<PaddleLayoutClass> TEXTUAL_LAYOUT_HINTS = Set.of(
            PaddleLayoutClass.ABSTRACT,
            PaddleLayoutClass.ASIDE_TEXT,
            PaddleLayoutClass.TEXT,
            PaddleLayoutClass.DOCUMENT_TITLE,
            PaddleLayoutClass.PARAGRAPH_TITLE,
            PaddleLayoutClass.HEADER,
            PaddleLayoutClass.FOOTER,
            PaddleLayoutClass.INLINE_FORMULA,
            PaddleLayoutClass.TABLE
    );

    private final RegionSelectionMode regionSelectionMode;

    public PaddleLayoutReadingOrderResolver() {
        this(RegionSelectionMode.MAPPING_TEXTUAL_HINTS);
    }

    public PaddleLayoutReadingOrderResolver(RegionSelectionMode regionSelectionMode) {
        this.regionSelectionMode = regionSelectionMode == null
                ? RegionSelectionMode.MAPPING_TEXTUAL_HINTS : regionSelectionMode;
    }

    public PaddleLayoutReadingDebugReport resolve(PaddleLayoutResult layout, OCRHelper.OcrResultWords ocr) {
        List<String> warnings = new ArrayList<>();
        List<PaddleLayoutRegion> regions = filterRegions(layout, warnings, regionSelectionMode);
        List<PaddleLayoutReadingLine> lines = buildLines(ocr == null ? Collections.emptyList() : ocr.words);
        List<PaddleLayoutReconstructionDiagnostic> reconstructionDiagnostics = new ArrayList<>();
        lines = PaddleLayoutWordBoxLineReconstructor.reconstruct(lines, layout.sourceWidth, layout.sourceHeight,
                ocr == null ? 0 : ocr.words.size(), warnings, reconstructionDiagnostics);
        lines = PaddleLayoutLineSplitter.split(lines, layout.sourceWidth, warnings);
        addResidualWideLineDiagnostics(lines, layout.sourceWidth, ocr == null ? 0 : ocr.words.size(),
                reconstructionDiagnostics);
        if (!reconstructionDiagnostics.isEmpty()) {
            List<PaddleLayoutReconstructionDiagnostic> safetyDiagnostics = new ArrayList<>();
            lines = PaddleLayoutWordBoxLineReconstructor.applyResidualSafetyGate(lines, layout.sourceWidth, warnings,
                    safetyDiagnostics);
            reconstructionDiagnostics.clear();
            reconstructionDiagnostics.addAll(safetyDiagnostics);
            addResidualWideLineDiagnostics(lines, layout.sourceWidth, ocr == null ? 0 : ocr.words.size(),
                    reconstructionDiagnostics);
        }
        Map<PaddleLayoutRegion, List<PaddleLayoutReadingLine>> assigned = new HashMap<>();
        List<PaddleLayoutRegionAssignment> assignments = new ArrayList<>();
        List<PaddleLayoutReadingLine> unassigned = new ArrayList<>();
        for (PaddleLayoutRegion region : regions) assigned.put(region, new ArrayList<>());
        for (PaddleLayoutReadingLine line : lines) {
            PaddleLayoutRegionAssignment assignment = PaddleLayoutRegionAssignment.best(line, regions);
            assignments.add(assignment);
            if (assignment.region == null) unassigned.add(line);
            else {
                List<PaddleLayoutReadingLine> regionLines = assigned.get(assignment.region);
                if (regionLines != null) regionLines.add(line);
                else unassigned.add(line);
            }
        }

        List<RectF> columnBoxes = new ArrayList<>();
        Map<PaddleLayoutRegion, RectF> regionBoxes = new HashMap<>();
        for (PaddleLayoutRegion region : regions) {
            RectF box = new RectF(region.left, region.top, region.right, region.bottom);
            columnBoxes.add(box);
            regionBoxes.put(region, box);
        }
        Map<RectF, Integer> columns = PaddleLayoutColumnDetector.assignColumns(columnBoxes, layout.sourceWidth);
        List<PaddleLayoutReadingBlock> candidates = new ArrayList<>();
        for (PaddleLayoutRegion region : regions) {
            List<PaddleLayoutReadingLine> blockLines = assigned.get(region);
            if (blockLines == null) blockLines = new ArrayList<>();
            sortLines(blockLines);
            RectF box = regionBoxes.get(region);
            Integer columnValue = columns.get(box);
            int column = columnValue == null ? 0 : columnValue;
            boolean opaque = region.semanticClass == PaddleLayoutClass.TABLE;
            if (!blockLines.isEmpty() || opaque) {
                if (shouldSplitRegionBlock(region, blockLines, layout.sourceWidth)) {
                    List<PaddleLayoutReadingBlock> splitBlocks = PaddleLayoutFallbackColumnDetector.cluster(
                            blockLines, layout.sourceWidth, warnings);
                    for (PaddleLayoutReadingBlock splitBlock : splitBlocks) {
                        candidates.add(new PaddleLayoutReadingBlock(0, splitBlock.columnIndex, null,
                                splitBlock.lines, false, splitBlock.role, splitBlock.columnConfidence,
                                splitBlock.box, splitBlock.visualBox));
                    }
                } else {
                    candidates.add(new PaddleLayoutReadingBlock(0, column, region, blockLines, opaque));
                }
            }
        }
        if (!unassigned.isEmpty()) {
            sortLines(unassigned);
            if (regions.isEmpty()) warnings.add("ALL_LINES_UNASSIGNED count=" + unassigned.size());
            candidates.addAll(PaddleLayoutFallbackColumnDetector.cluster(unassigned, layout.sourceWidth, warnings));
        }
        candidates.sort(Comparator
                .comparingInt((PaddleLayoutReadingBlock b) -> b.columnIndex)
                .thenComparingDouble(b -> b.box.top)
                .thenComparingDouble(b -> b.box.left));
        List<PaddleLayoutReadingBlock> blocks = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            PaddleLayoutReadingBlock b = candidates.get(i);
            blocks.add(new PaddleLayoutReadingBlock(i + 1, b.columnIndex, b.region, b.lines, b.opaque,
                    b.role, b.columnConfidence, b.box, b.visualBox));
        }
        PaddleLayoutReadingQuality quality = PaddleLayoutReadingQuality.analyze(blocks, assignments, lines,
                lines.size() - unassigned.size(), unassigned.size(), layout.sourceWidth, layout.sourceHeight,
                ocr == null ? 0 : ocr.words.size());
        return new PaddleLayoutReadingDebugReport(blocks, layout.sourceWidth, layout.sourceHeight,
                layout.regions.size(), regions.size(), ocr == null ? 0 : ocr.words.size(),
                lines.size() - unassigned.size(), unassigned.size(), warnings, quality, reconstructionDiagnostics);
    }

    private static List<PaddleLayoutRegion> filterRegions(PaddleLayoutResult layout, List<String> warnings,
                                                          RegionSelectionMode regionSelectionMode) {
        List<PaddleLayoutRegion> out = new ArrayList<>();
        float pageArea = Math.max(1f, layout.sourceWidth * layout.sourceHeight);
        for (PaddleLayoutRegion region : layout.regions) {
            if (!isRegionCandidate(region, layout.sourceWidth, layout.sourceHeight, regionSelectionMode)) continue;
            if (region.confidence < MIN_REGION_CONFIDENCE) continue;
            float areaRatio = Math.max(0f, region.right - region.left) * Math.max(0f, region.bottom - region.top) / pageArea;
            if (areaRatio < MIN_REGION_AREA_RATIO) continue;
            boolean clipped = region.left <= 1f || region.top <= 1f || region.right >= layout.sourceWidth - 1f
                    || region.bottom >= layout.sourceHeight - 1f;
            if (clipped && region.semanticClass != PaddleLayoutClass.HEADER) {
                warnings.add("suppressed clipped " + region.label + " region");
                continue;
            }
            out.add(region);
        }
        out.sort(Comparator.comparingDouble((PaddleLayoutRegion r) -> r.top).thenComparingDouble(r -> r.left));
        return suppressDuplicates(out, warnings);
    }

    private static boolean isRegionCandidate(PaddleLayoutRegion region, int sourceWidth, int sourceHeight,
                                             RegionSelectionMode regionSelectionMode) {
        if (regionSelectionMode == RegionSelectionMode.MAPPING_TEXTUAL_HINTS) {
            return TEXTUAL_LAYOUT_HINTS.contains(region.semanticClass);
        }
        float width = Math.max(0f, region.right - region.left);
        float height = Math.max(0f, region.bottom - region.top);
        if (width <= 0f || height <= 0f) return false;
        float pageArea = Math.max(1f, sourceWidth * (float) sourceHeight);
        float areaRatio = width * height / pageArea;
        float widthRatio = width / Math.max(1f, sourceWidth);
        float heightRatio = height / Math.max(1f, sourceHeight);
        if (areaRatio < MIN_REGION_AREA_RATIO) return false;
        if (heightRatio < 0.010f && widthRatio < 0.20f) return false;
        if (widthRatio < 0.025f && heightRatio < 0.20f) return false;
        return region.confidence >= MIN_REGION_CONFIDENCE;
    }

    private static List<PaddleLayoutRegion> suppressDuplicates(List<PaddleLayoutRegion> regions, List<String> warnings) {
        List<PaddleLayoutRegion> out = new ArrayList<>();
        int suppressed = 0;
        for (PaddleLayoutRegion region : regions) {
            boolean duplicate = false;
            for (int i = 0; i < out.size(); i++) {
                PaddleLayoutRegion existing = out.get(i);
                if (overlap(existing, region) > 0.88f || containment(existing, region) > 0.92f) {
                    duplicate = true;
                    if (priority(region) > priority(existing)
                            || (priority(region) == priority(existing) && region.confidence > existing.confidence)) {
                        out.set(i, region);
                    }
                    suppressed++;
                    break;
                }
            }
            if (!duplicate) out.add(region);
        }
        if (suppressed > 0) warnings.add("HEAVY_OVERLAP_SUPPRESSION count=" + suppressed);
        out.sort(Comparator.comparingDouble((PaddleLayoutRegion r) -> r.top).thenComparingDouble(r -> r.left));
        return out;
    }

    private static boolean shouldSplitRegionBlock(PaddleLayoutRegion region, List<PaddleLayoutReadingLine> lines,
                                                  int pageWidth) {
        if (lines.size() < 4) return false;
        float regionWidth = Math.max(0f, region.right - region.left);
        float minLeft = Float.MAX_VALUE;
        float maxLeft = 0f;
        float maxRight = 0f;
        for (PaddleLayoutReadingLine line : lines) {
            minLeft = Math.min(minLeft, line.box.left);
            maxLeft = Math.max(maxLeft, line.box.left);
            maxRight = Math.max(maxRight, line.box.right);
        }
        float contentWidth = maxRight - minLeft;
        if (regionWidth < pageWidth * 0.52f && contentWidth < pageWidth * 0.52f) return false;
        return maxLeft - minLeft > pageWidth * 0.20f;
    }

    private static int priority(PaddleLayoutRegion region) {
        switch (region.semanticClass) {
            case TEXT:
            case ASIDE_TEXT:
                return 5;
            case DOCUMENT_TITLE:
            case PARAGRAPH_TITLE:
            case ABSTRACT:
                return 4;
            case HEADER:
            case TABLE:
            case FOOTER:
                return 3;
            case INLINE_FORMULA:
                return 2;
            default:
                return 1;
        }
    }

    private static float overlap(PaddleLayoutRegion a, PaddleLayoutRegion b) {
        float inter = intersection(a, b);
        float union = area(a) + area(b) - inter;
        return union <= 0f ? 0f : inter / union;
    }

    private static float containment(PaddleLayoutRegion a, PaddleLayoutRegion b) {
        float smaller = Math.min(area(a), area(b));
        return smaller <= 0f ? 0f : intersection(a, b) / smaller;
    }

    private static float intersection(PaddleLayoutRegion a, PaddleLayoutRegion b) {
        float left = Math.max(a.left, b.left);
        float top = Math.max(a.top, b.top);
        float right = Math.min(a.right, b.right);
        float bottom = Math.min(a.bottom, b.bottom);
        return Math.max(0f, right - left) * Math.max(0f, bottom - top);
    }

    private static float area(PaddleLayoutRegion region) {
        return Math.max(0f, region.right - region.left) * Math.max(0f, region.bottom - region.top);
    }

    private static List<PaddleLayoutReadingLine> buildLines(List<RecognizedWord> words) {
        List<RecognizedWord> sorted = new ArrayList<>(words == null ? Collections.emptyList() : words);
        sorted.sort(Comparator.comparingDouble((RecognizedWord w) -> w.getBoundingBox().centerY())
                .thenComparingDouble(w -> w.getBoundingBox().left));
        List<List<RecognizedWord>> groups = new ArrayList<>();
        for (RecognizedWord word : sorted) {
            RectF box = word.getBoundingBox();
            List<RecognizedWord> best = null;
            float bestDistance = Float.MAX_VALUE;
            for (List<RecognizedWord> group : groups) {
                RectF gb = groupBounds(group);
                float threshold = Math.max(10f, Math.max(gb.height(), box.height()) * 0.65f);
                float distance = Math.abs(gb.centerY() - box.centerY());
                if (distance < threshold && distance < bestDistance) {
                    best = group;
                    bestDistance = distance;
                }
            }
            if (best == null) {
                best = new ArrayList<>();
                groups.add(best);
            }
            best.add(word);
        }
        List<PaddleLayoutReadingLine> lines = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) lines.add(new PaddleLayoutReadingLine(i + 1, groups.get(i)));
        sortLines(lines);
        return lines;
    }

    private static RectF groupBounds(List<RecognizedWord> words) {
        RectF out = new RectF();
        boolean first = true;
        for (RecognizedWord word : words) {
            RectF b = word.getBoundingBox();
            if (first) {
                out.set(b);
                first = false;
            } else out.union(b);
        }
        return out;
    }

    private static void sortLines(List<PaddleLayoutReadingLine> lines) {
        lines.sort(Comparator.comparingDouble((PaddleLayoutReadingLine l) -> l.box.top)
                .thenComparingDouble(l -> l.box.left));
    }

    private static void addResidualWideLineDiagnostics(List<PaddleLayoutReadingLine> lines, int sourceWidth,
                                                       int ocrWordCount,
                                                       List<PaddleLayoutReconstructionDiagnostic> diagnostics) {
        if (lines == null || diagnostics == null || ocrWordCount < 120) return;
        for (PaddleLayoutReadingLine line : lines) {
            float widthRatio = line.box.width() / Math.max(1f, sourceWidth);
            if (line.words.size() < 18 || widthRatio < 0.55f) continue;
            boolean alreadyCovered = false;
            for (PaddleLayoutReconstructionDiagnostic diagnostic : diagnostics) {
                if (Math.abs(diagnostic.reconstructedLineSourceSpan.left - line.box.left) < 2f
                        && Math.abs(diagnostic.reconstructedLineSourceSpan.right - line.box.right) < 2f
                        && Math.abs(diagnostic.reconstructedLineSourceSpan.top - line.box.top) < 2f) {
                    alreadyCovered = true;
                    break;
                }
            }
            if (alreadyCovered) continue;
            boolean crossesGutter = line.box.left < sourceWidth * 0.46f && line.box.right > sourceWidth * 0.54f;
            int columnsTouched = crossesGutter ? 2 : 1;
            diagnostics.add(new PaddleLayoutReconstructionDiagnostic(line.index, line.index, widthRatio, crossesGutter,
                    maxVerticalOverlapRatio(line, lines), line.words.size(), line.box, columnsTouched,
                    meanHorizontalGap(line), maxHorizontalGap(line), meanHorizontalGap(line), maxHorizontalGap(line),
                    meanVerticalDrift(line), maxVerticalDrift(line)));
        }
    }

    private static float maxVerticalOverlapRatio(PaddleLayoutReadingLine line, List<PaddleLayoutReadingLine> lines) {
        float max = 0f;
        for (PaddleLayoutReadingLine other : lines) {
            if (other == line) continue;
            float vertical = Math.max(0f, Math.min(line.box.bottom, other.box.bottom) - Math.max(line.box.top, other.box.top));
            max = Math.max(max, vertical / Math.max(1f, Math.min(line.box.height(), other.box.height())));
        }
        return max;
    }

    private static float meanHorizontalGap(PaddleLayoutReadingLine line) {
        if (line.words.size() < 2) return 0f;
        float sum = 0f;
        for (int i = 1; i < line.words.size(); i++) {
            sum += Math.max(0f, line.words.get(i).getBoundingBox().left - line.words.get(i - 1).getBoundingBox().right);
        }
        return sum / (line.words.size() - 1);
    }

    private static float maxHorizontalGap(PaddleLayoutReadingLine line) {
        float max = 0f;
        for (int i = 1; i < line.words.size(); i++) {
            max = Math.max(max, Math.max(0f,
                    line.words.get(i).getBoundingBox().left - line.words.get(i - 1).getBoundingBox().right));
        }
        return max;
    }

    private static float meanVerticalDrift(PaddleLayoutReadingLine line) {
        if (line.words.size() < 2) return 0f;
        float sum = 0f;
        for (int i = 1; i < line.words.size(); i++) {
            sum += Math.abs(line.words.get(i).getBoundingBox().centerY() - line.words.get(i - 1).getBoundingBox().centerY());
        }
        return sum / (line.words.size() - 1);
    }

    private static float maxVerticalDrift(PaddleLayoutReadingLine line) {
        float max = 0f;
        for (int i = 1; i < line.words.size(); i++) {
            max = Math.max(max, Math.abs(line.words.get(i).getBoundingBox().centerY()
                    - line.words.get(i - 1).getBoundingBox().centerY()));
        }
        return max;
    }
}