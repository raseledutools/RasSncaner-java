/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.ocr.paddle;

import android.graphics.Bitmap;
import android.graphics.RectF;
import androidx.annotation.VisibleForTesting;
import de.schliweb.makeacopy.utils.ocr.OCRHelper;
import de.schliweb.makeacopy.utils.ocr.RecognizedWord;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for constructing OCR result objects from image processing outputs.
 *
 * <p>This class provides methods for assembling OCR recognition results using predicted
 * quadrilateral text regions and associated recognition data. It includes functionality
 * for grouping text regions into lines, handling sub-word detection in quadrilaterals,
 * and interpolating regions of quadrilateral shapes for accurate text localization.
 *
 * <p>The class is not instantiable and primarily operates through static methods, some
 * of which are exposed for testing purposes.
 */
final class PaddleResultBuilder {

    /**
     * Tolerance factor used for grouping quads into lines during OCR result processing.
     * This factor determines the maximum allowable vertical overlap between the
     * center Y-coordinates of quads for them to be considered part of the same line.
     * Specifically, the tolerance is calculated as {@code LINE_TOLERANCE_FACTOR × median quad height}.
     */
    @VisibleForTesting static final double LINE_TOLERANCE_FACTOR = 0.6;

    private PaddleResultBuilder() {}

    /**
     * A functional interface that defines a method for cropping a region of interest from an image.
     * It extracts a specified quadrilateral region from the provided image bitmap.
     */
    @FunctionalInterface
    interface Cropper {
        Bitmap crop(Bitmap full, Quad quad);
    }

    static OCRHelper.OcrResultWords build(
            Bitmap full, List<Quad> quads, PaddleRecOrtRunner rec) throws Exception {
        return build(full, quads, rec, PaddleQuadCropper::crop);
    }

    @VisibleForTesting
    static OCRHelper.OcrResultWords build(
            Bitmap full, List<Quad> quads, PaddleRecOrtRunner rec, Cropper cropper)
            throws Exception {
        if (quads == null || quads.isEmpty()) {
            return new OCRHelper.OcrResultWords("", null, new ArrayList<>());
        }
        // Schritt 2 (Layout-Rekonstruktion v2): zu hohe Det-Quads vor Recognition per
        // horizontalem Projection Profile in Zeilen-Sub-Quads zerlegen. Konservativ:
        // greift nur, wenn Höhe deutlich über medianer Quad-Höhe liegt.
        List<Quad> effectiveQuads = LineSplitter.splitTallQuads(full, quads);

        // Debug-Dumps (nur wenn aktiv und Bitmap registriert): Originalbild mit Det-Quads.
        PaddleDebugDumper.dumpOriginalWithQuads(full, effectiveQuads);

        // Reading-Order via Zeilen-Gruppierung: vertikales Center-Y-Overlap mit Toleranz
        // = LINE_TOLERANCE_FACTOR * mediane Quad-Höhe. Innerhalb der Zeile nach Center-X
        // sortiert. Zwischen Zeilen '\n', innerhalb single-space.
        List<List<Quad>> lines = groupQuadsIntoLines(effectiveQuads);

        List<RecognizedWord> words = new ArrayList<>();
        StringBuilder textBuilder = new StringBuilder();
        double confSum = 0.0;
        int confCount = 0;

        int globalCropIndex = 0;
        for (int li = 0; li < lines.size(); li++) {
            List<Quad> line = lines.get(li);
            if (li > 0) {
                textBuilder.append('\n');
            }
            for (int qi = 0; qi < line.size(); qi++) {
                Quad q = line.get(qi);
                Bitmap crop = null;
                try {
                    crop = cropper.crop(full, q);
                    PaddleRecOrtRunner.RecOutput out = rec.recognize(crop);
                    String text = out.text() != null ? out.text() : "";

                    // Debug-Dump pro Crop (nur wenn aktiv).
                    PaddleDebugDumper.dumpCrop(full, globalCropIndex++, q, crop, out);

                    // Space-/Token-Rekonstruktion: Recognition liefert keine Spaces.
                    // Versuche, den Crop per vertikalem Projection-Profile in Wort-Segmente
                    // zu trennen und den erkannten Text proportional zu verteilen.
                    float aggConf100 =
                            Math.max(0f, Math.min(100f, out.confidence() * 100f));
                    List<RecognizedWord> subWords =
                            WordSplitter.split(q, crop, text, aggConf100);
                    String emittedText;
                    if (subWords != null && subWords.size() >= 2) {
                        StringBuilder sb = new StringBuilder();
                        for (int wi = 0; wi < subWords.size(); wi++) {
                            if (wi > 0) sb.append(' ');
                            sb.append(subWords.get(wi).getText());
                        }
                        emittedText = sb.toString();
                    } else {
                        subWords = buildSubWords(q, out);
                        emittedText = text;
                    }

                    if (qi > 0 && textBuilder.length() > 0) {
                        char last = textBuilder.charAt(textBuilder.length() - 1);
                        if (last != ' ' && last != '\n' && !emittedText.isEmpty()
                                && emittedText.charAt(0) != ' ') {
                            textBuilder.append(' ');
                        }
                    }
                    textBuilder.append(emittedText);

                    for (RecognizedWord sw : subWords) {
                        words.add(sw);
                        confSum += sw.getConfidence();
                        confCount++;
                    }
                } finally {
                    if (crop != null && crop != full && !crop.isRecycled()) {
                        crop.recycle();
                    }
                }
            }
        }

        Integer meanConf =
                (confCount == 0) ? null : Integer.valueOf((int) Math.round(confSum / confCount));
        // Debug-Dump abschließen (nur wenn aktiv und für full registriert).
        PaddleDebugDumper.finishSample(full, textBuilder.toString(), meanConf);
        return new OCRHelper.OcrResultWords(textBuilder.toString(), meanConf, words);
    }

    /**
     * Groups a list of {@link Quad} objects into lines based on their vertical positions and dimensions.
     * The method sorts the provided quads by their vertical and horizontal centroids, calculates a
     * tolerance value based on the median height of the quads, and clusters the quads within this
     * tolerance into distinct lines. Each line is further sorted by horizontal positions of the quads.
     *
     * @param quads the list of {@link Quad} objects to be grouped into lines
     * @return a list of lines, where each line is a list of {@link Quad} objects grouped vertically
     */
    @VisibleForTesting
    static List<List<Quad>> groupQuadsIntoLines(List<Quad> quads) {
        List<Quad> sortedByY = new ArrayList<>(quads);
        // Primärsortierung nach Center-Y, Tie-Break Center-X – stabile Vorordnung.
        sortedByY.sort(
                java.util.Comparator.<Quad>comparingDouble(PaddleResultBuilder::centerY)
                        .thenComparingDouble(PaddleResultBuilder::centerX));

        // Mediane Höhe als Skalenreferenz für die Toleranz.
        double[] heights = new double[sortedByY.size()];
        for (int i = 0; i < sortedByY.size(); i++) {
            heights[i] = Math.max(1.0, sortedByY.get(i).maxY() - sortedByY.get(i).minY());
        }
        double[] sortedHeights = heights.clone();
        java.util.Arrays.sort(sortedHeights);
        double medianHeight = sortedHeights[sortedHeights.length / 2];
        double tol = LINE_TOLERANCE_FACTOR * medianHeight;

        List<List<Quad>> lines = new ArrayList<>();
        List<Quad> current = new ArrayList<>();
        double currentRefY = Double.NaN;
        for (Quad q : sortedByY) {
            double cy = centerY(q);
            if (current.isEmpty()) {
                current.add(q);
                currentRefY = cy;
            } else if (Math.abs(cy - currentRefY) <= tol) {
                current.add(q);
                // Referenz als laufender Mittelwert aktualisieren, robustifiziert gegen Drift.
                currentRefY = (currentRefY * (current.size() - 1) + cy) / current.size();
            } else {
                current.sort(java.util.Comparator.comparingDouble(PaddleResultBuilder::centerX));
                lines.add(current);
                current = new ArrayList<>();
                current.add(q);
                currentRefY = cy;
            }
        }
        if (!current.isEmpty()) {
            current.sort(java.util.Comparator.comparingDouble(PaddleResultBuilder::centerX));
            lines.add(current);
        }
        return lines;
    }

    private static double centerY(Quad q) {
        return (q.minY() + q.maxY()) * 0.5;
    }

    private static double centerX(Quad q) {
        return (q.minX() + q.maxX()) * 0.5;
    }

    /**
     * Builds a list of sub-words represented as {@link RecognizedWord} objects, given a {@link Quad}
     * and the OCR output containing recognized text and associated metadata.
     *
     * The method determines whether geometric data is available from the OCR output to properly
     * split the recognized text into sub-words. When geometry data is insufficient or unavailable,
     * it falls back to using the bounding box of the entire quadrilateral.
     *
     * @param q the quadrilateral that defines the region of interest for the recognized text
     * @param out the OCR output containing recognized text, confidence levels, and geometry
     * @return a list of {@link RecognizedWord} objects representing the constructed sub-words,
     *         each with its own text, bounding box, and confidence score
     */
    private static List<RecognizedWord> buildSubWords(Quad q, PaddleRecOrtRunner.RecOutput out) {
        String fullText = out.text() != null ? out.text() : "";
        // Aggregat-Konfidenz der Engine, 0..1 → 0..100 (für Fallback und als Mittel).
        float aggConf100 = clampConf100(out.confidence() * 100f);

        List<CtcDecoder.RecognizedToken> tokens = out.tokens();
        boolean hasGeometry =
                tokens != null
                        && !tokens.isEmpty()
                        && out.frameCount() > 0
                        && out.scaledCropWidth() > 0
                        && out.paddedCropWidth() > 0;

        if (!hasGeometry || tokens.size() < 2) {
            // Fallback: gesamte Det-Box.
            RectF bbox = quadBBox(q);
            String text = (tokens != null && tokens.size() == 1) ? tokens.get(0).text() : fullText;
            float conf =
                    (tokens != null && tokens.size() == 1)
                            ? clampConf100(tokens.get(0).confidence() * 100f)
                            : aggConf100;
            List<RecognizedWord> result = new ArrayList<>(1);
            if (!text.isEmpty()) {
                result.add(new RecognizedWord(text, bbox, conf));
            }
            return result;
        }

        final int T = out.frameCount();
        final double paddedW = out.paddedCropWidth();
        final double scaledW = out.scaledCropWidth();
        // Inhaltsbreite in padded-Koordinaten ist scaledW (Padding mit weiß rechts).
        // Mapping Frame k (0..T-1) → x in padded = (k + 0.5) * paddedW / T,
        // anschließend clip & normalisieren auf [0..1] über scaledW.
        List<RecognizedWord> result = new ArrayList<>(tokens.size());
        for (CtcDecoder.RecognizedToken tok : tokens) {
            if (tok.text() == null || tok.text().isEmpty()) continue;
            double xStart = ((double) tok.frameStart()) * paddedW / T; // linke Frame-Kante
            double xEnd = ((double) (tok.frameEnd() + 1)) * paddedW / T; // rechte Frame-Kante
            double uStart = clamp01(xStart / scaledW);
            double uEnd = clamp01(xEnd / scaledW);
            if (uEnd <= uStart) {
                // Degenerierter Fall (z.B. Frame jenseits scaledW): minimale Spanne erzwingen.
                uEnd = Math.min(1.0, uStart + 1e-3);
            }
            RectF bbox = interpolateQuadStrip(q, uStart, uEnd);
            float conf = clampConf100(tok.confidence() * 100f);
            result.add(new RecognizedWord(tok.text(), bbox, conf));
        }
        if (result.isEmpty()) {
            // Defensive: keine nicht-leeren Tokens → Fallback.
            if (!fullText.isEmpty()) {
                result.add(new RecognizedWord(fullText, quadBBox(q), aggConf100));
            }
        }
        return result;
    }

    /**
     * Computes a rectangular bounding box for a segment of a quadrilateral strip
     * defined by a start and end interpolation parameter. The method utilizes the
     * vertices of a {@link Quad} to interpolate positions along the strip's top
     * and bottom edges and derives the minimum and maximum coordinates of the
     * resultant rectangle.
     *
     * @param q the quadrilateral representing the context for interpolation, assumed to
     *          have vertices ordered as top-left (TL), top-right (TR), bottom-right (BR),
     *          and bottom-left (BL)
     * @param uStart the starting interpolation parameter, typically between 0.0 and 1.0,
     *               where 0.0 indicates the left edge and 1.0 indicates the right edge
     *               of the quadrilateral
     * @param uEnd the ending interpolation parameter, typically between 0.0 and 1.0,
     *             where this parameter must be greater than or equal to uStart
     * @return a {@link RectF} containing the interpolated bounding box defined by the minimum
     *         and maximum x and y coordinates of the interpolated vertices
     */
    @VisibleForTesting
    static RectF interpolateQuadStrip(Quad q, double uStart, double uEnd) {
        float[] r = interpolateQuadStripFloats(q, uStart, uEnd);
        return new RectF(r[0], r[1], r[2], r[3]);
    }

    /**
     * Computes a rectangular bounding box as an array of floats for a segment of a quadrilateral
     * strip defined by a start and end interpolation parameter. The method interpolates positions
     * along the top and bottom edges of the quadrilateral using the given parameters and derives
     * the minimum and maximum coordinates.
     *
     * @param q the quadrilateral representing the context for interpolation, assumed to have
     *          vertices ordered as top-left (TL), top-right (TR), bottom-right (BR), and bottom-left (BL)
     * @param uStart the starting interpolation parameter, typically between 0.0 and 1.0,
     *               where 0.0 indicates the left edge and 1.0 indicates the right edge of the quadrilateral
     * @param uEnd the ending interpolation parameter, typically between 0.0 and 1.0,
     *             where this parameter must be greater than or equal to uStart
     * @return a float array of size 4 containing the interpolated bounding box defined by
     *         the minimum x, minimum y, maximum x, and maximum y coordinates, in that order
     */
    @VisibleForTesting
    static float[] interpolateQuadStripFloats(Quad q, double uStart, double uEnd) {
        // Quad-Konvention: TL=0, TR=1, BR=2, BL=3.
        double tlx = q.x[0], tly = q.y[0];
        double trx = q.x[1], try_ = q.y[1];
        double brx = q.x[2], bry = q.y[2];
        double blx = q.x[3], bly = q.y[3];

        double topX0 = tlx + uStart * (trx - tlx);
        double topY0 = tly + uStart * (try_ - tly);
        double topX1 = tlx + uEnd * (trx - tlx);
        double topY1 = tly + uEnd * (try_ - tly);
        double botX0 = blx + uStart * (brx - blx);
        double botY0 = bly + uStart * (bry - bly);
        double botX1 = blx + uEnd * (brx - blx);
        double botY1 = bly + uEnd * (bry - bly);

        float minX = (float) Math.min(Math.min(topX0, topX1), Math.min(botX0, botX1));
        float maxX = (float) Math.max(Math.max(topX0, topX1), Math.max(botX0, botX1));
        float minY = (float) Math.min(Math.min(topY0, topY1), Math.min(botY0, botY1));
        float maxY = (float) Math.max(Math.max(topY0, topY1), Math.max(botY0, botY1));
        return new float[] {minX, minY, maxX, maxY};
    }

    private static RectF quadBBox(Quad q) {
        return new RectF(
                (float) q.minX(), (float) q.minY(), (float) q.maxX(), (float) q.maxY());
    }

    private static float clampConf100(float v) {
        if (v < 0f) return 0f;
        if (v > 100f) return 100f;
        return v;
    }

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }
}
