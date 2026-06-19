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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

final class PaddleLayoutSemanticDiagnostics {
    private final Map<Integer, ClassEntry> classes = new TreeMap<>();
    private final Map<String, Integer> coordinateModes = new LinkedHashMap<>();
    private final Map<String, Integer> groundTruthBestLabels = new TreeMap<>();
    private final List<String> warnings = new ArrayList<>();
    private final PaddleLayoutReliabilityAnalyzer reliabilityAnalyzer = new PaddleLayoutReliabilityAnalyzer();
    private int fixtures;
    private int totalRegions;
    private int clippedRegions;
    private int reversibleRegions;
    private int transformCheckedRegions;
    private float maxTransformError;

    void addFixture(String fixtureName, PaddleLayoutResult result, PaddleLayoutGroundTruth truth) {
        fixtures++;
        reliabilityAnalyzer.addFixture(fixtureName, result);
        for (PaddleLayoutRegion region : result.regions) {
            totalRegions++;
            classes.computeIfAbsent(region.classId, ClassEntry::new).add(region, result.sourceWidth, result.sourceHeight);
            coordinateModes.compute(region.coordinateMode, (mode, count) -> count == null ? 1 : count + 1);
            addCoordinateDiagnostics(fixtureName, region, result);
        }
        addOverlapDiagnostics(result, truth);
        addSuspicionDiagnostics(fixtureName, result);
    }

    String toHumanSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("semanticDiagnostics fixtures=").append(fixtures)
                .append(" totalRegions=").append(totalRegions)
                .append(" coordinateModes=").append(coordinateModes)
                .append(" clippedRegions=").append(clippedRegions)
                .append(String.format(Locale.US, " transformReversible=%d/%d maxTransformError=%.3f\n",
                        reversibleRegions, transformCheckedRegions, maxTransformError));
        sb.append("classes:\n");
        for (ClassEntry entry : classes.values()) {
            sb.append(String.format(Locale.US,
                    "  id=%d label=%s count=%d score[min/avg/max]=%.3f/%.3f/%.3f area[min/avg/max]=%.5f/%.5f/%.5f aspectAvg=%.3f bestGt=%s\n",
                    entry.classId, entry.label, entry.count, entry.minScore, entry.avgScore(), entry.maxScore,
                    entry.minAreaRatio, entry.avgAreaRatio(), entry.maxAreaRatio, entry.avgAspectRatio(),
                    entry.bestGroundTruthRoles));
        }
        sb.append("groundTruthBestLabels=").append(groundTruthBestLabels).append('\n');
        if (!warnings.isEmpty()) {
            sb.append("warnings:\n");
            for (String warning : warnings) sb.append("  - ").append(warning).append('\n');
        }
        sb.append(reliabilityAnalyzer.toHumanSummary());
        return sb.toString();
    }

    String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"modelProvenance\": {\n");
        sb.append("    \"asset\": \"paddleocr/v5/layout.ort\",\n");
        sb.append("    \"verifiedInputs\": [\"image\", \"scale_factor\"],\n");
        sb.append("    \"verifiedInputShape\": [1,3,480,480],\n");
        sb.append("    \"labelHypothesis\": \"PP-DocLayoutV2/V3-style 25-class diagnostic mapping; exported artifact diverges from nominal PP-DocLayout-S 11-class module config\"\n");
        sb.append("  },\n");
        sb.append("  \"fixtures\": ").append(fixtures).append(",\n");
        sb.append("  \"totalRegions\": ").append(totalRegions).append(",\n");
        sb.append("  \"coordinateModes\": ");
        appendMap(sb, coordinateModes).append(",\n");
        sb.append(String.format(Locale.US,
                "  \"coordinateValidation\": {\"clippedRegions\": %d, \"transformCheckedRegions\": %d, \"reversibleRegions\": %d, \"maxTransformError\": %.6f},\n",
                clippedRegions, transformCheckedRegions, reversibleRegions, maxTransformError));
        sb.append("  \"groundTruthBestLabels\": ");
        appendMap(sb, groundTruthBestLabels).append(",\n");
        sb.append("  \"classes\": {\n");
        int i = 0;
        for (ClassEntry entry : classes.values()) {
            if (i++ > 0) sb.append(",\n");
            sb.append("    \"").append(entry.classId).append("\": ");
            entry.appendJson(sb);
        }
        sb.append("\n  },\n");
        sb.append("  \"suspiciousClasses\": [");
        int s = 0;
        for (ClassEntry entry : classes.values()) {
            if (entry.isSuspicious()) {
                if (s++ > 0) sb.append(',');
                sb.append('"').append(escape(entry.label)).append('"');
            }
        }
        sb.append("],\n");
        sb.append("  \"semanticReliability\": ").append(reliabilityAnalyzer.toJson()).append(",\n");
        sb.append("  \"warnings\": [");
        for (int w = 0; w < warnings.size(); w++) {
            if (w > 0) sb.append(',');
            sb.append('"').append(escape(warnings.get(w))).append('"');
        }
        sb.append("]\n}");
        return sb.toString();
    }

    private void addCoordinateDiagnostics(String fixtureName, PaddleLayoutRegion region, PaddleLayoutResult result) {
        boolean clipped = region.left <= 0f || region.top <= 0f
                || region.right >= result.sourceWidth || region.bottom >= result.sourceHeight;
        if (clipped) clippedRegions++;
        if ("model_input_letterbox".equals(region.coordinateMode)) {
            transformCheckedRegions++;
            float roundTripLeft = result.transform.modelX(region.left);
            float roundTripTop = result.transform.modelY(region.top);
            float error = Math.max(Math.abs(roundTripLeft - region.modelLeft), Math.abs(roundTripTop - region.modelTop));
            maxTransformError = Math.max(maxTransformError, error);
            if (error <= 1.25f) reversibleRegions++;
            else warnings.add(fixtureName + ": transform drift id=" + region.classId + " error=" + error);
        }
    }

    private void addOverlapDiagnostics(PaddleLayoutResult result, PaddleLayoutGroundTruth truth) {
        for (PaddleLayoutGroundTruth.Zone zone : truth.zones) {
            RectF zoneBox = truth.sourceBox(zone, result.sourceWidth, result.sourceHeight);
            float best = 0f;
            PaddleLayoutRegion bestRegion = null;
            for (PaddleLayoutRegion region : result.regions) {
                float iou = iou(zoneBox, new RectF(region.left, region.top, region.right, region.bottom));
                if (iou > best) {
                    best = iou;
                    bestRegion = region;
                }
            }
            String key = zone.role + "->" + (bestRegion == null ? "none" : bestRegion.label);
            groundTruthBestLabels.compute(key, (k, count) -> count == null ? 1 : count + 1);
            if (bestRegion != null) {
                classes.computeIfAbsent(bestRegion.classId, ClassEntry::new).addBestGroundTruth(zone.role);
            }
        }
    }

    private void addSuspicionDiagnostics(String fixtureName, PaddleLayoutResult result) {
        int table = 0;
        int text = 0;
        for (PaddleLayoutRegion region : result.regions) {
            if (region.semanticClass == PaddleLayoutClass.TABLE) table++;
            if (region.semanticClass == PaddleLayoutClass.TEXT) text++;
        }
        if (table > text && table >= 3) {
            warnings.add(fixtureName + ": table detections dominate text detections (table=" + table + ", text=" + text + ")");
        }
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

    private static StringBuilder appendMap(StringBuilder sb, Map<String, Integer> map) {
        sb.append('{');
        int i = 0;
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (i++ > 0) sb.append(',');
            sb.append('"').append(escape(entry.getKey())).append("\": ").append(entry.getValue());
        }
        return sb.append('}');
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class ClassEntry {
        final int classId;
        final String label;
        final Map<String, Integer> bestGroundTruthRoles = new TreeMap<>();
        int count;
        float minScore = Float.POSITIVE_INFINITY;
        float maxScore = Float.NEGATIVE_INFINITY;
        double scoreSum;
        double areaRatioSum;
        float minAreaRatio = Float.POSITIVE_INFINITY;
        float maxAreaRatio;
        double aspectRatioSum;

        ClassEntry(int classId) {
            this.classId = classId;
            this.label = PaddleLayoutClass.fromClassId(classId).label();
        }

        void add(PaddleLayoutRegion region, int sourceWidth, int sourceHeight) {
            count++;
            minScore = Math.min(minScore, region.confidence);
            maxScore = Math.max(maxScore, region.confidence);
            scoreSum += region.confidence;
            float width = Math.max(0f, region.right - region.left);
            float height = Math.max(0f, region.bottom - region.top);
            float areaRatio = width * height / Math.max(1f, sourceWidth * (float) sourceHeight);
            areaRatioSum += areaRatio;
            minAreaRatio = Math.min(minAreaRatio, areaRatio);
            maxAreaRatio = Math.max(maxAreaRatio, areaRatio);
            aspectRatioSum += height <= 0f ? 0d : width / height;
        }

        void addBestGroundTruth(String role) {
            bestGroundTruthRoles.compute(role, (key, count) -> count == null ? 1 : count + 1);
        }

        double avgScore() {
            return count == 0 ? 0d : scoreSum / count;
        }

        double avgAreaRatio() {
            return count == 0 ? 0d : areaRatioSum / count;
        }

        double avgAspectRatio() {
            return count == 0 ? 0d : aspectRatioSum / count;
        }

        boolean isSuspicious() {
            return "unknown".equals(label) || (label.equals("table") && count >= 3 && avgAreaRatio() > 0.03d);
        }

        void appendJson(StringBuilder sb) {
            sb.append(String.format(Locale.US,
                    "{\"label\": \"%s\", \"count\": %d, \"confidenceStats\": {\"min\": %.6f, \"avg\": %.6f, \"max\": %.6f}, "
                            + "\"regionSizeStats\": {\"minAreaRatio\": %.8f, \"avgAreaRatio\": %.8f, \"maxAreaRatio\": %.8f, \"avgAspectRatio\": %.6f}, ",
                    escape(label), count, minScore, avgScore(), maxScore,
                    minAreaRatio == Float.POSITIVE_INFINITY ? 0f : minAreaRatio, avgAreaRatio(), maxAreaRatio,
                    avgAspectRatio()));
            sb.append("\"bestGroundTruthRoles\": ");
            appendMap(sb, bestGroundTruthRoles);
            sb.append(", \"reliabilityHint\": \"").append(isSuspicious() ? "inspect" : "observed").append("\"}");
        }
    }
}