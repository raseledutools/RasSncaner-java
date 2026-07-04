/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import de.schliweb.makeacopy.utils.layout.DocumentLayoutAnalyzer;
import de.schliweb.makeacopy.utils.layout.DocumentRegion;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/**
 * OCRHelper — intentionally OCR-DISABLED build.
 *
 * <p>This flavor no longer bundles Tesseract (or any other text-recognition engine). Document
 * edge/corner detection (DocQuad, in the main source set) is completely unaffected and keeps
 * working normally — only text recognition has been removed to keep the app lightweight.
 *
 * <p>Every public method below intentionally mirrors the signatures of the full OCR-enabled
 * implementation so the rest of the app (export flow, review UI, background jobs, etc.) keeps
 * compiling and running unchanged. Calls that would have produced recognized text now return
 * "not initialized" / empty results instead, which the existing UI already knows how to surface
 * to the user (see OCRFragment's "Engine not initialized" handling).
 */
public class OCRHelper {
  private static final String TAG = "OCRHelper";
  private static final String DEFAULT_LANGUAGE = "eng";

  public static final int OCR_MODE_ORIGINAL = 0;
  public static final int OCR_MODE_QUICK = 1;
  public static final int OCR_MODE_ROBUST = 2;
  public static final int OCR_MODE_PADDLE = 3;

  @Getter private int pageSegMode = 0;
  @Getter private int recognitionMode = OCR_MODE_ROBUST;
  @Getter private boolean forceBinaryRobust = false;
  private boolean useBestModelSettings = false;
  private String language = DEFAULT_LANGUAGE;

  public OCRHelper(Context context) {
    // No engine to set up — OCR is disabled in this build.
  }

  public static File getTessdataDir(Context context) {
    return new File(context.getFilesDir(), "tessdata");
  }

  public static String decodeNumericEntities(String text) {
    if (text == null || text.isEmpty()) return text;
    java.util.regex.Matcher decMatcher =
        java.util.regex.Pattern.compile("&#(\\d+);").matcher(text);
    StringBuffer sb = new StringBuffer();
    while (decMatcher.find()) {
      try {
        int codePoint = Integer.parseInt(decMatcher.group(1));
        String replacement = new String(Character.toChars(codePoint));
        decMatcher.appendReplacement(
            sb, java.util.regex.Matcher.quoteReplacement(replacement));
      } catch (Throwable ignore) {
        // Best-effort; failure is non-critical
      }
    }
    decMatcher.appendTail(sb);
    text = sb.toString();

    java.util.regex.Matcher hexMatcher =
        java.util.regex.Pattern.compile("&#[xX]([0-9a-fA-F]+);").matcher(text);
    sb = new StringBuffer();
    while (hexMatcher.find()) {
      try {
        int codePoint = Integer.parseInt(hexMatcher.group(1), 16);
        String replacement = new String(Character.toChars(codePoint));
        hexMatcher.appendReplacement(
            sb, java.util.regex.Matcher.quoteReplacement(replacement));
      } catch (Throwable ignore) {
        // Best-effort; failure is non-critical
      }
    }
    hexMatcher.appendTail(sb);
    return sb.toString();
  }

  /** OCR is disabled in this build — always reports failure so callers show a clear message. */
  public boolean initTesseract() {
    Log.i(TAG, "initTesseract: OCR is disabled in this build");
    return false;
  }

  public void shutdown() {
    // Nothing to release — no engine was started.
  }

  public boolean isTesseractInitialized() {
    return false;
  }

  public void setPageSegMode(int mode) {
    this.pageSegMode = mode;
  }

  public void setPageSegmentationMode(OcrPageSegmentationMode mode) {
    // No-op: nothing to configure without a real engine.
  }

  public void setRecognitionMode(int mode) {
    this.recognitionMode = mode;
  }

  public void setForceBinaryRobust(boolean enable) {
    this.forceBinaryRobust = enable;
  }

  public void setReinitPerRun(boolean enable) {
    // No-op: no engine to reinitialize.
  }

  public void setUseBestModelSettings(boolean enable) {
    this.useBestModelSettings = enable;
  }

  public boolean isUsingBestModelSettings() {
    return useBestModelSettings;
  }

  public void setPaddleHighQualityDetectionEnabled(boolean enable) {
    // Standard flavor never includes PaddleOCR; kept for API parity.
  }

  public void setLanguage(String language) throws IOException {
    this.language = (language == null || language.isEmpty()) ? DEFAULT_LANGUAGE : language;
  }

  public void applyDefaultsForLanguage(String langSpec) {
    // No-op: no engine to configure.
  }

  public String[] getAvailableLanguages() {
    return new String[0];
  }

  public boolean isLanguageAvailable(String lang) {
    return false;
  }

  public void close() {
    // Nothing to release.
  }

  /** Always returns an empty result — OCR is disabled in this build. */
  public OcrResultWords runOcrWithRetry(Bitmap bitmap) {
    return new OcrResultWords("", null, new ArrayList<>());
  }

  public void logAllVariables() {
    // No-op: no engine variables to log.
  }

  public Integer getMeanConfidenceSafe() {
    return null;
  }

  public static class OcrResult {
    public final String text;
    public final Integer meanConfidence;

    public OcrResult(String text, Integer meanConfidence) {
      this.text = text != null ? text : "";
      this.meanConfidence = meanConfidence;
    }
  }

  public static class OcrResultWords extends OcrResult {
    public final List<RecognizedWord> words;

    public OcrResultWords(String text, Integer meanConfidence, List<RecognizedWord> words) {
      super(text, meanConfidence);
      this.words = (words != null) ? words : new ArrayList<>();
    }
  }

  public static class OcrResultWithLayout extends OcrResult {
    public final List<RegionOcrResult> regionResults;
    public final DocumentLayoutAnalyzer.AnalysisResult layoutAnalysis;

    public OcrResultWithLayout(
        String text,
        Integer meanConfidence,
        List<RegionOcrResult> regionResults,
        DocumentLayoutAnalyzer.AnalysisResult layoutAnalysis) {
      super(text, meanConfidence);
      this.regionResults = (regionResults != null) ? regionResults : new ArrayList<>();
      this.layoutAnalysis = layoutAnalysis;
    }
  }

  public record RegionOcrResult(DocumentRegion region, OcrResultWords ocrResult) {}

  /** Always returns an empty layout result — OCR is disabled in this build. */
  public OcrResultWithLayout runOcrWithLayout(Bitmap bitmap) {
    return new OcrResultWithLayout("", null, new ArrayList<>(), null);
  }

  public boolean hasComplexLayout(Bitmap bitmap) {
    return false;
  }

  public int getDocumentColumnCount(Bitmap bitmap) {
    return 1;
  }
}
