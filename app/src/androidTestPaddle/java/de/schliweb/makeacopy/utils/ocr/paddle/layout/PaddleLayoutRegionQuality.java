/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.ocr.paddle.layout;

import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class PaddleLayoutRegionQuality {
    final int tinyRegions;
    final int giantRegions;
    final int clippedRegions;
    final int edgeTouchingRegions;
    final int lowConfidenceRegions;
    final int extremeAspectRegions;
    final int nearDuplicatePairs;
    final int suspiciousTextTablePairs;

    private PaddleLayoutRegionQuality(int tinyRegions, int giantRegions, int clippedRegions,
                                      int edgeTouchingRegions, int lowConfidenceRegions,
                                      int extremeAspectRegions, int nearDuplicatePairs,
                                      int suspiciousTextTablePairs) {
        this.tinyRegions = tinyRegions;
        this.giantRegions = giantRegions;
        this.clippedRegions = clippedRegions;
        this.edgeTouchingRegions = edgeTouchingRegions;
        this.lowConfidenceRegions = lowConfidenceRegions;
        this.extremeAspectRegions = extremeAspectRegions;
        this.nearDuplicatePairs = nearDuplicatePairs;
        this.suspiciousTextTablePairs = suspiciousTextTablePairs;
    }

    static PaddleLayoutRegionQuality analyze(PaddleLayoutResult result) {
        int tiny = 0;
        int giant = 0;
        int clipped = 0;
        int edge = 0;
        int lowConfidence = 0;
        int extremeAspect = 0;
        int duplicates = 0;
        int textTable = 0;
        List<PaddleLayoutRegion> regions = result.regions;
        for (PaddleLayoutRegion region : regions) {
            float areaRatio = areaRatio(region, result.sourceWidth, result.sourceHeight);
            float aspect = aspectRatio(region);
            boolean touchesEdge = region.left <= 1f || region.top <= 1f
                    || region.right >= result.sourceWidth - 1f || region.bottom >= result.sourceHeight - 1f;
            if (areaRatio < 0.0005f) tiny++;
            if (areaRatio > 0.60f) giant++;
            if (region.left <= 0f || region.top <= 0f || region.right >= result.sourceWidth || region.bottom >= result.sourceHeight) clipped++;
            if (touchesEdge) edge++;
            if (region.confidence < 0.35f) lowConfidence++;
            if (aspect > 18f || aspect < 0.055f) extremeAspect++;
        }
        for (int i = 0; i < regions.size(); i++) {
            PaddleLayoutRegion a = regions.get(i);
            for (int j = i + 1; j < regions.size(); j++) {
                PaddleLayoutRegion b = regions.get(j);
                float overlap = iou(box(a), box(b));
                if (overlap >= 0.82f) duplicates++;
                if (overlap >= 0.35f && isTextTablePair(a, b)) textTable++;
            }
        }
        return new PaddleLayoutRegionQuality(tiny, giant, clipped, edge, lowConfidence,
                extremeAspect, duplicates, textTable);
    }

    String toHumanSummary() {
        List<String> issues = new ArrayList<>();
        if (tinyRegions > 0) issues.add("tiny=" + tinyRegions);
        if (giantRegions > 0) issues.add("giant=" + giantRegions);
        if (clippedRegions > 0) issues.add("clipped=" + clippedRegions);
        if (edgeTouchingRegions > 0) issues.add("edge=" + edgeTouchingRegions);
        if (lowConfidenceRegions > 0) issues.add("lowConfidence=" + lowConfidenceRegions);
        if (extremeAspectRegions > 0) issues.add("extremeAspect=" + extremeAspectRegions);
        if (nearDuplicatePairs > 0) issues.add("nearDuplicatePairs=" + nearDuplicatePairs);
        if (suspiciousTextTablePairs > 0) issues.add("textTableOverlapPairs=" + suspiciousTextTablePairs);
        return issues.isEmpty() ? "quality=clean" : "quality=" + issues;
    }

    void appendJson(StringBuilder sb) {
        sb.append(String.format(Locale.US,
                "{\"tinyRegions\": %d, \"giantRegions\": %d, \"clippedRegions\": %d, "
                        + "\"edgeTouchingRegions\": %d, \"lowConfidenceRegions\": %d, "
                        + "\"extremeAspectRegions\": %d, \"nearDuplicatePairs\": %d, "
                        + "\"suspiciousTextTablePairs\": %d}",
                tinyRegions, giantRegions, clippedRegions, edgeTouchingRegions, lowConfidenceRegions,
                extremeAspectRegions, nearDuplicatePairs, suspiciousTextTablePairs));
    }

    static float areaRatio(PaddleLayoutRegion region, int sourceWidth, int sourceHeight) {
        return Math.max(0f, region.right - region.left) * Math.max(0f, region.bottom - region.top)
                / Math.max(1f, sourceWidth * (float) sourceHeight);
    }

    static float aspectRatio(PaddleLayoutRegion region) {
        float height = Math.max(0f, region.bottom - region.top);
        return height <= 0f ? 0f : Math.max(0f, region.right - region.left) / height;
    }

    static float iou(RectF a, RectF b) {
        float left = Math.max(a.left, b.left);
        float top = Math.max(a.top, b.top);
        float right = Math.min(a.right, b.right);
        float bottom = Math.min(a.bottom, b.bottom);
        float intersection = Math.max(0f, right - left) * Math.max(0f, bottom - top);
        float union = Math.max(0f, a.width()) * Math.max(0f, a.height())
                + Math.max(0f, b.width()) * Math.max(0f, b.height()) - intersection;
        return union <= 0f ? 0f : intersection / union;
    }

    static RectF box(PaddleLayoutRegion region) {
        return new RectF(region.left, region.top, region.right, region.bottom);
    }

    private static boolean isTextTablePair(PaddleLayoutRegion a, PaddleLayoutRegion b) {
        return (a.semanticClass == PaddleLayoutClass.TEXT && b.semanticClass == PaddleLayoutClass.TABLE)
                || (a.semanticClass == PaddleLayoutClass.TABLE && b.semanticClass == PaddleLayoutClass.TEXT);
    }
}