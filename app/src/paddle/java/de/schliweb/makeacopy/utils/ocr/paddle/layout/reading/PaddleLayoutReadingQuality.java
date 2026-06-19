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
import java.util.List;
import java.util.Locale;

public final class PaddleLayoutReadingQuality {
    public final int blockCount;
    public final int columnCount;
    public final int suspiciousJumpCount;
    public final int crossColumnJumpCount;
    public final int weakAssignmentCount;
    public final int ambiguousAssignmentCount;
    public final float assignedLineRatio;
    public final List<PaddleLayoutOrderingIssue> issues;
    public final List<PaddleLayoutOrderingIssue> informationalTraces;

    private PaddleLayoutReadingQuality(int blockCount, int columnCount, int suspiciousJumpCount,
                                       int crossColumnJumpCount, int weakAssignmentCount,
                                       int ambiguousAssignmentCount, float assignedLineRatio,
                                       List<PaddleLayoutOrderingIssue> issues,
                                       List<PaddleLayoutOrderingIssue> informationalTraces) {
        this.blockCount = blockCount;
        this.columnCount = columnCount;
        this.suspiciousJumpCount = suspiciousJumpCount;
        this.crossColumnJumpCount = crossColumnJumpCount;
        this.weakAssignmentCount = weakAssignmentCount;
        this.ambiguousAssignmentCount = ambiguousAssignmentCount;
        this.assignedLineRatio = assignedLineRatio;
        this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
        this.informationalTraces = Collections.unmodifiableList(new ArrayList<>(informationalTraces));
    }

    static PaddleLayoutReadingQuality analyze(List<PaddleLayoutReadingBlock> blocks,
                                              List<PaddleLayoutRegionAssignment> assignments,
                                              List<PaddleLayoutReadingLine> allLines,
                                              int assignedLineCount, int unassignedLineCount, int sourceWidth,
                                              int sourceHeight, int ocrWordCount) {
        List<PaddleLayoutOrderingIssue> issues = new ArrayList<>();
        List<PaddleLayoutOrderingIssue> informationalTraces = new ArrayList<>();
        int maxColumn = -1;
        int crossColumn = 0;
        int largeGaps = 0;
        int oscillations = 0;
        for (PaddleLayoutReadingBlock block : blocks) maxColumn = Math.max(maxColumn, block.columnIndex);
        for (int i = 1; i < blocks.size(); i++) {
            PaddleLayoutReadingBlock previous = blocks.get(i - 1);
            PaddleLayoutReadingBlock current = blocks.get(i);
            if (current.columnIndex != previous.columnIndex) {
                if (isSuppressedExpectedColumnMajorTransition(blocks, i)) {
                    informationalTraces.add(expectedColumnMajorTransition(previous, current));
                } else {
                    crossColumn++;
                    issues.add(new PaddleLayoutOrderingIssue(PaddleLayoutOrderingIssue.Type.CROSS_COLUMN_JUMP,
                            "reading order switches column", previous.orderIndex, current.orderIndex, 0.5f));
                }
            }
            if (i > 1) {
                PaddleLayoutReadingBlock beforePrevious = blocks.get(i - 2);
                if (beforePrevious.columnIndex == current.columnIndex
                        && previous.columnIndex != current.columnIndex) oscillations++;
            }
            float verticalGap = current.box.top - previous.box.bottom;
            if (verticalGap > Math.max(160f, sourceHeight * 0.08f)) {
                if (isSuppressedExpectedFooterGap(previous, current, sourceHeight)) {
                    informationalTraces.add(suppressedExpectedFooterGap(previous, current, verticalGap, sourceHeight));
                } else {
                    PaddleLayoutOrderingIssue gapIssue = largeGapIssue(previous, current, verticalGap, sourceHeight);
                    if (gapIssue.severityLevel == PaddleLayoutOrderingIssue.SeverityLevel.INFO) {
                        informationalTraces.add(gapIssue);
                    } else {
                        largeGaps++;
                        issues.add(gapIssue);
                    }
                }
            }
            PaddleLayoutOrderingIssue overlapIssue = overlapIssue(previous, current);
            if (overlapIssue != null) {
                if (overlapIssue.severityLevel == PaddleLayoutOrderingIssue.SeverityLevel.INFO) {
                    informationalTraces.add(overlapIssue);
                } else {
                    issues.add(overlapIssue);
                }
            }
        }
        if (oscillations > 0) {
            issues.add(new PaddleLayoutOrderingIssue(PaddleLayoutOrderingIssue.Type.CROSS_COLUMN_JUMP,
                    "OSCILLATING_COLUMN_FLOW count=" + oscillations, -1, -1, 0.8f));
        }
        int weak = 0;
        int ambiguous = 0;
        for (PaddleLayoutRegionAssignment assignment : assignments) {
            if (assignment.region == null) continue;
            float localScore = Math.max(assignment.score, finalBlockAssignmentScore(assignment.line, blocks));
            if (localScore < 0.18f) {
                weak++;
                issues.add(new PaddleLayoutOrderingIssue(PaddleLayoutOrderingIssue.Type.WEAK_ASSIGNMENT,
                        String.format(Locale.US, "weak line assignment score=%.3f", localScore),
                        assignment.line.index, -1, localScore));
            }
            if (assignment.ambiguous && localScore < 0.18f) {
                ambiguous++;
                issues.add(new PaddleLayoutOrderingIssue(PaddleLayoutOrderingIssue.Type.AMBIGUOUS_ASSIGNMENT,
                        String.format(Locale.US, "ambiguous assignment score=%.3f second=%.3f", assignment.score,
                                assignment.secondBestScore), assignment.line.index, -1, 0.6f));
            }
        }
        if (unassignedLineCount > 0) {
            issues.add(new PaddleLayoutOrderingIssue(PaddleLayoutOrderingIssue.Type.UNASSIGNED_LINES,
                    unassignedLineCount + " OCR lines were not assigned to a layout-region hint", -1, -1,
                    Math.min(1f, unassignedLineCount / Math.max(1f, assignedLineCount + unassignedLineCount))));
        }
        addOcrLineHealthIssues(issues, allLines, sourceWidth, sourceHeight, ocrWordCount);
        for (PaddleLayoutReadingBlock block : blocks) {
            if (block.opaque && block.lines.isEmpty()) {
                issues.add(new PaddleLayoutOrderingIssue(PaddleLayoutOrderingIssue.Type.EMPTY_OPAQUE_BLOCK,
                        "opaque table/layout block has no assigned OCR lines", block.orderIndex, -1, 0.3f));
            }
        }
        float ratio = assignedLineCount / Math.max(1f, assignedLineCount + unassignedLineCount);
        return new PaddleLayoutReadingQuality(blocks.size(), maxColumn + 1, crossColumn + largeGaps, crossColumn,
                weak, ambiguous, ratio, issues, informationalTraces);
    }

    private static void addOcrLineHealthIssues(List<PaddleLayoutOrderingIssue> issues,
                                               List<PaddleLayoutReadingLine> lines,
                                               int sourceWidth, int sourceHeight, int ocrWordCount) {
        if (lines.isEmpty()) return;
        int denseWideLines = 0;
        int pageSizedLines = 0;
        int residualWideLines = 0;
        int maxWords = 0;
        float pageArea = Math.max(1f, sourceWidth * sourceHeight);
        for (PaddleLayoutReadingLine line : lines) {
            int wordCount = line.words.size();
            maxWords = Math.max(maxWords, wordCount);
            float widthRatio = line.box.width() / Math.max(1f, sourceWidth);
            float heightRatio = line.box.height() / Math.max(1f, sourceHeight);
            float areaRatio = Math.max(0f, line.box.width()) * Math.max(0f, line.box.height()) / pageArea;
            if (wordCount >= 32 && widthRatio >= 0.70f) denseWideLines++;
            if (wordCount >= 18 && widthRatio >= 0.55f && heightRatio <= 0.055f) residualWideLines++;
            if (wordCount >= 80 && (areaRatio >= 0.18f || (widthRatio >= 0.70f && heightRatio >= 0.18f))) {
                pageSizedLines++;
            }
        }
        if (pageSizedLines > 0) {
            issues.add(new PaddleLayoutOrderingIssue(PaddleLayoutOrderingIssue.Type.OCR_LINE_HEALTH,
                    "PATHOLOGICAL_OCR_LINE_GEOMETRY pageSizedLines=" + pageSizedLines + " maxWords=" + maxWords,
                    -1, -1, 0.95f));
        }
        if (ocrWordCount >= 120 && lines.size() < 8) {
            issues.add(new PaddleLayoutOrderingIssue(PaddleLayoutOrderingIssue.Type.OCR_LINE_HEALTH,
                    "TOO_FEW_OCR_LINES_FOR_DENSE_WORDS lines=" + lines.size() + " words=" + ocrWordCount,
                    -1, -1, 0.85f));
        }
        if (denseWideLines > 0 && ocrWordCount >= 80) {
            issues.add(new PaddleLayoutOrderingIssue(PaddleLayoutOrderingIssue.Type.OCR_LINE_HEALTH,
                    "DENSE_WIDE_OCR_LINES count=" + denseWideLines + " maxWords=" + maxWords,
                    -1, -1, 0.70f));
        }
        if (residualWideLines > 0 && ocrWordCount >= 120) {
            issues.add(new PaddleLayoutOrderingIssue(PaddleLayoutOrderingIssue.Type.OCR_LINE_HEALTH,
                    "RESIDUAL_WIDE_RECONSTRUCTED_LINES count=" + residualWideLines + " maxWords=" + maxWords,
                    -1, -1, 0.55f));
        }
    }

    private static PaddleLayoutOrderingIssue suppressedExpectedFooterGap(PaddleLayoutReadingBlock previous,
                                                                         PaddleLayoutReadingBlock current,
                                                                         float verticalGap, int sourceHeight) {
        return new PaddleLayoutOrderingIssue(PaddleLayoutOrderingIssue.Type.LARGE_VERTICAL_GAP,
                String.format(Locale.US, "SUPPRESSED_EXPECTED_FOOTER_GAP gap=%.1fpx normalized=%.3f role=%s",
                        verticalGap, verticalGap / Math.max(1f, sourceHeight), current.role.name()),
                previous.orderIndex, current.orderIndex, 0f, PaddleLayoutOrderingIssue.SeverityLevel.INFO,
                PaddleLayoutOrderingIssue.Category.SUPPRESSED_FALSE_POSITIVE);
    }

    private static PaddleLayoutOrderingIssue expectedColumnMajorTransition(PaddleLayoutReadingBlock previous,
                                                                          PaddleLayoutReadingBlock current) {
        return new PaddleLayoutOrderingIssue(PaddleLayoutOrderingIssue.Type.CROSS_COLUMN_JUMP,
                String.format(Locale.US, "EXPECTED_COLUMN_MAJOR_TRANSITION column=%d->%d",
                        previous.columnIndex, current.columnIndex), previous.orderIndex, current.orderIndex, 0f,
                PaddleLayoutOrderingIssue.SeverityLevel.INFO,
                PaddleLayoutOrderingIssue.Category.EXPECTED_FLOW_TRANSITION);
    }

    private static PaddleLayoutOrderingIssue largeGapIssue(PaddleLayoutReadingBlock previous,
                                                           PaddleLayoutReadingBlock current,
                                                           float verticalGap, int sourceHeight) {
        float normalizedGap = verticalGap / Math.max(1f, sourceHeight);
        float horizontalOverlap = Math.max(0f, Math.min(previous.box.right, current.box.right)
                - Math.max(previous.box.left, current.box.left));
        float minWidth = Math.max(1f, Math.min(previous.box.width(), current.box.width()));
        float horizontalContinuity = horizontalOverlap / minWidth;
        float centerShift = Math.abs(previous.box.centerX() - current.box.centerX())
                / Math.max(1f, Math.max(previous.box.width(), current.box.width()));
        boolean sameColumn = previous.columnIndex == current.columnIndex;
        boolean monotone = current.box.top >= previous.box.bottom;
        boolean locallyContinuous = horizontalContinuity >= 0.55f || centerShift <= 0.18f;
        boolean lowSeverityGap = sameColumn && monotone && locallyContinuous && normalizedGap <= 0.32f;
        if (lowSeverityGap) {
            return new PaddleLayoutOrderingIssue(PaddleLayoutOrderingIssue.Type.LARGE_VERTICAL_GAP,
                    String.format(Locale.US,
                            "LOW_SEVERITY_SAME_COLUMN_GAP gap=%.1fpx normalized=%.3f horizontalContinuity=%.3f centerShift=%.3f",
                            verticalGap, normalizedGap, horizontalContinuity, centerShift),
                    previous.orderIndex, current.orderIndex, normalizedGap,
                    PaddleLayoutOrderingIssue.SeverityLevel.INFO,
                    PaddleLayoutOrderingIssue.Category.EXPECTED_FLOW_TRANSITION);
        }
        return new PaddleLayoutOrderingIssue(PaddleLayoutOrderingIssue.Type.LARGE_VERTICAL_GAP,
                String.format(Locale.US,
                        "large vertical gap %.1fpx normalized=%.3f sameColumn=%s horizontalContinuity=%.3f centerShift=%.3f",
                        verticalGap, normalizedGap, sameColumn, horizontalContinuity, centerShift),
                previous.orderIndex, current.orderIndex, Math.min(1f, normalizedGap));
    }

    private static PaddleLayoutOrderingIssue overlapIssue(PaddleLayoutReadingBlock previous,
                                                          PaddleLayoutReadingBlock current) {
        OverlapMetrics metrics = overlapMetrics(previous.box, current.box);
        if (metrics.overMin <= 0.35f) return null;
        if (metrics.contained && metrics.overLarge <= 0.025f && metrics.areaRatio <= 0.08f) {
            return new PaddleLayoutOrderingIssue(PaddleLayoutOrderingIssue.Type.OVERLAPPING_BLOCKS,
                    String.format(Locale.US,
                            "LOW_SEVERITY_CONTAINED_OVERLAP overMin=%.3f overLarge=%.3f areaRatio=%.3f",
                            metrics.overMin, metrics.overLarge, metrics.areaRatio),
                    previous.orderIndex, current.orderIndex, metrics.overLarge,
                    PaddleLayoutOrderingIssue.SeverityLevel.INFO,
                    PaddleLayoutOrderingIssue.Category.GEOMETRY_CONSISTENCY);
        }
        return new PaddleLayoutOrderingIssue(PaddleLayoutOrderingIssue.Type.OVERLAPPING_BLOCKS,
                String.format(Locale.US,
                        "adjacent reading blocks overlap strongly overMin=%.3f overLarge=%.3f areaRatio=%.3f contained=%s",
                        metrics.overMin, metrics.overLarge, metrics.areaRatio, metrics.contained),
                previous.orderIndex, current.orderIndex, Math.max(0.55f, Math.min(1f, metrics.overMin)));
    }

    private static boolean isSuppressedExpectedFooterGap(PaddleLayoutReadingBlock previous,
                                                         PaddleLayoutReadingBlock current, int sourceHeight) {
        if (sourceHeight <= 0) return false;
        if (current.box.top < sourceHeight * 0.82f) return false;
        if (current.box.bottom < previous.box.bottom) return false;
        if (current.role != PaddleLayoutDiagnosticRole.FOOTNOTE_CANDIDATE
                && current.role != PaddleLayoutDiagnosticRole.OPAQUE_TABLE_LIKE) return false;
        float currentHeight = Math.max(1f, current.box.height());
        float previousHeight = Math.max(1f, previous.box.height());
        float currentWidth = Math.max(1f, current.box.width());
        float previousWidth = Math.max(1f, previous.box.width());
        boolean compactFooter = currentHeight <= Math.max(48f, previousHeight * 0.7f)
                && currentWidth <= Math.max(previousWidth * 1.35f, sourceHeight * 0.35f);
        boolean shortText = current.lines.size() <= 2;
        return compactFooter && shortText;
    }

    private static boolean isSuppressedExpectedColumnMajorTransition(List<PaddleLayoutReadingBlock> blocks,
                                                                     int currentIndex) {
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

    private static float finalBlockAssignmentScore(PaddleLayoutReadingLine line, List<PaddleLayoutReadingBlock> blocks) {
        float best = 0f;
        for (PaddleLayoutReadingBlock block : blocks) {
            if (!block.lines.contains(line)) continue;
            float horizontal = horizontalCoverage(line.box, block.box);
            float vertical = verticalCoverage(line.box, block.box);
            float center = columnCenterPreference(line.box, block.box);
            best = Math.max(best, horizontal * 0.32f + vertical * 0.16f + center * 0.16f);
        }
        return best;
    }

    private static float horizontalCoverage(RectF line, RectF block) {
        float overlap = Math.max(0f, Math.min(line.right, block.right) - Math.max(line.left, block.left));
        return overlap / Math.max(1f, line.width());
    }

    private static float verticalCoverage(RectF line, RectF block) {
        float overlap = Math.max(0f, Math.min(line.bottom, block.bottom) - Math.max(line.top, block.top));
        return overlap / Math.max(1f, line.height());
    }

    private static float columnCenterPreference(RectF line, RectF block) {
        float blockWidth = Math.max(1f, block.width());
        float lineCenter = (line.left + line.right) * 0.5f;
        float blockCenter = (block.left + block.right) * 0.5f;
        float normalizedDistance = Math.abs(lineCenter - blockCenter) / (blockWidth * 0.5f);
        return Math.max(0f, 1f - normalizedDistance);
    }

    String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US,
                "{\"blockCount\":%d,\"columnCount\":%d,\"suspiciousJumpCount\":%d,\"crossColumnJumpCount\":%d,\"weakAssignmentCount\":%d,\"ambiguousAssignmentCount\":%d,\"assignedLineRatio\":%.5f,\"issues\":[",
                blockCount, columnCount, suspiciousJumpCount, crossColumnJumpCount, weakAssignmentCount,
                ambiguousAssignmentCount, assignedLineRatio));
        for (int i = 0; i < issues.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(issues.get(i).toJson());
        }
        sb.append("],\"informationalTraces\":[");
        for (int i = 0; i < informationalTraces.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(informationalTraces.get(i).toJson());
        }
        return sb.append("]}").toString();
    }

    private static OverlapMetrics overlapMetrics(RectF a, RectF b) {
        float left = Math.max(a.left, b.left);
        float top = Math.max(a.top, b.top);
        float right = Math.min(a.right, b.right);
        float bottom = Math.min(a.bottom, b.bottom);
        float inter = Math.max(0f, right - left) * Math.max(0f, bottom - top);
        float areaA = Math.max(0f, a.width()) * Math.max(0f, a.height());
        float areaB = Math.max(0f, b.width()) * Math.max(0f, b.height());
        float minArea = Math.min(areaA, areaB);
        float maxArea = Math.max(areaA, areaB);
        float overMin = minArea <= 0f ? 0f : inter / minArea;
        float overLarge = maxArea <= 0f ? 0f : inter / maxArea;
        float areaRatio = maxArea <= 0f ? 0f : minArea / maxArea;
        return new OverlapMetrics(overMin, overLarge, areaRatio, overMin >= 0.95f);
    }

    private static final class OverlapMetrics {
        final float overMin;
        final float overLarge;
        final float areaRatio;
        final boolean contained;

        OverlapMetrics(float overMin, float overLarge, float areaRatio, boolean contained) {
            this.overMin = overMin;
            this.overLarge = overLarge;
            this.areaRatio = areaRatio;
            this.contained = contained;
        }
    }
}