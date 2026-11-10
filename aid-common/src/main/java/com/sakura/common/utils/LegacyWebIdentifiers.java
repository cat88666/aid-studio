package com.sakura.common.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.net.URI;
import java.util.ArrayList;
import java.util.Set;

/** Boundary-only compatibility for the retained upstream Web client; stored identifiers stay Sakura. */
public final class LegacyWebIdentifiers {
    private static final Set<String> AGENT_FIELDS = Set.of("agentCode", "agentCodes", "agentPool");
    private static final String[] MARKERS = {"SELECTION_V1", "SELECTIONS_V2", "COMMENT_V1", "REPLACEMENTS_V1"};
    private LegacyWebIdentifiers() { }

    public static String agentCode(String code) {
        return code != null && code.startsWith("aid_") ? "sakura_" + code.substring(4) : code;
    }

    public static boolean isLegacyWeb(String path, String referer, String host) {
        if (path != null && path.startsWith("/aid/agent/")) return true;
        if (referer == null || host == null) return false;
        try {
            URI source = URI.create(referer), target = URI.create("http://" + host);
            return ("http".equals(source.getScheme()) || "https".equals(source.getScheme()))
                    && source.getHost() != null && source.getHost().equalsIgnoreCase(target.getHost())
                    && source.getPort() == target.getPort()
                    && ("/web".equals(source.getPath()) || source.getPath().startsWith("/web/"));
        } catch (IllegalArgumentException ignored) { return false; }
    }

    public static JsonNode request(JsonNode value) { return transform(value, false, false); }
    public static JsonNode response(JsonNode value) { return transform(value, true, false); }

    private static JsonNode transform(JsonNode value, boolean legacyOutput, boolean agentField) {
        if (value == null) return null;
        if (value.isTextual()) {
            String text = value.textValue();
            if (agentField) {
                if (legacyOutput && text.startsWith("sakura_")) text = "aid_" + text.substring(7);
                else if (!legacyOutput) text = agentCode(text);
            }
            String from = legacyOutput ? "SAKURA" : "AID", to = legacyOutput ? "AID" : "SAKURA";
            text = text.replace("[" + from + "_MODE:SCREENPLAY]", "[" + to + "_MODE:SCREENPLAY]");
            for (String marker : MARKERS) {
                text = text.replace("[[" + from + "_SCRIPT_" + marker + "]]", "[[" + to + "_SCRIPT_" + marker + "]]")
                        .replace("[[/" + from + "_SCRIPT_" + marker + "]]", "[[/" + to + "_SCRIPT_" + marker + "]]");
            }
            return TextNode.valueOf(text);
        }
        if (value instanceof ObjectNode object) {
            var fields = new ArrayList<String>(); object.fieldNames().forEachRemaining(fields::add);
            for (String field : fields) object.set(field, transform(object.get(field), legacyOutput,
                    agentField || AGENT_FIELDS.contains(field)));
        } else if (value instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) array.set(i, transform(array.get(i), legacyOutput, agentField));
        }
        return value;
    }
}
