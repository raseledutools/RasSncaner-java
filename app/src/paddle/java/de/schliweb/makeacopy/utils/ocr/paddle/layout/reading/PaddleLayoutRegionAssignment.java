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

import de.schliweb.makeacopy.utils.ocr.paddle.layout.PaddleLayoutRegion;

final class PaddleLayoutRegionAssignment {
    final PaddleLayoutReadingLine line;
    final PaddleLayoutRegion region;
    final float score;
    final float secondBestScore;
    final boolean centerInside;
    final boolean ambiguous;

    PaddleLayoutRegionAssignment(PaddleLayoutReadingLine line, PaddleLayoutRegion region, float score,
                                 float secondBestScore, boolean centerInside, boolean ambiguous) {
        this.line = line;
        this.region = region;
        this.score = score;
        this.secondBestScore = secondBestScore;
        this.centerInside = centerInside;
        this.ambiguous = ambiguous;
    }

    static PaddleLayoutRegionAssignment best(PaddleLayoutReadingLine line, Iterable<PaddleLayoutRegion> regions) {
        PaddleLayoutRegion bestRegion = null;
        float bestScore = 0f;
        float secondBestScore = 0f;
        boolean bestInside = false;
        for (PaddleLayoutRegion region : regions) {
            RectF rb = new RectF(region.left, region.top, region.right, region.bottom);
            boolean inside = rb.contains(line.centerX(), line.centerY());
            float score = iou(line.box, rb)
                    + horizontalCoverage(line.box, rb) * 0.32f
                    + columnCenterPreference(line.box, rb) * 0.16f
                    + (inside ? 0.50f : 0f)
                    + Math.min(0.20f, region.confidence * 0.20f);
            if (score > bestScore) {
                secondBestScore = bestScore;
                bestScore = score;
                bestRegion = region;
                bestInside = inside;
            } else if (score > secondBestScore) {
                secondBestScore = score;
            }
        }
        if (bestRegion == null || bestScore < 0.05f) {
            return new PaddleLayoutRegionAssignment(line, null, 0f, secondBestScore, false, false);
        }
        boolean ambiguous = secondBestScore > 0.05f && bestScore - secondBestScore < 0.08f;
        return new PaddleLayoutRegionAssignment(line, bestRegion, bestScore, secondBestScore, bestInside, ambiguous);
    }

    private static float horizontalCoverage(RectF line, RectF region) {
        float overlap = Math.max(0f, Math.min(line.right, region.right) - Math.max(line.left, region.left));
        float lineWidth = Math.max(1f, line.right - line.left);
        return overlap / lineWidth;
    }

    private static float columnCenterPreference(RectF line, RectF region) {
        float regionWidth = Math.max(1f, region.right - region.left);
        float lineCenter = (line.left + line.right) * 0.5f;
        float regionCenter = (region.left + region.right) * 0.5f;
        float normalizedDistance = Math.abs(lineCenter - regionCenter) / (regionWidth * 0.5f);
        return Math.max(0f, 1f - normalizedDistance);
    }

    private static float iou(RectF a, RectF b) {
        float left = Math.max(a.left, b.left);
        float top = Math.max(a.top, b.top);
        float right = Math.min(a.right, b.right);
        float bottom = Math.min(a.bottom, b.bottom);
        float inter = Math.max(0f, right - left) * Math.max(0f, bottom - top);
        float areaA = Math.max(0f, a.width()) * Math.max(0f, a.height());
        float areaB = Math.max(0f, b.width()) * Math.max(0f, b.height());
        float union = areaA + areaB - inter;
        return union <= 0f ? 0f : inter / union;
    }
}