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

import java.util.Locale;

public final class PaddleLayoutOrderingIssue {
    public enum SeverityLevel {
        INFO,
        WARNING
    }

    public enum Category {
        ASSIGNMENT_CONFIDENCE,
        GEOMETRY_CONSISTENCY,
        OCR_GEOMETRY_HEALTH,
        EXPECTED_FLOW_TRANSITION,
        SUPPRESSED_FALSE_POSITIVE
    }

    public enum Type {
        UNASSIGNED_LINES,
        WEAK_ASSIGNMENT,
        AMBIGUOUS_ASSIGNMENT,
        CROSS_COLUMN_JUMP,
        LARGE_VERTICAL_GAP,
        OVERLAPPING_BLOCKS,
        EMPTY_OPAQUE_BLOCK,
        OCR_LINE_HEALTH
    }

    public final Type type;
    public final String message;
    public final int firstOrderIndex;
    public final int secondOrderIndex;
    public final float severity;
    public final SeverityLevel severityLevel;
    public final Category category;

    PaddleLayoutOrderingIssue(Type type, String message, int firstOrderIndex, int secondOrderIndex, float severity) {
        this(type, message, firstOrderIndex, secondOrderIndex, severity, SeverityLevel.WARNING, defaultCategory(type));
    }

    PaddleLayoutOrderingIssue(Type type, String message, int firstOrderIndex, int secondOrderIndex, float severity,
                              SeverityLevel severityLevel, Category category) {
        this.type = type;
        this.message = message;
        this.firstOrderIndex = firstOrderIndex;
        this.secondOrderIndex = secondOrderIndex;
        this.severity = severity;
        this.severityLevel = severityLevel;
        this.category = category;
    }

    String toJson() {
        return String.format(Locale.US,
                "{\"type\":%s,\"category\":%s,\"severityLevel\":%s,\"message\":%s,\"firstOrderIndex\":%d,\"secondOrderIndex\":%d,\"severity\":%.4f}",
                PaddleLayoutReadingLine.quote(type.name()), PaddleLayoutReadingLine.quote(category.name()),
                PaddleLayoutReadingLine.quote(severityLevel.name()), PaddleLayoutReadingLine.quote(message),
                firstOrderIndex, secondOrderIndex, severity);
    }

    private static Category defaultCategory(Type type) {
        switch (type) {
            case WEAK_ASSIGNMENT:
            case AMBIGUOUS_ASSIGNMENT:
            case UNASSIGNED_LINES:
                return Category.ASSIGNMENT_CONFIDENCE;
            case CROSS_COLUMN_JUMP:
            case LARGE_VERTICAL_GAP:
                return Category.EXPECTED_FLOW_TRANSITION;
            case OVERLAPPING_BLOCKS:
            case EMPTY_OPAQUE_BLOCK:
                return Category.GEOMETRY_CONSISTENCY;
            case OCR_LINE_HEALTH:
                return Category.OCR_GEOMETRY_HEALTH;
            default:
                return Category.GEOMETRY_CONSISTENCY;
        }
    }
}