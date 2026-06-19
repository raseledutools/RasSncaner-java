/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.ocr.paddle.layout.diagnostic;

import android.graphics.RectF;

import de.schliweb.makeacopy.utils.ocr.RecognizedWord;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.PaddleLayoutClass;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.PaddleLayoutRegion;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.PaddleLayoutResult;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.diagnostic.ocrd.OcrdDiagnosticComparison;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.reading.PaddleLayoutOrderingIssue;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.reading.PaddleLayoutReadingBlock;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.reading.PaddleLayoutReadingDebugReport;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class PaddleLayoutClassIdDiagnostics {
    private final Map<Integer, ClassEntry> classEntries = new TreeMap<>();
    private final List<PageEntry> pages = new ArrayList<>();
    private final List<ComparisonPageEntry> comparisonPages = new ArrayList<>();
    private int totalRegions;

    public void addPage(String pageId, PaddleLayoutResult layoutResult,
                        PaddleLayoutReadingDebugReport mappedReport,
                        PaddleLayoutReadingDebugReport rawGeometryReport,
                        OcrdDiagnosticComparison mappedComparison,
                        List<RecognizedWord> words) {
        int mappedFiltered = mappedReport == null ? 0 : mappedReport.filteredRegionCount;
        int rawFiltered = rawGeometryReport == null ? 0 : rawGeometryReport.filteredRegionCount;
        pages.add(new PageEntry(pageId, layoutResult.regions.size(), mappedFiltered, rawFiltered,
                issueSummary(mappedReport), issueSummary(rawGeometryReport)));
        comparisonPages.add(new ComparisonPageEntry(pageId, mappedReport, rawGeometryReport, mappedComparison));
        for (int i = 0; i < layoutResult.regions.size(); i++) {
            PaddleLayoutRegion region = layoutResult.regions.get(i);
            totalRegions++;
            classEntries.computeIfAbsent(region.classId, ClassEntry::new)
                    .add(pageId, i, region, layoutResult.sourceWidth, layoutResult.sourceHeight, mappedReport,
                            rawGeometryReport, words);
        }
    }

    public String toJson(String scope, Map<String, String> metadataEvidence) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"scope\": ").append(quote(scope)).append(",\n");
        sb.append("  \"mappingUnderValidation\": \"PaddleLayoutClass.fromClassId\",\n");
        sb.append("  \"mappingDecision\": \"unchanged_diagnostic_only\",\n");
        sb.append("  \"mappingVerificationStatus\": ").append(quote(PaddleLayoutClass.mappingVerificationStatus())).append(",\n");
        sb.append("  \"recommendedDiagnosticPolicy\": ").append(quote(PaddleLayoutClass.recommendedDiagnosticPolicy())).append(",\n");
        sb.append("  \"localPaddleXEvidence\": \"paddleocr 3.5.0 / paddlex 3.5.1 official_categories.py defines PP-DocLayout-S/L/M as 0=paragraph_title, 1=image, 2=text, ..., 22=aside_text; Android layout.ort has no adjacent label metadata.\",\n");
        sb.append("  \"metadataEvidence\": ");
        appendStringMap(sb, metadataEvidence);
        sb.append(",\n  \"pageCount\": ").append(pages.size()).append(",\n");
        sb.append("  \"totalRegions\": ").append(totalRegions).append(",\n");
        sb.append("  \"pages\": [");
        for (int i = 0; i < pages.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('\n').append("    ");
            pages.get(i).appendJson(sb);
        }
        if (!pages.isEmpty()) sb.append('\n');
        sb.append("  ],\n  \"classes\": {");
        int i = 0;
        for (ClassEntry entry : classEntries.values()) {
            if (i++ > 0) sb.append(',');
            sb.append('\n').append("    \"").append(entry.classId).append("\": ");
            entry.appendJson(sb);
        }
        if (!classEntries.isEmpty()) sb.append('\n');
        sb.append("  }\n}\n");
        return sb.toString();
    }

    public String comparisonReportJson(String scope) {
        Map<String, Integer> textualityCounts = new LinkedHashMap<>();
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"scope\": ").append(quote(scope)).append(",\n");
        sb.append("  \"policy\": \"diagnostic_only_mapped_vs_raw_id_geometry\",\n");
        sb.append("  \"mappingDecision\": \"unchanged\",\n");
        sb.append("  \"mappingVerificationStatus\": ").append(quote(PaddleLayoutClass.mappingVerificationStatus())).append(",\n");
        sb.append("  \"recommendedDiagnosticPolicy\": ").append(quote(PaddleLayoutClass.recommendedDiagnosticPolicy())).append(",\n");
        sb.append("  \"pageCount\": ").append(comparisonPages.size()).append(",\n");
        sb.append("  \"aggregateDifferences\": ");
        appendAggregateDifferences(sb);
        sb.append(",\n  \"rawClassIdTextuality\": {");
        int i = 0;
        for (ClassEntry entry : classEntries.values()) {
            String classification = entry.geometryTextuality();
            textualityCounts.compute(classification, (key, value) -> value == null ? 1 : value + 1);
            if (i++ > 0) sb.append(',');
            sb.append('\n').append("    \"").append(entry.classId).append("\": ");
            entry.appendComparisonJson(sb, classification);
        }
        if (!classEntries.isEmpty()) sb.append('\n');
        sb.append("  },\n  \"textualitySummary\": ");
        appendIntegerMap(sb, textualityCounts);
        sb.append(",\n  \"pages\": [");
        for (i = 0; i < comparisonPages.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('\n').append("    ");
            comparisonPages.get(i).appendJson(sb);
        }
        if (!comparisonPages.isEmpty()) sb.append('\n');
        sb.append("  ]\n}\n");
        return sb.toString();
    }

    private void appendAggregateDifferences(StringBuilder sb) {
        int mappedFallback = 0;
        int rawFallback = 0;
        double candidateDelta = 0;
        double assignedDelta = 0;
        double blockDelta = 0;
        double columnDelta = 0;
        Map<String, Integer> mappedIssueCounts = new TreeMap<>();
        Map<String, Integer> rawIssueCounts = new TreeMap<>();
        Map<String, Integer> ocrdCategories = new TreeMap<>();
        for (ComparisonPageEntry page : comparisonPages) {
            candidateDelta += page.rawTextualCandidateCount - page.mappedTextualCandidateCount;
            assignedDelta += page.rawAssignedLineRatio - page.mappedAssignedLineRatio;
            blockDelta += page.rawBlockCount - page.mappedBlockCount;
            columnDelta += page.rawColumnCount - page.mappedColumnCount;
            if (page.mappedFallbackColumnUsage) mappedFallback++;
            if (page.rawFallbackColumnUsage) rawFallback++;
            addAll(mappedIssueCounts, page.mappedIssueTypes);
            addAll(rawIssueCounts, page.rawIssueTypes);
            addAll(ocrdCategories, page.ocrdComparisonCategories);
        }
        int count = Math.max(1, comparisonPages.size());
        sb.append(String.format(Locale.US,
                "{\"averageTextualCandidateDeltaRawMinusMapped\": %.3f, \"averageAssignedLineRatioDeltaRawMinusMapped\": %.6f, \"averageBlockCountDeltaRawMinusMapped\": %.3f, \"averageColumnCountDeltaRawMinusMapped\": %.3f, \"mappedFallbackColumnPages\": %d, \"rawFallbackColumnPages\": %d, ",
                candidateDelta / count, assignedDelta / count, blockDelta / count, columnDelta / count,
                mappedFallback, rawFallback));
        sb.append("\"mappedIssueTypes\": ");
        appendIntegerMap(sb, mappedIssueCounts);
        sb.append(", \"rawIssueTypes\": ");
        appendIntegerMap(sb, rawIssueCounts);
        sb.append(", \"ocrdComparisonCategories\": ");
        appendIntegerMap(sb, ocrdCategories);
        sb.append('}');
    }

    private static void appendStringMap(StringBuilder sb, Map<String, String> map) {
        sb.append('{');
        if (map != null) {
            int i = 0;
            for (Map.Entry<String, String> entry : map.entrySet()) {
                if (i++ > 0) sb.append(',');
                sb.append('\n').append("    ").append(quote(entry.getKey())).append(": ").append(quote(entry.getValue()));
            }
            if (!map.isEmpty()) sb.append('\n').append("  ");
        }
        sb.append('}');
    }

    private static void appendIntegerMap(StringBuilder sb, Map<String, Integer> map) {
        sb.append('{');
        int i = 0;
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (i++ > 0) sb.append(',');
            sb.append(quote(entry.getKey())).append(": ").append(entry.getValue());
        }
        sb.append('}');
    }

    private static void addAll(Map<String, Integer> counts, Collection<String> values) {
        for (String value : values) counts.compute(value, (key, current) -> current == null ? 1 : current + 1);
    }

    private static String position(float centerX, float centerY) {
        String vertical = centerY < 0.25f ? "top" : centerY > 0.75f ? "bottom" : "middle";
        String horizontal = centerX < 0.33f ? "left" : centerX > 0.67f ? "right" : "center";
        return vertical + "_" + horizontal;
    }

    private static String issueSummary(PaddleLayoutReadingDebugReport report) {
        if (report == null || report.quality == null) return null;
        return "issues=" + report.quality.issues.size()
                + ",crossColumn=" + report.quality.crossColumnJumpCount
                + ",weak=" + report.quality.weakAssignmentCount
                + ",ambiguous=" + report.quality.ambiguousAssignmentCount;
    }

    public static String classifyTextuality(int count, double medianAreaRatio, double medianAspectRatio,
                                            double averageConfidence, double lineCoverage,
                                            double wordOverlapCoverage, double stableContributionRatio) {
        if (count < 3) return "TOO_RARE_TO_CLASSIFY";
        double textualEvidence = 0;
        if (lineCoverage >= 0.50) textualEvidence += 2;
        else if (lineCoverage >= 0.20) textualEvidence += 1;
        if (wordOverlapCoverage >= 0.45) textualEvidence += 2;
        else if (wordOverlapCoverage >= 0.15) textualEvidence += 1;
        if (medianAspectRatio >= 1.8 && medianAreaRatio >= 0.002) textualEvidence += 1;
        if (averageConfidence >= 0.45) textualEvidence += 1;
        if (stableContributionRatio >= 0.60) textualEvidence += 1;
        double nonTextualEvidence = 0;
        if (lineCoverage < 0.10) nonTextualEvidence += 2;
        if (wordOverlapCoverage < 0.10) nonTextualEvidence += 2;
        if (medianAreaRatio > 0.35 || medianAspectRatio < 0.35) nonTextualEvidence += 1;
        if (textualEvidence >= 5 && textualEvidence >= nonTextualEvidence + 2) return "TEXTUAL_BY_GEOMETRY";
        if (nonTextualEvidence >= 4 && nonTextualEvidence >= textualEvidence + 2) return "NON_TEXTUAL_BY_GEOMETRY";
        return "AMBIGUOUS_BY_GEOMETRY";
    }

    private static String quote(String text) {
        if (text == null) return "null";
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static final class PageEntry {
        final String pageId;
        final int layoutRegionCount;
        final int mappedTextualCandidateCount;
        final int rawGeometryCandidateCount;
        final String mappedQualitySummary;
        final String rawGeometryQualitySummary;

        PageEntry(String pageId, int layoutRegionCount, int mappedTextualCandidateCount,
                  int rawGeometryCandidateCount, String mappedQualitySummary, String rawGeometryQualitySummary) {
            this.pageId = pageId;
            this.layoutRegionCount = layoutRegionCount;
            this.mappedTextualCandidateCount = mappedTextualCandidateCount;
            this.rawGeometryCandidateCount = rawGeometryCandidateCount;
            this.mappedQualitySummary = mappedQualitySummary;
            this.rawGeometryQualitySummary = rawGeometryQualitySummary;
        }

        void appendJson(StringBuilder sb) {
            sb.append("{\"pageId\": ").append(quote(pageId))
                    .append(", \"layoutRegionCount\": ").append(layoutRegionCount)
                    .append(", \"mappedTextualCandidateCount\": ").append(mappedTextualCandidateCount)
                    .append(", \"rawGeometryCandidateCount\": ").append(rawGeometryCandidateCount)
                    .append(", \"mappedQualitySummary\": ").append(quote(mappedQualitySummary))
                    .append(", \"rawGeometryQualitySummary\": ").append(quote(rawGeometryQualitySummary)).append('}');
        }
    }

    private static final class ComparisonPageEntry {
        final String pageId;
        final int mappedTextualCandidateCount;
        final int rawTextualCandidateCount;
        final boolean mappedFallbackColumnUsage;
        final boolean rawFallbackColumnUsage;
        final double mappedAssignedLineRatio;
        final double rawAssignedLineRatio;
        final int mappedBlockCount;
        final int rawBlockCount;
        final int mappedColumnCount;
        final int rawColumnCount;
        final double mappedOverlapSeverity;
        final String mappedReadingOrderSeverity;
        final String rawReadingOrderSeverity;
        final List<String> mappedIssueTypes;
        final List<String> rawIssueTypes;
        final List<String> ocrdComparisonCategories;

        ComparisonPageEntry(String pageId, PaddleLayoutReadingDebugReport mappedReport,
                            PaddleLayoutReadingDebugReport rawReport, OcrdDiagnosticComparison mappedComparison) {
            this.pageId = pageId;
            mappedTextualCandidateCount = mappedReport == null ? 0 : mappedReport.filteredRegionCount;
            rawTextualCandidateCount = rawReport == null ? 0 : rawReport.filteredRegionCount;
            mappedFallbackColumnUsage = hasFallback(mappedReport);
            rawFallbackColumnUsage = hasFallback(rawReport);
            mappedAssignedLineRatio = assignedRatio(mappedReport);
            rawAssignedLineRatio = assignedRatio(rawReport);
            mappedBlockCount = blockCount(mappedReport);
            rawBlockCount = blockCount(rawReport);
            mappedColumnCount = columnCount(mappedReport);
            rawColumnCount = columnCount(rawReport);
            mappedOverlapSeverity = mappedComparison == null ? 0d : mappedComparison.overlapSeverity;
            mappedReadingOrderSeverity = severity(mappedReport);
            rawReadingOrderSeverity = severity(rawReport);
            mappedIssueTypes = issueTypes(mappedReport);
            rawIssueTypes = issueTypes(rawReport);
            ocrdComparisonCategories = mappedComparison == null ? Collections.emptyList() : mappedComparison.failureCategories;
        }

        void appendJson(StringBuilder sb) {
            sb.append(String.format(Locale.US,
                    "{\"pageId\": %s, \"mappedTextualCandidateCount\": %d, \"rawGeometryTextualCandidateCount\": %d, \"textualCandidateDeltaRawMinusMapped\": %d, \"mappedFallbackColumnUsage\": %s, \"rawFallbackColumnUsage\": %s, \"mappedAssignedLineRatio\": %.6f, \"rawAssignedLineRatio\": %.6f, \"mappedBlockCount\": %d, \"rawBlockCount\": %d, \"mappedColumnCount\": %d, \"rawColumnCount\": %d, \"mappedOverlapSeverity\": %.6f, \"mappedReadingOrderSeverity\": %s, \"rawReadingOrderSeverity\": %s, ",
                    quote(pageId), mappedTextualCandidateCount, rawTextualCandidateCount,
                    rawTextualCandidateCount - mappedTextualCandidateCount, mappedFallbackColumnUsage,
                    rawFallbackColumnUsage, mappedAssignedLineRatio, rawAssignedLineRatio, mappedBlockCount,
                    rawBlockCount, mappedColumnCount, rawColumnCount, mappedOverlapSeverity,
                    quote(mappedReadingOrderSeverity), quote(rawReadingOrderSeverity)));
            sb.append("\"mappedIssueTypes\": ");
            appendStringList(sb, mappedIssueTypes);
            sb.append(", \"rawIssueTypes\": ");
            appendStringList(sb, rawIssueTypes);
            sb.append(", \"ocrdComparisonCategories\": ");
            appendStringList(sb, ocrdComparisonCategories);
            sb.append('}');
        }
    }

    private static final class ClassEntry {
        final int classId;
        final String currentLabel;
        final Map<String, Integer> positions = new LinkedHashMap<>();
        final List<String> samples = new ArrayList<>();
        int count;
        double confidenceSum;
        double areaRatioSum;
        double centerXSum;
        double centerYSum;
        final List<Double> areaRatios = new ArrayList<>();
        final List<Double> aspectRatios = new ArrayList<>();
        int mappedLineContributionCount;
        int rawLineContributionCount;
        int stableContributionCount;
        int wordOverlapCount;

        ClassEntry(int classId) {
            this.classId = classId;
            this.currentLabel = PaddleLayoutClass.fromClassId(classId).label();
        }

        void add(String pageId, int index, PaddleLayoutRegion region, int sourceWidth, int sourceHeight,
                 PaddleLayoutReadingDebugReport mappedReport, PaddleLayoutReadingDebugReport rawGeometryReport,
                 List<RecognizedWord> words) {
            count++;
            confidenceSum += region.confidence;
            float width = Math.max(0f, region.right - region.left);
            float height = Math.max(0f, region.bottom - region.top);
            double areaRatio = width * height / Math.max(1f, sourceWidth * (float) sourceHeight);
            areaRatioSum += areaRatio;
            areaRatios.add(areaRatio);
            aspectRatios.add((double) (width / Math.max(1f, height)));
            float centerX = (region.left + region.right) * 0.5f / Math.max(1f, sourceWidth);
            float centerY = (region.top + region.bottom) * 0.5f / Math.max(1f, sourceHeight);
            centerXSum += centerX;
            centerYSum += centerY;
            positions.compute(position(centerX, centerY), (key, value) -> value == null ? 1 : value + 1);
            if (samples.size() < 5) samples.add(pageId + "#" + index);
            boolean mappedLines = contributesLines(mappedReport, region.classId);
            boolean rawLines = contributesLines(rawGeometryReport, region.classId);
            if (mappedLines) mappedLineContributionCount++;
            if (rawLines) rawLineContributionCount++;
            if (mappedLines && rawLines && isStable(rawGeometryReport)) stableContributionCount++;
            if (overlapsWords(region, words)) wordOverlapCount++;
        }

        void appendJson(StringBuilder sb) {
            sb.append(String.format(Locale.US,
                    "{\"currentLabel\": %s, \"count\": %d, \"averageConfidence\": %.6f, \"averageAreaRatio\": %.8f, \"averageCenter\": [%.6f, %.6f], ",
                    quote(currentLabel), count, confidenceSum / Math.max(1, count), areaRatioSum / Math.max(1, count),
                    centerXSum / Math.max(1, count), centerYSum / Math.max(1, count)));
            sb.append("\"commonSpatialPosition\": ").append(quote(commonPosition())).append(", \"positionCounts\": {");
            int i = 0;
            for (Map.Entry<String, Integer> entry : positions.entrySet()) {
                if (i++ > 0) sb.append(',');
                sb.append(quote(entry.getKey())).append(": ").append(entry.getValue());
            }
            sb.append("}, \"sampleRegions\": [");
            for (i = 0; i < samples.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(quote(samples.get(i)));
            }
            sb.append("]}");
        }

        void appendComparisonJson(StringBuilder sb, String textuality) {
            sb.append(String.format(Locale.US,
                    "{\"currentLabel\": %s, \"localPaddleXOfficialLabel\": %s, \"currentMappingMatchesLocalPaddleX\": %s, \"geometryTextuality\": %s, \"count\": %d, \"medianAreaRatio\": %.8f, \"medianWidthHeightRatio\": %.6f, \"averageConfidence\": %.6f, \"typicalVerticalPosition\": %.6f, \"mappedLineAssignmentCoverage\": %.6f, \"lineAssignmentCoverage\": %.6f, \"ocrWordOverlapCoverage\": %.6f, \"stableReadingOrderContributionRatio\": %.6f, \"commonSpatialPosition\": %s, ",
                    quote(currentLabel), quote(PaddleLayoutClass.localPaddleXOfficialLabelForClassId(classId)),
                    currentLabel.equals(PaddleLayoutClass.localPaddleXOfficialLabelForClassId(classId)),
                    quote(textuality), count, median(areaRatios), median(aspectRatios),
                    confidenceSum / Math.max(1, count), centerYSum / Math.max(1, count),
                    mappedLineContributionCount / (double) Math.max(1, count),
                    rawLineContributionCount / (double) Math.max(1, count),
                    wordOverlapCount / (double) Math.max(1, count),
                    stableContributionCount / (double) Math.max(1, count), quote(commonPosition())));
            sb.append("\"sampleRegions\": [");
            for (int i = 0; i < samples.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(quote(samples.get(i)));
            }
            sb.append("]}");
        }

        private String geometryTextuality() {
            return classifyTextuality(count, median(areaRatios), median(aspectRatios), confidenceSum / Math.max(1, count),
                    rawLineContributionCount / (double) Math.max(1, count),
                    wordOverlapCount / (double) Math.max(1, count),
                    stableContributionCount / (double) Math.max(1, count));
        }

        private String commonPosition() {
            String best = "none";
            int bestCount = 0;
            for (Map.Entry<String, Integer> entry : positions.entrySet()) {
                if (entry.getValue() > bestCount) {
                    best = entry.getKey();
                    bestCount = entry.getValue();
                }
            }
            return best;
        }
    }

    private static boolean hasFallback(PaddleLayoutReadingDebugReport report) {
        if (report == null) return false;
        for (PaddleLayoutReadingBlock block : report.blocks) {
            if (block.region == null || block.columnConfidence.toLowerCase(Locale.US).contains("fallback")) return true;
        }
        for (String warning : report.warnings) {
            if (warning.contains("FALLBACK")) return true;
        }
        return false;
    }

    private static double assignedRatio(PaddleLayoutReadingDebugReport report) {
        return report == null || report.quality == null ? 0d : report.quality.assignedLineRatio;
    }

    private static int blockCount(PaddleLayoutReadingDebugReport report) {
        return report == null || report.quality == null ? 0 : report.quality.blockCount;
    }

    private static int columnCount(PaddleLayoutReadingDebugReport report) {
        return report == null || report.quality == null ? 0 : report.quality.columnCount;
    }

    private static String severity(PaddleLayoutReadingDebugReport report) {
        if (report == null || report.quality == null) return "NONE";
        int issues = report.quality.issues.size();
        if (report.quality.crossColumnJumpCount > 0 || issues >= 3) return "READABILITY_RISK";
        if (issues > 0 || report.quality.weakAssignmentCount > 0 || report.quality.ambiguousAssignmentCount > 0) {
            return "SUSPICIOUS_BUT_CONTAINED";
        }
        return "HARMLESS_INFORMATIONAL_WARNING";
    }

    private static List<String> issueTypes(PaddleLayoutReadingDebugReport report) {
        List<String> out = new ArrayList<>();
        if (report == null || report.quality == null) return out;
        for (PaddleLayoutOrderingIssue issue : report.quality.issues) out.add(issue.type.name());
        return out;
    }

    private static boolean contributesLines(PaddleLayoutReadingDebugReport report, int classId) {
        if (report == null) return false;
        for (PaddleLayoutReadingBlock block : report.blocks) {
            if (block.region != null && block.region.classId == classId && !block.lines.isEmpty()) return true;
        }
        return false;
    }

    private static boolean isStable(PaddleLayoutReadingDebugReport report) {
        return report != null && report.quality != null && report.quality.crossColumnJumpCount == 0
                && report.quality.issues.size() <= 1;
    }

    private static boolean overlapsWords(PaddleLayoutRegion region, List<RecognizedWord> words) {
        if (words == null || words.isEmpty()) return false;
        RectF box = new RectF(region.left, region.top, region.right, region.bottom);
        for (RecognizedWord word : words) {
            RectF wordBox = word.getBoundingBox();
            if (RectF.intersects(box, wordBox)) return true;
        }
        return false;
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) return 0d;
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) return sorted.get(middle);
        return (sorted.get(middle - 1) + sorted.get(middle)) * 0.5d;
    }

    private static void appendStringList(StringBuilder sb, List<String> values) {
        sb.append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(quote(values.get(i)));
        }
        sb.append(']');
    }
}