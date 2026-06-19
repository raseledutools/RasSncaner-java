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

public final class PaddleLayoutModelInfo {
    public static final String TAG = "PaddleLayoutOrt";
    public static final String IMAGE_INPUT_NAME = "image";
    public static final String SCALE_FACTOR_INPUT_NAME = "scale_factor";
    public static final int INPUT_WIDTH = 480;
    public static final int INPUT_HEIGHT = 480;
    public static final boolean RGB_CHANNEL_ORDER = true;
    static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    static final float[] STD = {0.229f, 0.224f, 0.225f};
    public static final float DEFAULT_CONFIDENCE_THRESHOLD = 0.30f;
    public static final int SAMPLE_VALUE_LIMIT = 36;

    private PaddleLayoutModelInfo() {}
}