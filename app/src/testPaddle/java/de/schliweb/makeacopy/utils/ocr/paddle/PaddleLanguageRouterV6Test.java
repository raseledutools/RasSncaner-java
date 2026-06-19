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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit-Test für das experimentelle PP-OCRv6-small-Routing in {@link PaddleLanguageRouter}.
 *
 * Erwartetes Verhalten:
 * - Default: Flag aus → v5-Routing bleibt vollständig unverändert.
 * - Flag an: nur Sprachen, die das v6-small-Modell laut Modellkarte unterstützt, werden auf
 *   {@link PaddleLanguageRouter#MODEL_V6_SMALL} geroutet.
 * - Nicht unterstützte Sprachen (rus, ell, ara, fas, hin, tha, kor) bleiben auch mit aktivem
 *   Flag auf den v5-Spezialmodellen.
 */
public class PaddleLanguageRouterV6Test {

    @Before
    public void resetFlag() {
        PaddleLanguageRouter.setV6SmallExperimentalEnabled(false);
    }

    @After
    public void disableFlag() {
        PaddleLanguageRouter.setV6SmallExperimentalEnabled(false);
    }

    @Test
    public void v6_disabled_by_default() {
        assertFalse(PaddleLanguageRouter.isV6SmallExperimentalEnabled());
        assertEquals("en", PaddleLanguageRouter.resolveRecModel("eng"));
        assertEquals("latin", PaddleLanguageRouter.resolveRecModel("deu"));
        assertEquals("zh", PaddleLanguageRouter.resolveRecModel("chi_sim"));
    }

    @Test
    public void v6_enabled_routes_supported_languages() {
        PaddleLanguageRouter.setV6SmallExperimentalEnabled(true);
        for (String lang : new String[] {
                "eng", "deu", "fra", "spa", "ita", "por", "nld", "pol", "ces", "slk",
                "hun", "ron", "dan", "nor", "swe", "tur", "chi_sim", "chi_tra", "jpn"}) {
            assertEquals(
                    "lang=" + lang,
                    PaddleLanguageRouter.MODEL_V6_SMALL,
                    PaddleLanguageRouter.resolveRecModel(lang));
        }
        assertEquals(
                PaddleLanguageRouter.MODEL_V6_SMALL,
                PaddleLanguageRouter.resolveRecModel("deu+eng"));
    }

    @Test
    public void v6_enabled_keeps_unsupported_languages_on_v5() {
        PaddleLanguageRouter.setV6SmallExperimentalEnabled(true);
        assertEquals("eslav", PaddleLanguageRouter.resolveRecModel("rus"));
        assertEquals("el", PaddleLanguageRouter.resolveRecModel("ell"));
        assertEquals("arabic", PaddleLanguageRouter.resolveRecModel("ara"));
        assertEquals("arabic", PaddleLanguageRouter.resolveRecModel("fas"));
        assertEquals("devanagari", PaddleLanguageRouter.resolveRecModel("hin"));
        assertEquals("th", PaddleLanguageRouter.resolveRecModel("tha"));
        assertEquals("zh", PaddleLanguageRouter.resolveRecModel("kor"));
    }

    @Test
    public void v6_enabled_mixed_spec_with_unsupported_falls_back_to_v5() {
        PaddleLanguageRouter.setV6SmallExperimentalEnabled(true);
        // rus ist nicht v6-fähig → gesamter Spec bleibt auf v5-Routing.
        assertEquals("en", PaddleLanguageRouter.resolveRecModel("eng+rus"));
        assertEquals("latin", PaddleLanguageRouter.resolveRecModel("deu+ara"));
    }

    @Test
    public void isV6SmallSupported_matrix() {
        assertTrue(PaddleLanguageRouter.isV6SmallSupported("eng"));
        assertTrue(PaddleLanguageRouter.isV6SmallSupported("deu+eng"));
        assertFalse(PaddleLanguageRouter.isV6SmallSupported("rus"));
        assertFalse(PaddleLanguageRouter.isV6SmallSupported("eng+rus"));
        assertFalse(PaddleLanguageRouter.isV6SmallSupported(""));
        assertFalse(PaddleLanguageRouter.isV6SmallSupported(null));
    }

    @Test
    public void explicit_v6_entry_routes_to_v6_regardless_of_flag() {
        // The dedicated v6 entry (selected explicitly in the UI) must always route to v6,
        // independent of the experimental auto-routing flag.
        PaddleLanguageRouter.setV6SmallExperimentalEnabled(false);
        assertEquals(
                PaddleLanguageRouter.MODEL_V6_SMALL,
                PaddleLanguageRouter.resolveRecModel(PaddleLanguageRouter.MODEL_V6_SMALL));
        PaddleLanguageRouter.setV6SmallExperimentalEnabled(true);
        assertEquals(
                PaddleLanguageRouter.MODEL_V6_SMALL,
                PaddleLanguageRouter.resolveRecModel(PaddleLanguageRouter.MODEL_V6_SMALL));
    }

    @Test
    public void v5_selection_unchanged_when_autorouting_disabled() {
        // With auto-routing disabled (production default), every regular language selection keeps
        // using the v5 models — only the explicit v6 entry reaches v6.
        PaddleLanguageRouter.setV6SmallExperimentalEnabled(false);
        assertEquals("en", PaddleLanguageRouter.resolveRecModel("eng"));
        assertEquals("latin", PaddleLanguageRouter.resolveRecModel("deu"));
        assertEquals("zh", PaddleLanguageRouter.resolveRecModel("chi_sim"));
    }

    @Test
    public void v6_model_key_resolves_assets_via_v6_branch() {
        assertTrue(PaddleAssets.isV6Model(PaddleLanguageRouter.MODEL_V6_SMALL));
        assertFalse(PaddleAssets.isV6Model("latin"));
        assertFalse(PaddleAssets.isV6Model(null));
    }
}
