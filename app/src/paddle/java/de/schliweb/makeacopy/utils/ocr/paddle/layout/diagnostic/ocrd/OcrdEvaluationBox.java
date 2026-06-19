/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.ocr.paddle.layout.diagnostic.ocrd;

public final class OcrdEvaluationBox {
    public final float left;
    public final float top;
    public final float right;
    public final float bottom;

    public OcrdEvaluationBox(float left, float top, float right, float bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public float width() {
        return Math.max(0f, right - left);
    }

    public float height() {
        return Math.max(0f, bottom - top);
    }

    public float area() {
        return width() * height();
    }

    public float centerX() {
        return (left + right) * 0.5f;
    }

    public float centerY() {
        return (top + bottom) * 0.5f;
    }

    public float intersectionArea(OcrdEvaluationBox other) {
        if (other == null) return 0f;
        float l = Math.max(left, other.left);
        float t = Math.max(top, other.top);
        float r = Math.min(right, other.right);
        float b = Math.min(bottom, other.bottom);
        return Math.max(0f, r - l) * Math.max(0f, b - t);
    }

    public float iou(OcrdEvaluationBox other) {
        float inter = intersectionArea(other);
        float union = area() + (other == null ? 0f : other.area()) - inter;
        return union <= 0f ? 0f : inter / union;
    }

    String toJson() {
        return "[" + left + "," + top + "," + right + "," + bottom + "]";
    }
}