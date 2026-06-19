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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.RectF;

import de.schliweb.makeacopy.utils.ocr.RecognizedWord;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class PaddleLayoutLineSplitterTest {
    @Test
    public void split_usesAdaptiveGapsForWideMergedColumns() {
        List<String> warnings = new ArrayList<>();
        List<PaddleLayoutReadingLine> lines = List.of(new PaddleLayoutReadingLine(1, List.of(
                word("left", 40, 20),
                word("column", 85, 20),
                word("text", 150, 20),
                word("right", 360, 20),
                word("column", 420, 20),
                word("text", 500, 20))));

        List<PaddleLayoutReadingLine> split = PaddleLayoutLineSplitter.split(lines, 600, warnings);

        assertEquals(2, split.size());
        assertEquals("left column text", split.get(0).text);
        assertEquals("right column text", split.get(1).text);
        assertTrue(warnings.toString(), warnings.contains("COLUMN_SPLIT_HEURISTIC_USED splitSubLines=1"));
        assertTrue(warnings.toString(), warnings.contains("FORCED_GAP_SPLIT lines=1"));
    }

    @Test
    public void split_preservesNormalSingleColumnProse() {
        List<String> warnings = new ArrayList<>();
        List<PaddleLayoutReadingLine> lines = List.of(new PaddleLayoutReadingLine(1, List.of(
                word("normal", 40, 20),
                word("single", 110, 20),
                word("column", 175, 20),
                word("prose", 245, 20),
                word("line", 305, 20),
                word("flow", 355, 20))));

        List<PaddleLayoutReadingLine> split = PaddleLayoutLineSplitter.split(lines, 600, warnings);

        assertEquals(1, split.size());
        assertEquals("normal single column prose line flow", split.get(0).text);
        assertTrue(warnings.toString(), warnings.isEmpty());
    }

    private static RecognizedWord word(String text, float left, float top) {
        RectF box = new RectF();
        box.left = left;
        box.top = top;
        box.right = left + Math.max(30f, text.length() * 8f);
        box.bottom = top + 20f;
        return new TestWord(text, box);
    }

    private static final class TestWord extends RecognizedWord {
        private final RectF box;

        TestWord(String text, RectF box) {
            super(text, box, 95f);
            this.box = box;
        }

        @Override
        public RectF getBoundingBox() {
            RectF copy = new RectF();
            copy.left = box.left;
            copy.top = box.top;
            copy.right = box.right;
            copy.bottom = box.bottom;
            return copy;
        }
    }
}