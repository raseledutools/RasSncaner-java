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

import de.schliweb.makeacopy.utils.ocr.RecognizedWord;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.diagnostic.ocrd.OcrdDiagnosticComparison;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.diagnostic.ocrd.OcrdCorpusDiagnosticSummary;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.diagnostic.ocrd.OcrdEvaluationBox;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.diagnostic.ocrd.OcrdEvaluationLine;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.diagnostic.ocrd.OcrdEvaluationPage;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.diagnostic.ocrd.OcrdEvaluationRegion;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OcrdDiagnosticComparisonColumnDiagnosticsTest {
    @Test
    public void singleColumnReferenceWithFallbackSplit_reportsFalseColumnCandidate() {
        OcrdDiagnosticComparison comparison = OcrdDiagnosticComparison.compare(singleColumnReference(), report(
                block(0, 0, "fallback", line(0, 100, 100, 430, 120, 8), line(1, 105, 140, 435, 160, 8)),
                block(1, 1, "fallback", line(2, 450, 100, 790, 120, 8), line(3, 445, 140, 780, 160, 8))));

        assertEquals(1, comparison.referenceColumnCount);
        assertEquals(2, comparison.columnDiagnostics.detectedColumnCount);
        assertTrue(comparison.columnDiagnostics.falseColumnCandidateOnSingleColumnReference);
        assertTrue(comparison.failureCategories.contains("FALSE_COLUMN_CANDIDATE_ON_SINGLE_COLUMN_REFERENCE"));
        assertFalse(comparison.failureCategories.contains("MARGINALIA_OR_SIDEBAR_CANDIDATE"));
    }

    @Test
    public void trueSidebarLikeColumn_reportsSidebarCandidateInsteadOfFalseSplit() {
        OcrdDiagnosticComparison comparison = OcrdDiagnosticComparison.compare(singleColumnReference(), report(
                block(0, 0, "region", line(0, 130, 100, 740, 120, 14), line(1, 128, 140, 735, 160, 14),
                        line(2, 126, 180, 738, 200, 14), line(3, 127, 220, 734, 240, 14),
                        line(4, 128, 260, 736, 280, 14), line(5, 129, 300, 737, 320, 14)),
                block(1, 1, "fallback", line(6, 790, 95, 830, 115, 1))));

        assertTrue(comparison.columnDiagnostics.marginaliaOrSidebarCandidate);
        assertTrue(comparison.failureCategories.contains("MARGINALIA_OR_SIDEBAR_CANDIDATE"));
        assertFalse(comparison.failureCategories.contains("FALSE_COLUMN_CANDIDATE_ON_SINGLE_COLUMN_REFERENCE"));
    }

    @Test
    public void residualWideReconstructedLine_reportsDedicatedDiagnosticArtifact() {
        OcrdDiagnosticComparison comparison = OcrdDiagnosticComparison.compare(singleColumnReference(), report(
                block(0, 0, "region", line(0, 80, 100, 760, 120, 20))));

        assertEquals(1, comparison.columnDiagnostics.residualWideReconstructedLineCount);
        assertTrue(comparison.failureCategories.contains("RESIDUAL_WIDE_RECONSTRUCTION_ARTIFACT"));
        assertTrue(comparison.toJson().contains("\"residualWideReconstructedLineCount\":1"));
    }

    @Test
    public void diagnosticSeverityRanksFalseColumnsBelowReconstructionArtifacts() {
        OcrdDiagnosticComparison falseColumn = OcrdDiagnosticComparison.compare(singleColumnReference(), report(
                block(0, 0, "fallback", line(0, 100, 100, 430, 120, 8), line(1, 105, 140, 435, 160, 8)),
                block(1, 1, "fallback", line(2, 450, 100, 790, 120, 8))));
        OcrdDiagnosticComparison reconstructionArtifact = OcrdDiagnosticComparison.compare(singleColumnReference(), report(
                block(0, 0, "region", line(0, 80, 100, 760, 120, 20))));

        assertEquals("SUSPICIOUS_BUT_CONTAINED", falseColumn.diagnosticSeverity);
        assertEquals("SEVERE_GEOMETRY_CORRUPTION", reconstructionArtifact.diagnosticSeverity);
        assertTrue(reconstructionArtifact.toJson().contains("\"diagnosticSeverity\": \"SEVERE_GEOMETRY_CORRUPTION\""));
    }

    @Test
    public void corpusSummaryAggregatesExplainabilityFrequenciesAndDistributions() {
        OcrdDiagnosticComparison falseColumn = OcrdDiagnosticComparison.compare(singleColumnReference(), report(
                block(0, 0, "fallback", line(0, 100, 100, 430, 120, 8), line(1, 105, 140, 435, 160, 8)),
                block(1, 1, "fallback", line(2, 450, 100, 790, 120, 8))));
        OcrdDiagnosticComparison sidebar = OcrdDiagnosticComparison.compare(singleColumnReference(), report(
                block(0, 0, "region", line(0, 130, 100, 740, 120, 14), line(1, 128, 140, 735, 160, 14),
                        line(2, 126, 180, 738, 200, 14), line(3, 127, 220, 734, 240, 14),
                        line(4, 128, 260, 736, 280, 14), line(5, 129, 300, 737, 320, 14)),
                block(1, 1, "fallback", line(6, 790, 95, 830, 115, 1))));

        OcrdCorpusDiagnosticSummary summary = OcrdCorpusDiagnosticSummary.from(Arrays.asList(falseColumn, sidebar));

        assertEquals(2, summary.pageCount);
        assertEquals(1, summary.falseColumnCandidateCount);
        assertEquals(1, summary.sidebarCandidateCount);
        assertEquals(Integer.valueOf(1), summary.diagnosticSeverityCounts.get("SUSPICIOUS_BUT_CONTAINED"));
        assertEquals(Integer.valueOf(1), summary.diagnosticSeverityCounts.get("READABILITY_RISK"));
        assertTrue(summary.toJson().contains("\"ocrGeometryHealthDistribution\""));
        assertTrue(summary.toJson().contains("\"falseColumnCandidateCount\": 1"));
    }

    @Test
    public void reconstructionGutterCrossing_reportsDedicatedCategoryAndJsonMetrics() {
        PaddleLayoutReconstructionDiagnostic diagnostic = reconstructionDiagnostic(0.66f, true, 0.10f, 14,
                2, 42f, 120f, 4f);
        OcrdDiagnosticComparison comparison = OcrdDiagnosticComparison.compare(singleColumnReference(), report(
                Collections.singletonList(diagnostic), block(0, 0, "region", line(0, 120, 100, 720, 120, 14))));

        assertTrue(comparison.failureCategories.contains("RECONSTRUCTION_GUTTER_CROSSING"));
        assertTrue(comparison.failureCategories.contains("RECONSTRUCTION_OVER_MERGE"));
        assertTrue(comparison.failureCategories.contains("RECONSTRUCTION_COLUMN_INTERLEAVING"));
        assertTrue(comparison.toJson().contains("RECONSTRUCTION_DIAGNOSTICS"));
    }

    @Test
    public void reconstructionOverlapAndFragmentChain_reportsInstabilityCategories() {
        PaddleLayoutReconstructionDiagnostic diagnostic = reconstructionDiagnostic(0.50f, false, 0.80f, 22,
                1, 18f, 54f, 14f);
        OcrdDiagnosticComparison comparison = OcrdDiagnosticComparison.compare(singleColumnReference(), report(
                Collections.singletonList(diagnostic), block(0, 0, "region", line(0, 120, 100, 570, 120, 22))));

        assertTrue(comparison.failureCategories.contains("RECONSTRUCTION_FRAGMENT_CHAIN"));
        assertTrue(comparison.explainabilityTraces.toString().contains("maxOverlap=0.800"));
        assertTrue(comparison.toJson().contains("RECONSTRUCTION_FRAGMENT_CHAIN"));
    }

    private static OcrdEvaluationPage singleColumnReference() {
        List<OcrdEvaluationLine> lines = Arrays.asList(
                new OcrdEvaluationLine("l1", "reference one", new OcrdEvaluationBox(100, 100, 780, 120), Collections.emptyList()),
                new OcrdEvaluationLine("l2", "reference two", new OcrdEvaluationBox(100, 140, 780, 160), Collections.emptyList()),
                new OcrdEvaluationLine("l3", "reference three", new OcrdEvaluationBox(100, 180, 780, 200), Collections.emptyList()));
        return new OcrdEvaluationPage("p1", 900, 1200, Collections.singletonList(
                new OcrdEvaluationRegion("r1", "TextRegion", new OcrdEvaluationBox(90, 80, 800, 1000), lines)),
                Collections.emptyList());
    }

    private static PaddleLayoutReadingDebugReport report(PaddleLayoutReadingBlock... blocks) {
        return report(Collections.emptyList(), blocks);
    }

    private static PaddleLayoutReadingDebugReport report(List<PaddleLayoutReconstructionDiagnostic> diagnostics,
                                                        PaddleLayoutReadingBlock... blocks) {
        int assigned = 0;
        for (PaddleLayoutReadingBlock block : blocks) assigned += block.lines.size();
        return new PaddleLayoutReadingDebugReport(Arrays.asList(blocks), 900, 1200, blocks.length, blocks.length,
                assigned * 4, assigned, 0, Collections.emptyList(), null, diagnostics);
    }

    private static PaddleLayoutReconstructionDiagnostic reconstructionDiagnostic(float widthRatio, boolean crossesGutter,
                                                                                float overlapAfterMerge,
                                                                                int fragmentCount, int columnsTouched,
                                                                                float meanGap, float maxGap,
                                                                                float maxDrift) {
        return new PaddleLayoutReconstructionDiagnostic(1, 1, widthRatio, crossesGutter, overlapAfterMerge,
                fragmentCount, new RectF(100, 100, 800, 130), columnsTouched, meanGap, maxGap,
                meanGap, maxGap, maxDrift / 2f, maxDrift);
    }

    private static PaddleLayoutReadingBlock block(int order, int column, String confidence,
                                                  PaddleLayoutReadingLine... lines) {
        RectF box = new RectF();
        boolean first = true;
        for (PaddleLayoutReadingLine line : lines) {
            if (first) {
                box.set(line.box);
                first = false;
            } else {
                box.union(line.box);
            }
        }
        PaddleLayoutReadingBlock block = new PaddleLayoutReadingBlock(order, column, null, Arrays.asList(lines), false,
                PaddleLayoutDiagnosticRole.BODY_CANDIDATE, confidence, box);
        block.box.left = box.left;
        block.box.top = box.top;
        block.box.right = box.right;
        block.box.bottom = box.bottom;
        return block;
    }

    private static PaddleLayoutReadingLine line(int index, float left, float top, float right, float bottom,
                                                int wordCount) {
        List<RecognizedWord> words = new ArrayList<>();
        float width = (right - left) / Math.max(1, wordCount);
        for (int i = 0; i < wordCount; i++) {
            float wordLeft = left + i * width;
            words.add(new RecognizedWord("w" + i, new RectF(wordLeft, top, wordLeft + width * 0.8f, bottom), 90));
        }
        PaddleLayoutReadingLine line = new PaddleLayoutReadingLine(index, words);
        line.box.left = left;
        line.box.top = top;
        line.box.right = right;
        line.box.bottom = bottom;
        return line;
    }
}