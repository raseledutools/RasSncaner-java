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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PaddleLayoutReadingDebugReport {
    public final List<PaddleLayoutReadingBlock> blocks;
    public final int sourceWidth;
    public final int sourceHeight;
    public final int inputRegionCount;
    public final int filteredRegionCount;
    public final int ocrWordCount;
    public final int assignedLineCount;
    public final int unassignedLineCount;
    public final List<String> warnings;
    public final PaddleLayoutReadingQuality quality;
    public final List<PaddleLayoutReconstructionDiagnostic> reconstructionDiagnostics;

    PaddleLayoutReadingDebugReport(List<PaddleLayoutReadingBlock> blocks, int sourceWidth, int sourceHeight,
                                   int inputRegionCount, int filteredRegionCount, int ocrWordCount,
                                   int assignedLineCount, int unassignedLineCount, List<String> warnings,
                                   PaddleLayoutReadingQuality quality,
                                   List<PaddleLayoutReconstructionDiagnostic> reconstructionDiagnostics) {
        this.blocks = Collections.unmodifiableList(new ArrayList<>(blocks));
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
        this.inputRegionCount = inputRegionCount;
        this.filteredRegionCount = filteredRegionCount;
        this.ocrWordCount = ocrWordCount;
        this.assignedLineCount = assignedLineCount;
        this.unassignedLineCount = unassignedLineCount;
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        this.quality = quality;
        this.reconstructionDiagnostics = Collections.unmodifiableList(new ArrayList<>(reconstructionDiagnostics));
    }

    public String toStructuredText() {
        StringBuilder sb = new StringBuilder();
        for (PaddleLayoutReadingBlock block : blocks) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(block.toStructuredText());
        }
        return sb.toString();
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"sourceWidth\": ").append(sourceWidth).append(",\n");
        sb.append("  \"sourceHeight\": ").append(sourceHeight).append(",\n");
        sb.append("  \"inputRegionCount\": ").append(inputRegionCount).append(",\n");
        sb.append("  \"filteredRegionCount\": ").append(filteredRegionCount).append(",\n");
        sb.append("  \"ocrWordCount\": ").append(ocrWordCount).append(",\n");
        sb.append("  \"assignedLineCount\": ").append(assignedLineCount).append(",\n");
        sb.append("  \"unassignedLineCount\": ").append(unassignedLineCount).append(",\n");
        sb.append("  \"quality\": ").append(quality == null ? "null" : quality.toJson()).append(",\n");
        sb.append("  \"reconstructionDiagnostics\": [");
        for (int i = 0; i < reconstructionDiagnostics.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(reconstructionDiagnostics.get(i).toJson());
        }
        sb.append("],\n");
        sb.append("  \"warnings\": [");
        for (int i = 0; i < warnings.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(PaddleLayoutReadingLine.quote(warnings.get(i)));
        }
        sb.append("],\n  \"blocks\": [\n");
        for (int i = 0; i < blocks.size(); i++) {
            sb.append("    ").append(blocks.get(i).toJson());
            sb.append(i + 1 < blocks.size() ? ",\n" : "\n");
        }
        sb.append("  ]\n}\n");
        return sb.toString();
    }
}