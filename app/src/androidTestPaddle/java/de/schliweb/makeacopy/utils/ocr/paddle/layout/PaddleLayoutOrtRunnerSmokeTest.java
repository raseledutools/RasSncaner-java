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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PaddleLayoutOrtRunnerSmokeTest {
    @Test
    public void analyzeSyntheticBitmap_logsTensorMetadata() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        Bitmap bitmap = Bitmap.createBitmap(900, 1200, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTextSize(42f);
        canvas.drawText("MakeACopy Paddle layout smoke test", 80, 160, paint);
        canvas.drawText("This bitmap is intentionally simple.", 80, 240, paint);

        try (PaddleLayoutOrtRunner runner = new PaddleLayoutOrtRunner(context)) {
            PaddleLayoutResult result = runner.analyze(bitmap);
            assertNotNull(result);
            assertFalse(result.outputNames.isEmpty());
            assertFalse(result.outputShapes.isEmpty());
        } finally {
            bitmap.recycle();
        }
    }
}