/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.ocr.paddle.layout.diagnostic.ocrd;

import de.schliweb.makeacopy.utils.ocr.paddle.layout.reading.PaddleLayoutReadingBlock;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.reading.PaddleLayoutReadingDebugReport;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.reading.PaddleLayoutReadingLine;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.reading.PaddleLayoutReconstructionDiagnostic;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.reading.PaddleLayoutOrderingIssue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class OcrdDiagnosticComparison {
    public final String pageId;
    public final int ocrdLineCount;
    public final int paddleLineCount;
    public final int readingOrderReferenceCount;
    public final int crossColumnInstabilityCount;
    public final int sameColumnContinuityBreakCount;
    public final float reconstructedLineCoverage;
    public final float overlapSeverity;
    public final String referenceLayoutClass;
    public final int referenceColumnCount;
    public final ColumnDiagnostics columnDiagnostics;
    public final SafetyGateDiagnostics safetyGateDiagnostics;
    public final String diagnosticSeverity;
    public final List<String> failureCategories;
    public final List<String> explainabilityTraces;

    private OcrdDiagnosticComparison(String pageId, int ocrdLineCount, int paddleLineCount,
                                     int readingOrderReferenceCount, int crossColumnInstabilityCount,
                                     int sameColumnContinuityBreakCount, float reconstructedLineCoverage,
                                     float overlapSeverity, String referenceLayoutClass, int referenceColumnCount,
                                     ColumnDiagnostics columnDiagnostics, SafetyGateDiagnostics safetyGateDiagnostics,
                                     String diagnosticSeverity,
                                     List<String> failureCategories, List<String> explainabilityTraces) {
        this.pageId = pageId;
        this.ocrdLineCount = ocrdLineCount;
        this.paddleLineCount = paddleLineCount;
        this.readingOrderReferenceCount = readingOrderReferenceCount;
        this.crossColumnInstabilityCount = crossColumnInstabilityCount;
        this.sameColumnContinuityBreakCount = sameColumnContinuityBreakCount;
        this.reconstructedLineCoverage = reconstructedLineCoverage;
        this.overlapSeverity = overlapSeverity;
        this.referenceLayoutClass = referenceLayoutClass;
        this.referenceColumnCount = referenceColumnCount;
        this.columnDiagnostics = columnDiagnostics;
        this.safetyGateDiagnostics = safetyGateDiagnostics;
        this.diagnosticSeverity = diagnosticSeverity;
        this.failureCategories = Collections.unmodifiableList(new ArrayList<>(failureCategories));
        this.explainabilityTraces = Collections.unmodifiableList(new ArrayList<>(explainabilityTraces));
    }

    public static OcrdDiagnosticComparison compare(OcrdEvaluationPage page, PaddleLayoutReadingDebugReport report) {
        int referenceLines = countReferenceLines(page);
        int paddleLines = countPaddleLines(report);
        int crossColumn = report == null || report.quality == null ? 0 : report.quality.crossColumnJumpCount;
        int sameColumn = report == null || report.quality == null ? 0 : report.quality.suspiciousJumpCount;
        float coverage = referenceLines == 0 ? 0f : Math.min(1f, paddleLines / (float) referenceLines);
        float overlap = maxOverlapIssueSeverity(report);
        int referenceColumns = estimateReferenceColumnCount(page);
        String layoutClass = classifyReferenceLayout(page, referenceColumns);
        ColumnDiagnostics columnDiagnostics = ColumnDiagnostics.from(page, report, referenceColumns);
        SafetyGateDiagnostics safetyGateDiagnostics = SafetyGateDiagnostics.from(report);
        List<String> categories = new ArrayList<>();
        List<String> traces = new ArrayList<>();
        addPageShapeTaxonomy(page, referenceColumns, layoutClass, categories, traces);
        addColumnTaxonomy(columnDiagnostics, categories, traces);
        addReportTaxonomy(report, referenceLines, paddleLines, coverage, overlap, crossColumn, sameColumn,
                referenceColumns, categories, traces);
        if (report != null && report.quality != null && report.quality.crossColumnJumpCount == 0) {
            addOnce(traces, "COLUMN_MAJOR_READABILITY_ACCEPTED");
        }
        if (page != null && !page.readingOrderIds.isEmpty()) addOnce(traces, "OCRD_READING_ORDER_AVAILABLE");
        if (categories.isEmpty()) categories.add("NO_ACTIVE_GEOMETRY_FAILURE");
        String severity = classifySeverity(categories, columnDiagnostics, coverage, overlap, crossColumn, sameColumn);
        return new OcrdDiagnosticComparison(page == null ? "" : page.pageId, referenceLines, paddleLines,
                page == null ? 0 : page.readingOrderIds.size(), crossColumn, sameColumn, coverage, overlap,
                layoutClass, referenceColumns, columnDiagnostics, safetyGateDiagnostics, severity, categories, traces);
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"pageId\": ").append(quote(pageId)).append(",\n");
        sb.append("  \"ocrdLineCount\": ").append(ocrdLineCount).append(",\n");
        sb.append("  \"paddleLineCount\": ").append(paddleLineCount).append(",\n");
        sb.append("  \"readingOrderReferenceCount\": ").append(readingOrderReferenceCount).append(",\n");
        sb.append("  \"crossColumnInstabilityCount\": ").append(crossColumnInstabilityCount).append(",\n");
        sb.append("  \"sameColumnContinuityBreakCount\": ").append(sameColumnContinuityBreakCount).append(",\n");
        sb.append(String.format(Locale.US, "  \"reconstructedLineCoverage\": %.3f,\n", reconstructedLineCoverage));
        sb.append(String.format(Locale.US, "  \"overlapSeverity\": %.3f,\n", overlapSeverity));
        sb.append("  \"referenceLayoutClass\": ").append(quote(referenceLayoutClass)).append(",\n");
        sb.append("  \"referenceColumnCount\": ").append(referenceColumnCount).append(",\n");
        sb.append("  \"columnDiagnostics\": ").append(columnDiagnostics == null ? "null" : columnDiagnostics.toJson()).append(",\n");
        sb.append("  \"safetyGateDiagnostics\": ").append(safetyGateDiagnostics == null ? "null" : safetyGateDiagnostics.toJson()).append(",\n");
        sb.append("  \"diagnosticSeverity\": ").append(quote(diagnosticSeverity)).append(",\n");
        appendArray(sb, "failureCategories", failureCategories);
        sb.append(",\n");
        appendArray(sb, "explainabilityTraces", explainabilityTraces);
        sb.append("\n}\n");
        return sb.toString();
    }

    private static int countReferenceLines(OcrdEvaluationPage page) {
        if (page == null) return 0;
        int count = 0;
        for (OcrdEvaluationRegion region : page.regions) count += region.lines.size();
        return count;
    }

    private static int countPaddleLines(PaddleLayoutReadingDebugReport report) {
        if (report == null) return 0;
        int count = 0;
        for (PaddleLayoutReadingBlock block : report.blocks) count += block.lines.size();
        return count;
    }

    private static float maxOverlapIssueSeverity(PaddleLayoutReadingDebugReport report) {
        if (report == null || report.quality == null) return 0f;
        float out = 0f;
        for (PaddleLayoutOrderingIssue issue : report.quality.issues) {
            if (issue.type == PaddleLayoutOrderingIssue.Type.OVERLAPPING_BLOCKS) out = Math.max(out, issue.severity);
        }
        return out;
    }

    private static String classifySeverity(List<String> categories, ColumnDiagnostics diagnostics, float coverage,
                                           float overlap, int crossColumn, int sameColumn) {
        if (overlap >= 0.75f || coverage < 0.50f || crossColumn > 1
                || categories.contains("OVERLAPPING_ASSIGNMENT_CONFLICT")) {
            return "SEVERE_GEOMETRY_CORRUPTION";
        }
        if (categories.size() == 1 && (categories.contains("NO_ACTIVE_GEOMETRY_FAILURE")
                || categories.contains("LOW_SEVERITY_CONTAINED_OVERLAP"))) {
            return "HARMLESS_INFORMATIONAL_WARNING";
        }
        if (diagnostics != null && diagnostics.falseColumnCandidateOnSingleColumnReference
                && crossColumn == 0 && overlap == 0f) {
            return "SUSPICIOUS_BUT_CONTAINED";
        }
        if (coverage < 0.82f || crossColumn > 0 || sameColumn > 1
                || categories.contains("RESIDUAL_WIDE_RECONSTRUCTION_ARTIFACT")) {
            return "READABILITY_RISK";
        }
        if (diagnostics != null && (diagnostics.falseColumnCandidateOnSingleColumnReference
                || diagnostics.marginaliaOrSidebarCandidate || diagnostics.reconstructionActivationCount > 0)) {
            return "SUSPICIOUS_BUT_CONTAINED";
        }
        return "SUSPICIOUS_BUT_CONTAINED";
    }

    private static void addPageShapeTaxonomy(OcrdEvaluationPage page, int referenceColumns, String layoutClass,
                                             List<String> categories, List<String> traces) {
        if (page == null) return;
        addOnce(traces, "OCRD_REFERENCE_LAYOUT=" + layoutClass);
        if (referenceColumns > 1) addOnce(traces, "OCRD_REFERENCE_COLUMNS=" + referenceColumns);
        if ("table-like".equals(layoutClass)) addOnce(categories, "TABLE_STRUCTURE_AMBIGUITY");
        if ("marginalia-sidebar".equals(layoutClass)) addOnce(categories, "SIDEBAR_POLLUTION");
        if (referenceColumns > 1 && !page.readingOrderIds.isEmpty()) addOnce(traces, "EXPECTED_COLUMN_MAJOR_TRANSITION");
    }

    private static void addReportTaxonomy(PaddleLayoutReadingDebugReport report, int referenceLines, int paddleLines,
                                          float coverage, float overlap, int crossColumn, int sameColumn,
                                          int referenceColumns, List<String> categories, List<String> traces) {
        if (referenceLines > 0 && coverage < 0.65f) addOnce(categories, "OCR_LINE_COLLAPSE");
        if (referenceLines > 0 && paddleLines > 0 && coverage < 0.82f) addOnce(categories, "RESIDUAL_WIDE_RECONSTRUCTED_LINES");
        if (overlap > 0.35f) addOnce(categories, "OVERLAPPING_ASSIGNMENT_CONFLICT");
        else if (overlap > 0f) addOnce(categories, "LOW_SEVERITY_CONTAINED_OVERLAP");
        if (crossColumn > 0) addOnce(categories, "CROSS_COLUMN_INTERLEAVING");
        if (sameColumn > 0) addOnce(categories, "SAME_COLUMN_CONTINUITY_BREAK");
        addReconstructionTaxonomy(report, categories, traces);
        if (report == null || report.quality == null) return;
        if (report.quality.weakAssignmentCount > 0 || report.unassignedLineCount > 0) {
            addOnce(categories, "LOW_CONFIDENCE_FALLBACK_COLUMNS");
        }
        if (referenceColumns > 1 && report.quality.columnCount != referenceColumns) {
            addOnce(categories, "COLUMN_GUTTER_INSTABILITY");
        }
        for (String warning : report.warnings) {
            if (warning.contains("RECONSTRUCT") || warning.contains("OCR_LINE")) addOnce(traces, warning);
            if (warning.contains("RECONSTRUCTION_SAFETY_GATE_TRIGGERED")) {
                addOnce(categories, "RECONSTRUCTION_SAFETY_GATE_TRIGGERED");
            }
            if (warning.contains("RECONSTRUCTION_SAFETY_SPLIT_APPLIED")) {
                addOnce(categories, "RECONSTRUCTION_SAFETY_SPLIT_APPLIED");
            }
            if (warning.contains("RECONSTRUCTION_SAFETY_SPLIT_SKIPPED_LOW_CONFIDENCE")) {
                addOnce(categories, "RECONSTRUCTION_SAFETY_SPLIT_SKIPPED_LOW_CONFIDENCE");
            }
            if (warning.contains("fallback") || warning.contains("LOW_CONFIDENCE")) {
                addOnce(categories, "LOW_CONFIDENCE_FALLBACK_COLUMNS");
            }
        }
    }

    private static void addReconstructionTaxonomy(PaddleLayoutReadingDebugReport report, List<String> categories,
                                                  List<String> traces) {
        if (report == null || report.reconstructionDiagnostics.isEmpty()) return;
        int gutterCrossings = 0;
        int overMerges = 0;
        int columnInterleavings = 0;
        int fragmentChains = 0;
        Map<String, Integer> candidateQuality = new LinkedHashMap<>();
        float maxWidth = 0f;
        float maxOverlap = 0f;
        float maxGap = 0f;
        float maxDrift = 0f;
        for (PaddleLayoutReconstructionDiagnostic diagnostic : report.reconstructionDiagnostics) {
            maxWidth = Math.max(maxWidth, diagnostic.reconstructedLineWidthRatio);
            maxOverlap = Math.max(maxOverlap, diagnostic.reconstructedLineOverlapAfterMerge);
            maxGap = Math.max(maxGap, diagnostic.reconstructionHorizontalGapMax);
            maxDrift = Math.max(maxDrift, diagnostic.reconstructionVerticalDriftMax);
            if (diagnostic.reconstructedLineCrossesEstimatedGutter) gutterCrossings++;
            if (diagnostic.reconstructedLineWidthRatio >= 0.58f || diagnostic.reconstructionHorizontalGapMax >= 96f) overMerges++;
            if (diagnostic.reconstructedLineEstimatedColumnsTouched > 1
                    && (diagnostic.reconstructedLineCrossesEstimatedGutter || diagnostic.reconstructedLineWidthRatio >= 0.52f)) {
                columnInterleavings++;
            }
            if (diagnostic.reconstructedLineFragmentCount >= 18 && diagnostic.reconstructionVerticalDriftMax >= 8f) {
                fragmentChains++;
            }
            addCandidateQualityClassifications(candidateQuality, diagnostic.safetyGateDecisionJson);
        }
        addOnce(traces, String.format(Locale.US,
                "RECONSTRUCTION_DIAGNOSTICS lines=%d gutterCrossings=%d overMerges=%d columnInterleavings=%d fragmentChains=%d maxWidth=%.3f maxOverlap=%.3f maxGap=%.1f maxDrift=%.1f candidateQuality=%s",
                report.reconstructionDiagnostics.size(), gutterCrossings, overMerges, columnInterleavings,
                fragmentChains, maxWidth, maxOverlap, maxGap, maxDrift, candidateQuality));
        if (gutterCrossings > 0) addOnce(categories, "RECONSTRUCTION_GUTTER_CROSSING");
        if (overMerges > 0) addOnce(categories, "RECONSTRUCTION_OVER_MERGE");
        if (columnInterleavings > 0) addOnce(categories, "RECONSTRUCTION_COLUMN_INTERLEAVING");
        if (fragmentChains > 0) addOnce(categories, "RECONSTRUCTION_FRAGMENT_CHAIN");
        for (String classification : candidateQuality.keySet()) addOnce(categories, classification);
    }

    private static void addCandidateQualityClassifications(Map<String, Integer> out, String json) {
        if (json == null || json.isBlank()) return;
        int key = json.indexOf("\"candidateQualityClassifications\"");
        if (key < 0) return;
        int start = json.indexOf('[', key);
        int end = json.indexOf(']', start);
        if (start < 0 || end < start) return;
        String body = json.substring(start + 1, end);
        int startPart = 0;
        for (int i = 0; i <= body.length(); i++) {
            if (i == body.length() || body.charAt(i) == ',') {
                String classification = body.substring(startPart, i).replace("\"", "").trim();
                if (!classification.isEmpty()) out.compute(classification,
                        (ignored, current) -> current == null ? 1 : current + 1);
                startPart = i + 1;
            }
        }
    }

    private static void addColumnTaxonomy(ColumnDiagnostics diagnostics, List<String> categories, List<String> traces) {
        if (diagnostics == null) return;
        addOnce(traces, "COLUMN_DIAGNOSTICS detected=" + diagnostics.detectedColumnCount
                + " fallback=" + diagnostics.fallbackColumnCount
                + " gutter=" + String.format(Locale.US, "%.3f", diagnostics.minimumGutterWidthRatio));
        if (diagnostics.falseColumnCandidateOnSingleColumnReference) {
            addOnce(categories, "FALSE_COLUMN_CANDIDATE_ON_SINGLE_COLUMN_REFERENCE");
        }
        if (diagnostics.marginaliaOrSidebarCandidate) addOnce(categories, "MARGINALIA_OR_SIDEBAR_CANDIDATE");
        if (diagnostics.residualWideReconstructedLineCount > 0) {
            addOnce(categories, "RESIDUAL_WIDE_RECONSTRUCTION_ARTIFACT");
        }
    }

    private static int estimateReferenceColumnCount(OcrdEvaluationPage page) {
        if (page == null || page.regions.isEmpty() || page.width <= 0) return 0;
        List<OcrdEvaluationRegion> textRegions = new ArrayList<>(page.regions);
        textRegions.sort((a, b) -> Float.compare(a.box.left, b.box.left));
        int columns = 0;
        float lastRight = -1f;
        for (OcrdEvaluationRegion region : textRegions) {
            if (region.box == null || region.lines.isEmpty()) continue;
            if (lastRight < 0f || region.box.left - lastRight > page.width * 0.08f) columns++;
            lastRight = Math.max(lastRight, region.box.right);
        }
        return Math.max(1, columns);
    }

    private static String classifyReferenceLayout(OcrdEvaluationPage page, int referenceColumns) {
        if (page == null) return "unknown";
        int tableHints = 0;
        int sidebarHints = 0;
        int denseRegions = 0;
        for (OcrdEvaluationRegion region : page.regions) {
            String type = region.type == null ? "" : region.type.toLowerCase(Locale.ROOT);
            if (type.contains("table")) tableHints++;
            if (type.contains("marginal") || type.contains("sidebar") || type.contains("aside")) sidebarHints++;
            if (region.lines.size() >= 12) denseRegions++;
        }
        if (tableHints > 0) return "table-like";
        if (sidebarHints > 0 || hasNarrowOuterRegion(page)) return "marginalia-sidebar";
        if (denseRegions > 0 && referenceColumns > 1) return "dense-lexicon";
        if (referenceColumns >= 2) return "two-column";
        return "single-column";
    }

    private static boolean hasNarrowOuterRegion(OcrdEvaluationPage page) {
        if (page == null || page.width <= 0) return false;
        for (OcrdEvaluationRegion region : page.regions) {
            if (region.box == null) continue;
            float widthRatio = region.box.width() / Math.max(1f, page.width);
            boolean outer = region.box.right < page.width * 0.28f || region.box.left > page.width * 0.72f;
            if (outer && widthRatio < 0.18f && !region.lines.isEmpty()) return true;
        }
        return false;
    }

    private static void addOnce(List<String> values, String value) {
        if (!values.contains(value)) values.add(value);
    }

    private static void appendArray(StringBuilder sb, String name, List<String> values) {
        sb.append("  \"").append(name).append("\": [");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(quote(values.get(i)));
        }
        sb.append(']');
    }

    private static String quote(String text) {
        return text == null ? "null" : "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public static final class ColumnDiagnostics {
        public final int referenceColumnCount;
        public final int detectedColumnCount;
        public final int fallbackColumnCount;
        public final List<Float> columnWidthRatios;
        public final List<Float> gutterWidthRatios;
        public final float minimumGutterWidthRatio;
        public final List<Float> adjacentColumnOverlapRatios;
        public final float lineCountBalance;
        public final List<Float> medianLineWidthRatios;
        public final List<Float> leftEdgeVarianceRatios;
        public final List<Float> rightEdgeVarianceRatios;
        public final boolean suspectedSideColumnShortFragments;
        public final boolean suspectedSideColumnMarginaliaText;
        public final int reconstructionActivationCount;
        public final int residualWideReconstructedLineCount;
        public final boolean falseColumnCandidateOnSingleColumnReference;
        public final boolean marginaliaOrSidebarCandidate;

        private ColumnDiagnostics(int referenceColumnCount, int detectedColumnCount, int fallbackColumnCount,
                                  List<Float> columnWidthRatios, List<Float> gutterWidthRatios,
                                  float minimumGutterWidthRatio, List<Float> adjacentColumnOverlapRatios, float lineCountBalance,
                                  List<Float> medianLineWidthRatios, List<Float> leftEdgeVarianceRatios,
                                  List<Float> rightEdgeVarianceRatios, boolean suspectedSideColumnShortFragments,
                                  boolean suspectedSideColumnMarginaliaText, int reconstructionActivationCount,
                                  int residualWideReconstructedLineCount,
                                  boolean falseColumnCandidateOnSingleColumnReference,
                                  boolean marginaliaOrSidebarCandidate) {
            this.referenceColumnCount = referenceColumnCount;
            this.detectedColumnCount = detectedColumnCount;
            this.fallbackColumnCount = fallbackColumnCount;
            this.columnWidthRatios = Collections.unmodifiableList(new ArrayList<>(columnWidthRatios));
            this.gutterWidthRatios = Collections.unmodifiableList(new ArrayList<>(gutterWidthRatios));
            this.minimumGutterWidthRatio = minimumGutterWidthRatio;
            this.adjacentColumnOverlapRatios = Collections.unmodifiableList(new ArrayList<>(adjacentColumnOverlapRatios));
            this.lineCountBalance = lineCountBalance;
            this.medianLineWidthRatios = Collections.unmodifiableList(new ArrayList<>(medianLineWidthRatios));
            this.leftEdgeVarianceRatios = Collections.unmodifiableList(new ArrayList<>(leftEdgeVarianceRatios));
            this.rightEdgeVarianceRatios = Collections.unmodifiableList(new ArrayList<>(rightEdgeVarianceRatios));
            this.suspectedSideColumnShortFragments = suspectedSideColumnShortFragments;
            this.suspectedSideColumnMarginaliaText = suspectedSideColumnMarginaliaText;
            this.reconstructionActivationCount = reconstructionActivationCount;
            this.residualWideReconstructedLineCount = residualWideReconstructedLineCount;
            this.falseColumnCandidateOnSingleColumnReference = falseColumnCandidateOnSingleColumnReference;
            this.marginaliaOrSidebarCandidate = marginaliaOrSidebarCandidate;
        }

        static ColumnDiagnostics from(OcrdEvaluationPage page, PaddleLayoutReadingDebugReport report,
                                      int referenceColumnCount) {
            int pageWidth = report == null || report.sourceWidth <= 0 ? (page == null ? 0 : page.width) : report.sourceWidth;
            Map<Integer, List<PaddleLayoutReadingBlock>> byColumn = new HashMap<>();
            int fallback = 0;
            int reconstruction = 0;
            int residualWide = 0;
            if (report != null) {
                for (String warning : report.warnings) if (warning.contains("RECONSTRUCT")) reconstruction++;
                for (PaddleLayoutReadingBlock block : report.blocks) {
                    byColumn.computeIfAbsent(block.columnIndex, ignored -> new ArrayList<>()).add(block);
                    if (block.columnConfidence.toLowerCase(Locale.ROOT).contains("fallback")
                            || block.columnConfidence.toLowerCase(Locale.ROOT).contains("weak")) fallback++;
                    for (PaddleLayoutReadingLine line : block.lines) {
                        float widthRatio = rectWidth(line.box.left, line.box.right) / Math.max(1f, pageWidth);
                        if (line.words.size() >= 16 && widthRatio >= 0.50f) residualWide++;
                    }
                }
            }
            List<ColumnShape> shapes = new ArrayList<>();
            for (Map.Entry<Integer, List<PaddleLayoutReadingBlock>> entry : byColumn.entrySet()) {
                shapes.add(ColumnShape.from(entry.getValue()));
            }
            shapes.sort((a, b) -> Float.compare(a.left, b.left));
            List<Float> widths = new ArrayList<>();
            List<Float> gutters = new ArrayList<>();
            List<Float> overlaps = new ArrayList<>();
            List<Float> medians = new ArrayList<>();
            List<Float> leftVariances = new ArrayList<>();
            List<Float> rightVariances = new ArrayList<>();
            int minLines = Integer.MAX_VALUE;
            int maxLines = 0;
            for (int i = 0; i < shapes.size(); i++) {
                ColumnShape shape = shapes.get(i);
                widths.add(shape.width / Math.max(1f, pageWidth));
                medians.add(shape.medianLineWidth / Math.max(1f, pageWidth));
                leftVariances.add(shape.leftVariance / Math.max(1f, pageWidth));
                rightVariances.add(shape.rightVariance / Math.max(1f, pageWidth));
                minLines = Math.min(minLines, shape.lineCount);
                maxLines = Math.max(maxLines, shape.lineCount);
                if (i > 0) {
                    ColumnShape previous = shapes.get(i - 1);
                    gutters.add((shape.left - previous.right) / Math.max(1f, pageWidth));
                    float overlap = Math.max(0f, previous.right - shape.left);
                    overlaps.add(overlap / Math.max(1f, Math.min(previous.width, shape.width)));
                }
            }
            float balance = maxLines == 0 ? 0f : minLines / (float) maxLines;
            boolean shortSide = hasShortSideColumn(shapes);
            boolean marginaliaText = hasMarginaliaLikeText(shapes);
            if (residualWide == 0) {
                for (ColumnShape shape : shapes) {
                    if (shape.lineCount <= 2 && shape.width / Math.max(1f, pageWidth) >= 0.50f) residualWide++;
                }
            }
            boolean falseColumn = referenceColumnCount == 1 && shapes.size() > 1 && fallback > 0
                    && !marginaliaText && balance >= 0.30f && min(gutters) <= 0.08f;
            boolean sidebar = shortSide || marginaliaText;
            return new ColumnDiagnostics(referenceColumnCount, shapes.size(), fallback, widths, gutters, min(gutters), overlaps,
                    balance, medians, leftVariances, rightVariances, shortSide, marginaliaText, reconstruction,
                    residualWide, falseColumn, sidebar);
        }

        String toJson() {
            return String.format(Locale.US,
                    "{\"referenceColumnCount\":%d,\"detectedColumnCount\":%d,\"fallbackColumnCount\":%d,"
                            + "\"columnWidthRatios\":%s,\"gutterWidthRatios\":%s,\"minimumGutterWidthRatio\":%.3f,"
                            + "\"columnOverlapRatios\":%s,"
                            + "\"lineCountBalance\":%.3f,\"medianLineWidthRatios\":%s,"
                            + "\"leftEdgeVarianceRatios\":%s,\"rightEdgeVarianceRatios\":%s,"
                            + "\"suspectedSideColumnShortFragments\":%s,\"suspectedSideColumnMarginaliaText\":%s,"
                            + "\"reconstructionActivationCount\":%d,\"residualWideReconstructedLineCount\":%d,"
                            + "\"falseColumnCandidateOnSingleColumnReference\":%s,"
                            + "\"marginaliaOrSidebarCandidate\":%s}", referenceColumnCount, detectedColumnCount,
                    fallbackColumnCount, floats(columnWidthRatios), floats(gutterWidthRatios),
                    minimumGutterWidthRatio, floats(adjacentColumnOverlapRatios), lineCountBalance,
                    floats(medianLineWidthRatios), floats(leftEdgeVarianceRatios), floats(rightEdgeVarianceRatios),
                    suspectedSideColumnShortFragments, suspectedSideColumnMarginaliaText, reconstructionActivationCount,
                    residualWideReconstructedLineCount, falseColumnCandidateOnSingleColumnReference,
                    marginaliaOrSidebarCandidate);
        }
    }

    public static final class SafetyGateDiagnostics {
        public final int triggeredCount;
        public final int splitAppliedCount;
        public final int splitSkippedLowConfidenceCount;
        public final Map<String, Integer> lowConfidenceSkipReasonCounts;
        public final Map<String, Integer> candidateQualityClassificationCounts;

        private SafetyGateDiagnostics(int triggeredCount, int splitAppliedCount, int splitSkippedLowConfidenceCount,
                                      Map<String, Integer> lowConfidenceSkipReasonCounts,
                                      Map<String, Integer> candidateQualityClassificationCounts) {
            this.triggeredCount = triggeredCount;
            this.splitAppliedCount = splitAppliedCount;
            this.splitSkippedLowConfidenceCount = splitSkippedLowConfidenceCount;
            this.lowConfidenceSkipReasonCounts = new LinkedHashMap<>(lowConfidenceSkipReasonCounts);
            this.candidateQualityClassificationCounts = new LinkedHashMap<>(candidateQualityClassificationCounts);
        }

        static SafetyGateDiagnostics from(PaddleLayoutReadingDebugReport report) {
            int triggered = 0;
            int applied = 0;
            int skipped = 0;
            Map<String, Integer> reasons = new HashMap<>();
            Map<String, Integer> candidateQuality = new LinkedHashMap<>();
            if (report != null) {
                for (String warning : report.warnings) {
                    if (warning.contains("RECONSTRUCTION_SAFETY_GATE_TRIGGERED")) triggered++;
                    if (warning.contains("RECONSTRUCTION_SAFETY_SPLIT_APPLIED")) applied++;
                    if (warning.contains("RECONSTRUCTION_SAFETY_SPLIT_SKIPPED_LOW_CONFIDENCE")) {
                        skipped++;
                        addSkipReasons(reasons, warning);
                    }
                }
                for (PaddleLayoutReconstructionDiagnostic diagnostic : report.reconstructionDiagnostics) {
                    addCandidateQualityClassifications(candidateQuality, diagnostic.safetyGateDecisionJson);
                }
            }
            return new SafetyGateDiagnostics(triggered, applied, skipped, reasons, candidateQuality);
        }

        String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append('{')
                    .append("\"triggeredCount\":").append(triggeredCount).append(',')
                    .append("\"splitAppliedCount\":").append(splitAppliedCount).append(',')
                    .append("\"splitSkippedLowConfidenceCount\":").append(splitSkippedLowConfidenceCount).append(',')
                    .append("\"lowConfidenceSkipReasonCounts\":");
            appendReasonMap(sb, lowConfidenceSkipReasonCounts);
            sb.append(',').append("\"candidateQualityClassificationCounts\":");
            appendReasonMap(sb, candidateQualityClassificationCounts);
            sb.append('}');
            return sb.toString();
        }

        private static void addSkipReasons(Map<String, Integer> reasons, String warning) {
            int index = warning.indexOf("skipReasons=");
            if (index < 0) return;
            String tail = warning.substring(index + "skipReasons=".length());
            for (String reason : tail.split("\\|", -1)) {
                if (reason.isBlank()) continue;
                reasons.compute(reason.trim(), (ignored, current) -> current == null ? 1 : current + 1);
            }
        }

        private static void appendReasonMap(StringBuilder sb, Map<String, Integer> values) {
            sb.append('{');
            int i = 0;
            for (Map.Entry<String, Integer> entry : values.entrySet()) {
                if (i++ > 0) sb.append(',');
                sb.append(quote(entry.getKey())).append(':').append(entry.getValue());
            }
            sb.append('}');
        }
    }

    private static boolean hasShortSideColumn(List<ColumnShape> shapes) {
        if (shapes.size() < 2) return false;
        int maxLines = 0;
        float maxWidth = 0f;
        for (ColumnShape shape : shapes) maxLines = Math.max(maxLines, shape.lineCount);
        for (ColumnShape shape : shapes) maxWidth = Math.max(maxWidth, shape.width);
        for (ColumnShape shape : shapes) {
            boolean side = shape == shapes.get(0) || shape == shapes.get(shapes.size() - 1);
            if (side && shape.width <= maxWidth * 0.35f && shape.lineCount <= Math.max(2, maxLines / 4)
                    && shape.medianLineWidth <= shape.width * 0.65f) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMarginaliaLikeText(List<ColumnShape> shapes) {
        if (shapes.size() < 2) return false;
        for (ColumnShape shape : shapes) {
            boolean side = shape == shapes.get(0) || shape == shapes.get(shapes.size() - 1);
            if (side && shape.shortTextLineRatio >= 0.75f && shape.lineCount <= 4) return true;
        }
        return false;
    }

    private static float min(List<Float> values) {
        if (values.isEmpty()) return 0f;
        float out = Float.MAX_VALUE;
        for (Float value : values) out = Math.min(out, value == null ? 0f : value);
        return out;
    }

    private static String floats(List<Float> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format(Locale.US, "%.3f", values.get(i)));
        }
        return sb.append(']').toString();
    }

    private static final class ColumnShape {
        final float left;
        final float right;
        final float width;
        final int lineCount;
        final float medianLineWidth;
        final float leftVariance;
        final float rightVariance;
        final float shortTextLineRatio;

        private ColumnShape(float left, float right, int lineCount, float medianLineWidth,
                            float leftVariance, float rightVariance, float shortTextLineRatio) {
            this.left = left;
            this.right = right;
            this.width = Math.max(0f, right - left);
            this.lineCount = lineCount;
            this.medianLineWidth = medianLineWidth;
            this.leftVariance = leftVariance;
            this.rightVariance = rightVariance;
            this.shortTextLineRatio = shortTextLineRatio;
        }

        static ColumnShape from(List<PaddleLayoutReadingBlock> blocks) {
            float left = Float.MAX_VALUE;
            float right = 0f;
            List<Float> lineWidths = new ArrayList<>();
            List<Float> lefts = new ArrayList<>();
            List<Float> rights = new ArrayList<>();
            int shortTextLines = 0;
            int lineCount = 0;
            for (PaddleLayoutReadingBlock block : blocks) {
                left = Math.min(left, block.box.left);
                right = Math.max(right, block.box.right);
                for (PaddleLayoutReadingLine line : block.lines) {
                    lineCount++;
                    left = Math.min(left, line.box.left);
                    right = Math.max(right, line.box.right);
                    lineWidths.add(rectWidth(line.box.left, line.box.right));
                    lefts.add(line.box.left);
                    rights.add(line.box.right);
                    if (line.text.trim().length() <= 8 || line.words.size() <= 2) shortTextLines++;
                }
            }
            if (left == Float.MAX_VALUE) left = 0f;
            float median = median(lineWidths);
            return new ColumnShape(left, right, lineCount, median, standardDeviation(lefts),
                    standardDeviation(rights), lineCount == 0 ? 0f : shortTextLines / (float) lineCount);
        }
    }

    private static float median(List<Float> values) {
        if (values.isEmpty()) return 0f;
        List<Float> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) return sorted.get(middle);
        return (sorted.get(middle - 1) + sorted.get(middle)) * 0.5f;
    }

    private static float standardDeviation(List<Float> values) {
        if (values.size() < 2) return 0f;
        float sum = 0f;
        for (Float value : values) sum += value;
        float mean = sum / values.size();
        float variance = 0f;
        for (Float value : values) {
            float delta = value - mean;
            variance += delta * delta;
        }
        return (float) Math.sqrt(variance / values.size());
    }

    private static float rectWidth(float left, float right) {
        return Math.max(0f, right - left);
    }
}