/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package de.schliweb.makeacopy.utils.ocr.paddle;

import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utility class for handling language-to-model mappings and resolving recognition models
 * for the Paddle OCR framework. This class is non-instantiable and provides static methods
 * and data structures for determining the appropriate recognition model and asset base name
 * based on language specifications.
 */
final class PaddleLanguageRouter {

    /**
     * Model key for the experimental PP-OCRv6 "small" unified recognition model.
     * Assets live under {@code paddleocr/v6/small} (separate from the v5 assets) and are
     * resolved by dedicated v6 branches in {@link PaddleAssets}. This key is intentionally
     * NOT part of {@link #MODEL_KEYS} / {@link #ASSET_BASENAME}, so the existing v5
     * resolution remains untouched unless the experimental flag is explicitly enabled.
     */
    static final String MODEL_V6_SMALL = "paddle_v6_small";

    /**
     * Languages supported by the PP-OCRv6 small unified recognition model (per the
     * PP-OCRv6 paper / model card). Only these may be routed to {@link #MODEL_V6_SMALL}.
     * Languages such as rus/ell/ara/fas/hin/tha/kor are NOT in this set and must keep
     * using the v5 specialised models.
     */
    static final Set<String> V6_SMALL_SUPPORTED_LANGS;

    /**
     * Experimental opt-in switch for routing supported languages to PP-OCRv6 small.
     * Default {@code false}: production behaviour stays on v5.
     */
    private static volatile boolean v6SmallExperimentalEnabled = false;

    /**
     * A mapping of model keys to their corresponding asset base names. Each entry associates
     * a model key with the base name of the corresponding asset without file extensions or
     * suffixes (e.g., {@code .ort}, {@code _dict.txt}).
     *
     * This map is used internally for resolving assets based on model keys during language
     * processing operations.
     *
     * Immutable and statically initialized for consistent access across the application.
     */
    static final Map<String, String> ASSET_BASENAME;

    /**
     * Immutable list of keys representing supported language model identifiers.
     * Each key in this list corresponds to a specific language or script
     * supported by the Paddle OCR framework.
     *
     * The keys include:
     * - "en": English
     * - "latin": General Latin-based scripts
     * - "eslav": East Slavic languages
     * - "cyrillic": General Cyrillic-based scripts
     * - "arabic": Arabic script
     * - "devanagari": Devanagari script
     * - "th": Thai language
     * - "el": Greek language
     * - "zh": Chinese language
     *
     * This list is unmodifiable to ensure thread-safety and to prevent
     * accidental modifications.
     */
    static final List<String> MODEL_KEYS =
            Collections.unmodifiableList(Arrays.asList(
                    "en", "latin", "eslav", "cyrillic", "arabic", "devanagari", "th", "el", "zh"));

    /**
     * A mapping between language specification codes (e.g., "eng", "deu+eng")
     * and associated model keys used to retrieve language-specific models.
     *
     * This map is used internally to resolve the appropriate model for a given
     * language spec. If a mapping does not exist for a given language spec,
     * fallback handling is applied elsewhere in the application.
     */
    private static final Map<String, String> LANG_TO_MODEL;

    static {
        Map<String, String> base = new HashMap<>();
        base.put("en", "en_PP-OCRv5_mobile_rec");
        base.put("latin", "latin_PP-OCRv5_mobile_rec");
        base.put("eslav", "eslav_PP-OCRv5_mobile_rec");
        base.put("cyrillic", "cyrillic_PP-OCRv5_mobile_rec");
        base.put("arabic", "arabic_PP-OCRv5_mobile_rec");
        base.put("devanagari", "devanagari_PP-OCRv5_mobile_rec");
        base.put("th", "th_PP-OCRv5_mobile_rec");
        base.put("el", "el_PP-OCRv5_mobile_rec");
        base.put("zh", "PP-OCRv5_mobile_rec");
        ASSET_BASENAME = Collections.unmodifiableMap(base);

        Map<String, String> m = new HashMap<>();

        for (String modelKey : MODEL_KEYS) {
            m.put(modelKey, modelKey);
        }

        // en
        m.put("eng", "en");
        m.put("en", "en");

        // latin
        for (String c : new String[] {
                "deu", "ger", "fra", "fre", "spa", "ita", "por", "nld", "dut",
                "dan", "swe", "nor", "fin", "ces", "cze", "pol", "hun", "ron", "rum",
                "tur", "hrv", "slv", "slo", "slk", "cat", "eus", "baq", "glg",
                "lit", "lav", "est", "isl", "ice", "gle", "cym", "wel", "mlt",
                "afr", "ind", "msa", "swa", "tgl", "vie", "lat",
                "de", "fr", "es", "it", "pt", "nl"}) {
            m.put(c, "latin");
        }

        // eslav (Default für rus)
        m.put("rus", "eslav");
        m.put("ru", "eslav");
        m.put("ukr", "eslav");
        m.put("uk", "eslav");
        m.put("bel", "eslav");

        // cyrillic (allgemein)
        m.put("bul", "cyrillic");
        m.put("mkd", "cyrillic");
        m.put("mac", "cyrillic");
        m.put("srp", "cyrillic"); // serbisch (kyrillisch); latein-Variante via "srp_latn"→latin
        m.put("srp_latn", "latin");
        m.put("kaz", "cyrillic");
        m.put("kir", "cyrillic");
        m.put("mon", "cyrillic");

        // arabic
        m.put("ara", "arabic");
        m.put("ar", "arabic");
        m.put("fas", "arabic");
        m.put("per", "arabic");
        m.put("fa", "arabic");
        m.put("urd", "arabic");
        m.put("ur", "arabic");
        m.put("pus", "arabic");

        // devanagari
        m.put("hin", "devanagari");
        m.put("hi", "devanagari");
        m.put("mar", "devanagari");
        m.put("nep", "devanagari");
        m.put("san", "devanagari");

        // th
        m.put("tha", "th");
        m.put("th", "th");

        // el
        m.put("ell", "el");
        m.put("gre", "el");
        m.put("el", "el");

        // zh (generic / multilingual / chinesisch)
        m.put("chi_sim", "zh");
        m.put("chi_tra", "zh");
        m.put("zh", "zh");
        m.put("zh_cn", "zh");
        m.put("zh_tw", "zh");
        m.put("jpn", "zh");
        m.put("ja", "zh");
        m.put("kor", "zh");
        m.put("ko", "zh");

        LANG_TO_MODEL = Collections.unmodifiableMap(m);

        // PP-OCRv6 small: unified Latin/CJK recognition model. Mapping follows the
        // documented language list of the model; everything else stays on v5.
        Set<String> v6 = new LinkedHashSet<>(Arrays.asList(
                // English
                "eng", "en",
                // German, French, Spanish, Italian, Portuguese, Dutch
                "deu", "ger", "de", "fra", "fre", "fr", "spa", "es",
                "ita", "it", "por", "pt", "nld", "dut", "nl",
                // Polish, Czech, Slovak, Hungarian, Romanian
                "pol", "ces", "cze", "slk", "slo", "hun", "ron", "rum",
                // Scandinavian + Finnish
                "dan", "nor", "swe", "fin", "isl", "ice",
                // Turkish, Vietnamese, Indonesian, Malay, Azerbaijani
                "tur", "vie", "ind", "msa", "aze",
                // Other Latin-script languages from the v6 model card
                "afr", "bos", "hrv", "cym", "wel", "est", "gle", "kur",
                "lit", "lav", "mlt", "mri", "mao", "oci", "slv", "sqi", "alb",
                "swa", "tgl", "uzb", "lat", "srp_latn", "cat", "eus", "baq",
                "glg", "ltz", "roh", "que",
                // CJK subset supported by v6 small (NOT Korean)
                "chi_sim", "chi_tra", "zh", "zh_cn", "zh_tw", "jpn", "ja"));
        V6_SMALL_SUPPORTED_LANGS = Collections.unmodifiableSet(v6);
    }

    private static final Pattern TOKEN_SPLITTER = Pattern.compile("[+,;]");

    private PaddleLanguageRouter() {
        // no instances
    }

    /**
     * Resolves the recognition model key corresponding to the provided language specification.
     * The method processes the language specification string, splits it into tokens using
     * delimiters, and maps the tokens to model keys, if applicable. The resolved model key
     * is determined based on a predefined priority order.
     *
     * @param langSpec the language specification string, or {@code null}. The string may include
     *                 multiple languages separated by ',', '+' or ';'.
     * @return the resolved model key as a {@code String}, or {@code null} if no matching model key
     *         is found.
     */
    @Nullable
    static String resolveRecModel(@Nullable String langSpec) {
        if (langSpec == null) return null;
        String s = langSpec.trim();
        if (s.isEmpty()) return null;
        s = s.toLowerCase(Locale.ROOT);

        // Explicit selection of the dedicated PP-OCRv6 small entry (a pseudo "language"
        // exposed in the UI when FEATURE_PADDLE_V6_SMALL is enabled) always routes to the
        // v6 model, independent of the experimental auto-routing flag. The regular v5
        // language selection therefore stays exactly as it is.
        if (MODEL_V6_SMALL.equals(s)) {
            return MODEL_V6_SMALL;
        }

        // Experimental PP-OCRv6 small auto-routing: only when explicitly enabled AND every
        // requested language is covered by the v6 model. This opt-in path is used by the
        // evaluation harnesses (A/B v5 vs. v6); production routing relies on the explicit
        // selection above. Unsupported languages (e.g. rus/ell/ara/fas/hin/tha/kor) never
        // reach v6.
        if (v6SmallExperimentalEnabled && isV6SmallSupported(s)) {
            return MODEL_V6_SMALL;
        }

        // In Tesseract sind '+' Trennzeichen; wir akzeptieren zusätzlich ',' und ';'.
        String[] tokens = TOKEN_SPLITTER.split(s, -1);
        Set<String> hits = new LinkedHashSet<>();
        for (String tok : tokens) {
            String t = tok.trim();
            if (t.isEmpty()) continue;
            String key = LANG_TO_MODEL.get(t);
            if (key != null) {
                hits.add(key);
            }
        }
        if (hits.isEmpty()) return null;
        // Vorrangordnung wie in MODEL_KEYS dokumentiert.
        for (String mk : MODEL_KEYS) {
            if (hits.contains(mk)) return mk;
        }
        // Defensive — sollte nicht erreichbar sein, da hits ⊆ MODEL_KEYS.
        return hits.iterator().next();
    }

    /**
     * Retrieves the base name of an asset associated with the provided model key.
     * The model key is used to look up a predefined mapping of asset base names.
     *
     * @param modelKey the key representing the model, or {@code null} if no key is provided.
     * @return the base name of the asset as a {@code String}, or {@code null} if the model key
     *         is {@code null} or does not exist in the mapping.
     */
    @Nullable
    static String assetBaseName(@Nullable String modelKey) {
        if (modelKey == null) return null;
        return ASSET_BASENAME.get(modelKey);
    }

    /**
     * Enables or disables the experimental PP-OCRv6 small routing.
     * Default is disabled; v5 remains the production path.
     */
    static void setV6SmallExperimentalEnabled(boolean enabled) {
        v6SmallExperimentalEnabled = enabled;
    }

    static boolean isV6SmallExperimentalEnabled() {
        return v6SmallExperimentalEnabled;
    }

    /**
     * Returns {@code true} iff the language spec is non-empty and ALL requested language
     * tokens are supported by the PP-OCRv6 small unified model.
     */
    static boolean isV6SmallSupported(@Nullable String langSpec) {
        if (langSpec == null) return false;
        String s = langSpec.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return false;
        String[] tokens = TOKEN_SPLITTER.split(s, -1);
        boolean any = false;
        for (String tok : tokens) {
            String t = tok.trim();
            if (t.isEmpty()) continue;
            if (!V6_SMALL_SUPPORTED_LANGS.contains(t)) return false;
            any = true;
        }
        return any;
    }
}
