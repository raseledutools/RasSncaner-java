/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.ocr.paddle.layout.reading;

public final class PaddleReadingOrderTextExporter {
    private PaddleReadingOrderTextExporter() {}

    public static String exportPlainText(PaddleLayoutReadingDebugReport report, boolean semanticMarkers) {
        if (report == null) return "";
        if (semanticMarkers) return report.toStructuredText();
        StringBuilder sb = new StringBuilder();
        for (PaddleLayoutReadingBlock block : report.blocks) {
            for (PaddleLayoutReadingLine line : block.lines) {
                if (line.text.isBlank()) continue;
                appendSeparator(sb);
                sb.append(line.text);
            }
            if (block.opaque && block.lines.isEmpty()) {
                appendSeparator(sb);
                sb.append("<Table/layout region omitted>");
            }
        }
        return sb.toString();
    }

    private static void appendSeparator(StringBuilder sb) {
        if (hasContent(sb)) sb.append('\n');
    }

    private static boolean hasContent(StringBuilder sb) {
        return sb.length() > 0;
    }
}