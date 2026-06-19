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

import android.graphics.RectF;

import de.schliweb.makeacopy.utils.ocr.paddle.layout.PaddleLayoutRegion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class PaddleLayoutReadingBlock {
    public final int orderIndex;
    public final int columnIndex;
    public final PaddleLayoutRegion region;
    public final RectF box;
    public final RectF visualBox;
    public final List<PaddleLayoutReadingLine> lines;
    public final boolean opaque;
    public final PaddleLayoutDiagnosticRole role;
    public final String columnConfidence;

    PaddleLayoutReadingBlock(int orderIndex, int columnIndex, PaddleLayoutRegion region,
                             List<PaddleLayoutReadingLine> lines, boolean opaque) {
        this(orderIndex, columnIndex, region, lines, opaque, roleFor(region, opaque));
    }

    PaddleLayoutReadingBlock(int orderIndex, int columnIndex, PaddleLayoutRegion region,
                             List<PaddleLayoutReadingLine> lines, boolean opaque,
                             PaddleLayoutDiagnosticRole role) {
        this(orderIndex, columnIndex, region, lines, opaque, role, "region");
    }

    PaddleLayoutReadingBlock(int orderIndex, int columnIndex, PaddleLayoutRegion region,
                             List<PaddleLayoutReadingLine> lines, boolean opaque,
                             PaddleLayoutDiagnosticRole role, String columnConfidence) {
        this(orderIndex, columnIndex, region, lines, opaque, role, columnConfidence, null);
    }

    PaddleLayoutReadingBlock(int orderIndex, int columnIndex, PaddleLayoutRegion region,
                             List<PaddleLayoutReadingLine> lines, boolean opaque,
                             PaddleLayoutDiagnosticRole role, String columnConfidence, RectF refinedBox) {
        this(orderIndex, columnIndex, region, lines, opaque, role, columnConfidence, refinedBox, null);
    }

    PaddleLayoutReadingBlock(int orderIndex, int columnIndex, PaddleLayoutRegion region,
                             List<PaddleLayoutReadingLine> lines, boolean opaque,
                             PaddleLayoutDiagnosticRole role, String columnConfidence, RectF refinedBox,
                             RectF visualBox) {
        this.orderIndex = orderIndex;
        this.columnIndex = columnIndex;
        this.region = region;
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines == null ? Collections.emptyList() : lines));
        this.box = refinedBox == null ? refinedBounds(region, this.lines, opaque) : new RectF(refinedBox);
        this.visualBox = visualBox == null ? new RectF(this.box) : new RectF(visualBox);
        this.opaque = opaque;
        this.role = role == null ? roleFor(region, opaque) : role;
        this.columnConfidence = columnConfidence == null ? "unknown" : columnConfidence;
    }

    public String label() {
        return role.label;
    }

    public String rawLabel() {
        return region == null ? "unassigned" : region.label;
    }

    public String toStructuredText() {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(label()).append(']');
        if (opaque && lines.isEmpty()) sb.append("\n<Table/layout region omitted>");
        for (PaddleLayoutReadingLine line : lines) {
            if (!line.text.isBlank()) sb.append('\n').append(line.text);
        }
        return sb.toString();
    }

    String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US,
                "{\"orderIndex\":%d,\"columnIndex\":%d,\"role\":%s,\"rawLabel\":%s,\"classId\":%d,\"opaque\":%s,\"columnConfidence\":%s,\"box\":[%.2f,%.2f,%.2f,%.2f],\"visualBox\":[%.2f,%.2f,%.2f,%.2f],\"lines\":[",
                orderIndex, columnIndex, PaddleLayoutReadingLine.quote(label()),
                PaddleLayoutReadingLine.quote(rawLabel()), region == null ? -1 : region.classId,
                opaque, PaddleLayoutReadingLine.quote(columnConfidence), box.left, box.top, box.right, box.bottom,
                visualBox.left, visualBox.top, visualBox.right, visualBox.bottom));
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(lines.get(i).toJson());
        }
        return sb.append("]}").toString();
    }

    private static PaddleLayoutDiagnosticRole roleFor(PaddleLayoutRegion region, boolean opaque) {
        if (opaque) return PaddleLayoutDiagnosticRole.OPAQUE_TABLE_LIKE;
        if (region == null) return PaddleLayoutDiagnosticRole.UNASSIGNED_COLUMN;
        switch (region.semanticClass) {
            case DOCUMENT_TITLE:
            case PARAGRAPH_TITLE:
            case ABSTRACT:
                return PaddleLayoutDiagnosticRole.TITLE_CANDIDATE;
            case ASIDE_TEXT:
                return PaddleLayoutDiagnosticRole.TEXT_CONTAINER_CANDIDATE;
            case FOOTER:
                return PaddleLayoutDiagnosticRole.OPAQUE_TABLE_LIKE;
            case INLINE_FORMULA:
                return PaddleLayoutDiagnosticRole.FOOTNOTE_CANDIDATE;
            default:
                return PaddleLayoutDiagnosticRole.BODY_CANDIDATE;
        }
    }

    private static RectF refinedBounds(PaddleLayoutRegion region, List<PaddleLayoutReadingLine> lines, boolean opaque) {
        if (region == null) return bounds(lines);
        RectF regionBox = new RectF(region.left, region.top, region.right, region.bottom);
        if (lines == null || lines.isEmpty()) return regionBox;
        RectF lineBox = bounds(lines);
        if (opaque) return regionBox;
        return lineBox;
    }

    private static RectF bounds(List<PaddleLayoutReadingLine> lines) {
        RectF out = new RectF();
        boolean first = true;
        if (lines != null) {
            for (PaddleLayoutReadingLine line : lines) {
                if (first) {
                    out.set(line.box);
                    first = false;
                } else {
                    out.union(line.box);
                }
            }
        }
        return out;
    }
}