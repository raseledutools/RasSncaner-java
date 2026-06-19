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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.StringTokenizer;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public final class OcrdPageXmlParser {
    public OcrdEvaluationPage parse(String xml) throws Exception {
        return parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    public OcrdEvaluationPage parse(InputStream input) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        setFeatureIfSupported(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setFeatureIfSupported(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFeatureIfSupported(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        Document doc = factory.newDocumentBuilder().parse(new InputSource(input));
        Element page = first(doc.getDocumentElement(), "Page");
        if (page == null) throw new IOException("PAGE/XML has no Page element");
        List<OcrdEvaluationRegion> regions = new ArrayList<>();
        for (Element region : children(page, "TextRegion")) regions.add(parseRegion(region));
        return new OcrdEvaluationPage(attr(page, "imageFilename"), intAttr(page, "imageWidth"),
                intAttr(page, "imageHeight"), regions, parseReadingOrder(page));
    }

    private static OcrdEvaluationRegion parseRegion(Element region) {
        List<OcrdEvaluationLine> lines = new ArrayList<>();
        for (Element line : children(region, "TextLine")) lines.add(parseLine(line));
        return new OcrdEvaluationRegion(attr(region, "id"), attr(region, "type"), parseBox(region), lines);
    }

    private static OcrdEvaluationLine parseLine(Element line) {
        List<OcrdEvaluationWord> words = new ArrayList<>();
        for (Element word : children(line, "Word")) {
            words.add(new OcrdEvaluationWord(attr(word, "id"), textEquiv(word), parseBox(word)));
        }
        words.sort(Comparator.comparingDouble(w -> w.box == null ? 0f : w.box.left));
        return new OcrdEvaluationLine(attr(line, "id"), textEquiv(line), parseBox(line), words);
    }

    private static List<String> parseReadingOrder(Element page) {
        List<String> ids = new ArrayList<>();
        Element order = first(page, "ReadingOrder");
        if (order == null) return ids;
        collectOrderedRegionRefs(order, ids);
        return ids;
    }

    private static void collectOrderedRegionRefs(Element element, List<String> out) {
        for (Element child : children(element, null)) {
            if ("RegionRefIndexed".equals(child.getLocalName()) || "RegionRef".equals(child.getLocalName())) {
                String ref = attr(child, "regionRef");
                if (!ref.isEmpty()) out.add(ref);
            }
            collectOrderedRegionRefs(child, out);
        }
    }

    private static String textEquiv(Element element) {
        Element unicode = first(element, "Unicode");
        return unicode == null ? "" : unicode.getTextContent().trim();
    }

    private static OcrdEvaluationBox parseBox(Element element) {
        Element coords = first(element, "Coords");
        if (coords == null) return null;
        String points = attr(coords, "points");
        if (points.isEmpty()) return null;
        float left = Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float right = 0f;
        float bottom = 0f;
        StringTokenizer tokenizer = new StringTokenizer(points);
        while (tokenizer.hasMoreTokens()) {
            String point = tokenizer.nextToken();
            int comma = point.indexOf(',');
            if (comma <= 0 || comma + 1 >= point.length()) continue;
            float x = Float.parseFloat(point.substring(0, comma));
            float y = Float.parseFloat(point.substring(comma + 1));
            left = Math.min(left, x);
            top = Math.min(top, y);
            right = Math.max(right, x);
            bottom = Math.max(bottom, y);
        }
        return left == Float.MAX_VALUE ? null : new OcrdEvaluationBox(left, top, right, bottom);
    }

    private static Element first(Element root, String localName) {
        if (root == null) return null;
        if (localName.equals(root.getLocalName())) return root;
        NodeList nodes = root.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element) {
                Element found = first((Element) node, localName);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static List<Element> children(Element root, String localName) {
        List<Element> out = new ArrayList<>();
        NodeList nodes = root.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element && (localName == null || localName.equals(node.getLocalName()))) {
                out.add((Element) node);
            }
        }
        return out;
    }

    private static String attr(Element element, String name) {
        return element == null || !element.hasAttribute(name) ? "" : element.getAttribute(name);
    }

    private static int intAttr(Element element, String name) {
        String value = attr(element, name);
        return value.isEmpty() ? 0 : Integer.parseInt(value);
    }

    private static void setFeatureIfSupported(DocumentBuilderFactory factory, String feature, boolean value)
            throws ParserConfigurationException {
        try {
            factory.setFeature(feature, value);
        } catch (ParserConfigurationException e) {
            String message = e.getMessage();
            if (message == null || !message.contains(feature)) throw e;
        }
    }
}