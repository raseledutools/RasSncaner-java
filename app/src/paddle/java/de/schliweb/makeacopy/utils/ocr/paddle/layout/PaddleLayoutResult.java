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

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class PaddleLayoutResult {
    public final int sourceWidth;
    public final int sourceHeight;
    public final int inputWidth;
    public final int inputHeight;
    public final String inputName;
    public final PaddleLayoutTransform transform;
    public final List<String> outputNames;
    public final Map<String, long[]> outputShapes;
    public final Map<String, String> outputSamples;
    public final List<PaddleLayoutRegion> regions;

    PaddleLayoutResult(PaddleLayoutTransform transform, String inputName, List<String> outputNames,
                       Map<String, long[]> outputShapes, Map<String, String> outputSamples,
                       List<PaddleLayoutRegion> regions) {
        this.sourceWidth = transform.sourceWidth;
        this.sourceHeight = transform.sourceHeight;
        this.inputWidth = transform.inputWidth;
        this.inputHeight = transform.inputHeight;
        this.inputName = inputName;
        this.transform = transform;
        this.outputNames = Collections.unmodifiableList(outputNames);
        this.outputShapes = Collections.unmodifiableMap(outputShapes);
        this.outputSamples = Collections.unmodifiableMap(outputSamples);
        this.regions = Collections.unmodifiableList(regions);
    }
}