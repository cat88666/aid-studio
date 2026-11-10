package com.sakura.compat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakura.common.utils.LegacyWebIdentifiers;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyWebCompatibilityTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void normalizesOnlyAgentIdentifiersAndExactScriptMarkers() throws Exception {
        var input = mapper.readTree("""
            {"agentCode":"aid_visual_director","agentCodes":{"scene":"aid_scene_builder"},
             "prompt":"AID user prose [[AID_SCRIPT_SELECTION_V1]]x[[/AID_SCRIPT_SELECTION_V1]]",
             "modelCode":"aid_custom_model","skillCode":"screenplay","other":"aid_keep"}
            """);
        var normalized = LegacyWebIdentifiers.request(input.deepCopy());
        assertEquals("sakura_visual_director", normalized.get("agentCode").asText());
        assertEquals("sakura_scene_builder", normalized.at("/agentCodes/scene").asText());
        assertEquals("AID user prose [[SAKURA_SCRIPT_SELECTION_V1]]x[[/SAKURA_SCRIPT_SELECTION_V1]]", normalized.get("prompt").asText());
        assertEquals(input.get("modelCode"), normalized.get("modelCode"));
        assertEquals(input.get("skillCode"), normalized.get("skillCode"));
        assertEquals(input.get("other"), normalized.get("other"));
        assertEquals(input, LegacyWebIdentifiers.response(normalized.deepCopy()));
        assertEquals(normalized, LegacyWebIdentifiers.request(normalized.deepCopy()));
    }

    @Test void legacyResponseRequiresOldEndpointOrSameHostWebReferer() {
        assertTrue(LegacyWebIdentifiers.isLegacyWeb("/aid/agent/list", null, null));
        assertTrue(LegacyWebIdentifiers.isLegacyWeb("/api/user/skill/execution/run/detail", "https://studio.example/web/works/", "studio.example"));
        assertFalse(LegacyWebIdentifiers.isLegacyWeb("/api/user/skill/execution/run/detail", "https://other.example/web/", "studio.example"));
        assertFalse(LegacyWebIdentifiers.isLegacyWeb("/api/user/skill/execution/run/detail", "https://studio.example/", "studio.example"));
        assertFalse(LegacyWebIdentifiers.isLegacyWeb("/api/user/skill/execution/run/detail", null, "studio.example"));
        assertFalse(LegacyWebIdentifiers.isLegacyWeb("/api/user/skill/execution/run/detail", "https://studio.example.evil/web/", "studio.example"));
    }

    @Test void canonicalAndNullAgentCodesRemainStable() {
        assertNull(LegacyWebIdentifiers.agentCode(null));
        assertEquals("sakura_director", LegacyWebIdentifiers.agentCode("sakura_director"));
        assertEquals("paid_provider", LegacyWebIdentifiers.agentCode("paid_provider"));
    }
}
