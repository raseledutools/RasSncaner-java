/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.ocr.paddle.layout.diagnostic.ocrd;

public final class OcrdEvaluationWord {
    public final String id;
    public final String text;
    public final OcrdEvaluationBox box;

    public OcrdEvaluationWord(String id, String text, OcrdEvaluationBox box) {
        this.id = id;
        this.text = text == null ? "" : text;
        this.box = box;
    }
}