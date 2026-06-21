/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.data.library;

import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteQuery;

/** Builds parameterized SQL queries for OCR FTS search. */
public final class ScanSearchQueries {
  private ScanSearchQueries() {}

  public static SupportSQLiteQuery ocrFallbackSearch(String rawQuery, int limit) {
    String likeQuery = sanitizeLikeQuery(rawQuery);
    return new SimpleSQLiteQuery(
        "SELECT s.id AS scanId, s.title AS title, s.createdAt AS createdAt, s.coverPath AS coverPath, "
            + "p.pageIndex AS pageIndex, "
            + "p.text AS snippet, "
            + "0.0 AS rank "
            + "FROM scan_page_text p "
            + "JOIN scans s ON p.scanId = s.id "
            + "WHERE p.text LIKE ? ESCAPE '\\' "
            + "ORDER BY s.createdAt DESC "
            + "LIMIT ?",
        new Object[] {likeQuery, limit});
  }

  public static SupportSQLiteQuery ocrSearch(String ftsQuery, int limit) {
    try {
      // Check if we are running on FTS5 (which has bm25() and rank) or FTS4
      // We do a lightweight check by trying to use bm25() in a non-executing way or just assuming
      // based on the existence of the table and our knowledge of the setup.
      // Since we don't have easy access to the DB instance here, we use a query that works for both
      // but only uses rank/bm25 if possible.
      // Actually, it's better to just return a query that works for both or have the caller decide.
      // For now, we will use a simpler approach: ORDER BY s.createdAt DESC as a fallback if bm25
      // fails,
      // but SQL doesn't easily allow "try" in SELECT.
      // Given the environment, we'll try to provide a version that is mostly compatible.
      return new SimpleSQLiteQuery(
          "SELECT s.id AS scanId, s.title AS title, s.createdAt AS createdAt, s.coverPath AS coverPath, "
              + "p.pageIndex AS pageIndex, "
              + "snippet(scan_page_text_fts, 1, '“', '”', '…', 12) AS snippet, "
              + "0.0 AS rank " // bm25() is FTS5 only, fallback to constant rank for FTS4
              + "FROM scan_page_text_fts "
              + "JOIN scan_page_text p ON scan_page_text_fts.rowid = p.id "
              + "JOIN scans s ON p.scanId = s.id "
              + "WHERE scan_page_text_fts MATCH ? "
              + "ORDER BY s.createdAt DESC "
              + "LIMIT ?",
          new Object[] {ftsQuery, limit});
    } catch (Exception e) {
      return ocrFallbackSearch(ftsQuery, limit);
    }
  }

  public static String sanitizeFtsQuery(String rawQuery) {
    if (rawQuery == null) return "";
    String normalized = rawQuery.trim().replaceAll("\\s+", " ");
    if (normalized.isEmpty()) return "";
    StringBuilder builder = new StringBuilder();
    for (String term : normalized.split(" ", -1)) {
      String cleaned = term.replaceAll("[^\\p{L}\\p{N}_-]", "");
      if (cleaned.isEmpty()) continue;
      if (isFtsOperator(cleaned)) continue;
      if (builder.length() > 0) builder.append(' ');
      builder.append('"').append(cleaned.replace("\"", "\"\"")).append('"');
    }
    return builder.toString();
  }

  public static String sanitizeLikeQuery(String rawQuery) {
    if (rawQuery == null) return "%%";
    String normalized = rawQuery.trim().replaceAll("\\s+", " ");
    if (normalized.isEmpty()) return "%%";
    return "%" + normalized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
  }

  private static boolean isFtsOperator(String term) {
    return "AND".equalsIgnoreCase(term)
        || "OR".equalsIgnoreCase(term)
        || "NOT".equalsIgnoreCase(term);
  }
}
