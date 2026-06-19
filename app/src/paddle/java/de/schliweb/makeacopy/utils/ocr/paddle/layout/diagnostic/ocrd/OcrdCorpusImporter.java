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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class OcrdCorpusImporter {
    private final OcrdPageXmlParser parser;

    public OcrdCorpusImporter() {
        this(new OcrdPageXmlParser());
    }

    public OcrdCorpusImporter(OcrdPageXmlParser parser) {
        this.parser = parser;
    }

    public List<OcrdEvaluationPage> importPages(Path bagOrDirectory, int maxPages) throws Exception {
        List<OcrdEvaluationPage> allPages = uniquePages(bagOrDirectory);
        int limit = maxPages <= 0 ? allPages.size() : Math.min(maxPages, allPages.size());
        List<OcrdEvaluationPage> pages = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            pages.add(allPages.get(i));
        }
        return pages;
    }

    public OcrdEvaluationPage importPageXml(Path pageXmlFile) throws Exception {
        try (var input = Files.newInputStream(pageXmlFile)) {
            return parser.parse(input);
        }
    }

    public List<OcrdResolvedPage> selectResolvedPages(Path bagOrDirectory, int maxPages) throws Exception {
        List<PageXmlWithPage> pageXmlFiles = uniquePageXmlFiles(bagOrDirectory);
        int limit = maxPages <= 0 ? pageXmlFiles.size() : Math.min(maxPages, pageXmlFiles.size());
        List<OcrdResolvedPage> pages = new ArrayList<>();
        OcrdPageImageResolver resolver = new OcrdPageImageResolver();
        for (int i = 0; i < limit; i++) {
            Path pageXmlFile = pageXmlFiles.get(i).pageXmlFile;
            OcrdEvaluationPage page = pageXmlFiles.get(i).page;
            pages.add(new OcrdResolvedPage(pageXmlFile, page,
                    resolver.resolveImage(bagOrDirectory, pageXmlFile, page.pageId)));
        }
        return pages;
    }

    public List<Path> pageXmlFiles(Path bagOrDirectory) throws IOException {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(bagOrDirectory)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml"))
                    .filter(this::looksLikePageXml)
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(out::add);
        }
        return out;
    }

    private List<OcrdEvaluationPage> uniquePages(Path bagOrDirectory) throws Exception {
        List<OcrdEvaluationPage> pages = new ArrayList<>();
        for (PageXmlWithPage pageXmlWithPage : uniquePageXmlFiles(bagOrDirectory)) {
            pages.add(pageXmlWithPage.page);
        }
        return pages;
    }

    private List<PageXmlWithPage> uniquePageXmlFiles(Path bagOrDirectory) throws Exception {
        Map<String, PageXmlWithPage> unique = new LinkedHashMap<>();
        for (Path pageXmlFile : pageXmlFiles(bagOrDirectory)) {
            OcrdEvaluationPage page = importPageXml(pageXmlFile);
            String key = page.pageId == null || page.pageId.isBlank()
                    ? pageXmlFile.getFileName().toString()
                    : page.pageId;
            unique.putIfAbsent(key, new PageXmlWithPage(pageXmlFile, page));
        }
        return new ArrayList<>(unique.values());
    }

    private static final class PageXmlWithPage {
        private final Path pageXmlFile;
        private final OcrdEvaluationPage page;

        private PageXmlWithPage(Path pageXmlFile, OcrdEvaluationPage page) {
            this.pageXmlFile = pageXmlFile;
            this.page = page;
        }
    }

    private boolean looksLikePageXml(Path path) {
        try (Stream<String> lines = Files.lines(path)) {
            return lines.limit(20).anyMatch(line -> line.contains("PcGts") || line.contains("Page"));
        } catch (IOException | UncheckedIOException e) {
            return false;
        }
    }
}