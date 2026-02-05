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
import tw.bk.appcommon.error.ErrorCode;
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

    private final StockMarketProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public List<TpexWarrantItem> fetchWarrants() {
        try {
            String url = buildUrl("/tpex_warrant");
            String body = restClient.get().uri(url)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                return List.of();
            }
            JsonNode root = objectMapper.readTree(body);
            List<JsonNode> items = extractItems(root);
            List<TpexWarrantItem> results = new ArrayList<>();
            for (JsonNode node : items) {
                String code = readText(node, "Code");
                if (code == null || code.isBlank()) {
                    continue;
                }
                String name = readText(node, "Name");
                String underlying = readText(node, "UnderlyingCode");
                LocalDate expiry = parseDate(readText(node, "ExpirationDate"));
                results.add(new TpexWarrantItem(code.trim(), trimOrNull(name), trimOrNull(underlying), expiry));
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
        if (root.has("data") && root.get("data").isArray()) {
            List<JsonNode> list = new ArrayList<>();
            root.get("data").forEach(list::add);
            return list;
        }
        return List.of();
    }

    private String buildUrl(String path) {
        String base = properties.getTpex().getBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
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

    public record TpexWarrantItem(String code, String name, String underlyingSymbol, LocalDate expiryDate) {
    }
}
