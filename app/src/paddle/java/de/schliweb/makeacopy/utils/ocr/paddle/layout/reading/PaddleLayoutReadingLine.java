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

import de.schliweb.makeacopy.utils.ocr.RecognizedWord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class PaddleLayoutReadingLine {
    public final int index;
    public final RectF box;
    public final List<RecognizedWord> words;
    public final String text;
    public final float meanConfidence;

    PaddleLayoutReadingLine(int index, List<RecognizedWord> words) {
        this.index = index;
        List<RecognizedWord> sorted = new ArrayList<>(words == null ? Collections.emptyList() : words);
        sorted.sort((a, b) -> Float.compare(a.getBoundingBox().left, b.getBoundingBox().left));
        this.words = Collections.unmodifiableList(sorted);
        this.box = bounds(sorted);
        this.text = join(sorted);
        this.meanConfidence = confidence(sorted);
    }

    public float centerX() {
        return box.centerX();
    }

    public float centerY() {
        return box.centerY();
    }

    String toJson() {
        return String.format(Locale.US,
                "{\"index\":%d,\"text\":%s,\"confidence\":%.2f,\"box\":[%.2f,%.2f,%.2f,%.2f],\"wordCount\":%d}",
                index, quote(text), meanConfidence, box.left, box.top, box.right, box.bottom, words.size());
    }

    private static RectF bounds(List<RecognizedWord> words) {
        RectF out = new RectF();
        boolean first = true;
        for (RecognizedWord word : words) {
            RectF b = word.getBoundingBox();
            if (first) {
                out.left = b.left;
                out.top = b.top;
                out.right = b.right;
                out.bottom = b.bottom;
                first = false;
            } else {
                out.left = Math.min(out.left, b.left);
                out.top = Math.min(out.top, b.top);
                out.right = Math.max(out.right, b.right);
                out.bottom = Math.max(out.bottom, b.bottom);
            }
        }
        return out;
    }

    private static String join(List<RecognizedWord> words) {
        StringBuilder sb = new StringBuilder();
        for (RecognizedWord word : words) {
            String t = word.getText();
            if (t == null || t.isBlank()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(t.trim());
        }
        return sb.toString();
    }

    private static float confidence(List<RecognizedWord> words) {
        if (words.isEmpty()) return 0f;
        float sum = 0f;
        for (RecognizedWord word : words) sum += word.getConfidence();
        return sum / words.size();
    }

    static String quote(String text) {
        if (text == null) return "null";
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}