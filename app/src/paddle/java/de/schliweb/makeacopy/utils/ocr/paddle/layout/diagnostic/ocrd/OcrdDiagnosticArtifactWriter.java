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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class OcrdDiagnosticArtifactWriter {
    private final Path root;
    private final Clock clock;

    public OcrdDiagnosticArtifactWriter(Path root, Clock clock) {
        this.root = root;
        this.clock = clock;
    }

    public Path writeRun(String corpusName, List<OcrdDiagnosticComparison> comparisons) throws IOException {
        String runId = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(clock.getZone()).format(clock.instant());
        Path runDir = root.resolve(safe(corpusName)).resolve(runId);
        Files.createDirectories(runDir);
        StringBuilder summary = new StringBuilder();
        summary.append("{\n  \"corpusName\": ").append(quote(corpusName)).append(",\n");
        summary.append("  \"diagnosticSummary\": ")
                .append(OcrdCorpusDiagnosticSummary.from(comparisons).toJson().replace("\n", "\n  "))
                .append(",\n  \"pages\": [\n");
        for (int i = 0; i < comparisons.size(); i++) {
            OcrdDiagnosticComparison comparison = comparisons.get(i);
            Path pageFile = runDir.resolve(safe(comparison.pageId.isEmpty() ? "page-" + i : comparison.pageId) + "-comparison.json");
            Files.write(pageFile, comparison.toJson().getBytes(StandardCharsets.UTF_8));
            summary.append("    ").append(comparison.toJson().replace("\n", "\n    ").trim());
            summary.append(i + 1 < comparisons.size() ? ",\n" : "\n");
        }
        summary.append("  ]\n}\n");
        Files.write(runDir.resolve("corpus-summary.json"), summary.toString().getBytes(StandardCharsets.UTF_8));
        return runDir;
    }

    private static String safe(String value) {
        String text = value == null || value.isBlank() ? "ocrd" : value;
        return text.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String quote(String text) {
        return text == null ? "null" : "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}