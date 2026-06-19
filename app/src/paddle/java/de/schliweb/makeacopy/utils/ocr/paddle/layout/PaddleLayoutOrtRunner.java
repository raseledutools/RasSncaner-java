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

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import de.schliweb.makeacopy.utils.ocr.paddle.PaddleAssets;

import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PaddleLayoutOrtRunner implements AutoCloseable {
    private final OrtEnvironment env;
    private final OrtSession session;
    private final PaddleLayoutPreprocessor preprocessor = new PaddleLayoutPreprocessor();
    private final PaddleLayoutParser parser = new PaddleLayoutParser();

    public PaddleLayoutOrtRunner(Context context) throws Exception {
        if (context == null) throw new IOException("context is null");
        Context appContext = context.getApplicationContext();
        PaddleAssets.ensureLayoutExtracted(appContext);
        File modelFile = PaddleAssets.getLayoutModelFile(appContext);
        env = OrtEnvironment.getEnvironment();
        session = createSessionWithFallback(env, modelFile.getAbsolutePath());
        logSessionMetadata();
    }

    public PaddleLayoutResult analyze(Bitmap bitmap) throws OrtException {
        PaddleLayoutPreprocessor.Input input = preprocessor.preprocess(bitmap);
        long[] inputShape = {1, 3, input.transform.inputHeight, input.transform.inputWidth};
        long[] scaleFactorShape = {1, 2};
        try (OnnxTensor imageTensor = OnnxTensor.createTensor(env, input.buffer(), inputShape);
             OnnxTensor scaleFactorTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input.scaleFactor), scaleFactorShape)) {
            Map<String, OnnxTensor> inputs = new LinkedHashMap<>();
            inputs.put(PaddleLayoutModelInfo.IMAGE_INPUT_NAME, imageTensor);
            inputs.put(PaddleLayoutModelInfo.SCALE_FACTOR_INPUT_NAME, scaleFactorTensor);
            try (OrtSession.Result result = session.run(inputs)) {
                return parseResult(result, input.transform);
            }
        }
    }

    public PaddleLayoutResult analyze(Bitmap bitmap, File debugDir, String sampleName)
            throws OrtException, IOException {
        PaddleLayoutResult result = analyze(bitmap);
        PaddleLayoutDebugDumper.dump(debugDir, sampleName, bitmap, result);
        return result;
    }

    private PaddleLayoutResult parseResult(OrtSession.Result result, PaddleLayoutTransform transform) throws OrtException {
        List<String> outputNames = new ArrayList<>();
        Map<String, long[]> outputShapes = new LinkedHashMap<>();
        Map<String, String> outputSamples = new LinkedHashMap<>();
        List<PaddleLayoutRegion> regions = new ArrayList<>();
        for (Map.Entry<String, OnnxValue> e : result) {
            outputNames.add(e.getKey());
            long[] shape = shapeOf(e.getValue());
            outputShapes.put(e.getKey(), shape);
            Object value = e.getValue().getValue();
            String sample = parser.sample(value);
            outputSamples.put(e.getKey(), sample);
            Log.i(PaddleLayoutModelInfo.TAG,
                    "output name=" + e.getKey()
                            + " type=" + e.getValue().getType()
                            + " valueClass=" + (value == null ? "null" : value.getClass().getName())
                            + " shape=" + formatShape(shape)
                            + " sample=" + sample);
            regions.addAll(parser.parse(value, shape, transform));
        }
        return new PaddleLayoutResult(transform, PaddleLayoutModelInfo.IMAGE_INPUT_NAME, outputNames, outputShapes,
                outputSamples, regions);
    }

    private void logSessionMetadata() throws OrtException {
        for (Map.Entry<String, NodeInfo> e : session.getInputInfo().entrySet()) {
            Log.i(PaddleLayoutModelInfo.TAG, "input " + e.getKey() + " " + describe(e.getValue()));
        }
        for (Map.Entry<String, NodeInfo> e : session.getOutputInfo().entrySet()) {
            Log.i(PaddleLayoutModelInfo.TAG, "output " + e.getKey() + " " + describe(e.getValue()));
        }
        Log.i(PaddleLayoutModelInfo.TAG,
                "preprocess input=" + PaddleLayoutModelInfo.INPUT_WIDTH + "x" + PaddleLayoutModelInfo.INPUT_HEIGHT
                        + " inputs=image nchw=float32, scale_factor=[heightScale,widthScale] rgb=" + PaddleLayoutModelInfo.RGB_CHANNEL_ORDER
                        + " mean/std ImageNet-like [0,1] normalization");
    }

    private static OrtSession createSessionWithFallback(OrtEnvironment env, String modelPath) throws Exception {
        try (OrtSession.SessionOptions opts = new OrtSession.SessionOptions()) {
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            opts.setIntraOpNumThreads(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
            return env.createSession(modelPath, opts);
        } catch (Exception e) {
            Log.w(PaddleLayoutModelInfo.TAG, "layout session optimized creation failed, retrying default", e);
            try (OrtSession.SessionOptions opts = new OrtSession.SessionOptions()) {
                return env.createSession(modelPath, opts);
            }
        }
    }

    private static String describe(NodeInfo info) {
        Object raw = info.getInfo();
        if (raw instanceof TensorInfo tensorInfo) {
            return "tensor type=" + tensorInfo.type + " shape=" + formatShape(tensorInfo.getShape());
        }
        return String.valueOf(raw);
    }

    private static long[] shapeOf(OnnxValue value) {
        if (value.getInfo() instanceof TensorInfo tensorInfo) {
            return tensorInfo.getShape();
        }
        return new long[0];
    }

    private static String formatShape(long[] shape) {
        if (shape == null) return "null";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < shape.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(shape[i]);
        }
        return sb.append(']').toString();
    }

    @Override
    public void close() throws OrtException {
        session.close();
    }
}