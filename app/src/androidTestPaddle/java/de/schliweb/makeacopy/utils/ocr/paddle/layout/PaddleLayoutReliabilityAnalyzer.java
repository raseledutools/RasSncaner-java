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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class PaddleLayoutReliabilityAnalyzer {
    private final Map<Integer, ClassAccumulator> classes = new TreeMap<>();
    private final Map<String, PaddleLayoutRegionQuality> fixtureQuality = new LinkedHashMap<>();
    private final Map<String, FilterExperiment> filterExperiments = new LinkedHashMap<>();
    private final PaddleLayoutClassConfusionMatrix confusionMatrix = new PaddleLayoutClassConfusionMatrix();
    private int totalRegions;

    void addFixture(String fixtureName, PaddleLayoutResult result) {
        PaddleLayoutRegionQuality quality = PaddleLayoutRegionQuality.analyze(result);
        fixtureQuality.put(fixtureName, quality);
        confusionMatrix.add(result);
        for (PaddleLayoutRegion region : result.regions) {
            totalRegions++;
            classes.computeIfAbsent(region.classId, ClassAccumulator::new)
                    .add(fixtureName, region, result.sourceWidth, result.sourceHeight);
        }
        addFilterExperiment("low_confidence_0_35", result, keepLowConfidenceFiltered(result));
        addFilterExperiment("quality_geometry", result, keepGeometryQualityFiltered(result));
        addFilterExperiment("text_table_overlap", result, keepTextTableOverlapFiltered(result));
    }

    List<PaddleLayoutSemanticReliability> reliabilityRankings() {
        List<PaddleLayoutSemanticReliability> rankings = new ArrayList<>();
        for (ClassAccumulator entry : classes.values()) {
            rankings.add(entry.toReliability(confusionMatrix.ambiguityForLabel(entry.label)));
        }
        rankings.sort((a, b) -> Double.compare(b.reliabilityScore, a.reliabilityScore));
        return rankings;
    }

    String toHumanSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("semanticReliability totalRegions=").append(totalRegions).append('\n');
        sb.append("trustedSubset:\n");
        for (PaddleLayoutSemanticReliability reliability : reliabilityRankings()) {
            sb.append("  ").append(reliability.toHumanSummary()).append('\n');
        }
        sb.append("fixtureQuality:\n");
        for (Map.Entry<String, PaddleLayoutRegionQuality> entry : fixtureQuality.entrySet()) {
            sb.append("  ").append(entry.getKey()).append(' ').append(entry.getValue().toHumanSummary()).append('\n');
        }
        sb.append("filterExperiments:\n");
        for (FilterExperiment experiment : filterExperiments.values()) {
            sb.append("  ").append(experiment.toHumanSummary()).append('\n');
        }
        sb.append(confusionMatrix.toHumanSummary());
        return sb.toString();
    }

    String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"totalRegions\": ").append(totalRegions).append(",\n");
        sb.append("  \"trustedSemanticSubset\": {\n");
        appendTrustedSubsetJson(sb);
        sb.append("  },\n");
        sb.append("  \"reliabilityRankings\": {\n");
        int i = 0;
        for (PaddleLayoutSemanticReliability reliability : reliabilityRankings()) {
            if (i++ > 0) sb.append(",\n");
            sb.append("    \"").append(reliability.classId).append("\": ");
            reliability.appendJson(sb);
        }
        sb.append("\n  },\n");
        sb.append("  \"regionQualityByFixture\": {\n");
        i = 0;
        for (Map.Entry<String, PaddleLayoutRegionQuality> entry : fixtureQuality.entrySet()) {
            if (i++ > 0) sb.append(",\n");
            sb.append("    \"").append(escape(entry.getKey())).append("\": ");
            entry.getValue().appendJson(sb);
        }
        sb.append("\n  },\n");
        sb.append("  \"filterExperiments\": {\n");
        i = 0;
        for (FilterExperiment experiment : filterExperiments.values()) {
            if (i++ > 0) sb.append(",\n");
            sb.append("    \"").append(escape(experiment.name)).append("\": ");
            experiment.appendJson(sb);
        }
        sb.append("\n  },\n");
        sb.append("  \"classConfusionMatrix\": ");
        confusionMatrix.appendJson(sb);
        sb.append("\n}");
        return sb.toString();
    }

    private void appendTrustedSubsetJson(StringBuilder sb) {
        Map<String, List<String>> categories = new LinkedHashMap<>();
        categories.put("likely_reliable", new ArrayList<>());
        categories.put("partially_reliable", new ArrayList<>());
        categories.put("unreliable_noisy", new ArrayList<>());
        categories.put("unknown_semantics", new ArrayList<>());
        for (PaddleLayoutSemanticReliability reliability : reliabilityRankings()) {
            List<String> labels = categories.get(reliability.category);
            if (labels != null) labels.add(reliability.label);
        }
        int i = 0;
        for (Map.Entry<String, List<String>> entry : categories.entrySet()) {
            if (i++ > 0) sb.append(",\n");
            sb.append("    \"").append(entry.getKey()).append("\": [");
            for (int j = 0; j < entry.getValue().size(); j++) {
                if (j > 0) sb.append(',');
                sb.append('"').append(escape(entry.getValue().get(j))).append('"');
            }
            sb.append(']');
        }
        sb.append('\n');
    }

    private void addFilterExperiment(String name, PaddleLayoutResult result, int kept) {
        filterExperiments.computeIfAbsent(name, FilterExperiment::new).add(result.regions.size(), kept);
    }

    private static int keepLowConfidenceFiltered(PaddleLayoutResult result) {
        int kept = 0;
        for (PaddleLayoutRegion region : result.regions) if (region.confidence >= 0.35f) kept++;
        return kept;
    }

    private static int keepGeometryQualityFiltered(PaddleLayoutResult result) {
        int kept = 0;
        for (PaddleLayoutRegion region : result.regions) {
            float area = PaddleLayoutRegionQuality.areaRatio(region, result.sourceWidth, result.sourceHeight);
            float aspect = PaddleLayoutRegionQuality.aspectRatio(region);
            if (area >= 0.0005f && area <= 0.60f && aspect >= 0.055f && aspect <= 18f) kept++;
        }
        return kept;
    }

    private static int keepTextTableOverlapFiltered(PaddleLayoutResult result) {
        Set<Integer> suppressed = new HashSet<>();
        for (int i = 0; i < result.regions.size(); i++) {
            PaddleLayoutRegion a = result.regions.get(i);
            for (int j = i + 1; j < result.regions.size(); j++) {
                PaddleLayoutRegion b = result.regions.get(j);
                if (!isTextTablePair(a, b)) continue;
                float overlap = PaddleLayoutRegionQuality.iou(PaddleLayoutRegionQuality.box(a), PaddleLayoutRegionQuality.box(b));
                if (overlap >= 0.35f) suppressed.add(a.confidence < b.confidence ? i : j);
            }
        }
        return result.regions.size() - suppressed.size();
    }

    private static boolean isTextTablePair(PaddleLayoutRegion a, PaddleLayoutRegion b) {
        return (a.semanticClass == PaddleLayoutClass.TEXT && b.semanticClass == PaddleLayoutClass.TABLE)
                || (a.semanticClass == PaddleLayoutClass.TABLE && b.semanticClass == PaddleLayoutClass.TEXT);
    }

    private static double coefficientOfVariation(double sum, double sumSquares, int count) {
        if (count <= 1 || sum == 0d) return 0d;
        double mean = sum / count;
        double variance = Math.max(0d, sumSquares / count - mean * mean);
        return Math.sqrt(variance) / Math.max(0.000001d, mean);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class ClassAccumulator {
        final int classId;
        final String label;
        final Set<String> fixtures = new HashSet<>();
        int count;
        double confidenceSum;
        double areaSum;
        double areaSquareSum;
        double aspectSum;
        double aspectSquareSum;

        ClassAccumulator(int classId) {
            this.classId = classId;
            this.label = PaddleLayoutClass.fromClassId(classId).label();
        }

        void add(String fixtureName, PaddleLayoutRegion region, int sourceWidth, int sourceHeight) {
            fixtures.add(fixtureName);
            count++;
            confidenceSum += region.confidence;
            double area = PaddleLayoutRegionQuality.areaRatio(region, sourceWidth, sourceHeight);
            double aspect = PaddleLayoutRegionQuality.aspectRatio(region);
            areaSum += area;
            areaSquareSum += area * area;
            aspectSum += aspect;
            aspectSquareSum += aspect * aspect;
        }

        PaddleLayoutSemanticReliability toReliability(double ambiguityScore) {
            return new PaddleLayoutSemanticReliability(classId, label, count, fixtures.size(), confidenceSum / count,
                    coefficientOfVariation(areaSum, areaSquareSum, count),
                    coefficientOfVariation(aspectSum, aspectSquareSum, count), ambiguityScore);
        }
    }

    private static final class FilterExperiment {
        final String name;
        int inputRegions;
        int keptRegions;
        int fixtures;

        FilterExperiment(String name) {
            this.name = name;
        }

        void add(int inputRegions, int keptRegions) {
            fixtures++;
            this.inputRegions += inputRegions;
            this.keptRegions += keptRegions;
        }

        String toHumanSummary() {
            return String.format(Locale.US, "%s fixtures=%d before=%d after=%d suppressed=%d",
                    name, fixtures, inputRegions, keptRegions, inputRegions - keptRegions);
        }

        void appendJson(StringBuilder sb) {
            sb.append(String.format(Locale.US,
                    "{\"fixtures\": %d, \"beforeRegions\": %d, \"afterRegions\": %d, \"suppressedRegions\": %d}",
                    fixtures, inputRegions, keptRegions, inputRegions - keptRegions));
        }
    }
}