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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class OcrdPageImageResolver {
    public Path resolveImage(Path bagOrDirectory, Path pageXmlFile, String imageFilename) throws IOException {
        if (imageFilename == null || imageFilename.trim().isEmpty()) return null;
        String normalized = imageFilename.replace('\\', '/');
        Path direct = pageXmlFile.getParent() == null ? Paths.get(normalized) : pageXmlFile.getParent().resolve(normalized);
        if (isSafeExistingFile(bagOrDirectory, direct)) return direct.normalize();
        Path rootDirect = bagOrDirectory.resolve(normalized);
        if (isSafeExistingFile(bagOrDirectory, rootDirect)) return rootDirect.normalize();
        Path siblingImageDir = pageXmlFile.getParent() == null || pageXmlFile.getParent().getParent() == null
                ? null
                : pageXmlFile.getParent().getParent().resolve(normalized);
        if (siblingImageDir != null && isSafeExistingFile(bagOrDirectory, siblingImageDir)) {
            return siblingImageDir.normalize();
        }

        String imageName = Paths.get(normalized).getFileName().toString().toLowerCase(Locale.ROOT);
        String imageStem = stem(imageName);
        List<Path> candidates = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(bagOrDirectory)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return isImageFile(fileName) && (fileName.equals(imageName) || stem(fileName).equals(imageStem));
                    })
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(candidates::add);
        }
        return candidates.isEmpty() ? null : candidates.get(0).normalize();
    }

    private static boolean isImageFile(String fileName) {
        return fileName.endsWith(".jpg")
                || fileName.endsWith(".jpeg")
                || fileName.endsWith(".png")
                || fileName.endsWith(".tif")
                || fileName.endsWith(".tiff");
    }

    private static String stem(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName : fileName.substring(0, dot);
    }

    private static boolean isSafeExistingFile(Path root, Path candidate) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedCandidate = candidate.toAbsolutePath().normalize();
        return normalizedCandidate.startsWith(normalizedRoot) && Files.isRegularFile(normalizedCandidate);
    }
}