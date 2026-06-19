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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class OcrdEvaluationLine {
    public final String id;
    public final String text;
    public final OcrdEvaluationBox box;
    public final List<OcrdEvaluationWord> words;

    public OcrdEvaluationLine(String id, String text, OcrdEvaluationBox box, List<OcrdEvaluationWord> words) {
        this.id = id;
        this.text = text == null ? "" : text;
        this.box = box;
        this.words = Collections.unmodifiableList(new ArrayList<>(words == null ? Collections.emptyList() : words));
    }
}