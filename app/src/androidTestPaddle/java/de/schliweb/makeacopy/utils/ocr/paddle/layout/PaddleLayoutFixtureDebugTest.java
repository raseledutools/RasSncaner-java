/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.ocr.paddle.layout;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import de.schliweb.makeacopy.utils.ocr.OCRHelper;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.reading.PaddleLayoutReadingBlock;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.reading.PaddleLayoutReadingDebugReport;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.reading.PaddleLayoutReadingLine;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.reading.PaddleLayoutReadingOrderResolver;
import de.schliweb.makeacopy.utils.ocr.paddle.layout.reading.PaddleReadingOrderTextExporter;

@RunWith(AndroidJUnit4.class)
public class PaddleLayoutFixtureDebugTest {
    private static final String TAG = "PaddleLayoutOrt";
    private static final String FIXTURE_DIR = "layout_test_data";
    private static final String REAL_SCAN_INPUT_DIR = "paddle-layout-input";
    private static final String[] REAL_SCAN_FILES = {
            "DOC_20260524_094418.jpg",
            "DOC_20260524_095954.jpg",
            "DOC_20260524_153302.jpg"
    };
    private static final String[] PDF_FIXTURES = {
            "single_column_s42.pdf",
            "two_column_s43.pdf",
            "three_column_s70.pdf",
            "sidebar_s46.pdf",
            "table_like_s48.pdf",
            "complex_s50.pdf",
            "separator_s53.pdf"
    };

    @Test
    public void analyzeRealLexiconScan_writesDebugArtifacts() throws Exception {
        Context appContext = ApplicationProvider.getApplicationContext();
        File debugDir = new File(appContext.getExternalFilesDir(null), "paddle-layout-debug");
        try (PaddleLayoutOrtRunner runner = new PaddleLayoutOrtRunner(appContext);
             OCRHelper ocrHelper = new OCRHelper(appContext)) {
            for (String realScanFile : REAL_SCAN_FILES) {
                File inputFile = new File(new File(appContext.getExternalFilesDir(null), REAL_SCAN_INPUT_DIR), realScanFile);
                assertTrue("Missing real scan input: push " + realScanFile + " to " + inputFile.getAbsolutePath(),
                        inputFile.isFile());

                Bitmap bitmap = BitmapFactory.decodeFile(inputFile.getAbsolutePath());
                assertNotNull(bitmap);
                try {
                    String sampleName = realScanFile.substring(0, realScanFile.lastIndexOf('.'));
                    PaddleLayoutResult result = runner.analyze(bitmap, debugDir, sampleName);
                    OCRHelper.OcrResultWords ocrResult = ocrHelper.runOcrWithRetry(bitmap);
                    PaddleLayoutReadingDebugReport readingReport =
                            new PaddleLayoutReadingOrderResolver().resolve(result, ocrResult);
                    PaddleLayoutClassStats classStats = new PaddleLayoutClassStats();
                    classStats.add(result);
                    String report = PaddleLayoutDebugReport.summarize(sampleName, result, null);
                    report += classStats.toHumanSummary();
                    writeText(new File(debugDir, sampleName + "-paddle-layout-report.txt"), report);
                    writeText(new File(debugDir, sampleName + "-layout-fixture-report.json"),
                            classStats.toJson(sampleName, result));
                    writeText(new File(debugDir, sampleName + "-reading-order.txt"),
                            readingReport.toStructuredText());
                    writeText(new File(debugDir, sampleName + "-reading-order-plain.txt"),
                            PaddleReadingOrderTextExporter.exportPlainText(readingReport, false));
                    writeText(new File(debugDir, sampleName + "-reading-order-debug.json"),
                            readingReport.toJson());
                    writeReadingOverlay(new File(debugDir, sampleName + "-reading-order-overlay.png"),
                            bitmap, readingReport);
                    assertFalse(result.outputNames.isEmpty());
                    assertTrue(new File(debugDir, sampleName + "-paddle-layout-debug.json").isFile());
                    assertTrue(new File(debugDir, sampleName + "-paddle-layout-overlay.png").isFile());
                    assertTrue(new File(debugDir, sampleName + "-reading-order-debug.json").isFile());
                } finally {
                    bitmap.recycle();
                }
            }
        }
    }

    @Test
    public void analyzeRealLayoutFixtures_writesDebugOverlays() throws Exception {
        Context appContext = ApplicationProvider.getApplicationContext();
        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        File debugDir = new File(appContext.getExternalFilesDir(null), "paddle-layout-debug");
        assertTrue(debugDir.mkdirs() || debugDir.isDirectory());
        PaddleLayoutSemanticDiagnostics corpusDiagnostics = new PaddleLayoutSemanticDiagnostics();
        Map<String, PaddleLayoutResult> fixtureResults = new LinkedHashMap<>();

        try (PaddleLayoutOrtRunner runner = new PaddleLayoutOrtRunner(appContext);
             OCRHelper ocrHelper = new OCRHelper(appContext)) {
            for (String pdf : PDF_FIXTURES) {
                Bitmap bitmap = renderPdfFirstPage(appContext, testContext, FIXTURE_DIR + "/" + pdf);
                assertNotNull(bitmap);
                try {
                    String sampleName = pdf.substring(0, pdf.length() - ".pdf".length());
                    writeBitmap(new File(debugDir, sampleName + "-source.png"), bitmap);
                    PaddleLayoutResult result = runner.analyze(bitmap, debugDir, sampleName);
                    PaddleLayoutGroundTruth truth = PaddleLayoutGroundTruth.load(testContext,
                            FIXTURE_DIR + "/" + sampleName + ".json");
                    corpusDiagnostics.addFixture(sampleName, result, truth);
                    fixtureResults.put(sampleName, result);
                    String report = PaddleLayoutDebugReport.summarize(sampleName, result, truth);
                    PaddleLayoutClassStats classStats = new PaddleLayoutClassStats();
                    classStats.add(result);
                    report += classStats.toHumanSummary();
                    assertFalse("No outputs for " + pdf, result.outputNames.isEmpty());
                    assertFalse("No output shapes for " + pdf, result.outputShapes.isEmpty());
                    OCRHelper.OcrResultWords ocrResult = ocrHelper.runOcrWithRetry(bitmap);
                    PaddleLayoutReadingDebugReport readingReport =
                            new PaddleLayoutReadingOrderResolver().resolve(result, ocrResult);
                    Log.i(TAG, report);
                    writeText(new File(debugDir, sampleName + "-paddle-layout-report.txt"), report);
                    writeText(new File(debugDir, sampleName + "-layout-fixture-report.json"),
                            classStats.toJson(sampleName, result));
                    writeText(new File(debugDir, sampleName + "-reading-order.txt"),
                            readingReport.toStructuredText());
                    writeText(new File(debugDir, sampleName + "-reading-order-plain.txt"),
                            PaddleReadingOrderTextExporter.exportPlainText(readingReport, false));
                    writeText(new File(debugDir, sampleName + "-reading-order-debug.json"),
                            readingReport.toJson());
                    writeReadingOverlay(new File(debugDir, sampleName + "-reading-order-overlay.png"),
                            bitmap, readingReport);
                    File json = new File(debugDir, sampleName + "-paddle-layout-debug.json");
                    File png = new File(debugDir, sampleName + "-paddle-layout-overlay.png");
                    File txt = new File(debugDir, sampleName + "-paddle-layout-report.txt");
                    File statsJson = new File(debugDir, sampleName + "-layout-fixture-report.json");
                    File readingJson = new File(debugDir, sampleName + "-reading-order-debug.json");
                    File readingTxt = new File(debugDir, sampleName + "-reading-order.txt");
                    File readingPlainTxt = new File(debugDir, sampleName + "-reading-order-plain.txt");
                    assertTrue(json.isFile());
                    assertTrue(png.isFile());
                    assertTrue(txt.isFile());
                    assertTrue(statsJson.isFile());
                    assertTrue(readingJson.isFile());
                    assertTrue(readingTxt.isFile());
                    assertTrue(readingPlainTxt.isFile());
                } finally {
                    bitmap.recycle();
                }
            }
            String corpusReport = corpusDiagnostics.toHumanSummary();
            Log.i(TAG, corpusReport);
            writeText(new File(debugDir, "layout-corpus-summary.txt"), corpusReport);
            writeText(new File(debugDir, "layout-corpus-summary.json"), corpusDiagnostics.toJson());
            assertFalse(fixtureResults.isEmpty());
            assertTrue(new File(debugDir, "layout-corpus-summary.json").isFile());
        }
    }

    private static Bitmap renderPdfFirstPage(Context appContext, Context testContext, String assetPath)
            throws IOException {
        File pdfFile = copyAssetToCache(appContext, testContext, assetPath);
        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(pfd)) {
            if (renderer.getPageCount() == 0) return null;
            try (PdfRenderer.Page page = renderer.openPage(0)) {
                final int targetDpi = 200;
                final float pdfDpi = 72f;
                float scale = targetDpi / pdfDpi;
                int width = (int) (page.getWidth() * scale);
                int height = (int) (page.getHeight() * scale);
                final int maxDimension = 2048;
                if (width > maxDimension || height > maxDimension) {
                    float downScale = Math.min((float) maxDimension / width, (float) maxDimension / height);
                    width = Math.max(1, (int) (width * downScale));
                    height = Math.max(1, (int) (height * downScale));
                    scale *= downScale;
                }
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(android.graphics.Color.WHITE);
                Matrix matrix = new Matrix();
                matrix.setScale(scale, scale);
                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                return bitmap;
            }
        }
    }

    private static File copyAssetToCache(Context appContext, Context testContext, String assetPath) throws IOException {
        File out = new File(appContext.getCacheDir(), assetPath.replace('/', '_'));
        try (InputStream in = testContext.getAssets().open(assetPath);
             FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buffer = new byte[16 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                fos.write(buffer, 0, n);
            }
        }
        return out;
    }

    private static void writeText(File file, String text) throws IOException {
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(text);
        }
    }

    private static void writeBitmap(File file, Bitmap bitmap) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw new IOException("Failed to write bitmap: " + file.getAbsolutePath());
            }
        }
    }

    private static void writeReadingOverlay(File file, Bitmap source, PaddleLayoutReadingDebugReport report)
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
        arrowPaint.setColor(Color.YELLOW);
        PaddleLayoutReadingBlock previous = null;
        for (PaddleLayoutReadingBlock block : report.blocks) {
            boxPaint.setColor(block.columnIndex % 2 == 0 ? Color.rgb(0, 180, 255) : Color.rgb(255, 160, 0));
            if (report.quality != null && report.quality.crossColumnJumpCount > 0) {
                boxPaint.setColor(block.columnIndex % 2 == 0 ? Color.rgb(80, 220, 255) : Color.rgb(255, 120, 40));
            }
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
                boolean crossColumn = previous.columnIndex != block.columnIndex;
                arrowPaint.setColor(crossColumn ? Color.RED : Color.YELLOW);
                canvas.drawLine(previous.visualBox.centerX(), previous.visualBox.centerY(),
                        block.visualBox.centerX(), block.visualBox.centerY(), arrowPaint);
            }
            previous = block;
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            overlay.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally {
            overlay.recycle();
        }
    }
}