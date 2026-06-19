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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class PaddleLayoutColumnDetector {
    private PaddleLayoutColumnDetector() {}

    static Map<RectF, Integer> assignColumns(List<RectF> boxes, int sourceWidth) {
        Map<RectF, Integer> result = new HashMap<>();
        if (boxes == null || boxes.isEmpty()) return result;
        List<RectF> sorted = new ArrayList<>(boxes);
        sorted.sort(Comparator.comparingDouble(RectF::centerX));
        float minGap = Math.max(96f, sourceWidth * 0.10f);
        int column = 0;
        float previousRight = sorted.get(0).right;
        result.put(sorted.get(0), column);
        for (int i = 1; i < sorted.size(); i++) {
            RectF box = sorted.get(i);
            if (box.left - previousRight > minGap) column++;
            result.put(box, column);
            previousRight = Math.max(previousRight, box.right);
        }
        return result;
    }
}