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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PaddleLayoutTransformParityTest {
    private static final float EPSILON = 0.001f;

    @Test
    public void letterboxTransform_documentsAndroidCurrentResizeForTallOcrdPage() {
        PaddleLayoutTransform transform = tallPageTransform();

        assertEquals(480, transform.inputWidth);
        assertEquals(480, transform.inputHeight);
        assertEquals(320, transform.scaledWidth);
        assertEquals(480, transform.scaledHeight);
        assertEquals(80f, transform.padLeft, EPSILON);
        assertEquals(0f, transform.padTop, EPSILON);
        assertEquals(480f / 1500f, transform.scale, EPSILON);
    }

    @Test
    public void scaleFactor_documentsPaddleXDirectResizeSemantics() {
        PaddleLayoutTransform transform = tallPageTransform();

        assertEquals(480f / 1500f, transform.scaleFactorY, EPSILON);
        assertEquals(480f / 1000f, transform.scaleFactorX, EPSILON);
        assertEquals(1000f, transform.paddleXSourceX(480f), EPSILON);
        assertEquals(1500f, transform.paddleXSourceY(480f), EPSILON);
    }

    @Test
    public void directResizeTransform_matchesPaddleXKeepRatioFalseSourceToModel() {
        PaddleLayoutTransform transform = tallPageDirectResizeTransform();

        assertEquals(PaddleLayoutPreprocessingMode.PADDLEX_DIRECT_RESIZE, transform.preprocessingMode);
        assertEquals(480, transform.scaledWidth);
        assertEquals(480, transform.scaledHeight);
        assertEquals(0f, transform.padLeft, EPSILON);
        assertEquals(0f, transform.padTop, EPSILON);
        assertEquals(240f, transform.modelX(500f), EPSILON);
        assertEquals(240f, transform.modelY(750f), EPSILON);
    }

    @Test
    public void directResizeBackprojection_usesIndependentPaddleXScaleFactors() {
        PaddleLayoutTransform transform = tallPageDirectResizeTransform();

        assertEquals(500f, transform.sourceX(240f), EPSILON);
        assertEquals(750f, transform.sourceY(240f), EPSILON);
        assertEquals(166.666f, transform.sourceX(80f), EPSILON);
        assertEquals(250f, transform.sourceY(80f), EPSILON);
        assertEquals(1000f, transform.sourceX(480f), EPSILON);
        assertEquals(1500f, transform.sourceY(480f), EPSILON);
    }

    @Test
    public void coordinateBackprojectionShowsLetterboxAndPaddleXMismatch() {
        PaddleLayoutTransform transform = tallPageTransform();

        assertEquals(500f, transform.sourceX(240f), EPSILON);
        assertEquals(500f, transform.paddleXSourceX(240f), EPSILON);

        assertEquals(0f, transform.sourceX(0f), EPSILON);
        assertEquals(0f, transform.paddleXSourceX(0f), EPSILON);
        assertEquals(1000f, transform.sourceX(480f), EPSILON);
        assertEquals(1000f, transform.paddleXSourceX(480f), EPSILON);

        assertEquals(0f, transform.sourceX(80f), EPSILON);
        assertEquals(166.666f, transform.paddleXSourceX(80f), EPSILON);
        assertEquals(1000f, transform.sourceX(400f), EPSILON);
        assertEquals(833.333f, transform.paddleXSourceX(400f), EPSILON);
    }

    @Test
    public void legacyScaleFactorInverseIsNotPaddleXBackprojection() {
        PaddleLayoutTransform transform = tallPageTransform();

        assertEquals(110f, transform.sourceXFromScaleFactorOutput(240f), EPSILON);
        assertEquals(500f, transform.paddleXSourceX(240f), EPSILON);
    }

    private static PaddleLayoutTransform tallPageTransform() {
        int sourceWidth = 1000;
        int sourceHeight = 1500;
        int inputWidth = 480;
        int inputHeight = 480;
        float scale = Math.min((float) inputWidth / sourceWidth, (float) inputHeight / sourceHeight);
        int scaledWidth = Math.max(1, Math.round(sourceWidth * scale));
        int scaledHeight = Math.max(1, Math.round(sourceHeight * scale));
        float padLeft = (inputWidth - scaledWidth) * 0.5f;
        float padTop = (inputHeight - scaledHeight) * 0.5f;
        return new PaddleLayoutTransform(sourceWidth, sourceHeight, inputWidth, inputHeight, scale,
                scaledWidth, scaledHeight, padLeft, padTop,
                (float) inputHeight / sourceHeight, (float) inputWidth / sourceWidth);
    }

    private static PaddleLayoutTransform tallPageDirectResizeTransform() {
        int sourceWidth = 1000;
        int sourceHeight = 1500;
        int inputWidth = 480;
        int inputHeight = 480;
        return new PaddleLayoutTransform(sourceWidth, sourceHeight, inputWidth, inputHeight, 0f,
                inputWidth, inputHeight, 0f, 0f,
                (float) inputHeight / sourceHeight, (float) inputWidth / sourceWidth,
                PaddleLayoutPreprocessingMode.PADDLEX_DIRECT_RESIZE);
    }
}