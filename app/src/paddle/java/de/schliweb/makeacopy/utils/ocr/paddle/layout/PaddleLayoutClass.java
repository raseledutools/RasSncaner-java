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

public enum PaddleLayoutClass {
    ABSTRACT("abstract"),
    ALGORITHM("algorithm"),
    ASIDE_TEXT("aside_text"),
    CHART("chart"),
    CONTENT("content"),
    DISPLAY_FORMULA("display_formula"),
    DOCUMENT_TITLE("doc_title"),
    FIGURE_TITLE("figure_title"),
    FOOTER("footer"),
    FOOTER_IMAGE("footer_image"),
    FOOTNOTE("footnote"),
    FORMULA("formula"),
    FORMULA_NUMBER("formula_number"),
    HEADER("header"),
    HEADER_IMAGE("header_image"),
    IMAGE("image"),
    INLINE_FORMULA("inline_formula"),
    NUMBER("number"),
    PARAGRAPH_TITLE("paragraph_title"),
    REFERENCE("reference"),
    REFERENCE_CONTENT("reference_content"),
    SEAL("seal"),
    TABLE("table"),
    TABLE_TITLE("table_title"),
    TEXT("text"),
    CHART_TITLE("chart_title"),
    VERTICAL_TEXT("vertical_text"),
    VISION_FOOTNOTE("vision_footnote"),
    UNKNOWN("unknown");

    private final String label;

    PaddleLayoutClass(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static PaddleLayoutClass fromClassId(int classId) {
        return switch (classId) {
            case 0 -> PARAGRAPH_TITLE;
            case 1 -> IMAGE;
            case 2 -> TEXT;
            case 3 -> NUMBER;
            case 4 -> ABSTRACT;
            case 5 -> CONTENT;
            case 6 -> FIGURE_TITLE;
            case 7 -> FORMULA;
            case 8 -> TABLE;
            case 9 -> TABLE_TITLE;
            case 10 -> REFERENCE;
            case 11 -> DOCUMENT_TITLE;
            case 12 -> FOOTNOTE;
            case 13 -> HEADER;
            case 14 -> ALGORITHM;
            case 15 -> FOOTER;
            case 16 -> SEAL;
            case 17 -> CHART_TITLE;
            case 18 -> CHART;
            case 19 -> FORMULA_NUMBER;
            case 20 -> HEADER_IMAGE;
            case 21 -> FOOTER_IMAGE;
            case 22 -> ASIDE_TEXT;
            default -> UNKNOWN;
        };
    }

    public static String mappingVerificationStatus() {
        return "VERIFIED_AGAINST_LOCAL_PADDLEX_3_5_1_PP_DOCLAYOUT_SLM_ORDER";
    }

    public static String recommendedDiagnosticPolicy() {
        return "Use PP-DocLayout labels as diagnostic soft hints only; preserve geometry-first analysis.";
    }

    public static String localPaddleXOfficialLabelForClassId(int classId) {
        return switch (classId) {
            case 0 -> "paragraph_title";
            case 1 -> "image";
            case 2 -> "text";
            case 3 -> "number";
            case 4 -> "abstract";
            case 5 -> "content";
            case 6 -> "figure_title";
            case 7 -> "formula";
            case 8 -> "table";
            case 9 -> "table_title";
            case 10 -> "reference";
            case 11 -> "doc_title";
            case 12 -> "footnote";
            case 13 -> "header";
            case 14 -> "algorithm";
            case 15 -> "footer";
            case 16 -> "seal";
            case 17 -> "chart_title";
            case 18 -> "chart";
            case 19 -> "formula_number";
            case 20 -> "header_image";
            case 21 -> "footer_image";
            case 22 -> "aside_text";
            default -> "unknown";
        };
    }
}