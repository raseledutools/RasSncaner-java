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

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PaddleLayoutWordBoxLineReconstructorSafetyTest {
    @Test
    public void gutterCrossingReconstructedLine_getsSafetySplit() {
        List<String> warnings = new ArrayList<>();
        List<PaddleLayoutReconstructionDiagnostic> diagnostics = new ArrayList<>();
        List<PaddleLayoutReadingLine> result = PaddleLayoutWordBoxLineReconstructor.reconstruct(
                Collections.singletonList(line(crossGutterWords())), 700, 60, 120, warnings, diagnostics);

        assertEquals(warnings.toString(), 2, result.size());
        assertTrue(warnings.toString(), warnings.toString().contains("RECONSTRUCTION_SAFETY_GATE_TRIGGERED"));
        assertTrue(warnings.toString().contains("RECONSTRUCTION_SAFETY_SPLIT_APPLIED"));
        assertFalse(warnings.toString().contains("RECONSTRUCTION_SAFETY_SPLIT_SKIPPED_LOW_CONFIDENCE"));
    }

    @Test
    public void lowConfidenceSafetySplit_isSkippedButTraced() {
        List<String> warnings = new ArrayList<>();
        List<PaddleLayoutReconstructionDiagnostic> diagnostics = new ArrayList<>();
        List<PaddleLayoutReadingLine> result = PaddleLayoutWordBoxLineReconstructor.reconstruct(
                Collections.singletonList(line(continuousWideWords(80, 8f, 2f))), 700, 60, 120, warnings,
                diagnostics);

        assertEquals(1, result.size());
        assertTrue(warnings.toString().contains("RECONSTRUCTION_SAFETY_GATE_TRIGGERED"));
        assertTrue(warnings.toString().contains("RECONSTRUCTION_SAFETY_SPLIT_SKIPPED_LOW_CONFIDENCE"));
        assertTrue(warnings.toString().contains("SPLIT_GAP_TOO_SMALL"));
        assertTrue(diagnostics.toString(), diagnostics.stream()
                .anyMatch(d -> d.safetyGateDecisionJson != null
                        && d.safetyGateDecisionJson.contains("SPLIT_GAP_TOO_SMALL")));
    }

    @Test
    public void unbalancedSafetySplit_reportsTinyFragmentAndBalanceReasons() {
        List<String> warnings = new ArrayList<>();
        List<PaddleLayoutReconstructionDiagnostic> diagnostics = new ArrayList<>();
        List<PaddleLayoutReadingLine> result = PaddleLayoutWordBoxLineReconstructor.applyResidualSafetyGate(
                Collections.singletonList(line(unbalancedGutterWords())), 700, warnings, diagnostics);

        assertEquals(1, result.size());
        assertTrue(warnings.toString(), warnings.toString().contains("SPLIT_WOULD_CREATE_TINY_FRAGMENT"));
        assertTrue(warnings.toString(), warnings.toString().contains("SPLIT_FRAGMENT_TOO_UNBALANCED"));
        assertTrue(diagnostics.stream().anyMatch(d -> d.safetyGateDecisionJson != null
                && d.safetyGateDecisionJson.contains("fragmentBalance")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.safetyGateDecisionJson != null
                && d.safetyGateDecisionJson.contains("GOOD_GAP_BUT_TINY_FRAGMENT")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.safetyGateDecisionJson != null
                && d.safetyGateDecisionJson.contains("POSSIBLE_MARGINALIA_SPLIT")));
    }

    @Test
    public void offGutterSafetySplit_reportsGutterAlignmentAndNoReductionReasons() {
        List<String> warnings = new ArrayList<>();
        List<PaddleLayoutReconstructionDiagnostic> diagnostics = new ArrayList<>();
        List<PaddleLayoutReadingLine> result = PaddleLayoutWordBoxLineReconstructor.applyResidualSafetyGate(
                Collections.singletonList(line(offGutterGapWords())), 700, warnings, diagnostics);

        assertEquals(1, result.size());
        assertTrue(warnings.toString(), warnings.toString().contains("SPLIT_NOT_ALIGNED_WITH_GUTTER"));
        assertTrue(warnings.toString(), warnings.toString().contains("SPLIT_NO_OVERLAP_REDUCTION"));
        assertTrue(diagnostics.stream().anyMatch(d -> d.safetyGateDecisionJson != null
                && d.safetyGateDecisionJson.contains("estimatedGutterAlignment")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.safetyGateDecisionJson != null
                && d.safetyGateDecisionJson.contains("GUTTER_ESTIMATE_UNSTABLE")));
    }

    @Test
    public void columnTouchReductionWithoutOverlapReduction_isReportedAsCandidateQuality() {
        List<PaddleLayoutReconstructionDiagnostic> diagnostics = new ArrayList<>();
        PaddleLayoutWordBoxLineReconstructor.applyResidualSafetyGate(
                Collections.singletonList(line(columnTouchReductionWords())), 700, new ArrayList<>(), diagnostics);

        assertTrue(diagnostics.stream().anyMatch(d -> d.safetyGateDecisionJson != null
                && d.safetyGateDecisionJson.contains("\"columnTouchReductionIfSplitApplied\":1")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.safetyGateDecisionJson != null
                && d.safetyGateDecisionJson.contains("GOOD_GAP_BUT_NO_OVERLAP_REDUCTION")));
    }

    @Test
    public void noisyWordGeometryCandidate_reportsAmbiguousGeometryQuality() {
        List<PaddleLayoutReconstructionDiagnostic> diagnostics = new ArrayList<>();
        PaddleLayoutWordBoxLineReconstructor.applyResidualSafetyGate(
                Collections.singletonList(line(noisyDriftWords())), 700, new ArrayList<>(), diagnostics);

        assertTrue(diagnostics.stream().anyMatch(d -> d.safetyGateDecisionJson != null
                && d.safetyGateDecisionJson.contains("WORD_GEOMETRY_TOO_NOISY")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.safetyGateDecisionJson != null
                && d.safetyGateDecisionJson.contains("localBaselineDrift")));
    }

    @Test
    public void longFragmentChain_isGatedAndSplitAtStrongGap() {
        List<String> warnings = new ArrayList<>();
        List<PaddleLayoutReadingLine> result = PaddleLayoutWordBoxLineReconstructor.reconstruct(
                Collections.singletonList(line(longFragmentChainWords())), 650, 60, 120, warnings,
                new ArrayList<>());

        assertEquals(warnings.toString(), 2, result.size());
        assertTrue(warnings.toString(), warnings.toString().contains("fragmentChain"));
        assertTrue(warnings.toString().contains("RECONSTRUCTION_SAFETY_SPLIT_APPLIED"));
    }

    @Test
    public void healthyReconstruction_remainsUnchanged() {
        List<String> warnings = new ArrayList<>();
        List<PaddleLayoutReadingLine> input = Collections.singletonList(line(continuousWideWords(18, 18f, 6f)));
        List<PaddleLayoutReadingLine> result = PaddleLayoutWordBoxLineReconstructor.reconstruct(input, 1000, 1400,
                18, warnings, new ArrayList<>());

        assertEquals(1, result.size());
        assertFalse(warnings.toString().contains("RECONSTRUCTION_SAFETY_GATE_TRIGGERED"));
    }

    private static PaddleLayoutReadingLine line(List<RecognizedWord> words) {
        return new PaddleLayoutReadingLine(1, words);
    }

    private static List<RecognizedWord> crossGutterWords() {
        List<RecognizedWord> words = new ArrayList<>();
        for (int i = 0; i < 40; i++) words.add(word("l" + i, 60 + i * 7, 100, 5));
        for (int i = 0; i < 40; i++) words.add(word("r" + i, 430 + i * 7, 102, 5));
        return words;
    }

    private static List<RecognizedWord> longFragmentChainWords() {
        List<RecognizedWord> words = new ArrayList<>();
        for (int i = 0; i < 20; i++) words.add(word("l" + i, 90 + i * 10, 100, 7));
        for (int i = 0; i < 20; i++) words.add(word("r" + i, 373 + i * 10, 101, 7));
        return words;
    }

    private static List<RecognizedWord> unbalancedGutterWords() {
        List<RecognizedWord> words = new ArrayList<>();
        for (int i = 0; i < 38; i++) words.add(word("l" + i, 70 + i * 7, 100, 5));
        words.add(word("r", 520, 100, 12));
        return words;
    }

    private static List<RecognizedWord> offGutterGapWords() {
        List<RecognizedWord> words = new ArrayList<>();
        for (int i = 0; i < 25; i++) words.add(word("l" + i, 208 + i * 7, 100, 5));
        words.add(word("r", 466, 100, 5));
        return words;
    }

    private static List<RecognizedWord> columnTouchReductionWords() {
        List<RecognizedWord> words = new ArrayList<>();
        for (int i = 0; i < 10; i++) words.add(word("l" + i, 70 + i * 18, 100, 12));
        for (int i = 0; i < 10; i++) words.add(word("r" + i, 420 + i * 18, 100, 12));
        return words;
    }

    private static List<RecognizedWord> noisyDriftWords() {
        List<RecognizedWord> words = new ArrayList<>();
        for (int i = 0; i < 24; i++) words.add(word("n" + i, 70 + i * 12, 90 + (i % 2) * 16, 8));
        for (int i = 0; i < 24; i++) words.add(word("m" + i, 430 + i * 8, 115 + (i % 2) * 18, 5));
        return words;
    }

    private static List<RecognizedWord> continuousWideWords(int count, float width, float gap) {
        List<RecognizedWord> words = new ArrayList<>();
        float left = 80f;
        for (int i = 0; i < count; i++) {
            words.add(word("w" + i, left, 100, width));
            left += width + gap;
        }
        return words;
    }

    private static RecognizedWord word(String text, float left, float top, float width) {
        RectF box = new RectF();
        box.left = left;
        box.top = top;
        box.right = left + width;
        box.bottom = top + 20;
        return new TestWord(text, box);
    }

    private static final class TestWord extends RecognizedWord {
        private final RectF box;

        TestWord(String text, RectF box) {
            super(text, box, 90f);
            this.box = box;
        }

        @Override
        public RectF getBoundingBox() {
            RectF copy = new RectF();
            copy.left = box.left;
            copy.top = box.top;
            copy.right = box.right;
            copy.bottom = box.bottom;
            return copy;
        }
    }
}