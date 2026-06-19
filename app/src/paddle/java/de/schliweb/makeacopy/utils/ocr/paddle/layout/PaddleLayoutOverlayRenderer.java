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

import java.util.Locale;

public final class PaddleLayoutOverlayRenderer {
    public enum Mode {
        SEMANTIC_LABEL,
        CLASS_ID,
        CONFIDENCE_HEAT,
        UNKNOWN_HIGHLIGHT,
        GEOMETRY_ONLY,
        REGION_DENSITY
    }

    public Bitmap render(Bitmap source, PaddleLayoutResult result) {
        return render(source, result, Options.defaults());
    }

    public Bitmap render(Bitmap source, PaddleLayoutResult result, Options options) {
        Options opts = options == null ? Options.defaults() : options;
        Bitmap overlay = source.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(overlay);
        Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
        box.setStyle(Paint.Style.STROKE);
        box.setStrokeWidth(Math.max(3f, Math.min(source.getWidth(), source.getHeight()) / 300f));
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE);
        text.setTextSize(Math.max(22f, source.getWidth() / 80f));
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setStyle(Paint.Style.FILL);

        if (opts.showSummary) {
            bg.setColor(Color.argb(190, 0, 0, 0));
            String summary = String.format(Locale.US, "PP-DocLayout diagnostic mode=%s threshold=%.2f regions=%d input=%dx%d scale=[%.4f,%.4f]",
                    opts.mode, opts.confidenceThreshold, result.regions.size(), result.inputWidth, result.inputHeight,
                    result.transform.scaleFactorY, result.transform.scaleFactorX);
            float tw = text.measureText(summary);
            float th = text.getTextSize();
            canvas.drawRect(0, 0, Math.min(source.getWidth(), tw + 16), th + 14, bg);
            canvas.drawText(summary, 8, th + 2, text);
        }

        for (int i = 0; i < result.regions.size(); i++) {
            PaddleLayoutRegion r = result.regions.get(i);
            if (opts.mode == Mode.UNKNOWN_HIGHLIGHT && r.semanticClass != PaddleLayoutClass.UNKNOWN) continue;
            int color = colorFor(r, opts.mode);
            box.setColor(color);
            bg.setColor(color);
            canvas.drawRect(new RectF(r.left, r.top, r.right, r.bottom), box);
            String label = labelFor(i, r, opts);
            if (opts.showCoordinates) {
                label += String.format(Locale.US, " src[%.0f,%.0f,%.0f,%.0f] mdl[%.0f,%.0f,%.0f,%.0f]",
                        r.left, r.top, r.right, r.bottom, r.modelLeft, r.modelTop, r.modelRight, r.modelBottom);
            }
            float tw = text.measureText(label);
            float th = text.getTextSize();
            float labelLeft = clampLabelLeft(r.left, source.getWidth() - tw - 12);
            float labelBottom = Math.max(th + 8, r.top);
            canvas.drawRect(labelLeft, labelBottom - th - 8, labelLeft + tw + 12, labelBottom, bg);
            canvas.drawText(label, labelLeft + 6, labelBottom - 6, text);
        }
        return overlay;
    }

    public static final class Options {
        public final boolean showIndex;
        public final boolean showCoordinates;
        public final boolean showSummary;
        public final float confidenceThreshold;
        public final Mode mode;

        private Options(boolean showIndex, boolean showCoordinates, boolean showSummary, float confidenceThreshold,
                        Mode mode) {
            this.showIndex = showIndex;
            this.showCoordinates = showCoordinates;
            this.showSummary = showSummary;
            this.confidenceThreshold = confidenceThreshold;
            this.mode = mode;
        }

        public static Options defaults() {
            return semanticLabels();
        }

        public static Options verboseCoordinates() {
            return new Options(true, true, true, PaddleLayoutModelInfo.DEFAULT_CONFIDENCE_THRESHOLD,
                    Mode.SEMANTIC_LABEL);
        }

        public static Options semanticLabels() {
            return new Options(true, false, true, PaddleLayoutModelInfo.DEFAULT_CONFIDENCE_THRESHOLD,
                    Mode.SEMANTIC_LABEL);
        }

        public static Options classIds() {
            return new Options(true, false, true, PaddleLayoutModelInfo.DEFAULT_CONFIDENCE_THRESHOLD, Mode.CLASS_ID);
        }

        public static Options confidenceHeat() {
            return new Options(true, false, true, PaddleLayoutModelInfo.DEFAULT_CONFIDENCE_THRESHOLD,
                    Mode.CONFIDENCE_HEAT);
        }

        public static Options unknownHighlight() {
            return new Options(true, false, true, PaddleLayoutModelInfo.DEFAULT_CONFIDENCE_THRESHOLD,
                    Mode.UNKNOWN_HIGHLIGHT);
        }

        public static Options geometryOnly() {
            return new Options(false, false, true, PaddleLayoutModelInfo.DEFAULT_CONFIDENCE_THRESHOLD,
                    Mode.GEOMETRY_ONLY);
        }

        public static Options regionDensity() {
            return new Options(true, false, true, PaddleLayoutModelInfo.DEFAULT_CONFIDENCE_THRESHOLD,
                    Mode.REGION_DENSITY);
        }
    }

    private static String labelFor(int index, PaddleLayoutRegion r, Options opts) {
        String body = switch (opts.mode) {
            case CLASS_ID -> String.format(Locale.US, "id=%d %.2f", r.classId, r.confidence);
            case CONFIDENCE_HEAT -> String.format(Locale.US, "%.2f %s", r.confidence, r.label);
            case UNKNOWN_HIGHLIGHT -> String.format(Locale.US, "UNKNOWN id=%d %.2f", r.classId, r.confidence);
            case GEOMETRY_ONLY -> String.format(Locale.US, "%.0fx%.0f", r.right - r.left, r.bottom - r.top);
            case REGION_DENSITY -> String.format(Locale.US, "%s area=%.1fk", r.label,
                    Math.max(0f, r.right - r.left) * Math.max(0f, r.bottom - r.top) / 1000f);
            case SEMANTIC_LABEL -> String.format(Locale.US, "%s %.2f", r.label, r.confidence);
        };
        return opts.showIndex ? String.format(Locale.US, "#%02d %s", index, body) : body;
    }

    private static int colorFor(PaddleLayoutRegion region, Mode mode) {
        if (mode == Mode.CONFIDENCE_HEAT) {
            int red = clampColor((int) (255f * (1f - region.confidence)));
            int green = clampColor((int) (255f * region.confidence));
            return Color.rgb(red, green, 0);
        }
        if (mode == Mode.UNKNOWN_HIGHLIGHT) return Color.RED;
        if (mode == Mode.GEOMETRY_ONLY) return Color.YELLOW;
        if (mode == Mode.REGION_DENSITY) {
            float area = Math.max(0f, region.right - region.left) * Math.max(0f, region.bottom - region.top);
            int alphaBlue = Math.max(70, clampColor((int) (area / 8000f)));
            return Color.rgb(255 - alphaBlue / 2, 180, alphaBlue);
        }
        PaddleLayoutClass cls = region.semanticClass;
        return switch (cls) {
            case TEXT -> Color.rgb(0, 180, 0);
            case DOCUMENT_TITLE, PARAGRAPH_TITLE, FIGURE_TITLE -> Color.rgb(0, 90, 255);
            case TABLE -> Color.rgb(255, 150, 0);
            case IMAGE, CHART -> Color.MAGENTA;
            case CONTENT, ABSTRACT, ASIDE_TEXT, VERTICAL_TEXT -> Color.CYAN;
            case HEADER, HEADER_IMAGE, FOOTER, FOOTER_IMAGE -> Color.rgb(180, 120, 255);
            case DISPLAY_FORMULA, INLINE_FORMULA, FORMULA_NUMBER -> Color.rgb(255, 90, 160);
            case UNKNOWN -> Color.RED;
            default -> Color.RED;
        };
    }

    private static float clampLabelLeft(float left, float maxLeft) {
        if (left < 0f) return 0f;
        return left > maxLeft ? Math.max(0f, maxLeft) : left;
    }

    private static int clampColor(int value) {
        if (value < 0) return 0;
        return Math.min(255, value);
    }
}