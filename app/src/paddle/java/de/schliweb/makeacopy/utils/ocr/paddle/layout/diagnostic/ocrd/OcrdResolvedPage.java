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

import java.nio.file.Path;

public final class OcrdResolvedPage {
    public final Path pageXmlFile;
    public final OcrdEvaluationPage page;
    public final Path imageFile;

    public OcrdResolvedPage(Path pageXmlFile, OcrdEvaluationPage page, Path imageFile) {
        this.pageXmlFile = pageXmlFile;
        this.page = page;
        this.imageFile = imageFile;
    }

    public boolean hasImage() {
        return imageFile != null;
    }

    public String safeSampleName() {
        String base = page == null || page.pageId == null || page.pageId.isEmpty()
                ? pageXmlFile.getFileName().toString()
                : page.pageId;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) base = base.substring(slash + 1);
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        return base.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}