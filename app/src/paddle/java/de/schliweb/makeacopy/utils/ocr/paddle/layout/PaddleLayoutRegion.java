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

public final class PaddleLayoutRegion {
    public final int classId;
    public final PaddleLayoutClass semanticClass;
    public final String label;
    public final float confidence;
    public final float modelLeft;
    public final float modelTop;
    public final float modelRight;
    public final float modelBottom;
    public final float left;
    public final float top;
    public final float right;
    public final float bottom;
    public final String coordinateMode;

    PaddleLayoutRegion(int classId, float confidence, float modelLeft, float modelTop,
                       float modelRight, float modelBottom, PaddleLayoutTransform transform) {
        this(classId, confidence, modelLeft, modelTop, modelRight, modelBottom,
                transform.sourceX(modelLeft), transform.sourceY(modelTop),
                transform.sourceX(modelRight), transform.sourceY(modelBottom),
                transform.preprocessingMode == PaddleLayoutPreprocessingMode.PADDLEX_DIRECT_RESIZE
                        ? "model_input_paddlex_direct_resize" : "model_input_letterbox");
    }

    static PaddleLayoutRegion fromSourceCoordinates(int classId, float confidence, float sourceLeft, float sourceTop,
                                                    float sourceRight, float sourceBottom,
                                                    PaddleLayoutTransform transform) {
        return new PaddleLayoutRegion(classId, confidence, sourceLeft, sourceTop, sourceRight, sourceBottom,
                clampToMax(sourceLeft, transform.sourceWidth), clampToMax(sourceTop, transform.sourceHeight),
                clampToMax(sourceRight, transform.sourceWidth), clampToMax(sourceBottom, transform.sourceHeight),
                "source_scaled_by_model_scale_factor");
    }

    static PaddleLayoutRegion fromScaleFactorOutput(int classId, float confidence, float scaledLeft, float scaledTop,
                                                    float scaledRight, float scaledBottom,
                                                    PaddleLayoutTransform transform) {
        return new PaddleLayoutRegion(classId, confidence, scaledLeft, scaledTop, scaledRight, scaledBottom,
                transform.sourceXFromScaleFactorOutput(scaledLeft),
                transform.sourceYFromScaleFactorOutput(scaledTop),
                transform.sourceXFromScaleFactorOutput(scaledRight),
                transform.sourceYFromScaleFactorOutput(scaledBottom),
                "model_scale_factor_output");
    }

    private PaddleLayoutRegion(int classId, float confidence, float modelLeft, float modelTop,
                               float modelRight, float modelBottom, float left, float top,
                               float right, float bottom, String coordinateMode) {
        this.classId = classId;
        this.semanticClass = PaddleLayoutClass.fromClassId(classId);
        this.label = semanticClass.label();
        this.confidence = confidence;
        this.modelLeft = modelLeft;
        this.modelTop = modelTop;
        this.modelRight = modelRight;
        this.modelBottom = modelBottom;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.coordinateMode = coordinateMode;
    }

    private static float clampToMax(float v, float max) {
        if (v < 0f) return 0f;
        return Math.min(v, max);
    }
}