package tw.bk.appstocks.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appstocks.config.StockMarketProperties;

@Slf4j
@Component
@RequiredArgsConstructor
public class TpexWarrantClient {
    private static final DateTimeFormatter[] DATE_FORMATS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    };
    private static final java.util.regex.Pattern CODE_PATTERN = java.util.regex.Pattern.compile("^\\d{6}$");
    private static final java.util.regex.Pattern CODE_EXTRACT_PATTERN = java.util.regex.Pattern.compile("\\d{6}");

    private final StockMarketProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public List<TpexWarrantItem> fetchWarrants() {
        try {
            String url = buildUrl("/tpex_warrant_issue");
            String body = restClient.get().uri(url)
                    .header("accept", "application/json")
                    .header("If-Modified-Since", "Mon, 26 Jul 1997 05:00:00 GMT")
                    .header("Cache-Control", "no-cache")
                    .header("Pragma", "no-cache")
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                return List.of();
            }
            JsonNode root = objectMapper.readTree(body);
            List<JsonNode> items = extractItems(root);
            List<TpexWarrantItem> results = new ArrayList<>();
            if (items.isEmpty()) {
                log.warn("TPEx warrant response has no items: url={}, sample={}",
                        url, summarize(body));
            }
            for (JsonNode node : items) {
                String code = readTextAny(node, "Code", "代號", "證券代號", "WarrantCode");
                if (code == null || code.isBlank()) {
                    continue;
                }
                String normalizedCode = code.trim().replaceAll("\\s+", "");
                if (!CODE_PATTERN.matcher(normalizedCode).matches()) {
                    java.util.regex.Matcher matcher = CODE_EXTRACT_PATTERN.matcher(normalizedCode);
                    if (matcher.find()) {
                        normalizedCode = matcher.group(0);
                    } else {
                        continue;
                    }
                }
                String name = readTextAny(node, "Name", "名稱", "證券名稱");
                String underlying = readTextAny(node,
                        "UnderlyingStockCode",
                        "UnderlyingCode",
                        "標的證券代號",
                        "標的代號");
                if (underlying == null || underlying.isBlank()) {
                    underlying = readTextAny(node, "UnderlyingStock", "標的證券", "標的");
                }
                LocalDate expiry = parseDate(readTextAny(node, "ExpiryDate", "ExpirationDate", "到期日"));
                results.add(new TpexWarrantItem(normalizedCode, trimOrNull(name), trimOrNull(underlying), expiry));
            }
            if (results.isEmpty()) {
                log.warn("TPEx warrant returned empty results: url={}, sample={}",
                        url, summarize(body));
            }
            return results;
        } catch (RestClientResponseException ex) {
            log.error("Failed to fetch TPEx warrants: status={}, body={}",
                    ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "TPEx warrant fetch failed");
        } catch (Exception ex) {
            log.error("Failed to fetch TPEx warrants", ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "TPEx warrant fetch failed");
        }
    }

    private List<JsonNode> extractItems(JsonNode root) {
        if (root == null || root.isNull()) {
            return List.of();
        }
        if (root.isArray()) {
            List<JsonNode> list = new ArrayList<>();
            root.forEach(list::add);
            return list;
        }
        List<JsonNode> fromData = extractFromData(root, "data");
        if (!fromData.isEmpty()) {
            return fromData;
        }
        if (root.has("data") && root.get("data").isObject()) {
            List<JsonNode> fromNested = extractFromData(root.get("data"), "data");
            if (!fromNested.isEmpty()) {
                return fromNested;
            }
        }
        List<JsonNode> fromUpperData = extractFromData(root, "Data");
        if (!fromUpperData.isEmpty()) {
            return fromUpperData;
        }
        if (root.has("aaData") && root.get("aaData").isArray()) {
            List<JsonNode> list = new ArrayList<>();
            root.get("aaData").forEach(list::add);
            return list;
        }
        return List.of();
    }

    private List<JsonNode> extractFromData(JsonNode root, String field) {
        if (!root.has(field) || !root.get(field).isArray()) {
            return List.of();
        }
        JsonNode data = root.get(field);
        // Case 1: array of objects
        if (!data.isEmpty() && data.get(0).isObject()) {
            List<JsonNode> list = new ArrayList<>();
            data.forEach(list::add);
            return list;
        }
        // Case 2: array of arrays with "fields" metadata
        if (root.has("fields") && root.get("fields").isArray() && !data.isEmpty()
                && data.get(0).isArray()) {
            List<String> fields = new ArrayList<>();
            root.get("fields").forEach(node -> fields.add(extractFieldName(node)));
            List<JsonNode> list = new ArrayList<>();
            data.forEach(row -> list.add(toObjectNode(fields, row)));
            return list;
        }
        return List.of();
    }

    private JsonNode toObjectNode(List<String> fields, JsonNode row) {
        var obj = objectMapper.createObjectNode();
        int size = Math.min(fields.size(), row.size());
        for (int i = 0; i < size; i++) {
            String key = fields.get(i);
            JsonNode value = row.get(i);
            if (key == null || key.isBlank() || value == null || value.isNull()) {
                continue;
            }
            obj.set(key, value);
        }
        return obj;
    }

    private String extractFieldName(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isObject()) {
            if (node.has("name")) {
                return node.get("name").asText();
            }
            if (node.has("field")) {
                return node.get("field").asText();
            }
            if (node.has("key")) {
                return node.get("key").asText();
            }
        }
        return node.asText();
    }

    private String buildUrl(String path) {
        String base = properties.getTpex().getBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        // Normalize to OpenAPI v1 base
        if (base.endsWith("/openapi")) {
            base = base + "/v1";
        }
        return base + path;
    }

    private String readText(JsonNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private String readTextAny(JsonNode node, String... fields) {
        if (fields == null) {
            return null;
        }
        for (String field : fields) {
            String text = readText(node, field);
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(normalized, formatter);
            } catch (Exception ignore) {
                // try next
            }
        }
        LocalDate roc = parseRocDate(normalized);
        return roc;
    }

    private LocalDate parseRocDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().replace('-', '/');
        try {
            if (normalized.matches("\\d{3}/\\d{2}/\\d{2}")) {
                String[] parts = normalized.split("/");
                int year = Integer.parseInt(parts[0]) + 1911;
                int month = Integer.parseInt(parts[1]);
                int day = Integer.parseInt(parts[2]);
                return LocalDate.of(year, month, day);
            }
            if (normalized.matches("\\d{7}")) {
                int year = Integer.parseInt(normalized.substring(0, 3)) + 1911;
                int month = Integer.parseInt(normalized.substring(3, 5));
                int day = Integer.parseInt(normalized.substring(5, 7));
                return LocalDate.of(year, month, day);
            }
        } catch (Exception ignore) {
            // ignore invalid roc date
        }
        return null;
    }

    private String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String summarize(String body) {
        if (body == null) {
            return "";
        }
        String normalized = body.replaceAll("\\s+", " ").trim();
        int limit = Math.min(normalized.length(), 300);
        return normalized.substring(0, limit);
    }

    public record TpexWarrantItem(String code, String name, String underlyingSymbol, LocalDate expiryDate) {
    }
}
