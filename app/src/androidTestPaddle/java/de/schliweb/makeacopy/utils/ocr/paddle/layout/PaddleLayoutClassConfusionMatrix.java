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

import java.util.Map;
import java.util.TreeMap;

final class PaddleLayoutClassConfusionMatrix {
    private final Map<String, PairEntry> pairs = new TreeMap<>();

    void add(PaddleLayoutResult result) {
        for (int i = 0; i < result.regions.size(); i++) {
            PaddleLayoutRegion a = result.regions.get(i);
            for (int j = i + 1; j < result.regions.size(); j++) {
                PaddleLayoutRegion b = result.regions.get(j);
                if (a.classId == b.classId) continue;
                float overlap = PaddleLayoutRegionQuality.iou(PaddleLayoutRegionQuality.box(a), PaddleLayoutRegionQuality.box(b));
                if (overlap < 0.15f) continue;
                String key = key(a.label, b.label);
                pairs.computeIfAbsent(key, ignored -> new PairEntry(key)).add(overlap, a.confidence, b.confidence);
            }
        }
    }

    double ambiguityForLabel(String label) {
        int pairCount = 0;
        double overlapSum = 0d;
        for (PairEntry entry : pairs.values()) {
            if (entry.contains(label)) {
                pairCount += entry.count;
                overlapSum += entry.overlapSum;
            }
        }
        if (pairCount == 0) return 0d;
        return Math.min(1d, (pairCount / 12d) * 0.5d + (overlapSum / pairCount) * 0.5d);
    }

    String toHumanSummary() {
        StringBuilder sb = new StringBuilder("classConfusionPairs:\n");
        for (PairEntry entry : pairs.values()) {
            sb.append("  ").append(entry.toHumanSummary()).append('\n');
        }
        return sb.toString();
    }

    void appendJson(StringBuilder sb) {
        sb.append('{');
        int i = 0;
        for (PairEntry entry : pairs.values()) {
            if (i++ > 0) sb.append(',');
            sb.append('\n').append("    \"").append(escape(entry.key)).append("\": ");
            entry.appendJson(sb);
        }
        if (!pairs.isEmpty()) sb.append('\n').append("  ");
        sb.append('}');
    }

    private static String key(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class PairEntry {
        final String key;
        int count;
        double overlapSum;
        double confidenceSum;
        float maxOverlap;

        PairEntry(String key) {
            this.key = key;
        }

        void add(float overlap, float confidenceA, float confidenceB) {
            count++;
            overlapSum += overlap;
            confidenceSum += (confidenceA + confidenceB) / 2d;
            maxOverlap = Math.max(maxOverlap, overlap);
        }

        boolean contains(String label) {
            return key.startsWith(label + "|") || key.endsWith("|" + label);
        }

        String toHumanSummary() {
            return String.format(java.util.Locale.US, "%s count=%d overlapAvg=%.3f overlapMax=%.3f confidenceAvg=%.3f",
                    key, count, overlapSum / count, maxOverlap, confidenceSum / count);
        }

        void appendJson(StringBuilder sb) {
            sb.append(String.format(java.util.Locale.US,
                    "{\"count\": %d, \"avgOverlap\": %.6f, \"maxOverlap\": %.6f, \"avgConfidence\": %.6f}",
                    count, overlapSum / count, maxOverlap, confidenceSum / count));
        }
    }
}