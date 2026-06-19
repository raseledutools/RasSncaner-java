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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class PaddleLayoutWordBoxLineReconstructor {
    private PaddleLayoutWordBoxLineReconstructor() {
    }

    static List<PaddleLayoutReadingLine> reconstruct(List<PaddleLayoutReadingLine> lines, int sourceWidth,
                                                     int sourceHeight, int ocrWordCount, List<String> warnings) {
        return reconstruct(lines, sourceWidth, sourceHeight, ocrWordCount, warnings, null);
    }

    static List<PaddleLayoutReadingLine> reconstruct(List<PaddleLayoutReadingLine> lines, int sourceWidth,
                                                     int sourceHeight, int ocrWordCount, List<String> warnings,
                                                     List<PaddleLayoutReconstructionDiagnostic> diagnostics) {
        if (lines == null || lines.isEmpty()) return new ArrayList<>();
        if (!shouldActivate(lines, sourceWidth, sourceHeight, ocrWordCount)) return new ArrayList<>(lines);
        List<PaddleLayoutReadingLine> reconstructed = new ArrayList<>();
        int affected = 0;
        int residualWideGroups = 0;
        int originalCount = lines.size();
        for (PaddleLayoutReadingLine line : lines) {
            if (!isPathological(line, sourceWidth, sourceHeight)) {
                reconstructed.add(line);
                continue;
            }
            List<List<RecognizedWord>> groups = regroup(line.words);
            if (groups.size() > Math.max(4, line.words.size() / 2)) {
                reconstructed.add(line);
                if (warnings != null) warnings.add("WORD_BOX_LINE_RECONSTRUCTION_SKIPPED line=" + line.index
                        + " candidateGroups=" + groups.size() + " words=" + line.words.size());
                continue;
            }
            affected++;
            List<List<RecognizedWord>> safeGroups = applySafetyGate(line, groups, sourceWidth, warnings, diagnostics);
            residualWideGroups += countResidualWideGroups(safeGroups, sourceWidth);
            int groupIndex = 0;
            for (List<RecognizedWord> group : safeGroups) {
                reconstructed.add(new PaddleLayoutReadingLine(0, group));
                if (diagnostics != null) diagnostics.add(diagnostic(line, group, safeGroups, groupIndex, sourceWidth));
                groupIndex++;
            }
        }
        sort(reconstructed);
        List<PaddleLayoutReadingLine> reindexed = new ArrayList<>();
        for (int i = 0; i < reconstructed.size(); i++) {
            reindexed.add(new PaddleLayoutReadingLine(i + 1, reconstructed.get(i).words));
        }
        if (warnings != null) {
            float confidence = affected == 0 ? 0f : Math.min(1f,
                    (reindexed.size() - originalCount) / Math.max(1f, ocrWordCount / 24f));
            warnings.add(String.format(Locale.US,
                    "WORD_BOX_LINE_RECONSTRUCTION activated affectedLines=%d originalLines=%d reconstructedLines=%d residualWideGroups=%d confidence=%.2f",
                    affected, originalCount, reindexed.size(), residualWideGroups, confidence));
        }
        return reindexed;
    }

    static List<PaddleLayoutReadingLine> applyResidualSafetyGate(List<PaddleLayoutReadingLine> lines, int sourceWidth,
                                                                 List<String> warnings,
                                                                 List<PaddleLayoutReconstructionDiagnostic> decisions) {
        if (lines == null || lines.isEmpty()) return new ArrayList<>();
        List<PaddleLayoutReadingLine> out = new ArrayList<>();
        boolean changed = false;
        for (PaddleLayoutReadingLine line : lines) {
            List<List<RecognizedWord>> safeGroups = applySafetyGate(line,
                    Collections.singletonList(new ArrayList<>(line.words)), sourceWidth, warnings, decisions);
            if (safeGroups.size() == 1 && safeGroups.get(0).size() == line.words.size()) {
                out.add(line);
            } else {
                changed = true;
                for (List<RecognizedWord> group : safeGroups) out.add(new PaddleLayoutReadingLine(0, group));
            }
        }
        if (!changed) return lines;
        sort(out);
        List<PaddleLayoutReadingLine> reindexed = new ArrayList<>();
        for (int i = 0; i < out.size(); i++) reindexed.add(new PaddleLayoutReadingLine(i + 1, out.get(i).words));
        return reindexed;
    }

    private static List<List<RecognizedWord>> applySafetyGate(PaddleLayoutReadingLine sourceLine,
                                                              List<List<RecognizedWord>> groups,
                                                              int sourceWidth, List<String> warnings,
                                                              List<PaddleLayoutReconstructionDiagnostic> decisions) {
        List<List<RecognizedWord>> out = new ArrayList<>();
        for (List<RecognizedWord> group : groups) {
            SafetyDecision decision = safetyDecision(group, sourceWidth);
            if (!decision.triggered) {
                out.add(group);
                continue;
            }
            if (decisions != null) decisions.add(safetyDiagnostic(sourceLine, group, sourceWidth, decision));
            if (warnings != null) warnings.add(String.format(Locale.US,
                    "RECONSTRUCTION_SAFETY_GATE_TRIGGERED line=%d words=%d widthRatio=%.3f maxGap=%.1f reasons=%s skipReasons=%s",
                    sourceLine.index, group.size(), decision.widthRatio, decision.maxGap, decision.reasons,
                    decision.skipReasons));
            List<List<RecognizedWord>> split = splitAtStrongestGap(group, decision.splitIndex);
            if (split.size() > 1) {
                out.addAll(split);
                if (warnings != null) warnings.add(String.format(Locale.US,
                        "RECONSTRUCTION_SAFETY_SPLIT_APPLIED line=%d fragments=%d splitGap=%.1f",
                        sourceLine.index, split.size(), decision.maxGap));
            } else {
                out.add(group);
                if (warnings != null) warnings.add(String.format(Locale.US,
                        "RECONSTRUCTION_SAFETY_SPLIT_SKIPPED_LOW_CONFIDENCE line=%d words=%d maxGap=%.1f skipReasons=%s",
                        sourceLine.index, group.size(), decision.maxGap, decision.skipReasons));
            }
        }
        return out;
    }

    private static SafetyDecision safetyDecision(List<RecognizedWord> group, int sourceWidth) {
        RectF box = bounds(group);
        float widthRatio = rectWidth(box) / Math.max(1f, sourceWidth);
        boolean crossesGutter = box.left < sourceWidth * 0.46f && box.right > sourceWidth * 0.54f;
        int columnsTouched = (box.left < sourceWidth * 0.48f && box.right > sourceWidth * 0.52f) ? 2 : 1;
        DistanceStats stats = distanceStats(group);
        boolean extremeWidth = widthRatio >= 0.64f;
        boolean longFragmentChain = group.size() >= 18;
        boolean gutterLikeGap = stats.maxHorizontalGap >= Math.max(72f, medianHeight(group) * 5.0f);
        boolean triggered = crossesGutter || columnsTouched > 1 || extremeWidth || longFragmentChain || gutterLikeGap;
        if (!triggered) return new SafetyDecision(false, -1, widthRatio, stats.maxHorizontalGap, "", "",
                safetyDecisionJson(group, sourceWidth, -1, false, ""));
        int splitIndex = strongestSplitIndex(group, sourceWidth);
        StringBuilder reasons = new StringBuilder();
        appendReason(reasons, crossesGutter, "gutterCrossing");
        appendReason(reasons, columnsTouched > 1, "columnsTouched=" + columnsTouched);
        appendReason(reasons, extremeWidth, "extremeWidth");
        appendReason(reasons, longFragmentChain, "fragmentChain");
        appendReason(reasons, gutterLikeGap, "gutterLikeGap");
        String skipReasons = splitIndex < 0 ? splitSkipReasons(group, sourceWidth, stats) : "";
        return new SafetyDecision(true, splitIndex, widthRatio, stats.maxHorizontalGap, reasons.toString(),
                skipReasons, safetyDecisionJson(group, sourceWidth, splitIndex, true, skipReasons));
    }

    private static void appendReason(StringBuilder sb, boolean enabled, String reason) {
        if (!enabled) return;
        if (sb.length() > 0) sb.append('|');
        sb.append(reason);
    }

    private static String splitSkipReasons(List<RecognizedWord> words, int sourceWidth, DistanceStats stats) {
        List<RecognizedWord> sorted = new ArrayList<>(words);
        sorted.sort(Comparator.comparingDouble(w -> w.getBoundingBox().left));
        SplitCandidate best = bestSplitCandidate(sorted, sourceWidth);
        StringBuilder reasons = new StringBuilder();
        if (best.gap <= 0f || best.gap < Math.max(64f, medianHeight(sorted) * 4.0f)) {
            appendReason(reasons, true, "SPLIT_GAP_TOO_SMALL");
        }
        if (best.leftWords < 2 || best.rightWords < 2) {
            appendReason(reasons, true, "SPLIT_WOULD_CREATE_TINY_FRAGMENT");
        }
        if (best.fragmentBalance < 0.25f) {
            appendReason(reasons, true, "SPLIT_FRAGMENT_TOO_UNBALANCED");
        }
        if (!best.gutterAligned) {
            appendReason(reasons, true, "SPLIT_NOT_ALIGNED_WITH_GUTTER");
        }
        if (best.columnTouchReduction <= 0 && best.overlapReduction <= 0f) {
            appendReason(reasons, true, "SPLIT_NO_OVERLAP_REDUCTION");
        }
        if (stats.meanVerticalDrift > Math.max(8f, medianHeight(sorted) * 0.55f)
                || stats.maxHorizontalGap <= 0f) {
            appendReason(reasons, true, "SPLIT_AMBIGUOUS_WORD_CHAIN");
        }
        return reasons.length() == 0 ? "SPLIT_AMBIGUOUS_WORD_CHAIN" : reasons.toString();
    }

    private static String safetyDecisionJson(List<RecognizedWord> words, int sourceWidth, int splitIndex,
                                             boolean triggered,
                                             String skipReasons) {
        List<RecognizedWord> sorted = new ArrayList<>(words);
        sorted.sort(Comparator.comparingDouble(w -> w.getBoundingBox().left));
        SplitCandidate best = bestSplitCandidate(sorted, sourceWidth);
        float medianGap = medianPositiveHorizontalGap(sorted);
        String quality = candidateQualityClassifications(best, sorted, medianGap, skipReasons);
        return String.format(Locale.US,
                "{\"triggered\":%s,\"splitApplied\":%s,\"candidateSplitGapCount\":%d,"
                        + "\"selectedBestGap\":%.2f,\"selectedBestGapIndex\":%d,"
                        + "\"gapToLineWidthRatio\":%.5f,\"gapToMedianWordGapRatio\":%.5f,"
                        + "\"wordsLeftOfSplit\":%d,\"wordsRightOfSplit\":%d,\"fragmentBalance\":%.5f,"
                        + "\"estimatedGutterAlignment\":%s,\"overlapReductionIfSplitApplied\":%.5f,"
                        + "\"columnTouchReductionIfSplitApplied\":%d,\"lowConfidenceSkipReasons\":%s,"
                        + "\"candidateQualityClassifications\":%s,\"rankedCandidateGaps\":%s}",
                triggered, splitIndex > 0, best.candidateCount, best.gap, best.index,
                best.gap / Math.max(1f, rectWidth(bounds(sorted))),
                best.gap / Math.max(1f, medianGap), best.leftWords, best.rightWords, best.fragmentBalance,
                best.gutterAligned, best.overlapReduction, best.columnTouchReduction, reasonsJson(skipReasons),
                reasonsJson(quality), rankedCandidateGapsJson(sorted, sourceWidth));
    }

    private static String candidateQualityClassifications(SplitCandidate best, List<RecognizedWord> sorted,
                                                          float medianGap, String skipReasons) {
        StringBuilder out = new StringBuilder();
        if (best.candidateCount == 0 || best.gap < Math.max(64f, medianHeight(sorted) * 4.0f)) {
            appendReason(out, true, "BAD_CANDIDATE_GENERATION");
        }
        if (best.gutterAligned && (best.leftWords < 2 || best.rightWords < 2 || best.fragmentBalance < 0.25f)) {
            appendReason(out, true, "GOOD_GAP_BUT_TINY_FRAGMENT");
        }
        if (best.gutterAligned && best.gap >= Math.max(64f, medianHeight(sorted) * 4.0f)
                && best.overlapReduction <= 0f) {
            appendReason(out, true, "GOOD_GAP_BUT_NO_OVERLAP_REDUCTION");
        }
        if (best.gap >= Math.max(64f, medianHeight(sorted) * 4.0f) && !best.gutterAligned
                && best.nearGutterDistanceRatio <= 0.24f) {
            appendReason(out, true, "GUTTER_ESTIMATE_UNSTABLE");
        }
        if ((skipReasons != null && skipReasons.contains("SPLIT_AMBIGUOUS_WORD_CHAIN"))
                || best.localBaselineDrift >= Math.max(8f, medianHeight(sorted) * 0.45f)) {
            appendReason(out, true, "WORD_GEOMETRY_TOO_NOISY");
        }
        if (best.gutterAligned && (best.leftWords <= 2 || best.rightWords <= 2)
                && best.gap / Math.max(1f, medianGap) >= 3.0f) {
            appendReason(out, true, "POSSIBLE_MARGINALIA_SPLIT");
        }
        if (best.columnTouchReduction > 0 && best.overlapReduction <= 0f) {
            appendReason(out, true, "GOOD_GAP_BUT_NO_OVERLAP_REDUCTION");
        }
        return out.toString();
    }

    private static String rankedCandidateGapsJson(List<RecognizedWord> sorted, int sourceWidth) {
        List<SplitCandidate> candidates = splitCandidates(sorted, sourceWidth);
        candidates.sort((a, b) -> Float.compare(b.gap, a.gap));
        StringBuilder sb = new StringBuilder("[");
        int limit = Math.min(5, candidates.size());
        for (int i = 0; i < limit; i++) {
            SplitCandidate c = candidates.get(i);
            if (i > 0) sb.append(',');
            sb.append(String.format(Locale.US,
                    "{\"rank\":%d,\"index\":%d,\"gap\":%.2f,\"gapSource\":%s,"
                            + "\"nearEstimatedGutter\":%s,\"estimatedGutterDistanceRatio\":%.5f,"
                            + "\"localWordDensityLeft\":%.5f,\"localWordDensityRight\":%.5f,"
                            + "\"localBaselineDrift\":%.2f,\"localYOverlapContinuity\":%.5f,"
                            + "\"fragmentTextLengthLeft\":%d,\"fragmentTextLengthRight\":%d,"
                            + "\"tinyFragmentGeometrySignal\":%s,\"columnTouchReductionIfSplitApplied\":%d}",
                    i + 1, c.index, c.gap, PaddleLayoutReadingLine.quote(c.gapSource), c.gutterAligned,
                    c.nearGutterDistanceRatio, c.localWordDensityLeft, c.localWordDensityRight,
                    c.localBaselineDrift, c.localYOverlapContinuity, c.fragmentTextLengthLeft,
                    c.fragmentTextLengthRight, c.tinyFragmentGeometrySignal, c.columnTouchReduction));
        }
        sb.append(']');
        return sb.toString();
    }

    private static String reasonsJson(String reasons) {
        if (reasons == null || reasons.isBlank()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        List<String> parts = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= reasons.length(); i++) {
            if (i == reasons.length() || reasons.charAt(i) == '|') {
                parts.add(reasons.substring(start, i));
                start = i + 1;
            }
        }
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(PaddleLayoutReadingLine.quote(parts.get(i)));
        }
        sb.append(']');
        return sb.toString();
    }

    private static SplitCandidate bestSplitCandidate(List<RecognizedWord> sorted, int sourceWidth) {
        SplitCandidate best = new SplitCandidate();
        List<SplitCandidate> candidates = splitCandidates(sorted, sourceWidth);
        for (SplitCandidate candidate : candidates) {
            best.candidateCount++;
            if (candidate.gap >= best.gap) {
                candidate.candidateCount = candidates.size();
                best = candidate;
            }
        }
        return best;
    }

    private static List<SplitCandidate> splitCandidates(List<RecognizedWord> sorted, int sourceWidth) {
        List<SplitCandidate> candidates = new ArrayList<>();
        RectF full = bounds(sorted);
        int fullColumns = (full.left < sourceWidth * 0.48f && full.right > sourceWidth * 0.52f) ? 2 : 1;
        float center = sourceWidth * 0.50f;
        for (int i = 1; i < sorted.size(); i++) {
            RectF previous = sorted.get(i - 1).getBoundingBox();
            RectF next = sorted.get(i).getBoundingBox();
            float gap = next.left - previous.right;
            if (gap <= 0f) continue;
            RectF left = bounds(sorted.subList(0, i));
            RectF right = bounds(sorted.subList(i, sorted.size()));
            int leftColumns = (left.left < sourceWidth * 0.48f && left.right > sourceWidth * 0.52f) ? 2 : 1;
            int rightColumns = (right.left < sourceWidth * 0.48f && right.right > sourceWidth * 0.52f) ? 2 : 1;
            SplitCandidate candidate = new SplitCandidate();
            candidate.index = i;
            candidate.gap = gap;
            candidate.leftWords = i;
            candidate.rightWords = sorted.size() - i;
            candidate.fragmentBalance = Math.min(i, sorted.size() - i) / Math.max(1f, sorted.size());
            candidate.gutterAligned = previous.right < sourceWidth * 0.54f && next.left > sourceWidth * 0.46f;
            candidate.overlapReduction = 0f;
            candidate.columnTouchReduction = Math.max(0, fullColumns - Math.max(leftColumns, rightColumns));
            candidate.nearGutterDistanceRatio = Math.abs(((previous.right + next.left) * 0.5f) - center) / Math.max(1f, sourceWidth);
            candidate.localWordDensityLeft = candidate.leftWords / Math.max(1f, rectWidth(left));
            candidate.localWordDensityRight = candidate.rightWords / Math.max(1f, rectWidth(right));
            candidate.localBaselineDrift = Math.abs(rectCenterY(previous) - rectCenterY(next));
            candidate.localYOverlapContinuity = verticalOverlapRatio(previous, next);
            candidate.fragmentTextLengthLeft = textLength(sorted.subList(0, i));
            candidate.fragmentTextLengthRight = textLength(sorted.subList(i, sorted.size()));
            candidate.tinyFragmentGeometrySignal = candidate.leftWords <= 2 || candidate.rightWords <= 2
                    || Math.min(rectWidth(left), rectWidth(right)) <= sourceWidth * 0.12f;
            candidate.gapSource = candidate.gutterAligned ? "word gap near estimated gutter" : "word gap";
            candidates.add(candidate);
        }
        return candidates;
    }

    private static float medianPositiveHorizontalGap(List<RecognizedWord> sorted) {
        List<Float> gaps = new ArrayList<>();
        for (int i = 1; i < sorted.size(); i++) {
            float gap = sorted.get(i).getBoundingBox().left - sorted.get(i - 1).getBoundingBox().right;
            if (gap > 0f) gaps.add(gap);
        }
        if (gaps.isEmpty()) return 0f;
        gaps.sort(Float::compare);
        return gaps.get(gaps.size() / 2);
    }

    private static final class SplitCandidate {
        int index = -1;
        int candidateCount = 0;
        float gap = 0f;
        int leftWords = 0;
        int rightWords = 0;
        float fragmentBalance = 0f;
        boolean gutterAligned = false;
        float overlapReduction = 0f;
        int columnTouchReduction = 0;
        float nearGutterDistanceRatio = 1f;
        float localWordDensityLeft = 0f;
        float localWordDensityRight = 0f;
        float localBaselineDrift = 0f;
        float localYOverlapContinuity = 0f;
        int fragmentTextLengthLeft = 0;
        int fragmentTextLengthRight = 0;
        boolean tinyFragmentGeometrySignal = false;
        String gapSource = "word gap";
    }

    private static int strongestSplitIndex(List<RecognizedWord> words, int sourceWidth) {
        if (words == null || words.size() < 4) return -1;
        List<RecognizedWord> sorted = new ArrayList<>(words);
        sorted.sort(Comparator.comparingDouble(w -> w.getBoundingBox().left));
        float medianHeight = medianHeight(sorted);
        float bestScore = 0f;
        int best = -1;
        for (int i = 1; i < sorted.size(); i++) {
            RectF left = sorted.get(i - 1).getBoundingBox();
            RectF right = sorted.get(i).getBoundingBox();
            float gap = right.left - left.right;
            if (gap <= 0f) continue;
            RectF leftBounds = bounds(sorted.subList(0, i));
            RectF rightBounds = bounds(sorted.subList(i, sorted.size()));
            boolean localFragments = rectWidth(leftBounds) <= sourceWidth * 0.58f
                    && rectWidth(rightBounds) <= sourceWidth * 0.58f;
            boolean enoughWords = i >= 2 && sorted.size() - i >= 2;
            boolean strongGap = gap >= Math.max(64f, medianHeight * 4.0f);
            boolean gutterCentered = left.right < sourceWidth * 0.54f && right.left > sourceWidth * 0.46f;
            float score = gap + (gutterCentered ? sourceWidth * 0.08f : 0f);
            if (localFragments && enoughWords && strongGap && score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    private static List<List<RecognizedWord>> splitAtStrongestGap(List<RecognizedWord> group, int splitIndex) {
        if (splitIndex <= 0 || splitIndex >= group.size()) {
            List<List<RecognizedWord>> single = new ArrayList<>();
            single.add(group);
            return single;
        }
        List<RecognizedWord> sorted = new ArrayList<>(group);
        sorted.sort(Comparator.comparingDouble(w -> w.getBoundingBox().left));
        List<List<RecognizedWord>> out = new ArrayList<>();
        out.add(new ArrayList<>(sorted.subList(0, splitIndex)));
        out.add(new ArrayList<>(sorted.subList(splitIndex, sorted.size())));
        return out;
    }

    private static final class SafetyDecision {
        final boolean triggered;
        final int splitIndex;
        final float widthRatio;
        final float maxGap;
        final String reasons;
        final String skipReasons;
        final String json;

        SafetyDecision(boolean triggered, int splitIndex, float widthRatio, float maxGap, String reasons,
                       String skipReasons, String json) {
            this.triggered = triggered;
            this.splitIndex = splitIndex;
            this.widthRatio = widthRatio;
            this.maxGap = maxGap;
            this.reasons = reasons;
            this.skipReasons = skipReasons;
            this.json = json;
        }
    }

    private static PaddleLayoutReconstructionDiagnostic safetyDiagnostic(PaddleLayoutReadingLine sourceLine,
                                                                         List<RecognizedWord> group, int sourceWidth,
                                                                         SafetyDecision decision) {
        RectF box = bounds(group);
        DistanceStats stats = distanceStats(group);
        boolean crossesGutter = box.left < sourceWidth * 0.46f && box.right > sourceWidth * 0.54f;
        int columnsTouched = (box.left < sourceWidth * 0.48f && box.right > sourceWidth * 0.52f) ? 2 : 1;
        return new PaddleLayoutReconstructionDiagnostic(sourceLine.index, sourceLine.index,
                rectWidth(box) / Math.max(1f, sourceWidth), crossesGutter, 0f, group.size(), box, columnsTouched,
                stats.meanMergeDistance, stats.maxMergeDistance, stats.meanHorizontalGap, stats.maxHorizontalGap,
                stats.meanVerticalDrift, stats.maxVerticalDrift, decision.json);
    }

    private static PaddleLayoutReconstructionDiagnostic diagnostic(PaddleLayoutReadingLine sourceLine,
                                                                  List<RecognizedWord> group,
                                                                  List<List<RecognizedWord>> groups,
                                                                  int groupIndex, int sourceWidth) {
        RectF box = bounds(group);
        float widthRatio = rectWidth(box) / Math.max(1f, sourceWidth);
        boolean crossesGutter = box.left < sourceWidth * 0.46f && box.right > sourceWidth * 0.54f;
        float overlapAfterMerge = maxVerticalOverlapRatio(box, groups, groupIndex);
        int columnsTouched = (box.left < sourceWidth * 0.48f && box.right > sourceWidth * 0.52f) ? 2 : 1;
        DistanceStats stats = distanceStats(group);
        return new PaddleLayoutReconstructionDiagnostic(sourceLine.index, 0, widthRatio, crossesGutter,
                overlapAfterMerge, group.size(), sourceLine.box, columnsTouched, stats.meanMergeDistance,
                stats.maxMergeDistance, stats.meanHorizontalGap, stats.maxHorizontalGap,
                stats.meanVerticalDrift, stats.maxVerticalDrift);
    }

    private static float maxVerticalOverlapRatio(RectF box, List<List<RecognizedWord>> groups, int groupIndex) {
        float max = 0f;
        for (int i = 0; i < groups.size(); i++) {
            if (i == groupIndex) continue;
            RectF other = bounds(groups.get(i));
            float vertical = Math.max(0f, Math.min(box.bottom, other.bottom) - Math.max(box.top, other.top));
            float denominator = Math.max(1f, Math.min(box.height(), other.height()));
            max = Math.max(max, vertical / denominator);
        }
        return max;
    }

    private static float verticalOverlapRatio(RectF a, RectF b) {
        float overlap = Math.max(0f, Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top));
        return overlap / Math.max(1f, Math.min(rectHeight(a), rectHeight(b)));
    }

    private static int textLength(List<RecognizedWord> words) {
        int length = 0;
        for (RecognizedWord word : words) {
            String text = word == null ? null : word.getText();
            if (text != null) length += text.length();
        }
        return length;
    }

    private static DistanceStats distanceStats(List<RecognizedWord> words) {
        if (words == null || words.size() < 2) return new DistanceStats();
        List<RecognizedWord> sorted = new ArrayList<>(words);
        sorted.sort(Comparator.comparingDouble(w -> w.getBoundingBox().left));
        float mergeSum = 0f;
        float mergeMax = 0f;
        float gapSum = 0f;
        float gapMax = 0f;
        float driftSum = 0f;
        float driftMax = 0f;
        int count = 0;
        RecognizedWord previous = null;
        for (RecognizedWord word : sorted) {
            if (previous != null) {
                RectF a = previous.getBoundingBox();
                RectF b = word.getBoundingBox();
                float horizontalGap = Math.max(0f, b.left - a.right);
                float verticalDrift = Math.abs(b.centerY() - a.centerY());
                float mergeDistance = (float) Math.hypot(horizontalGap, verticalDrift);
                mergeSum += mergeDistance;
                mergeMax = Math.max(mergeMax, mergeDistance);
                gapSum += horizontalGap;
                gapMax = Math.max(gapMax, horizontalGap);
                driftSum += verticalDrift;
                driftMax = Math.max(driftMax, verticalDrift);
                count++;
            }
            previous = word;
        }
        return new DistanceStats(mergeSum / Math.max(1, count), mergeMax, gapSum / Math.max(1, count), gapMax,
                driftSum / Math.max(1, count), driftMax);
    }

    private static final class DistanceStats {
        final float meanMergeDistance;
        final float maxMergeDistance;
        final float meanHorizontalGap;
        final float maxHorizontalGap;
        final float meanVerticalDrift;
        final float maxVerticalDrift;

        DistanceStats() {
            this(0f, 0f, 0f, 0f, 0f, 0f);
        }

        DistanceStats(float meanMergeDistance, float maxMergeDistance, float meanHorizontalGap, float maxHorizontalGap,
                      float meanVerticalDrift, float maxVerticalDrift) {
            this.meanMergeDistance = meanMergeDistance;
            this.maxMergeDistance = maxMergeDistance;
            this.meanHorizontalGap = meanHorizontalGap;
            this.maxHorizontalGap = maxHorizontalGap;
            this.meanVerticalDrift = meanVerticalDrift;
            this.maxVerticalDrift = maxVerticalDrift;
        }
    }

    private static int countResidualWideGroups(List<List<RecognizedWord>> groups, int sourceWidth) {
        int count = 0;
        float maxLocalWidth = Math.max(1f, sourceWidth) * 0.48f;
        for (List<RecognizedWord> group : groups) {
            if (group.size() >= 18 && rectWidth(bounds(group)) > maxLocalWidth) count++;
        }
        return count;
    }

    private static boolean shouldActivate(List<PaddleLayoutReadingLine> lines, int sourceWidth, int sourceHeight,
                                          int ocrWordCount) {
        if (ocrWordCount >= 120 && lines.size() < 8) return true;
        for (PaddleLayoutReadingLine line : lines) {
            if (isPathological(line, sourceWidth, sourceHeight)) return true;
        }
        return false;
    }

    private static boolean isPathological(PaddleLayoutReadingLine line, int sourceWidth, int sourceHeight) {
        int wordCount = line.words.size();
        RectF lineBox = bounds(line.words);
        float widthRatio = rectWidth(lineBox) / Math.max(1f, sourceWidth);
        float heightRatio = rectHeight(lineBox) / Math.max(1f, sourceHeight);
        float pageArea = Math.max(1f, sourceWidth * sourceHeight);
        float areaRatio = Math.max(0f, rectWidth(lineBox)) * Math.max(0f, rectHeight(lineBox)) / pageArea;
        return (wordCount >= 80 && (areaRatio >= 0.18f || (widthRatio >= 0.70f && heightRatio >= 0.18f)))
                || (wordCount >= 32 && widthRatio >= 0.70f && heightRatio >= 0.035f);
    }

    private static List<List<RecognizedWord>> regroup(List<RecognizedWord> words) {
        List<RecognizedWord> sorted = new ArrayList<>(words == null ? Collections.emptyList() : words);
        sorted.sort(Comparator.comparingDouble((RecognizedWord w) -> rectCenterY(w.getBoundingBox()))
                .thenComparingDouble(w -> w.getBoundingBox().left));
        float medianHeight = medianHeight(sorted);
        float tolerance = Math.max(4f, medianHeight * 0.62f);
        List<List<RecognizedWord>> groups = new ArrayList<>();
        List<Float> centers = new ArrayList<>();
        for (RecognizedWord word : sorted) {
            float centerY = rectCenterY(word.getBoundingBox());
            int best = -1;
            float bestDistance = Float.MAX_VALUE;
            for (int i = 0; i < centers.size(); i++) {
                float distance = Math.abs(centers.get(i) - centerY);
                if (distance <= tolerance && distance < bestDistance) {
                    best = i;
                    bestDistance = distance;
                }
            }
            if (best < 0) {
                List<RecognizedWord> group = new ArrayList<>();
                group.add(word);
                groups.add(group);
                centers.add(centerY);
            } else {
                List<RecognizedWord> group = groups.get(best);
                group.add(word);
                centers.set(best, weightedCenter(group));
            }
        }
        List<List<RecognizedWord>> continuityGroups = new ArrayList<>();
        for (List<RecognizedWord> group : groups) {
            group.sort(Comparator.comparingDouble(w -> w.getBoundingBox().left));
            continuityGroups.addAll(splitByHorizontalContinuity(group, medianHeight));
        }
        continuityGroups.sort(Comparator.comparingDouble((List<RecognizedWord> g) -> bounds(g).top)
                .thenComparingDouble(g -> bounds(g).left));
        return continuityGroups;
    }

    private static List<List<RecognizedWord>> splitByHorizontalContinuity(List<RecognizedWord> words,
                                                                          float medianHeight) {
        if (words.size() <= 1) {
            List<List<RecognizedWord>> single = new ArrayList<>();
            single.add(words);
            return single;
        }
        float continuityGap = Math.max(72f, medianHeight * 4.8f);
        List<List<RecognizedWord>> out = new ArrayList<>();
        List<RecognizedWord> current = new ArrayList<>();
        RecognizedWord previous = null;
        for (RecognizedWord word : words) {
            if (previous != null) {
                float gap = word.getBoundingBox().left - previous.getBoundingBox().right;
                boolean gutterLikeGap = gap >= continuityGap;
                if (gutterLikeGap && !current.isEmpty()) {
                    out.add(current);
                    current = new ArrayList<>();
                }
            }
            current.add(word);
            previous = word;
        }
        if (!current.isEmpty()) out.add(current);
        return out;
    }

    private static float medianHeight(List<RecognizedWord> words) {
        if (words.isEmpty()) return 10f;
        List<Float> heights = new ArrayList<>();
        for (RecognizedWord word : words) heights.add(Math.max(1f, rectHeight(word.getBoundingBox())));
        Collections.sort(heights);
        return heights.get(heights.size() / 2);
    }

    private static float weightedCenter(List<RecognizedWord> words) {
        float sum = 0f;
        for (RecognizedWord word : words) sum += rectCenterY(word.getBoundingBox());
        return sum / Math.max(1, words.size());
    }

    private static RectF bounds(List<RecognizedWord> words) {
        RectF out = new RectF();
        boolean first = true;
        for (RecognizedWord word : words) {
            RectF b = word.getBoundingBox();
            if (first) {
                out.left = b.left;
                out.top = b.top;
                out.right = b.right;
                out.bottom = b.bottom;
                first = false;
            } else {
                out.left = Math.min(out.left, b.left);
                out.top = Math.min(out.top, b.top);
                out.right = Math.max(out.right, b.right);
                out.bottom = Math.max(out.bottom, b.bottom);
            }
        }
        return out;
    }


    private static float rectWidth(RectF rect) {
        if (rect == null) return 0f;
        return rect.right - rect.left;
    }

    private static float rectHeight(RectF rect) {
        if (rect == null) return 0f;
        return rect.bottom - rect.top;
    }

    private static float rectCenterY(RectF rect) {
        if (rect == null) return 0f;
        return (rect.top + rect.bottom) * 0.5f;
    }

    private static void sort(List<PaddleLayoutReadingLine> lines) {
        lines.sort(Comparator.comparingDouble((PaddleLayoutReadingLine l) -> l.box.top)
                .thenComparingDouble(l -> l.box.left));
    }
}