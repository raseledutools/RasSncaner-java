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

public final class OcrdDiagnosticArtifactLayout {
    public final Path latestDir;
    public final Path runDir;

    public OcrdDiagnosticArtifactLayout(Path artifactRoot, String runName) {
        Path root = artifactRoot.resolve("ocrd");
        this.latestDir = root.resolve("latest");
        this.runDir = root.resolve("runs").resolve(runName);
    }

    public Path latestArtifact(String sampleName, String suffix) {
        return latestDir.resolve(safe(sampleName) + suffix);
    }

    public Path runArtifact(String sampleName, String suffix) {
        return runDir.resolve(safe(sampleName) + suffix);
    }

    public static String safe(String sampleName) {
        return (sampleName == null || sampleName.isEmpty() ? "sample" : sampleName)
                .replaceAll("[^A-Za-z0-9._-]", "_");
    }
}