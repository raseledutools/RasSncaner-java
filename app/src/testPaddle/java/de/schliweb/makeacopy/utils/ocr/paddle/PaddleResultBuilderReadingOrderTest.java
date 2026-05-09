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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import de.schliweb.makeacopy.utils.ocr.OCRHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Reading-Order-Tests für {@link PaddleResultBuilder} (Schritt 10a).
 *
 * <p>Stub-Det liefert Quads in absichtlich vermischter Reihenfolge (Zeile 2 vor Zeile 1, rechte
 * Spalte vor linker). Erwartet wird, dass {@code build(...)} die Quads zeilenweise (Center-Y mit
 * Toleranz × medianer Quad-Höhe) gruppiert und innerhalb der Zeile nach Center-X sortiert. Plain-
 * Text wird mit {@code \n} zwischen Zeilen und single-space innerhalb der Zeile zusammengesetzt.
 *
 * <p>Da der JVM-Stub-Recognizer in den Tests nur eine FIFO-Liste liefert, wird die Reihenfolge
 * der Recognizer-Aufrufe (= effektive Builder-Reihenfolge) über eine ID-→-Output-Map abgebildet:
 * jedes Quad bekommt eine eindeutige ID via {@code score}, der Recognizer matcht.
 */
public class PaddleResultBuilderReadingOrderTest {

    private static Quad rectQuad(double x, double y, double w, double h, double id) {
        return new Quad(
                new double[] {x, x + w, x + w, x},
                new double[] {y, y, y + h, y + h},
                id);
    }

    /** Recognizer, der pro Quad einen RecOutput liefert, indiziert über {@code Quad.score}. */
    private static final class IdRec extends PaddleRecOrtRunner {
        private final Map<Double, PaddleRecOrtRunner.RecOutput> byId;
        private final List<Double> callOrder = new ArrayList<>();
        private final List<Bitmap> capturedCrops = new ArrayList<>();
        // Letztes Quad, das zum Crop reichte – über Cropper-Hook gesetzt.
        Quad lastQuad;

        IdRec(Map<Double, PaddleRecOrtRunner.RecOutput> byId) {
            super();
            this.byId = byId;
        }

        @Override
        PaddleRecOrtRunner.RecOutput recognize(Bitmap quadCrop) {
            // Aufrufreihenfolge gemerkt vom Cropper-Hook (siehe build()).
            double id = lastQuad.score;
            callOrder.add(id);
            capturedCrops.add(quadCrop);
            return byId.get(id);
        }
    }

    private static PaddleRecOrtRunner.RecOutput textOnly(String text) {
        // Legacy 2-arg: keine Sub-Word-Geometrie → Builder verwendet ganze Det-Box als BBox.
        return new PaddleRecOrtRunner.RecOutput(text, 0.8f);
    }

    @Test
    public void build_twoLinesShuffled_emitsLinesInReadingOrderWithNewline() throws Exception {
        // Zeile 1 (y≈10): "Hello", "World"
        // Zeile 2 (y≈60): "foo", "bar", "baz"
        Quad q1Left = rectQuad(10, 10, 80, 20, 1.0);   // "Hello"
        Quad q1Right = rectQuad(110, 10, 80, 20, 2.0); // "World"
        Quad q2A = rectQuad(10, 60, 50, 20, 3.0);      // "foo"
        Quad q2B = rectQuad(70, 60, 50, 20, 4.0);      // "bar"
        Quad q2C = rectQuad(130, 60, 50, 20, 5.0);     // "baz"

        Map<Double, PaddleRecOrtRunner.RecOutput> byId = new HashMap<>();
        byId.put(1.0, textOnly("Hello"));
        byId.put(2.0, textOnly("World"));
        byId.put(3.0, textOnly("foo"));
        byId.put(4.0, textOnly("bar"));
        byId.put(5.0, textOnly("baz"));

        // Vermischte Det-Reihenfolge: Zeile 2 (rechts→links), dann Zeile 1 (rechts, links).
        List<Quad> shuffled = Arrays.asList(q2C, q2A, q2B, q1Right, q1Left);

        IdRec rec = new IdRec(byId);
        PaddleResultBuilder.Cropper hook = (full, quad) -> {
            rec.lastQuad = quad;
            return null;
        };
        OCRHelper.OcrResultWords result =
                PaddleResultBuilder.build(null, shuffled, rec, hook);

        assertNotNull(result);
        assertEquals("Hello World\nfoo bar baz", result.text);

        // Aufrufreihenfolge spiegelt Reading-Order wider.
        assertEquals(Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0), rec.callOrder);
    }

    @Test
    public void build_singleQuad_emitsOnlyTextNoSeparators() throws Exception {
        Quad only = rectQuad(0, 0, 100, 20, 1.0);
        Map<Double, PaddleRecOrtRunner.RecOutput> byId = new HashMap<>();
        byId.put(1.0, textOnly("solo"));

        IdRec rec = new IdRec(byId);
        PaddleResultBuilder.Cropper hook = (full, quad) -> {
            rec.lastQuad = quad;
            return null;
        };
        OCRHelper.OcrResultWords result =
                PaddleResultBuilder.build(null, Arrays.asList(only), rec, hook);

        assertEquals("solo", result.text);
    }

    @Test
    public void build_identicalCenterY_tieBreakByCenterX() throws Exception {
        // Drei Quads mit *identischem* Center-Y, jeweils unterschiedliche X.
        Quad qA = rectQuad(200, 0, 50, 20, 1.0); // mitte
        Quad qB = rectQuad(0, 0, 50, 20, 2.0);   // links
        Quad qC = rectQuad(400, 0, 50, 20, 3.0); // rechts

        Map<Double, PaddleRecOrtRunner.RecOutput> byId = new HashMap<>();
        byId.put(1.0, textOnly("B"));
        byId.put(2.0, textOnly("A"));
        byId.put(3.0, textOnly("C"));

        IdRec rec = new IdRec(byId);
        PaddleResultBuilder.Cropper hook = (full, quad) -> {
            rec.lastQuad = quad;
            return null;
        };
        OCRHelper.OcrResultWords result =
                PaddleResultBuilder.build(null, Arrays.asList(qA, qB, qC), rec, hook);

        // Erwartet: links → mitte → rechts in einer Zeile.
        assertEquals("A B C", result.text);
    }

    @Test
    public void build_skewedQuads_groupedByCenterYWithinTolerance() throws Exception {
        // Zwei leicht schräge Quads in derselben Zeile (Center-Y differiert minimal),
        // ein klar darunter liegendes Quad als Zeile 2.
        // Quad A: TL(0,0) TR(100,4) BR(100,24) BL(0,20) → centerY ≈ 12, height ≈ 22
        Quad qA = new Quad(new double[] {0, 100, 100, 0}, new double[] {0, 4, 24, 20}, 1.0);
        // Quad B: rechts daneben, leichter Versatz: centerY ≈ 14
        Quad qB = new Quad(
                new double[] {110, 210, 210, 110},
                new double[] {2, 6, 26, 22},
                2.0);
        // Quad C: deutlich tiefer, centerY ≈ 60
        Quad qC = rectQuad(0, 50, 100, 20, 3.0);

        Map<Double, PaddleRecOrtRunner.RecOutput> byId = new HashMap<>();
        byId.put(1.0, textOnly("alpha"));
        byId.put(2.0, textOnly("beta"));
        byId.put(3.0, textOnly("gamma"));

        IdRec rec = new IdRec(byId);
        PaddleResultBuilder.Cropper hook = (full, quad) -> {
            rec.lastQuad = quad;
            return null;
        };
        OCRHelper.OcrResultWords result =
                PaddleResultBuilder.build(null, Arrays.asList(qC, qB, qA), rec, hook);

        assertEquals("alpha beta\ngamma", result.text);
    }

    @Test
    public void groupQuadsIntoLines_directlyGroupsAndOrders() {
        // Direkter Test der Helfer-Methode.
        Quad q1Left = rectQuad(10, 10, 80, 20, 1.0);
        Quad q1Right = rectQuad(110, 10, 80, 20, 2.0);
        Quad q2A = rectQuad(10, 60, 50, 20, 3.0);
        Quad q2B = rectQuad(70, 60, 50, 20, 4.0);
        List<Quad> shuffled = Arrays.asList(q2B, q1Right, q2A, q1Left);

        List<List<Quad>> lines = PaddleResultBuilder.groupQuadsIntoLines(shuffled);
        assertEquals(2, lines.size());
        assertEquals(2, lines.get(0).size());
        assertEquals(2, lines.get(1).size());
        // Zeile 1: q1Left (id=1.0), q1Right (id=2.0)
        assertEquals(1.0, lines.get(0).get(0).score, 1e-9);
        assertEquals(2.0, lines.get(0).get(1).score, 1e-9);
        // Zeile 2: q2A (id=3.0), q2B (id=4.0)
        assertEquals(3.0, lines.get(1).get(0).score, 1e-9);
        assertEquals(4.0, lines.get(1).get(1).score, 1e-9);
    }

    @Test
    public void groupQuadsIntoLines_singleQuad_returnsSingleLine() {
        Quad only = rectQuad(0, 0, 50, 10, 1.0);
        List<List<Quad>> lines =
                PaddleResultBuilder.groupQuadsIntoLines(Arrays.asList(only));
        assertEquals(1, lines.size());
        assertEquals(1, lines.get(0).size());
        assertTrue(lines.get(0).get(0) == only);
    }
}
