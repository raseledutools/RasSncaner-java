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

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class PaddleLayoutDebugReport {
    private PaddleLayoutDebugReport() {}

    static String summarize(String sampleName, PaddleLayoutResult result, PaddleLayoutGroundTruth truth) {
        StringBuilder sb = new StringBuilder();
        sb.append("fixture=").append(sampleName).append('\n');
        sb.append("groundTruth=").append(truth).append('\n');
        sb.append("detections=").append(result.regions.size()).append(" classes=").append(detectedClassCounts(result)).append('\n');
        sb.append(String.format(Locale.US,
                "source=%dx%d input=%dx%d scaleFactor=[%.6f,%.6f] letterboxScale=%.6f pad=[%.2f,%.2f]\n",
                result.sourceWidth, result.sourceHeight, result.inputWidth, result.inputHeight,
                result.transform.scaleFactorY, result.transform.scaleFactorX, result.transform.scale,
                result.transform.padLeft, result.transform.padTop));
        if (truth == null) {
            sb.append("zoneOverlaps=unavailable (no ground truth)\n");
            return sb.toString();
        }
        sb.append("zoneOverlaps:\n");
        for (PaddleLayoutGroundTruth.Zone zone : truth.zones) {
            RectF zoneBox = truth.sourceBox(zone, result.sourceWidth, result.sourceHeight);
            float best = 0f;
            String bestLabel = "none";
            for (PaddleLayoutRegion region : result.regions) {
                float iou = iou(zoneBox, new RectF(region.left, region.top, region.right, region.bottom));
                if (iou > best) {
                    best = iou;
                    bestLabel = region.label;
                }
            }
            sb.append(String.format(Locale.US, "  %s role=%s textual=%s best=%s iou=%.3f srcBox=[%.1f,%.1f,%.1f,%.1f]\n",
                    zone.id, zone.role, zone.textual, bestLabel, best,
                    zoneBox.left, zoneBox.top, zoneBox.right, zoneBox.bottom));
        }
        return sb.toString();
    }

    private static Map<String, Integer> detectedClassCounts(PaddleLayoutResult result) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PaddleLayoutRegion region : result.regions) {
            counts.compute(region.label, (label, previous) -> previous == null ? 1 : previous + 1);
        }
        return counts;
    }

    private static float iou(RectF a, RectF b) {
        float left = Math.max(a.left, b.left);
        float top = Math.max(a.top, b.top);
        float right = Math.min(a.right, b.right);
        float bottom = Math.min(a.bottom, b.bottom);
        float intersection = Math.max(0f, right - left) * Math.max(0f, bottom - top);
        float union = Math.max(0f, a.width()) * Math.max(0f, a.height())
                + Math.max(0f, b.width()) * Math.max(0f, b.height()) - intersection;
        return union <= 0f ? 0f : intersection / union;
    }
}