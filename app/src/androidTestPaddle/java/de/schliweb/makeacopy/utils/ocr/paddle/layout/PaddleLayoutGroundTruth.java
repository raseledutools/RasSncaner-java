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

import android.content.Context;
import android.graphics.RectF;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class PaddleLayoutGroundTruth {
    final String filename;
    final String description;
    final float pageWidthPt;
    final float pageHeightPt;
    final List<Zone> zones;

    private PaddleLayoutGroundTruth(String filename, String description, float pageWidthPt, float pageHeightPt,
                                    List<Zone> zones) {
        this.filename = filename;
        this.description = description;
        this.pageWidthPt = pageWidthPt;
        this.pageHeightPt = pageHeightPt;
        this.zones = Collections.unmodifiableList(zones);
    }

    static PaddleLayoutGroundTruth load(Context testContext, String assetPath) throws Exception {
        String json = readAsset(testContext, assetPath);
        JSONObject root = new JSONObject(json);
        JSONObject page = root.getJSONArray("pages").getJSONObject(0);
        float pageWidth = (float) page.getDouble("width_pt");
        float pageHeight = (float) page.getDouble("height_pt");
        JSONArray rawZones = page.getJSONArray("zones");
        List<Zone> zones = new ArrayList<>();
        for (int i = 0; i < rawZones.length(); i++) {
            JSONObject zone = rawZones.getJSONObject(i);
            JSONArray bbox = zone.getJSONArray("bbox");
            zones.add(new Zone(
                    zone.getString("zone_id"),
                    zone.getString("role"),
                    zone.optBoolean("is_textual", false),
                    zone.optInt("reading_order", i),
                    new RectF((float) bbox.getDouble(0), (float) bbox.getDouble(1),
                            (float) bbox.getDouble(2), (float) bbox.getDouble(3))));
        }
        return new PaddleLayoutGroundTruth(root.getString("filename"), root.optString("description"),
                pageWidth, pageHeight, zones);
    }

    Map<String, Integer> roleCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Zone zone : zones) counts.compute(zone.role, (role, previous) -> previous == null ? 1 : previous + 1);
        return counts;
    }

    RectF sourceBox(Zone zone, int sourceWidth, int sourceHeight) {
        float sx = sourceWidth / pageWidthPt;
        float sy = sourceHeight / pageHeightPt;
        return new RectF(zone.bboxPt.left * sx, (pageHeightPt - zone.bboxPt.bottom) * sy,
                zone.bboxPt.right * sx, (pageHeightPt - zone.bboxPt.top) * sy);
    }

    static final class Zone {
        final String id;
        final String role;
        final boolean textual;
        final int readingOrder;
        final RectF bboxPt;

        private Zone(String id, String role, boolean textual, int readingOrder, RectF bboxPt) {
            this.id = id;
            this.role = role;
            this.textual = textual;
            this.readingOrder = readingOrder;
            this.bboxPt = bboxPt;
        }
    }

    private static String readAsset(Context context, String assetPath) throws IOException {
        try (InputStream in = context.getAssets().open(assetPath);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.US, "%s zones=%d roles=%s", filename, zones.size(), roleCounts());
    }
}