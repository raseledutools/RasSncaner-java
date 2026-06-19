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

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

final class PaddleLayoutClassStats {
    private final Map<Integer, Entry> byClassId = new TreeMap<>();
    private final Map<String, Integer> coordinateModes = new LinkedHashMap<>();
    private int totalRegions;

    void add(PaddleLayoutResult result) {
        for (PaddleLayoutRegion region : result.regions) {
            totalRegions++;
            byClassId.computeIfAbsent(region.classId, Entry::new).add(region, result.sourceWidth, result.sourceHeight);
            coordinateModes.compute(region.coordinateMode, (mode, count) -> count == null ? 1 : count + 1);
        }
    }

    String toJson(String fixtureName, PaddleLayoutResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"fixture\": \"").append(escape(fixtureName)).append("\",\n");
        sb.append("  \"totalRegions\": ").append(totalRegions).append(",\n");
        sb.append("  \"inputShape\": [1,3,").append(result.inputHeight).append(',').append(result.inputWidth).append("],\n");
        sb.append(String.format(Locale.US,
                "  \"scaleFactor\": [%.8f,%.8f],\n", result.transform.scaleFactorY, result.transform.scaleFactorX));
        sb.append("  \"coordinateModes\": ");
        appendMap(sb, coordinateModes).append(",\n");
        sb.append("  \"classesObserved\": {\n");
        int i = 0;
        for (Entry entry : byClassId.values()) {
            if (i++ > 0) sb.append(",\n");
            sb.append("    \"").append(entry.classId).append("\": ");
            entry.appendJson(sb);
        }
        sb.append("\n  },\n");
        sb.append("  \"unknownClassIds\": [");
        int unknown = 0;
        for (Entry entry : byClassId.values()) {
            if (entry.label.equals("unknown")) {
                if (unknown++ > 0) sb.append(',');
                sb.append(entry.classId);
            }
        }
        sb.append("]\n");
        sb.append("}\n");
        return sb.toString();
    }

    String toHumanSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("classStats totalRegions=").append(totalRegions).append(" coordinateModes=").append(coordinateModes).append('\n');
        for (Entry entry : byClassId.values()) {
            sb.append(String.format(Locale.US,
                    "  id=%d label=%s count=%d score[min/avg/max]=%.3f/%.3f/%.3f areaAvg=%.5f\n",
                    entry.classId, entry.label, entry.count, entry.minScore, entry.avgScore(), entry.maxScore,
                    entry.avgAreaRatio()));
        }
        return sb.toString();
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

    private static final class Entry {
        final int classId;
        final String label;
        int count;
        float minScore = Float.POSITIVE_INFINITY;
        float maxScore = Float.NEGATIVE_INFINITY;
        double scoreSum;
        double areaRatioSum;

        Entry(int classId) {
            this.classId = classId;
            this.label = PaddleLayoutClass.fromClassId(classId).label();
        }

        void add(PaddleLayoutRegion region, int sourceWidth, int sourceHeight) {
            count++;
            minScore = Math.min(minScore, region.confidence);
            maxScore = Math.max(maxScore, region.confidence);
            scoreSum += region.confidence;
            float area = Math.max(0f, region.right - region.left) * Math.max(0f, region.bottom - region.top);
            areaRatioSum += area / Math.max(1f, sourceWidth * (float) sourceHeight);
        }

        double avgScore() {
            return count == 0 ? 0d : scoreSum / count;
        }

        double avgAreaRatio() {
            return count == 0 ? 0d : areaRatioSum / count;
        }

        void appendJson(StringBuilder sb) {
            sb.append(String.format(Locale.US,
                    "{\"label\": \"%s\", \"count\": %d, \"confidenceStats\": {\"min\": %.6f, \"avg\": %.6f, \"max\": %.6f}, \"geometryStats\": {\"avgAreaRatio\": %.8f}}",
                    escape(label), count, minScore, avgScore(), maxScore, avgAreaRatio()));
        }
    }
}