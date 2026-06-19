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

import java.util.Locale;

final class PaddleLayoutSemanticReliability {
    final int classId;
    final String label;
    final int count;
    final int fixtureFrequency;
    final double avgConfidence;
    final double areaCoefficientOfVariation;
    final double aspectCoefficientOfVariation;
    final double ambiguityScore;
    final double reliabilityScore;
    final String category;

    PaddleLayoutSemanticReliability(int classId, String label, int count, int fixtureFrequency,
                                    double avgConfidence, double areaCoefficientOfVariation,
                                    double aspectCoefficientOfVariation, double ambiguityScore) {
        this.classId = classId;
        this.label = label;
        this.count = count;
        this.fixtureFrequency = fixtureFrequency;
        this.avgConfidence = avgConfidence;
        this.areaCoefficientOfVariation = areaCoefficientOfVariation;
        this.aspectCoefficientOfVariation = aspectCoefficientOfVariation;
        this.ambiguityScore = ambiguityScore;
        double support = Math.min(1d, count / 8d) * 0.18d + Math.min(1d, fixtureFrequency / 3d) * 0.17d;
        double confidence = Math.clamp(avgConfidence, 0d, 1d) * 0.35d;
        double stability = (1d - Math.min(1d, (areaCoefficientOfVariation + aspectCoefficientOfVariation) / 3d)) * 0.20d;
        double ambiguityPenalty = Math.min(0.35d, ambiguityScore * 0.35d);
        this.reliabilityScore = Math.clamp(confidence + support + stability - ambiguityPenalty, 0d, 1d);
        if ("unknown".equals(label) || count == 0) category = "unknown_semantics";
        else if (reliabilityScore >= 0.72d && ambiguityScore < 0.20d) category = "likely_reliable";
        else if (reliabilityScore >= 0.48d && ambiguityScore < 0.45d) category = "partially_reliable";
        else category = "unreliable_noisy";
    }

    String toHumanSummary() {
        return String.format(Locale.US,
                "id=%d label=%s category=%s score=%.3f count=%d fixtures=%d avgConfidence=%.3f areaCv=%.3f aspectCv=%.3f ambiguity=%.3f",
                classId, label, category, reliabilityScore, count, fixtureFrequency, avgConfidence,
                areaCoefficientOfVariation, aspectCoefficientOfVariation, ambiguityScore);
    }

    void appendJson(StringBuilder sb) {
        sb.append(String.format(Locale.US,
                "{\"label\": \"%s\", \"category\": \"%s\", \"score\": %.6f, \"count\": %d, "
                        + "\"fixtureFrequency\": %d, \"avgConfidence\": %.6f, "
                        + "\"areaCoefficientOfVariation\": %.6f, \"aspectCoefficientOfVariation\": %.6f, "
                        + "\"ambiguityScore\": %.6f}",
                escape(label), category, reliabilityScore, count, fixtureFrequency, avgConfidence,
                areaCoefficientOfVariation, aspectCoefficientOfVariation, ambiguityScore));
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}