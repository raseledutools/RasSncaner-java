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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import java.nio.FloatBuffer;

final class PaddleLayoutPreprocessor {
    private final PaddleLayoutPreprocessingMode preprocessingMode;

    PaddleLayoutPreprocessor() {
        this(PaddleLayoutPreprocessingMode.PADDLEX_DIRECT_RESIZE);
    }

    PaddleLayoutPreprocessor(PaddleLayoutPreprocessingMode preprocessingMode) {
        this.preprocessingMode = preprocessingMode;
    }
    static final class Input {
        final float[] nchw;
        final float[] scaleFactor;
        final PaddleLayoutTransform transform;

        Input(float[] nchw, float[] scaleFactor, PaddleLayoutTransform transform) {
            this.nchw = nchw;
            this.scaleFactor = scaleFactor;
            this.transform = transform;
        }

        FloatBuffer buffer() {
            return FloatBuffer.wrap(nchw);
        }
    }

    Input preprocess(Bitmap source) {
        if (source == null || source.isRecycled()) {
            throw new IllegalArgumentException("bitmap must be non-null and not recycled");
        }
        int inputW = PaddleLayoutModelInfo.INPUT_WIDTH;
        int inputH = PaddleLayoutModelInfo.INPUT_HEIGHT;
        if (preprocessingMode == PaddleLayoutPreprocessingMode.PADDLEX_DIRECT_RESIZE) {
            return preprocessDirectResize(source, inputW, inputH);
        }
        return preprocessLetterbox(source, inputW, inputH);
    }

    private Input preprocessDirectResize(Bitmap source, int inputW, int inputH) {
        float[] scaleFactor = new float[] {
                (float) inputH / source.getHeight(),
                (float) inputW / source.getWidth()
        };
        return new Input(toNchwDirectResize(source, inputW, inputH), scaleFactor, new PaddleLayoutTransform(source.getWidth(), source.getHeight(), inputW, inputH,
                0f, inputW, inputH, 0f, 0f, scaleFactor[0], scaleFactor[1], preprocessingMode));
    }

    private Input preprocessLetterbox(Bitmap source, int inputW, int inputH) {
        float scale = Math.min((float) inputW / source.getWidth(), (float) inputH / source.getHeight());
        int scaledW = Math.max(1, Math.round(source.getWidth() * scale));
        int scaledH = Math.max(1, Math.round(source.getHeight() * scale));
        float padLeft = (inputW - scaledW) * 0.5f;
        float padTop = (inputH - scaledH) * 0.5f;

        Bitmap input = Bitmap.createBitmap(inputW, inputH, Bitmap.Config.ARGB_8888);
        input.eraseColor(Color.WHITE);
        Canvas canvas = new Canvas(input);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        canvas.drawBitmap(source, null, new RectF(padLeft, padTop, padLeft + scaledW, padTop + scaledH), paint);

        float[] scaleFactor = new float[] {
                (float) inputH / source.getHeight(),
                (float) inputW / source.getWidth()
        };
        return new Input(toNchw(input), scaleFactor, new PaddleLayoutTransform(source.getWidth(), source.getHeight(), inputW, inputH,
                scale, scaledW, scaledH, padLeft, padTop, scaleFactor[0], scaleFactor[1], preprocessingMode));
    }

    private static float[] toNchw(Bitmap input) {
        int inputW = input.getWidth();
        int inputH = input.getHeight();
        float[] out = new float[3 * inputW * inputH];
        int[] pixels = new int[inputW * inputH];
        input.getPixels(pixels, 0, inputW, 0, 0, inputW, inputH);
        input.recycle();

        int plane = inputW * inputH;
        for (int i = 0; i < pixels.length; i++) {
            int c = pixels[i];
            float r = Color.red(c) / 255f;
            float g = Color.green(c) / 255f;
            float b = Color.blue(c) / 255f;
            out[i] = (r - PaddleLayoutModelInfo.MEAN[0]) / PaddleLayoutModelInfo.STD[0];
            out[plane + i] = (g - PaddleLayoutModelInfo.MEAN[1]) / PaddleLayoutModelInfo.STD[1];
            out[2 * plane + i] = (b - PaddleLayoutModelInfo.MEAN[2]) / PaddleLayoutModelInfo.STD[2];
        }
        return out;
    }

    private static float[] toNchwDirectResize(Bitmap source, int inputW, int inputH) {
        int sourceW = source.getWidth();
        int sourceH = source.getHeight();
        int[] pixels = new int[sourceW * sourceH];
        source.getPixels(pixels, 0, sourceW, 0, 0, sourceW, sourceH);

        float[] out = new float[3 * inputW * inputH];
        int plane = inputW * inputH;
        float scaleX = (float) sourceW / inputW;
        float scaleY = (float) sourceH / inputH;
        for (int y = 0; y < inputH; y++) {
            float sourceY = (y + 0.5f) * scaleY - 0.5f;
            int y0 = clamp((int) Math.floor(sourceY), 0, sourceH - 1);
            int y1 = clamp(y0 + 1, 0, sourceH - 1);
            float wy = sourceY - (float) Math.floor(sourceY);
            for (int x = 0; x < inputW; x++) {
                float sourceX = (x + 0.5f) * scaleX - 0.5f;
                int x0 = clamp((int) Math.floor(sourceX), 0, sourceW - 1);
                int x1 = clamp(x0 + 1, 0, sourceW - 1);
                float wx = sourceX - (float) Math.floor(sourceX);
                int c00 = pixels[y0 * sourceW + x0];
                int c01 = pixels[y0 * sourceW + x1];
                int c10 = pixels[y1 * sourceW + x0];
                int c11 = pixels[y1 * sourceW + x1];
                int dst = y * inputW + x;
                writeNormalized(out, dst, plane,
                        bilinear(Color.red(c00), Color.red(c01), Color.red(c10), Color.red(c11), wx, wy),
                        bilinear(Color.green(c00), Color.green(c01), Color.green(c10), Color.green(c11), wx, wy),
                        bilinear(Color.blue(c00), Color.blue(c01), Color.blue(c10), Color.blue(c11), wx, wy));
            }
        }
        return out;
    }

    private static float bilinear(int c00, int c01, int c10, int c11, float wx, float wy) {
        float top = c00 + (c01 - c00) * wx;
        float bottom = c10 + (c11 - c10) * wx;
        return top + (bottom - top) * wy;
    }

    private static void writeNormalized(float[] out, int index, int plane, float r8, float g8, float b8) {
        float r = r8 / 255f;
        float g = g8 / 255f;
        float b = b8 / 255f;
        out[index] = (r - PaddleLayoutModelInfo.MEAN[0]) / PaddleLayoutModelInfo.STD[0];
        out[plane + index] = (g - PaddleLayoutModelInfo.MEAN[1]) / PaddleLayoutModelInfo.STD[1];
        out[2 * plane + index] = (b - PaddleLayoutModelInfo.MEAN[2]) / PaddleLayoutModelInfo.STD[2];
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) return min;
        return Math.min(value, max);
    }
}