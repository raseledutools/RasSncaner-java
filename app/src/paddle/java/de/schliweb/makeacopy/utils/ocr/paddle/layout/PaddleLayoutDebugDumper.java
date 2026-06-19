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
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

public final class PaddleLayoutDebugDumper {
    private PaddleLayoutDebugDumper() {}

    public static void dump(File dir, String sampleName, Bitmap source, PaddleLayoutResult result) throws IOException {
        if (dir == null) return;
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(PaddleLayoutModelInfo.TAG, "Could not create layout debug dir: " + dir);
            return;
        }
        String safe = sampleName == null ? "sample" : sampleName.replaceAll("[^A-Za-z0-9._-]", "_");
        File json = new File(dir, safe + "-paddle-layout-debug.json");
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(json), StandardCharsets.UTF_8)) {
            w.write(toJson(result));
        }
        Bitmap overlay = new PaddleLayoutOverlayRenderer().render(source, result,
                PaddleLayoutOverlayRenderer.Options.verboseCoordinates());
        File png = new File(dir, safe + "-paddle-layout-overlay.png");
        try (FileOutputStream out = new FileOutputStream(png)) {
            overlay.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally {
            overlay.recycle();
        }
        writeOverlay(dir, safe + "-paddle-layout-overlay-class-id.png", source, result,
                PaddleLayoutOverlayRenderer.Options.classIds());
        writeOverlay(dir, safe + "-paddle-layout-overlay-confidence.png", source, result,
                PaddleLayoutOverlayRenderer.Options.confidenceHeat());
        writeOverlay(dir, safe + "-paddle-layout-overlay-unknown.png", source, result,
                PaddleLayoutOverlayRenderer.Options.unknownHighlight());
        writeOverlay(dir, safe + "-paddle-layout-overlay-geometry.png", source, result,
                PaddleLayoutOverlayRenderer.Options.geometryOnly());
        writeOverlay(dir, safe + "-paddle-layout-overlay-density.png", source, result,
                PaddleLayoutOverlayRenderer.Options.regionDensity());
        Log.i(PaddleLayoutModelInfo.TAG, "wrote debug files: " + json + " and " + png);
    }

    private static void writeOverlay(File dir, String name, Bitmap source, PaddleLayoutResult result,
                                     PaddleLayoutOverlayRenderer.Options options) throws IOException {
        Bitmap overlay = new PaddleLayoutOverlayRenderer().render(source, result, options);
        try (FileOutputStream out = new FileOutputStream(new File(dir, name))) {
            overlay.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally {
            overlay.recycle();
        }
    }

    private static String toJson(PaddleLayoutResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"sourceWidth\": ").append(result.sourceWidth).append(",\n");
        sb.append("  \"sourceHeight\": ").append(result.sourceHeight).append(",\n");
        sb.append("  \"inputWidth\": ").append(result.inputWidth).append(",\n");
        sb.append("  \"inputHeight\": ").append(result.inputHeight).append(",\n");
        sb.append("  \"inputShape\": [1, 3, ").append(result.inputHeight).append(", ").append(result.inputWidth).append("],\n");
        sb.append(String.format(Locale.US,
                "  \"transform\": {\"scale\": %.8f, \"scaledWidth\": %d, \"scaledHeight\": %d, \"padLeft\": %.4f, \"padTop\": %.4f, \"directResizeScaleX\": %.8f, \"directResizeScaleY\": %.8f},\n",
                result.transform.scale, result.transform.scaledWidth, result.transform.scaledHeight,
                result.transform.padLeft, result.transform.padTop,
                result.transform.scaleFactorX, result.transform.scaleFactorY));
        sb.append(String.format(Locale.US,
                "  \"overlay\": {\"bitmapWidth\": %d, \"bitmapHeight\": %d, \"coordinateSpace\": \"source\"},\n",
                result.sourceWidth, result.sourceHeight));
        sb.append(String.format(Locale.US,
                "  \"scaleFactor\": [%.8f, %.8f],\n",
                result.transform.scaleFactorY, result.transform.scaleFactorX));
        boolean directResize = result.transform.preprocessingMode == PaddleLayoutPreprocessingMode.PADDLEX_DIRECT_RESIZE;
        sb.append("  \"preprocessing\": {\"mode\": \"").append(result.transform.preprocessingMode.name())
                .append("\", \"resizePolicy\": \"").append(directResize ? "PADDLEX_DIRECT_RESIZE" : "LETTERBOX_KEEP_RATIO_WITH_WHITE_PADDING")
                .append("\", \"keepRatio\": ").append(!directResize)
                .append(", \"coordinateMode\": \"").append(directResize ? "direct_resize_scale_factor" : "legacy_letterbox_inverse")
                .append("\", \"preprocessingParityStatus\": \"").append(directResize ? "matches PaddleX Resize keep_ratio=false geometry" : "known mismatch with PaddleX direct-resize geometry")
                .append("\", \"referenceMismatch\": \"").append(directResize ? "none for resize geometry; normalization/channel/postprocess still diagnostic" : "PaddleX PP-DocLayout-S inference.yml uses Resize keep_ratio=false, target_size=[480,480], no padding")
                .append("\", \"directResizeScaleX\": ").append(String.format(Locale.US, "%.8f", result.transform.scaleFactorX))
                .append(", \"directResizeScaleY\": ").append(String.format(Locale.US, "%.8f", result.transform.scaleFactorY))
                .append(", \"resizedWidth\": ")
                .append(result.transform.scaledWidth).append(", \"resizedHeight\": ").append(result.transform.scaledHeight)
                .append(", \"paddedWidth\": ").append(result.inputWidth).append(", \"paddedHeight\": ").append(result.inputHeight)
                .append(", \"padLeft\": ").append(String.format(Locale.US, "%.4f", result.transform.padLeft))
                .append(", \"padTop\": ").append(String.format(Locale.US, "%.4f", result.transform.padTop))
                .append(", \"layout\": \"NCHW\", \"colorOrder\": \"RGB\", \"normalization\": \"(pixel/255 - mean) / std\", \"mean\": [0.485, 0.456, 0.406], \"std\": [0.229, 0.224, 0.225]},\n");
        sb.append("  \"postprocessing\": {\"androidConfidenceThreshold\": ").append(PaddleLayoutModelInfo.DEFAULT_CONFIDENCE_THRESHOLD)
                .append(", \"paddlexDrawThreshold\": 0.5, \"paddlexNmsScoreThreshold\": 0.3, \"paddlexNmsThreshold\": 0.5, \"parserRowInterpretation\": \"tries [class,score,x1,y1,x2,y2] and [x1,y1,x2,y2,score,class]\", \"nmsInAndroidParser\": false},\n");
        sb.append("  \"outputs\": {\n");
        int oi = 0;
        for (Map.Entry<String, long[]> e : result.outputShapes.entrySet()) {
            sb.append("    \"").append(e.getKey()).append("\": {\"shape\": ").append(shapeJson(e.getValue()))
                    .append(", \"sample\": ").append(quote(result.outputSamples.get(e.getKey()))).append("}");
            sb.append(++oi < result.outputShapes.size() ? ",\n" : "\n");
        }
        sb.append("  },\n  \"regions\": [\n");
        for (int i = 0; i < result.regions.size(); i++) {
            PaddleLayoutRegion r = result.regions.get(i);
            float inverseLeft = result.transform.sourceX(r.modelLeft);
            float inverseTop = result.transform.sourceY(r.modelTop);
            float inverseRight = result.transform.sourceX(r.modelRight);
            float inverseBottom = result.transform.sourceY(r.modelBottom);
            float scaleFactorLeft = result.transform.sourceXFromScaleFactorOutput(r.modelLeft);
            float scaleFactorTop = result.transform.sourceYFromScaleFactorOutput(r.modelTop);
            float scaleFactorRight = result.transform.sourceXFromScaleFactorOutput(r.modelRight);
            float scaleFactorBottom = result.transform.sourceYFromScaleFactorOutput(r.modelBottom);
            float paddleXLeft = result.transform.paddleXSourceX(r.modelLeft);
            float paddleXTop = result.transform.paddleXSourceY(r.modelTop);
            float paddleXRight = result.transform.paddleXSourceX(r.modelRight);
            float paddleXBottom = result.transform.paddleXSourceY(r.modelBottom);
            sb.append(String.format(Locale.US,
                    "    {\"index\": %d, \"classId\": %d, \"label\": \"%s\", \"confidence\": %.5f, \"coordinateMode\": \"%s\", \"sourceBox\": [%.2f, %.2f, %.2f, %.2f], \"rawBox\": [%.2f, %.2f, %.2f, %.2f], \"modelLetterboxInverseBox\": [%.2f, %.2f, %.2f, %.2f], \"legacyScaleFactorInverseBox\": [%.2f, %.2f, %.2f, %.2f], \"paddlexScaleFactorInverseBox\": [%.2f, %.2f, %.2f, %.2f]}",
                    i, r.classId, r.label, r.confidence, r.coordinateMode, r.left, r.top, r.right, r.bottom,
                    r.modelLeft, r.modelTop, r.modelRight, r.modelBottom,
                    inverseLeft, inverseTop, inverseRight, inverseBottom,
                    scaleFactorLeft, scaleFactorTop, scaleFactorRight, scaleFactorBottom,
                    paddleXLeft, paddleXTop, paddleXRight, paddleXBottom));
            sb.append(i + 1 < result.regions.size() ? ",\n" : "\n");
        }
        sb.append("  ]\n}\n");
        return sb.toString();
    }

    private static String shapeJson(long[] shape) {
        if (shape == null) return "null";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < shape.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(shape[i]);
        }
        return sb.append(']').toString();
    }

    private static String quote(String text) {
        if (text == null) return "null";
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}