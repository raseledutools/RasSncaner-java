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

public enum PaddleLayoutDiagnosticRole {
    TITLE_CANDIDATE("title_candidate"),
    BODY_CANDIDATE("body_candidate"),
    TEXT_CONTAINER_CANDIDATE("text_container_candidate"),
    SIDEBAR_CANDIDATE("sidebar_candidate"),
    OPAQUE_TABLE_LIKE("opaque_table_like"),
    FOOTNOTE_CANDIDATE("footnote_candidate"),
    UNASSIGNED_COLUMN("unassigned_column");

    public final String label;

    PaddleLayoutDiagnosticRole(String label) {
        this.label = label;
    }
}