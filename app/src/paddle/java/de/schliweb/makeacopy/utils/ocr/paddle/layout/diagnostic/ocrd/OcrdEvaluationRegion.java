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

public final class OcrdEvaluationRegion {
    public final String id;
    public final String type;
    public final OcrdEvaluationBox box;
    public final List<OcrdEvaluationLine> lines;

    public OcrdEvaluationRegion(String id, String type, OcrdEvaluationBox box, List<OcrdEvaluationLine> lines) {
        this.id = id;
        this.type = type == null ? "TextRegion" : type;
        this.box = box;
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines == null ? Collections.emptyList() : lines));
    }
}