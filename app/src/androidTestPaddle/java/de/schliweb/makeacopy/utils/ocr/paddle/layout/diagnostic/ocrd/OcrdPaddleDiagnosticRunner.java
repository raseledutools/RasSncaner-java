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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.schliweb.makeacopy.utils.ocr.OCRHelper;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.diagnostic.PaddleLayoutClassIdDiagnostics;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.PaddleLayoutDebugDumper;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.PaddleLayoutModelInfo;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.PaddleLayoutOrtRunner;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.PaddleLayoutResult;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.reading.PaddleLayoutReadingBlock;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.reading.PaddleLayoutReadingDebugReport;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.reading.PaddleLayoutReadingLine;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.reading.PaddleLayoutReadingOrderResolver;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.reading.PaddleLayoutReconstructionDiagnostic;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.reading.PaddleReadingOrderTextExporter;

public final class OcrdPaddleDiagnosticRunner {
    private final Context context;
    private final Path artifactRoot;

    public OcrdPaddleDiagnosticRunner(Context context, Path artifactRoot) {
        this.context = context;
        this.artifactRoot = artifactRoot;
    }

    public void run(Path ocrdRoot, int maxPages, String deviceVariant) throws Exception {
        String runName = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date())
                + "-" + OcrdDiagnosticArtifactLayout.safe(deviceVariant == null ? "device" : deviceVariant);
        OcrdDiagnosticArtifactLayout layout = new OcrdDiagnosticArtifactLayout(artifactRoot, runName);
        Files.createDirectories(layout.latestDir);
        Files.createDirectories(layout.runDir);

        List<OcrdResolvedPage> pages = new OcrdCorpusImporter().selectResolvedPages(ocrdRoot, maxPages);
        List<OcrdDiagnosticComparison> comparisons = new ArrayList<>();
        PaddleLayoutClassIdDiagnostics classIdDiagnostics = new PaddleLayoutClassIdDiagnostics();
        int skipped = 0;
        try (PaddleLayoutOrtRunner layoutRunner = new PaddleLayoutOrtRunner(context);
             OCRHelper ocrHelper = new OCRHelper(context)) {
            for (OcrdResolvedPage resolvedPage : pages) {
                if (!resolvedPage.hasImage()) {
                    writeMissingImage(layout, resolvedPage);
                    skipped++;
                    continue;
                }
                OcrdDiagnosticComparison comparison = runPage(layout, layoutRunner, ocrHelper, resolvedPage,
                        classIdDiagnostics);
                if (comparison == null) skipped++;
                else comparisons.add(comparison);
            }
        }
        writeCorpusArtifacts(layout.runDir, comparisons, pages.size(), skipped);
        writeCorpusArtifacts(layout.latestDir, comparisons, pages.size(), skipped);
        writeText(layout.runDir.resolve("layout-class-id-validation.json"),
                classIdDiagnostics.toJson("ocrd", layoutMetadataEvidence()));
        writeText(layout.latestDir.resolve("layout-class-id-validation.json"),
                classIdDiagnostics.toJson("ocrd", layoutMetadataEvidence()));
        writeText(layout.runDir.resolve("layout-class-id-comparison-report.json"),
                classIdDiagnostics.comparisonReportJson("ocrd"));
        writeText(layout.latestDir.resolve("layout-class-id-comparison-report.json"),
                classIdDiagnostics.comparisonReportJson("ocrd"));
    }

    private OcrdDiagnosticComparison runPage(OcrdDiagnosticArtifactLayout layout, PaddleLayoutOrtRunner layoutRunner,
                                             OCRHelper ocrHelper, OcrdResolvedPage resolvedPage,
                                             PaddleLayoutClassIdDiagnostics classIdDiagnostics) throws Exception {
        Bitmap bitmap = BitmapFactory.decodeFile(resolvedPage.imageFile.toString());
        if (bitmap == null) {
            writeMissingImage(layout, resolvedPage);
            return null;
        }
        try {
            String sampleName = resolvedPage.safeSampleName();
            PaddleLayoutResult layoutResult = layoutRunner.analyze(bitmap, layout.runDir.toFile(), sampleName);
            PaddleLayoutDebugDumper.dump(layout.latestDir.toFile(), sampleName, bitmap, layoutResult);
            OCRHelper.OcrResultWords ocrResult = ocrHelper.runOcrWithRetry(bitmap);
            PaddleLayoutReadingDebugReport readingReport = new PaddleLayoutReadingOrderResolver()
                    .resolve(layoutResult, ocrResult);
            PaddleLayoutReadingDebugReport rawGeometryReport = new PaddleLayoutReadingOrderResolver(
                    PaddleLayoutReadingOrderResolver.RegionSelectionMode.RAW_ID_GEOMETRY_ONLY)
                    .resolve(layoutResult, ocrResult);
            writeReadingArtifacts(layout.runDir, sampleName, bitmap, readingReport);
            writeReadingArtifacts(layout.latestDir, sampleName, bitmap, readingReport);
            writeText(layout.runDir.resolve(sampleName + "-reading-order-raw-id-geometry-debug.json"),
                    rawGeometryReport.toJson());
            writeText(layout.latestDir.resolve(sampleName + "-reading-order-raw-id-geometry-debug.json"),
                    rawGeometryReport.toJson());
            OcrdDiagnosticComparison comparison = OcrdDiagnosticComparison.compare(resolvedPage.page, readingReport);
            classIdDiagnostics.addPage(sampleName, layoutResult, readingReport, rawGeometryReport, comparison,
                    ocrResult.words);
            writeComparison(layout.runDir, sampleName, comparison);
            writeComparison(layout.latestDir, sampleName, comparison);
            return comparison;
        } finally {
            bitmap.recycle();
        }
    }

    private static Map<String, String> layoutMetadataEvidence() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("asset", "paddleocr/v5/layout.ort");
        out.put("adjacentClassLabelFile", "not bundled");
        out.put("adjacentPaddleXConfig", "not bundled");
        out.put("adjacentInferenceMetadata", "not bundled");
        out.put("currentMapping", "25-class PP-DocLayoutV2/V3-like diagnostic mapping");
        out.put("mappingStatus", "unverified for deployed ORT; diagnostic-only validation in progress");
        return out;
    }

    private static void writeComparison(Path dir, String sampleName, OcrdDiagnosticComparison comparison) throws IOException {
        writeText(dir.resolve(sampleName + "-ocrd-comparison-debug.json"), comparison.toJson());
    }

    private static void writeCorpusArtifacts(Path dir, List<OcrdDiagnosticComparison> comparisons, int selected,
                                             int skipped) throws IOException {
        OcrdCorpusDiagnosticSummary summary = OcrdCorpusDiagnosticSummary.from(comparisons);
        writeText(dir.resolve("corpus-summary.json"), summary.toJson() + "\n");
        writeText(dir.resolve("ocrd-evaluation-report.json"), evaluationReport(summary, comparisons, selected, skipped));
    }

    private static String evaluationReport(OcrdCorpusDiagnosticSummary summary,
                                           List<OcrdDiagnosticComparison> comparisons, int selected, int skipped) {
        List<OcrdDiagnosticComparison> worst = new ArrayList<>(comparisons);
        worst.sort(Comparator.comparingDouble(OcrdPaddleDiagnosticRunner::riskScore).reversed());
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"selectedPages\": ").append(selected).append(",\n");
        sb.append("  \"processedPages\": ").append(comparisons.size()).append(",\n");
        sb.append("  \"skippedPages\": ").append(skipped).append(",\n");
        sb.append("  \"dominantTaxonomyCategories\": ");
        appendTopKeys(sb, summary.taxonomyCounts, 5);
        sb.append(",\n  \"safetyGateDecisionSummary\": {")
                .append("\"triggeredCount\": ").append(summary.safetyGateTriggeredCount).append(", ")
                .append("\"splitAppliedCount\": ").append(summary.safetyGateSplitAppliedCount).append(", ")
                .append("\"splitSkippedLowConfidenceCount\": ")
                .append(summary.safetyGateSplitSkippedLowConfidenceCount).append(", ")
                .append("\"dominantSkipReasons\": ");
        appendTopKeys(sb, summary.safetyGateSkipReasonCounts, 6);
        sb.append(", \"candidateQualityClassifications\": ");
        appendTopKeys(sb, summary.candidateQualityClassificationCounts, 6);
        sb.append("},\n  \"safetyGateInterpretation\": ").append(quote(safetyGateInterpretation(summary))).append(',');
        sb.append("\n  \"representativeWorstPages\": [");
        int limit = Math.min(3, worst.size());
        for (int i = 0; i < limit; i++) {
            OcrdDiagnosticComparison comparison = worst.get(i);
            if (i > 0) sb.append(',');
            sb.append("\n    {\"pageId\": \"").append(escape(comparison.pageId)).append("\", ")
                    .append("\"severity\": \"").append(escape(comparison.diagnosticSeverity)).append("\", ")
                    .append(String.format(Locale.US, "\"overlapSeverity\": %.3f, ", comparison.overlapSeverity))
                    .append(String.format(Locale.US, "\"reconstructedLineCoverage\": %.3f}",
                            comparison.reconstructedLineCoverage));
        }
        if (limit > 0) sb.append('\n');
        sb.append("  ],\n");
        sb.append("  \"likelyNextInvestigationTarget\": \"")
                .append(escape(nextInvestigationTarget(summary))).append("\"\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static double riskScore(OcrdDiagnosticComparison comparison) {
        double score = comparison.overlapSeverity * 10.0 + (1.0 - comparison.reconstructedLineCoverage) * 4.0;
        if ("SEVERE_GEOMETRY_CORRUPTION".equals(comparison.diagnosticSeverity)) score += 10.0;
        if ("READABILITY_RISK".equals(comparison.diagnosticSeverity)) score += 5.0;
        return score;
    }

    private static String nextInvestigationTarget(OcrdCorpusDiagnosticSummary summary) {
        if (summary.residualWideReconstructionCount > 0) return "residual wide reconstructed OCR lines";
        if (summary.falseColumnCandidateCount > summary.sidebarCandidateCount) return "false fallback-column splits on single-column references";
        if (summary.sidebarCandidateCount > 0) return "sidebar and marginalia discrimination";
        if (summary.maxOverlapSeverity > 0f) return "overlapping assignment conflicts";
        return "expand deterministic OCR-D subset before changing reading-order heuristics";
    }

    private static String safetyGateInterpretation(OcrdCorpusDiagnosticSummary summary) {
        if (summary.safetyGateTriggeredCount == 0) return "safety gate did not trigger on this subset";
        if (summary.safetyGateSplitAppliedCount == 0) {
            return "safety gate remained cautious; all triggered candidates were low-confidence splits";
        }
        return "safety gate applied local splits only where existing conservative geometry checks passed";
    }

    private static void appendTopKeys(StringBuilder sb, Map<String, Integer> values, int limit) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(values.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        sb.append('[');
        for (int i = 0; i < Math.min(limit, entries.size()); i++) {
            if (i > 0) sb.append(',');
            Map.Entry<String, Integer> entry = entries.get(i);
            sb.append("{\"category\": \"").append(escape(entry.getKey())).append("\", \"count\": ")
                    .append(entry.getValue()).append('}');
        }
        sb.append(']');
    }

    private static String quote(String text) {
        return text == null ? "null" : "\"" + escape(text) + "\"";
    }

    private static void writeMissingImage(OcrdDiagnosticArtifactLayout layout, OcrdResolvedPage resolvedPage)
            throws IOException {
        String sampleName = resolvedPage.safeSampleName();
        String json = "{\n"
                + "  \"pageXmlFile\": \"" + escape(resolvedPage.pageXmlFile.toString()) + "\",\n"
                + "  \"pageId\": \"" + escape(resolvedPage.page == null ? "" : resolvedPage.page.pageId) + "\",\n"
                + "  \"status\": \"MISSING_IMAGE\"\n"
                + "}\n";
        writeText(layout.runArtifact(sampleName, "-ocrd-comparison-debug.json"), json);
        writeText(layout.latestArtifact(sampleName, "-ocrd-comparison-debug.json"), json);
        Log.w(PaddleLayoutModelInfo.TAG, "Skipping OCR-D page with missing image: " + resolvedPage.pageXmlFile);
    }

    private static void writeReadingArtifacts(Path dir, String sampleName, Bitmap bitmap,
                                              PaddleLayoutReadingDebugReport readingReport) throws IOException {
        writeText(dir.resolve(sampleName + "-reading-order-debug.json"), readingReport.toJson());
        writeText(dir.resolve(sampleName + "-reading-order.txt"), readingReport.toStructuredText());
        writeText(dir.resolve(sampleName + "-reading-order-plain.txt"),
                PaddleReadingOrderTextExporter.exportPlainText(readingReport, false));
        writeReadingOverlay(dir.resolve(sampleName + "-reading-order-overlay.png"), bitmap, readingReport);
    }

    private static void writeText(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(path.toFile()), StandardCharsets.UTF_8)) {
            writer.write(text);
        }
    }

    private static void writeReadingOverlay(Path path, Bitmap source, PaddleLayoutReadingDebugReport report)
            throws IOException {
        Bitmap overlay = source.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(overlay);
        Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(Math.max(3f, source.getWidth() / 500f));
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(Math.max(24f, source.getWidth() / 55f));
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(0xAA000000);
        Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setStrokeWidth(Math.max(2f, source.getWidth() / 700f));
        Paint reconstructionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        reconstructionPaint.setStyle(Paint.Style.STROKE);
        reconstructionPaint.setStrokeWidth(Math.max(3f, source.getWidth() / 420f));
        Paint reconstructionFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        reconstructionFillPaint.setStyle(Paint.Style.FILL);
        reconstructionFillPaint.setColor(0x55FF00FF);
        float estimatedGutterLeft = source.getWidth() * 0.46f;
        float estimatedGutterRight = source.getWidth() * 0.54f;
        for (PaddleLayoutReconstructionDiagnostic diagnostic : report.reconstructionDiagnostics) {
            reconstructionPaint.setColor(diagnostic.reconstructedLineCrossesEstimatedGutter ? Color.MAGENTA : Color.CYAN);
            canvas.drawRect(diagnostic.reconstructedLineSourceSpan, reconstructionPaint);
            if (diagnostic.reconstructedLineCrossesEstimatedGutter) {
                canvas.drawRect(estimatedGutterLeft, diagnostic.reconstructedLineSourceSpan.top,
                        estimatedGutterRight, diagnostic.reconstructedLineSourceSpan.bottom, reconstructionFillPaint);
            }
            if (diagnostic.reconstructedLineOverlapAfterMerge > 0.25f) {
                canvas.drawLine(diagnostic.reconstructedLineSourceSpan.left, diagnostic.reconstructedLineSourceSpan.centerY(),
                        diagnostic.reconstructedLineSourceSpan.right, diagnostic.reconstructedLineSourceSpan.centerY(),
                        reconstructionPaint);
            }
        }
        PaddleLayoutReadingBlock previous = null;
        for (PaddleLayoutReadingBlock block : report.blocks) {
            boxPaint.setColor(block.columnIndex % 2 == 0 ? Color.rgb(0, 180, 255) : Color.rgb(255, 160, 0));
            canvas.drawRect(block.visualBox, boxPaint);
            String label = block.orderIndex + " c" + block.columnIndex + " " + block.label();
            float labelWidth = textPaint.measureText(label) + 12f;
            canvas.drawRect(block.visualBox.left, Math.max(0f, block.visualBox.top - textPaint.getTextSize() - 10f),
                    block.visualBox.left + labelWidth, block.visualBox.top, bgPaint);
            canvas.drawText(label, block.visualBox.left + 6f,
                    Math.max(textPaint.getTextSize(), block.visualBox.top - 6f), textPaint);
            for (PaddleLayoutReadingLine line : block.lines) {
                canvas.drawCircle(line.box.left, line.box.centerY(), Math.max(4f, source.getWidth() / 420f), boxPaint);
            }
            if (previous != null) {
                arrowPaint.setColor(previous.columnIndex != block.columnIndex ? Color.RED : Color.YELLOW);
                canvas.drawLine(previous.visualBox.centerX(), previous.visualBox.centerY(),
                        block.visualBox.centerX(), block.visualBox.centerY(), arrowPaint);
            }
            previous = block;
        }
        try (FileOutputStream out = new FileOutputStream(path.toFile())) {
            overlay.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally {
            overlay.recycle();
        }
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}