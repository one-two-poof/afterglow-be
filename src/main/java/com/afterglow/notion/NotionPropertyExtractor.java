package com.afterglow.notion;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

public final class NotionPropertyExtractor {

    private NotionPropertyExtractor() {
    }

    /** Notion DB 컬럼 전체를 API 응답용 단순 문자열 맵으로 변환 */
    public static Map<String, String> extractAllAsMap(JsonNode properties) {
        Map<String, String> result = new LinkedHashMap<>();
        if (properties == null || !properties.isObject()) {
            return result;
        }
        properties.fields().forEachRemaining(entry -> {
            String value = extractSimpleValue(entry.getValue());
            result.put(entry.getKey(), value != null ? value : "");
        });
        return result;
    }

    private static String extractSimpleValue(JsonNode property) {
        if (property == null || property.isMissingNode()) {
            return "";
        }
        return switch (property.path("type").asText()) {
            case "title" -> joinPlainText(property.path("title"));
            case "rich_text" -> joinPlainText(property.path("rich_text"));
            case "select" -> {
                JsonNode select = property.path("select");
                yield select.isNull() || select.isMissingNode() ? "" : select.path("name").asText("");
            }
            case "multi_select" -> {
                JsonNode options = property.path("multi_select");
                if (!options.isArray()) {
                    yield "";
                }
                yield StreamSupport.stream(options.spliterator(), false)
                        .map(node -> node.path("name").asText(""))
                        .filter(name -> !name.isBlank())
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
            }
            case "number" -> {
                JsonNode number = property.path("number");
                yield number.isNull() ? "" : number.asText();
            }
            case "checkbox" -> String.valueOf(property.path("checkbox").asBoolean(false));
            case "url" -> property.path("url").asText("");
            case "email" -> property.path("email").asText("");
            case "phone_number" -> property.path("phone_number").asText("");
            case "date" -> {
                JsonNode date = property.path("date");
                if (date.isNull() || date.isMissingNode()) {
                    yield "";
                }
                String start = date.path("start").asText("");
                String end = date.path("end").asText("");
                yield end.isBlank() ? start : start + " ~ " + end;
            }
            default -> "";
        };
    }

    public static String extractTitle(JsonNode properties, String propertyName) {
        JsonNode property = properties.path(propertyName);
        if (property.isMissingNode() || property.isNull()) {
            return "";
        }
        return switch (property.path("type").asText()) {
            case "title" -> joinPlainText(property.path("title"));
            case "rich_text" -> joinPlainText(property.path("rich_text"));
            default -> "";
        };
    }

    public static String extractSelectName(JsonNode properties, String propertyName) {
        JsonNode property = properties.path(propertyName);
        if (property.isMissingNode() || property.isNull()) {
            return null;
        }
        if (!"select".equals(property.path("type").asText())) {
            return null;
        }
        JsonNode select = property.path("select");
        if (select.isMissingNode() || select.isNull()) {
            return null;
        }
        return select.path("name").asText(null);
    }

    private static String joinPlainText(JsonNode richTextArray) {
        if (!richTextArray.isArray()) {
            return "";
        }
        return StreamSupport.stream(richTextArray.spliterator(), false)
                .map(node -> node.path("plain_text").asText(""))
                .reduce("", String::concat)
                .trim();
    }
}
