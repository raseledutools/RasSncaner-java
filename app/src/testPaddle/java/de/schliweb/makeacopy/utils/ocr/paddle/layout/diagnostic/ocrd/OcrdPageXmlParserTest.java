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

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OcrdPageXmlParserTest {
    @Test
    public void parseExtractsPageRegionsLinesWordsAndReadingOrder() throws Exception {
        OcrdEvaluationPage page = new OcrdPageXmlParser().parse(pageXml());

        assertEquals("page-001.png", page.pageId);
        assertEquals(1000, page.width);
        assertEquals(1400, page.height);
        assertEquals(List.of("r2", "r1"), page.readingOrderIds);
        assertEquals(2, page.regions.size());
        assertEquals("r1", page.regions.get(0).id);
        assertEquals(2, page.regions.get(0).lines.size());
        assertEquals("Left one", page.regions.get(0).lines.get(0).text);
        assertEquals("Left", page.regions.get(0).lines.get(0).words.get(0).text);
        assertEquals(100f, page.regions.get(0).box.left, 0.01f);
        assertEquals(120f, page.regions.get(0).lines.get(0).box.top, 0.01f);
    }

    @Test
    public void importerUsesDeterministicCuratedSubsetOrderAndLimit() throws Exception {
        Path root = Files.createTempDirectory("ocrd-importer-test");
        Files.write(root.resolve("b.xml"), pageXml().replace("page-001", "page-002").getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("a.xml"), pageXml().getBytes(StandardCharsets.UTF_8));

        List<OcrdEvaluationPage> pages = new OcrdCorpusImporter().importPages(root, 1);

        assertEquals(1, pages.size());
        assertEquals("page-001.png", pages.get(0).pageId);
    }

    @Test
    public void importerDeduplicatesPageXmlVariantsByImageReferenceBeforeLimiting() throws Exception {
        Path root = Files.createTempDirectory("ocrd-importer-dedupe-test");
        Path block = Files.createDirectories(root.resolve("data/OCR-D-GT-SEG-BLOCK"));
        Path line = Files.createDirectories(root.resolve("data/OCR-D-GT-SEG-LINE"));
        Files.write(block.resolve("page-001.xml"), pageXml().getBytes(StandardCharsets.UTF_8));
        Files.write(line.resolve("page-001.xml"), pageXml().getBytes(StandardCharsets.UTF_8));
        Files.write(line.resolve("page-002.xml"), pageXml().replace("page-001", "page-002").getBytes(StandardCharsets.UTF_8));

        List<OcrdEvaluationPage> pages = new OcrdCorpusImporter().importPages(root, 20);
        List<OcrdResolvedPage> resolvedPages = new OcrdCorpusImporter().selectResolvedPages(root, 20);

        assertEquals(2, pages.size());
        assertEquals("page-001.png", pages.get(0).pageId);
        assertEquals("page-002.png", pages.get(1).pageId);
        assertEquals(2, resolvedPages.size());
        assertEquals("page-001", resolvedPages.get(0).safeSampleName());
        assertEquals("page-002", resolvedPages.get(1).safeSampleName());
    }

    @Test
    public void imageResolverFindsPageXmlRelativeRootRelativeAndFilenameFallbackImages() throws Exception {
        Path root = Files.createTempDirectory("ocrd-image-resolver-test");
        Path pageDir = Files.createDirectories(root.resolve("data/PAGE"));
        Path imageDir = Files.createDirectories(root.resolve("data/IMG"));
        Path pageXml = pageDir.resolve("page.xml");
        Files.write(pageXml, pageXml().replace("page-001.png", "../IMG/page-001.png").getBytes(StandardCharsets.UTF_8));
        Path image = imageDir.resolve("page-001.png");
        Files.write(image, new byte[]{1, 2, 3});

        OcrdResolvedPage resolved = new OcrdCorpusImporter().selectResolvedPages(root, 1).get(0);

        assertEquals(image.normalize(), resolved.imageFile);
        assertTrue(resolved.hasImage());
        assertEquals("page-001", resolved.safeSampleName());
    }

    @Test
    public void resolvedSubsetKeepsMissingImagesSafeAndDeterministic() throws Exception {
        Path root = Files.createTempDirectory("ocrd-missing-image-test");
        Files.write(root.resolve("b.xml"), pageXml().replace("page-001.png", "missing-b.png").getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("a.xml"), pageXml().replace("page-001.png", "missing-a.png").getBytes(StandardCharsets.UTF_8));

        List<OcrdResolvedPage> pages = new OcrdCorpusImporter().selectResolvedPages(root, 2);

        assertEquals(2, pages.size());
        assertEquals("missing-a.png", pages.get(0).page.pageId);
        assertFalse(pages.get(0).hasImage());
        assertNull(pages.get(0).imageFile);
        assertEquals("missing-b.png", pages.get(1).page.pageId);
    }

    @Test
    public void artifactLayoutUsesRunScopedOcrdLatestAndRunsPaths() {
        OcrdDiagnosticArtifactLayout layout = new OcrdDiagnosticArtifactLayout(Path.of("app/build/paddle-layout-artifacts"),
                "20260525-device-debug");

        assertEquals(Path.of("app/build/paddle-layout-artifacts/ocrd/latest/page_1-reading-order-debug.json"),
                layout.latestArtifact("page 1", "-reading-order-debug.json"));
        assertEquals(Path.of("app/build/paddle-layout-artifacts/ocrd/runs/20260525-device-debug/page_1-ocrd-comparison-debug.json"),
                layout.runArtifact("page 1", "-ocrd-comparison-debug.json"));
    }

    @Test
    public void comparisonAndArtifactsStayDiagnosticOnlyAndJsonBased() throws Exception {
        OcrdEvaluationPage page = new OcrdPageXmlParser().parse(pageXml());
        OcrdDiagnosticComparison comparison = OcrdDiagnosticComparison.compare(page, null);

        assertEquals(3, comparison.ocrdLineCount);
        assertEquals(0, comparison.paddleLineCount);
        assertTrue(comparison.failureCategories.contains("OCR_LINE_COLLAPSE"));
        assertEquals("two-column", comparison.referenceLayoutClass);
        assertTrue(comparison.explainabilityTraces.contains("OCRD_READING_ORDER_AVAILABLE"));

        OcrdCorpusDiagnosticSummary summary = OcrdCorpusDiagnosticSummary.from(List.of(comparison));
        assertEquals(1, (int) summary.taxonomyCounts.get("OCR_LINE_COLLAPSE"));
        assertEquals(1, (int) summary.referenceLayoutCounts.get("two-column"));

        Path out = Files.createTempDirectory("ocrd-artifacts-test");
        Clock clock = Clock.fixed(Instant.parse("2026-05-25T08:32:00Z"), ZoneOffset.UTC);
        Path run = new OcrdDiagnosticArtifactWriter(out, clock).writeRun("curated subset", List.of(comparison));

        assertTrue(Files.exists(run.resolve("corpus-summary.json")));
        assertTrue(Files.readAllLines(run.resolve("corpus-summary.json")).stream()
                .anyMatch(line -> line.contains("OCR_LINE_COLLAPSE")));
        assertTrue(Files.readAllLines(run.resolve("corpus-summary.json")).stream()
                .anyMatch(line -> line.contains("diagnosticSummary")));
    }

    @Test
    public void pageShapeTaxonomyFlagsTableAndSidebarEvidence() throws Exception {
        OcrdEvaluationPage tablePage = new OcrdPageXmlParser().parse(pageXml()
                .replace("type=\"paragraph\"", "type=\"table\""));
        OcrdDiagnosticComparison table = OcrdDiagnosticComparison.compare(tablePage, null);

        assertEquals("table-like", table.referenceLayoutClass);
        assertTrue(table.failureCategories.contains("TABLE_STRUCTURE_AMBIGUITY"));

        OcrdEvaluationPage sidebarPage = new OcrdPageXmlParser().parse(pageXml()
                .replace("type=\"paragraph\"", "type=\"marginalia\""));
        OcrdDiagnosticComparison sidebar = OcrdDiagnosticComparison.compare(sidebarPage, null);

        assertEquals("marginalia-sidebar", sidebar.referenceLayoutClass);
        assertTrue(sidebar.failureCategories.contains("SIDEBAR_POLLUTION"));
    }

    private static String pageXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<PcGts xmlns=\"http://schema.primaresearch.org/PAGE/gts/pagecontent/2019-07-15\">"
                + "<Page imageFilename=\"page-001.png\" imageWidth=\"1000\" imageHeight=\"1400\">"
                + "<ReadingOrder><OrderedGroup id=\"ro\"><RegionRefIndexed index=\"0\" regionRef=\"r2\"/>"
                + "<RegionRefIndexed index=\"1\" regionRef=\"r1\"/></OrderedGroup></ReadingOrder>"
                + "<TextRegion id=\"r1\" type=\"paragraph\"><Coords points=\"100,100 450,100 450,260 100,260\"/>"
                + "<TextLine id=\"l1\"><Coords points=\"110,120 430,120 430,150 110,150\"/><TextEquiv><Unicode>Left one</Unicode></TextEquiv>"
                + "<Word id=\"w2\"><Coords points=\"190,120 260,120 260,150 190,150\"/><TextEquiv><Unicode>one</Unicode></TextEquiv></Word>"
                + "<Word id=\"w1\"><Coords points=\"110,120 180,120 180,150 110,150\"/><TextEquiv><Unicode>Left</Unicode></TextEquiv></Word></TextLine>"
                + "<TextLine id=\"l2\"><Coords points=\"110,170 430,170 430,200 110,200\"/><TextEquiv><Unicode>Left two</Unicode></TextEquiv></TextLine></TextRegion>"
                + "<TextRegion id=\"r2\" type=\"paragraph\"><Coords points=\"550,100 900,100 900,200 550,200\"/>"
                + "<TextLine id=\"l3\"><Coords points=\"560,120 880,120 880,150 560,150\"/><TextEquiv><Unicode>Right one</Unicode></TextEquiv></TextLine></TextRegion>"
                + "</Page></PcGts>";
    }
}