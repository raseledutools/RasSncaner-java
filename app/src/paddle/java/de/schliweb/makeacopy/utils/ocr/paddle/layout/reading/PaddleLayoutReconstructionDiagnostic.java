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

import java.util.Locale;

public final class PaddleLayoutReconstructionDiagnostic {
    public final int sourceLineIndex;
    public final int reconstructedLineIndex;
    public final float reconstructedLineWidthRatio;
    public final boolean reconstructedLineCrossesEstimatedGutter;
    public final float reconstructedLineOverlapAfterMerge;
    public final int reconstructedLineFragmentCount;
    public final RectF reconstructedLineSourceSpan;
    public final int reconstructedLineEstimatedColumnsTouched;
    public final float reconstructionMergeDistanceMean;
    public final float reconstructionMergeDistanceMax;
    public final float reconstructionHorizontalGapMean;
    public final float reconstructionHorizontalGapMax;
    public final float reconstructionVerticalDriftMean;
    public final float reconstructionVerticalDriftMax;
    public final String safetyGateDecisionJson;

    PaddleLayoutReconstructionDiagnostic(int sourceLineIndex, int reconstructedLineIndex,
                                         float reconstructedLineWidthRatio,
                                         boolean reconstructedLineCrossesEstimatedGutter,
                                         float reconstructedLineOverlapAfterMerge,
                                         int reconstructedLineFragmentCount, RectF reconstructedLineSourceSpan,
                                         int reconstructedLineEstimatedColumnsTouched,
                                         float reconstructionMergeDistanceMean,
                                         float reconstructionMergeDistanceMax,
                                         float reconstructionHorizontalGapMean,
                                         float reconstructionHorizontalGapMax,
                                         float reconstructionVerticalDriftMean,
                                         float reconstructionVerticalDriftMax) {
        this(sourceLineIndex, reconstructedLineIndex, reconstructedLineWidthRatio,
                reconstructedLineCrossesEstimatedGutter, reconstructedLineOverlapAfterMerge,
                reconstructedLineFragmentCount, reconstructedLineSourceSpan, reconstructedLineEstimatedColumnsTouched,
                reconstructionMergeDistanceMean, reconstructionMergeDistanceMax, reconstructionHorizontalGapMean,
                reconstructionHorizontalGapMax, reconstructionVerticalDriftMean, reconstructionVerticalDriftMax, null);
    }

    PaddleLayoutReconstructionDiagnostic(int sourceLineIndex, int reconstructedLineIndex,
                                         float reconstructedLineWidthRatio,
                                         boolean reconstructedLineCrossesEstimatedGutter,
                                         float reconstructedLineOverlapAfterMerge,
                                         int reconstructedLineFragmentCount, RectF reconstructedLineSourceSpan,
                                         int reconstructedLineEstimatedColumnsTouched,
                                         float reconstructionMergeDistanceMean,
                                         float reconstructionMergeDistanceMax,
                                         float reconstructionHorizontalGapMean,
                                         float reconstructionHorizontalGapMax,
                                         float reconstructionVerticalDriftMean,
                                         float reconstructionVerticalDriftMax,
                                         String safetyGateDecisionJson) {
        this.sourceLineIndex = sourceLineIndex;
        this.reconstructedLineIndex = reconstructedLineIndex;
        this.reconstructedLineWidthRatio = reconstructedLineWidthRatio;
        this.reconstructedLineCrossesEstimatedGutter = reconstructedLineCrossesEstimatedGutter;
        this.reconstructedLineOverlapAfterMerge = reconstructedLineOverlapAfterMerge;
        this.reconstructedLineFragmentCount = reconstructedLineFragmentCount;
        this.reconstructedLineSourceSpan = new RectF(reconstructedLineSourceSpan);
        this.reconstructedLineEstimatedColumnsTouched = reconstructedLineEstimatedColumnsTouched;
        this.reconstructionMergeDistanceMean = reconstructionMergeDistanceMean;
        this.reconstructionMergeDistanceMax = reconstructionMergeDistanceMax;
        this.reconstructionHorizontalGapMean = reconstructionHorizontalGapMean;
        this.reconstructionHorizontalGapMax = reconstructionHorizontalGapMax;
        this.reconstructionVerticalDriftMean = reconstructionVerticalDriftMean;
        this.reconstructionVerticalDriftMax = reconstructionVerticalDriftMax;
        this.safetyGateDecisionJson = safetyGateDecisionJson;
    }

    public String toJson() {
        return String.format(Locale.US,
                "{\"sourceLineIndex\":%d,\"reconstructedLineIndex\":%d,\"reconstructedLineWidthRatio\":%.5f,"
                        + "\"reconstructedLineCrossesEstimatedGutter\":%s,\"reconstructedLineOverlapAfterMerge\":%.5f,"
                        + "\"reconstructedLineFragmentCount\":%d,\"reconstructedLineSourceSpan\":[%.2f,%.2f,%.2f,%.2f],"
                        + "\"reconstructedLineEstimatedColumnsTouched\":%d,\"reconstructionMergeDistanceStats\":{\"mean\":%.2f,\"max\":%.2f},"
                        + "\"reconstructionHorizontalGapStats\":{\"mean\":%.2f,\"max\":%.2f},"
                        + "\"reconstructionVerticalDriftStats\":{\"mean\":%.2f,\"max\":%.2f},"
                        + "\"safetyGateDecision\":%s}",
                sourceLineIndex, reconstructedLineIndex, reconstructedLineWidthRatio,
                reconstructedLineCrossesEstimatedGutter, reconstructedLineOverlapAfterMerge,
                reconstructedLineFragmentCount, reconstructedLineSourceSpan.left, reconstructedLineSourceSpan.top,
                reconstructedLineSourceSpan.right, reconstructedLineSourceSpan.bottom,
                reconstructedLineEstimatedColumnsTouched, reconstructionMergeDistanceMean, reconstructionMergeDistanceMax,
                reconstructionHorizontalGapMean, reconstructionHorizontalGapMax,
                reconstructionVerticalDriftMean, reconstructionVerticalDriftMax,
                safetyGateDecisionJson == null ? "null" : safetyGateDecisionJson);
    }
}