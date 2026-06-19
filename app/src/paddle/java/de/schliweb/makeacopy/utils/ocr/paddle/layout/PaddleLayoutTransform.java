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

public final class PaddleLayoutTransform {
    public final int sourceWidth;
    public final int sourceHeight;
    public final int inputWidth;
    public final int inputHeight;
    public final float scale;
    public final int scaledWidth;
    public final int scaledHeight;
    public final float padLeft;
    public final float padTop;
    public final float scaleFactorY;
    public final float scaleFactorX;
    public final PaddleLayoutPreprocessingMode preprocessingMode;

    PaddleLayoutTransform(int sourceWidth, int sourceHeight, int inputWidth, int inputHeight,
                          float scale, int scaledWidth, int scaledHeight, float padLeft, float padTop,
                          float scaleFactorY, float scaleFactorX) {
        this(sourceWidth, sourceHeight, inputWidth, inputHeight, scale, scaledWidth, scaledHeight, padLeft, padTop,
                scaleFactorY, scaleFactorX, PaddleLayoutPreprocessingMode.LEGACY_LETTERBOX);
    }

    PaddleLayoutTransform(int sourceWidth, int sourceHeight, int inputWidth, int inputHeight,
                          float scale, int scaledWidth, int scaledHeight, float padLeft, float padTop,
                          float scaleFactorY, float scaleFactorX, PaddleLayoutPreprocessingMode preprocessingMode) {
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
        this.inputWidth = inputWidth;
        this.inputHeight = inputHeight;
        this.scale = scale;
        this.scaledWidth = scaledWidth;
        this.scaledHeight = scaledHeight;
        this.padLeft = padLeft;
        this.padTop = padTop;
        this.scaleFactorY = scaleFactorY;
        this.scaleFactorX = scaleFactorX;
        this.preprocessingMode = preprocessingMode;
    }

    float sourceX(float modelX) {
        if (preprocessingMode == PaddleLayoutPreprocessingMode.PADDLEX_DIRECT_RESIZE) {
            return directResizeSourceX(modelX);
        }
        return clampSource((modelX - padLeft) / scale, sourceWidth);
    }

    float sourceY(float modelY) {
        if (preprocessingMode == PaddleLayoutPreprocessingMode.PADDLEX_DIRECT_RESIZE) {
            return directResizeSourceY(modelY);
        }
        return clampSource((modelY - padTop) / scale, sourceHeight);
    }

    float sourceXFromScaleFactorOutput(float scaledX) {
        return sourceX(scaledX * scaleFactorX);
    }

    float sourceYFromScaleFactorOutput(float scaledY) {
        return sourceY(scaledY * scaleFactorY);
    }

    float paddleXSourceX(float scaledX) {
        return directResizeSourceX(scaledX);
    }

    float paddleXSourceY(float scaledY) {
        return directResizeSourceY(scaledY);
    }

    float directResizeSourceX(float modelX) {
        return clampSource(modelX / scaleFactorX, sourceWidth);
    }

    float directResizeSourceY(float modelY) {
        return clampSource(modelY / scaleFactorY, sourceHeight);
    }

    float modelX(float sourceX) {
        if (preprocessingMode == PaddleLayoutPreprocessingMode.PADDLEX_DIRECT_RESIZE) {
            return sourceX * scaleFactorX;
        }
        return sourceX * scale + padLeft;
    }

    float modelY(float sourceY) {
        if (preprocessingMode == PaddleLayoutPreprocessingMode.PADDLEX_DIRECT_RESIZE) {
            return sourceY * scaleFactorY;
        }
        return sourceY * scale + padTop;
    }

    private static float clampSource(float v, float max) {
        if (v < 0f) return 0f;
        return Math.min(max, v);
    }
}