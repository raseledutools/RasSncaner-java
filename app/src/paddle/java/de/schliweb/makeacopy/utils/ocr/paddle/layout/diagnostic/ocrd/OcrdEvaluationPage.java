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

public final class OcrdEvaluationPage {
    public final String pageId;
    public final int width;
    public final int height;
    public final List<OcrdEvaluationRegion> regions;
    public final List<String> readingOrderIds;

    public OcrdEvaluationPage(String pageId, int width, int height, List<OcrdEvaluationRegion> regions,
                              List<String> readingOrderIds) {
        this.pageId = pageId;
        this.width = width;
        this.height = height;
        this.regions = Collections.unmodifiableList(new ArrayList<>(regions == null ? Collections.emptyList() : regions));
        this.readingOrderIds = Collections.unmodifiableList(new ArrayList<>(readingOrderIds == null ? Collections.emptyList() : readingOrderIds));
    }
}