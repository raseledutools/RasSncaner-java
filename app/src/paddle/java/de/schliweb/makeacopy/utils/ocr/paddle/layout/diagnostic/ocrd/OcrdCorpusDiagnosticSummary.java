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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class OcrdCorpusDiagnosticSummary {
    public final int pageCount;
    public final float meanReconstructedLineCoverage;
    public final float maxOverlapSeverity;
    public final Map<String, Integer> taxonomyCounts;
    public final Map<String, Integer> referenceLayoutCounts;
    public final Map<String, Integer> diagnosticSeverityCounts;
    public final Map<String, Integer> overlapSeverityDistribution;
    public final Map<String, Integer> ocrGeometryHealthDistribution;
    public final int falseColumnCandidateCount;
    public final int sidebarCandidateCount;
    public final int reconstructionActivationCount;
    public final int residualWideReconstructionCount;
    public final int safetyGateTriggeredCount;
    public final int safetyGateSplitAppliedCount;
    public final int safetyGateSplitSkippedLowConfidenceCount;
    public final Map<String, Integer> safetyGateSkipReasonCounts;
    public final Map<String, Integer> candidateQualityClassificationCounts;

    private OcrdCorpusDiagnosticSummary(int pageCount, float meanReconstructedLineCoverage,
                                        float maxOverlapSeverity, Map<String, Integer> taxonomyCounts,
                                        Map<String, Integer> referenceLayoutCounts,
                                        Map<String, Integer> diagnosticSeverityCounts,
                                        Map<String, Integer> overlapSeverityDistribution,
                                        Map<String, Integer> ocrGeometryHealthDistribution,
                                        int falseColumnCandidateCount, int sidebarCandidateCount,
                                        int reconstructionActivationCount, int residualWideReconstructionCount,
                                        int safetyGateTriggeredCount, int safetyGateSplitAppliedCount,
                                        int safetyGateSplitSkippedLowConfidenceCount,
                                        Map<String, Integer> safetyGateSkipReasonCounts,
                                        Map<String, Integer> candidateQualityClassificationCounts) {
        this.pageCount = pageCount;
        this.meanReconstructedLineCoverage = meanReconstructedLineCoverage;
        this.maxOverlapSeverity = maxOverlapSeverity;
        this.taxonomyCounts = new LinkedHashMap<>(taxonomyCounts);
        this.referenceLayoutCounts = new LinkedHashMap<>(referenceLayoutCounts);
        this.diagnosticSeverityCounts = new LinkedHashMap<>(diagnosticSeverityCounts);
        this.overlapSeverityDistribution = new LinkedHashMap<>(overlapSeverityDistribution);
        this.ocrGeometryHealthDistribution = new LinkedHashMap<>(ocrGeometryHealthDistribution);
        this.falseColumnCandidateCount = falseColumnCandidateCount;
        this.sidebarCandidateCount = sidebarCandidateCount;
        this.reconstructionActivationCount = reconstructionActivationCount;
        this.residualWideReconstructionCount = residualWideReconstructionCount;
        this.safetyGateTriggeredCount = safetyGateTriggeredCount;
        this.safetyGateSplitAppliedCount = safetyGateSplitAppliedCount;
        this.safetyGateSplitSkippedLowConfidenceCount = safetyGateSplitSkippedLowConfidenceCount;
        this.safetyGateSkipReasonCounts = new LinkedHashMap<>(safetyGateSkipReasonCounts);
        this.candidateQualityClassificationCounts = new LinkedHashMap<>(candidateQualityClassificationCounts);
    }

    public static OcrdCorpusDiagnosticSummary from(List<OcrdDiagnosticComparison> comparisons) {
        List<OcrdDiagnosticComparison> safe = comparisons == null ? List.of() : new ArrayList<>(comparisons);
        float coverageSum = 0f;
        float maxOverlap = 0f;
        Map<String, Integer> taxonomy = new LinkedHashMap<>();
        Map<String, Integer> layouts = new LinkedHashMap<>();
        Map<String, Integer> severities = new LinkedHashMap<>();
        Map<String, Integer> overlaps = new LinkedHashMap<>();
        Map<String, Integer> geometryHealth = new LinkedHashMap<>();
        int falseColumns = 0;
        int sidebars = 0;
        int reconstructionActivations = 0;
        int residualWide = 0;
        int safetyTriggered = 0;
        int safetyApplied = 0;
        int safetySkipped = 0;
        Map<String, Integer> safetyReasons = new LinkedHashMap<>();
        Map<String, Integer> candidateQuality = new LinkedHashMap<>();
        for (OcrdDiagnosticComparison comparison : safe) {
            coverageSum += comparison.reconstructedLineCoverage;
            maxOverlap = Math.max(maxOverlap, comparison.overlapSeverity);
            increment(layouts, comparison.referenceLayoutClass);
            increment(severities, comparison.diagnosticSeverity);
            increment(overlaps, overlapBucket(comparison.overlapSeverity));
            increment(geometryHealth, geometryHealthBucket(comparison));
            if (comparison.columnDiagnostics != null) {
                if (comparison.columnDiagnostics.falseColumnCandidateOnSingleColumnReference) falseColumns++;
                if (comparison.columnDiagnostics.marginaliaOrSidebarCandidate) sidebars++;
                if (comparison.columnDiagnostics.reconstructionActivationCount > 0) reconstructionActivations++;
                if (comparison.columnDiagnostics.residualWideReconstructedLineCount > 0) residualWide++;
            }
            if (comparison.safetyGateDiagnostics != null) {
                safetyTriggered += comparison.safetyGateDiagnostics.triggeredCount;
                safetyApplied += comparison.safetyGateDiagnostics.splitAppliedCount;
                safetySkipped += comparison.safetyGateDiagnostics.splitSkippedLowConfidenceCount;
                for (Map.Entry<String, Integer> entry : comparison.safetyGateDiagnostics.lowConfidenceSkipReasonCounts.entrySet()) {
                    safetyReasons.compute(entry.getKey(), (ignored, current) -> current == null ? entry.getValue() : current + entry.getValue());
                }
                for (Map.Entry<String, Integer> entry : comparison.safetyGateDiagnostics.candidateQualityClassificationCounts.entrySet()) {
                    candidateQuality.compute(entry.getKey(), (ignored, current) -> current == null ? entry.getValue() : current + entry.getValue());
                }
            }
            for (String category : comparison.failureCategories) increment(taxonomy, category);
        }
        float meanCoverage = safe.isEmpty() ? 0f : coverageSum / safe.size();
        return new OcrdCorpusDiagnosticSummary(safe.size(), meanCoverage, maxOverlap, taxonomy, layouts,
                severities, overlaps, geometryHealth, falseColumns, sidebars, reconstructionActivations, residualWide,
                safetyTriggered, safetyApplied, safetySkipped, safetyReasons, candidateQuality);
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"pageCount\": ").append(pageCount).append(",\n");
        sb.append(String.format(Locale.US, "  \"meanReconstructedLineCoverage\": %.3f,\n", meanReconstructedLineCoverage));
        sb.append(String.format(Locale.US, "  \"maxOverlapSeverity\": %.3f,\n", maxOverlapSeverity));
        appendMap(sb, "taxonomyCounts", taxonomyCounts);
        sb.append(",\n");
        appendMap(sb, "referenceLayoutCounts", referenceLayoutCounts);
        sb.append(",\n");
        appendMap(sb, "diagnosticSeverityCounts", diagnosticSeverityCounts);
        sb.append(",\n");
        appendMap(sb, "overlapSeverityDistribution", overlapSeverityDistribution);
        sb.append(",\n");
        appendMap(sb, "ocrGeometryHealthDistribution", ocrGeometryHealthDistribution);
        sb.append(",\n");
        sb.append("  \"falseColumnCandidateCount\": ").append(falseColumnCandidateCount).append(",\n");
        sb.append("  \"sidebarCandidateCount\": ").append(sidebarCandidateCount).append(",\n");
        sb.append("  \"reconstructionActivationCount\": ").append(reconstructionActivationCount).append(",\n");
        sb.append("  \"residualWideReconstructionCount\": ").append(residualWideReconstructionCount).append(",\n");
        sb.append("  \"safetyGateDecisionSummary\": {\n");
        sb.append("    \"triggeredCount\": ").append(safetyGateTriggeredCount).append(",\n");
        sb.append("    \"splitAppliedCount\": ").append(safetyGateSplitAppliedCount).append(",\n");
        sb.append("    \"splitSkippedLowConfidenceCount\": ").append(safetyGateSplitSkippedLowConfidenceCount).append(",\n");
        appendMap(sb, "skipReasonCounts", safetyGateSkipReasonCounts);
        sb.append(",\n");
        appendMap(sb, "candidateQualityClassificationCounts", candidateQualityClassificationCounts);
        sb.append("\n  }");
        sb.append("\n}");
        return sb.toString();
    }

    private static String overlapBucket(float severity) {
        if (severity <= 0f) return "none";
        if (severity < 0.35f) return "contained";
        if (severity < 0.75f) return "readability-risk";
        return "severe";
    }

    private static String geometryHealthBucket(OcrdDiagnosticComparison comparison) {
        if (comparison.overlapSeverity >= 0.75f || comparison.reconstructedLineCoverage < 0.50f) return "poor";
        if (comparison.overlapSeverity > 0f || comparison.reconstructedLineCoverage < 0.82f
                || comparison.crossColumnInstabilityCount > 0) return "unstable";
        if (comparison.columnDiagnostics != null && (comparison.columnDiagnostics.falseColumnCandidateOnSingleColumnReference
                || comparison.columnDiagnostics.marginaliaOrSidebarCandidate)) return "ambiguous";
        return "healthy";
    }

    private static void increment(Map<String, Integer> values, String key) {
        String safeKey = key == null || key.isBlank() ? "unknown" : key;
        values.compute(safeKey, (ignored, current) -> current == null ? 1 : current + 1);
    }

    private static void appendMap(StringBuilder sb, String name, Map<String, Integer> values) {
        sb.append("  \"").append(name).append("\": {");
        int i = 0;
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            if (i++ > 0) sb.append(',');
            sb.append("\n    ").append(quote(entry.getKey())).append(": ").append(entry.getValue());
        }
        if (!values.isEmpty()) sb.append('\n');
        sb.append("  }");
    }

    private static String quote(String text) {
        return text == null ? "null" : "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}