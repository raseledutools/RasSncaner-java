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

import android.util.Log;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class PaddleLayoutParser {
    List<PaddleLayoutRegion> parse(Object value, long[] shape, PaddleLayoutTransform transform) {
        List<float[]> rows = new ArrayList<>();
        collectRows(value, rows);
        List<Candidate> candidates = new ArrayList<>();
        boolean sourceCoordinateOutput = false;
        for (float[] row : rows) {
            if (row.length < 6) continue;
            Candidate candidate = parseRow(row, transform);
            if (candidate == null || candidate.score < PaddleLayoutModelInfo.DEFAULT_CONFIDENCE_THRESHOLD) continue;
            candidates.add(candidate);
            sourceCoordinateOutput |= candidate.sourceCoordinates;
        }
        List<PaddleLayoutRegion> regions = new ArrayList<>();
        for (Candidate candidate : candidates) {
            regions.add(sourceCoordinateOutput || candidate.sourceCoordinates
                    ? PaddleLayoutRegion.fromScaleFactorOutput(candidate.classId, candidate.score,
                    candidate.x1, candidate.y1, candidate.x2, candidate.y2, transform)
                    : new PaddleLayoutRegion(candidate.classId, candidate.score,
                    candidate.x1, candidate.y1, candidate.x2, candidate.y2, transform));
        }
        regions.sort(Comparator.comparingDouble((PaddleLayoutRegion r) -> r.top).thenComparingDouble(r -> r.left));
        Log.i(PaddleLayoutModelInfo.TAG, "parsed regions=" + regions.size() + " from shape=" + formatShape(shape));
        for (PaddleLayoutRegion r : regions) {
            Log.i(PaddleLayoutModelInfo.TAG, String.format(Locale.US,
                    "region classId=%d label=%s score=%.3f model=[%.1f,%.1f,%.1f,%.1f] source=[%.1f,%.1f,%.1f,%.1f]",
                    r.classId, r.label, r.confidence, r.modelLeft, r.modelTop, r.modelRight, r.modelBottom,
                    r.left, r.top, r.right, r.bottom));
        }
        return regions;
    }

    String sample(Object value) {
        List<Float> values = new ArrayList<>();
        collectScalars(value, values, PaddleLayoutModelInfo.SAMPLE_VALUE_LIMIT);
        return values.toString();
    }

    private static Candidate parseRow(float[] row, PaddleLayoutTransform transform) {
        Candidate a = candidate((int) row[0], row[1], row[2], row[3], row[4], row[5], transform);
        Candidate b = candidate((int) row[5], row[4], row[0], row[1], row[2], row[3], transform);
        if (a == null) return b;
        if (b == null) return a;
        return b.score > a.score ? b : a;
    }

    private static Candidate candidate(int classId, float score, float x1, float y1, float x2, float y2,
                                       PaddleLayoutTransform transform) {
        if (!Float.isFinite(score) || score < 0f || score > 1.0001f) return null;
        if (x2 < x1) { float t = x1; x1 = x2; x2 = t; }
        if (y2 < y1) { float t = y1; y1 = y2; y2 = t; }
        float max = Math.max(Math.max(x1, x2), Math.max(y1, y2));
        if (max <= 1.5f) {
            x1 *= transform.inputWidth; x2 *= transform.inputWidth;
            y1 *= transform.inputHeight; y2 *= transform.inputHeight;
        }
        if ((x2 - x1) < 1f || (y2 - y1) < 1f) return null;
        boolean sourceCoordinates = max > Math.max(transform.inputWidth, transform.inputHeight) + 2f
                && x2 <= transform.sourceWidth * 1.05f
                && y2 <= transform.sourceHeight * 1.05f;
        return new Candidate(classId, score, x1, y1, x2, y2, sourceCoordinates);
    }

    private static void collectRows(Object value, List<float[]> rows) {
        if (value == null) return;
        Class<?> cls = value.getClass();
        if (!cls.isArray()) return;
        Class<?> component = cls.getComponentType();
        if (component == float.class) {
            float[] row = (float[]) value;
            if (row.length >= 6) rows.add(row);
            return;
        }
        int len = Array.getLength(value);
        for (int i = 0; i < len; i++) collectRows(Array.get(value, i), rows);
    }

    private static void collectScalars(Object value, List<Float> out, int limit) {
        if (value == null || out.size() >= limit) return;
        Class<?> cls = value.getClass();
        if (!cls.isArray()) return;
        if (cls.getComponentType() == float.class) {
            float[] arr = (float[]) value;
            for (float v : arr) {
                if (out.size() >= limit) return;
                out.add(v);
            }
            return;
        }
        int len = Array.getLength(value);
        for (int i = 0; i < len && out.size() < limit; i++) collectScalars(Array.get(value, i), out, limit);
    }

    private static String formatShape(long[] shape) {
        if (shape == null) return "null";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < shape.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(shape[i]);
        }
        return sb.append(']').toString();
    }

    private record Candidate(int classId, float score, float x1, float y1, float x2, float y2,
                             boolean sourceCoordinates) {}
}