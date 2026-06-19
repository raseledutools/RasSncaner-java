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
import java.util.List;

final class PaddleLayoutLineSplitter {
    private PaddleLayoutLineSplitter() {}

    static List<PaddleLayoutReadingLine> split(List<PaddleLayoutReadingLine> lines, int pageWidth,
                                               List<String> warnings) {
        List<PaddleLayoutReadingLine> out = new ArrayList<>();
        int splitCount = 0;
        int forcedCount = 0;
        int repeatedGutterCount = 0;
        int multiSegmentCount = 0;
        for (PaddleLayoutReadingLine line : lines) {
            SplitResult split = splitWords(line, pageWidth);
            List<List<RecognizedWord>> groups = split.groups;
            if (groups.size() > 1) splitCount += groups.size() - 1;
            if (split.forced) forcedCount++;
            if (split.repeatedGutter) repeatedGutterCount++;
            if (groups.size() > 2) multiSegmentCount++;
            for (List<RecognizedWord> group : groups) out.add(new PaddleLayoutReadingLine(out.size() + 1, group));
        }
        if (splitCount > 0) warnings.add("COLUMN_SPLIT_HEURISTIC_USED splitSubLines=" + splitCount);
        if (forcedCount > 0) warnings.add("FORCED_GAP_SPLIT lines=" + forcedCount);
        if (repeatedGutterCount > 0) warnings.add("MULTI_GUTTER_SPLIT lines=" + repeatedGutterCount);
        if (multiSegmentCount > 0) warnings.add("MULTI_SEGMENT_SPLIT lines=" + multiSegmentCount);
        return out;
    }

    private static SplitResult splitWords(PaddleLayoutReadingLine line, int pageWidth) {
        List<List<RecognizedWord>> groups = new ArrayList<>();
        if (line.words.size() < 4) {
            groups.add(line.words);
            return new SplitResult(groups, false, false);
        }
        SplitResult gutterSplit = splitByRepeatedGutterGaps(line, pageWidth);
        if (gutterSplit.groups.size() > 1) return gutterSplit;
        List<RecognizedWord> current = new ArrayList<>();
        current.add(line.words.get(0));
        float medianWidth = medianWordWidth(line.words);
        for (int i = 1; i < line.words.size(); i++) {
            RecognizedWord previous = line.words.get(i - 1);
            RecognizedWord word = line.words.get(i);
            RectF pb = previous.getBoundingBox();
            RectF wb = word.getBoundingBox();
            float gap = wb.left - pb.right;
            float strongGap = Math.max(Math.max(medianWidth * 1.8f, height(line.box) * 1.35f), pageWidth * 0.025f);
            boolean wideLine = width(line.box) > pageWidth * 0.55f;
            boolean enoughWordsAfterSplit = line.words.size() - i >= 2 || (wideLine && line.words.size() - i >= 1);
            if (gap > strongGap && current.size() >= 2 && enoughWordsAfterSplit) {
                groups.add(current);
                current = new ArrayList<>();
            }
            current.add(word);
        }
        groups.add(current);
        boolean forced = false;
        if (groups.size() == 1 && width(line.box) > pageWidth * 0.55f) {
            int splitIndex = widestGapIndex(line.words);
            if (splitIndex > 1 && line.words.size() - splitIndex > 0) {
                float gap = line.words.get(splitIndex).getBoundingBox().left
                        - line.words.get(splitIndex - 1).getBoundingBox().right;
                float forcedGap = Math.max(medianWidth * 1.05f, pageWidth * 0.016f);
                if (gap > forcedGap) {
                    groups.clear();
                    groups.add(new ArrayList<>(line.words.subList(0, splitIndex)));
                    groups.add(new ArrayList<>(line.words.subList(splitIndex, line.words.size())));
                    forced = true;
                }
            }
        }
        return new SplitResult(groups, forced, false);
    }

    private static SplitResult splitByRepeatedGutterGaps(PaddleLayoutReadingLine line, int pageWidth) {
        List<List<RecognizedWord>> groups = new ArrayList<>();
        boolean wideLine = width(line.box) > pageWidth * 0.58f;
        if (!wideLine || line.words.size() < 6) {
            groups.add(line.words);
            return new SplitResult(groups, false, false);
        }
        float medianWidth = medianWordWidth(line.words);
        float medianGap = medianPositiveGap(line.words);
        float adaptiveGap = Math.max(Math.max(medianWidth * 1.25f, medianGap * 2.2f), pageWidth * 0.018f);
        List<RecognizedWord> current = new ArrayList<>();
        current.add(line.words.get(0));
        boolean split = false;
        for (int i = 1; i < line.words.size(); i++) {
            RecognizedWord previous = line.words.get(i - 1);
            RecognizedWord word = line.words.get(i);
            float gap = word.getBoundingBox().left - previous.getBoundingBox().right;
            int remaining = line.words.size() - i;
            boolean balanced = current.size() >= 2 && remaining >= 2;
            boolean trailingFragment = current.size() >= 3 && remaining == 1 && line.box.right > pageWidth * 0.72f;
            if (gap > adaptiveGap && (balanced || trailingFragment)) {
                groups.add(current);
                current = new ArrayList<>();
                split = true;
            }
            current.add(word);
        }
        groups.add(current);
        if (!split || groups.size() == 1) {
            groups.clear();
            groups.add(line.words);
            return new SplitResult(groups, false, false);
        }
        return new SplitResult(groups, true, true);
    }

    private static final class SplitResult {
        final List<List<RecognizedWord>> groups;
        final boolean forced;
        final boolean repeatedGutter;

        SplitResult(List<List<RecognizedWord>> groups, boolean forced, boolean repeatedGutter) {
            this.groups = groups;
            this.forced = forced;
            this.repeatedGutter = repeatedGutter;
        }
    }

    private static int widestGapIndex(List<RecognizedWord> words) {
        int index = -1;
        float widest = 0f;
        for (int i = 1; i < words.size(); i++) {
            float gap = words.get(i).getBoundingBox().left - words.get(i - 1).getBoundingBox().right;
            if (gap > widest) {
                widest = gap;
                index = i;
            }
        }
        return index;
    }

    private static float medianWordWidth(List<RecognizedWord> words) {
        List<Float> widths = new ArrayList<>();
        for (RecognizedWord word : words) widths.add(Math.max(1f, word.getBoundingBox().width()));
        widths.sort(Float::compare);
        return widths.get(widths.size() / 2);
    }

    private static float medianPositiveGap(List<RecognizedWord> words) {
        List<Float> gaps = new ArrayList<>();
        for (int i = 1; i < words.size(); i++) {
            float gap = words.get(i).getBoundingBox().left - words.get(i - 1).getBoundingBox().right;
            if (gap > 0f) gaps.add(gap);
        }
        if (gaps.isEmpty()) return 1f;
        gaps.sort(Float::compare);
        return gaps.get(gaps.size() / 2);
    }

    private static float width(RectF box) {
        return box.right - box.left;
    }

    private static float height(RectF box) {
        return box.bottom - box.top;
    }
}