/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.ocr.paddle.layout.diagnostic;

import de.schliweb.makeacopy.utils.ocr.paddle.layout.PaddleLayoutClass;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PaddleLayoutClassIdDiagnosticsTest {
    @Test
    public void classifyTextuality_marksTextLikeGeometryAsTextual() {
        assertEquals("TEXTUAL_BY_GEOMETRY", PaddleLayoutClassIdDiagnostics.classifyTextuality(
                12, 0.03, 5.0, 0.82, 0.75, 0.70, 0.80));
    }

    @Test
    public void classifyTextuality_marksSparseEvidenceAsNonTextual() {
        assertEquals("NON_TEXTUAL_BY_GEOMETRY", PaddleLayoutClassIdDiagnostics.classifyTextuality(
                9, 0.50, 0.20, 0.35, 0.02, 0.03, 0.10));
    }

    @Test
    public void classifyTextuality_keepsRareIdsUnclassified() {
        assertEquals("TOO_RARE_TO_CLASSIFY", PaddleLayoutClassIdDiagnostics.classifyTextuality(
                2, 0.03, 4.5, 0.90, 0.90, 0.90, 0.90));
    }

    @Test
    public void classifyTextuality_keepsMixedEvidenceAmbiguous() {
        assertEquals("AMBIGUOUS_BY_GEOMETRY", PaddleLayoutClassIdDiagnostics.classifyTextuality(
                6, 0.01, 1.2, 0.55, 0.25, 0.12, 0.30));
    }

    @Test
    public void fromClassId_matchesVerifiedPaddleXPPDocLayoutOrder() {
        String[] labels = {
                "paragraph_title", "image", "text", "number", "abstract", "content", "figure_title",
                "formula", "table", "table_title", "reference", "doc_title", "footnote", "header",
                "algorithm", "footer", "seal", "chart_title", "chart", "formula_number", "header_image",
                "footer_image", "aside_text"
        };
        for (int i = 0; i < labels.length; i++) {
            assertEquals(labels[i], PaddleLayoutClass.fromClassId(i).label());
            assertEquals(labels[i], PaddleLayoutClass.localPaddleXOfficialLabelForClassId(i));
        }
        assertEquals("unknown", PaddleLayoutClass.fromClassId(23).label());
        assertTrue(PaddleLayoutClass.mappingVerificationStatus().contains("VERIFIED"));
        assertTrue(PaddleLayoutClass.recommendedDiagnosticPolicy().contains("diagnostic soft hints"));
    }
}