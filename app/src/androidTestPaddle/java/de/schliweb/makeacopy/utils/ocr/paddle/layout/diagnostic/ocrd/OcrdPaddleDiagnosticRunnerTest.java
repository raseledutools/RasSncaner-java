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
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

@RunWith(AndroidJUnit4.class)
public class OcrdPaddleDiagnosticRunnerTest {
    private static final String DEFAULT_OCRD_ROOT = "/Users/christian/Documents/git/makeacopy/training/data/OCR-D";

    @Test
    public void analyzeCuratedOcrdSubset_writesPaddleDiagnosticArtifacts() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File externalDir = context.getExternalFilesDir(null);
        Path artifactRoot = Paths.get((externalDir == null ? context.getFilesDir() : externalDir).getAbsolutePath(),
                "paddle-layout-artifacts");
        String variant = Build.MANUFACTURER + "-" + Build.MODEL + "-api" + Build.VERSION.SDK_INT;
        String ocrdRoot = InstrumentationRegistry.getArguments().getString("ocrdRoot", DEFAULT_OCRD_ROOT);
        int maxPages = Integer.parseInt(InstrumentationRegistry.getArguments().getString("maxPages", "6"));
        Path root = Paths.get(ocrdRoot);
        new OcrdPaddleDiagnosticRunner(context, artifactRoot).run(root, maxPages, variant);
    }
}