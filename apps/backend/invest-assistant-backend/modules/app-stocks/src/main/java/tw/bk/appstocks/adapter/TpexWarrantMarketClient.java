package tw.bk.appstocks.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
public class TpexWarrantMarketClient {
    private static final DateTimeFormatter[] DATE_FORMATS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    };
    private static final DateTimeFormatter[] MONTH_FORMATS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("yyyy/MM"),
            DateTimeFormatter.ofPattern("yyyyMM"),
            DateTimeFormatter.ofPattern("yyyy-MM")
    };
    private static final Pattern CODE_PATTERN = Pattern.compile("^\\d{6}$");
    private static final Pattern CODE_EXTRACT_PATTERN = Pattern.compile("\\d{6}");

    private final StockMarketProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public List<TpexWarrantQuote> fetchDailyQuotes() {
        return fetchQuotes("/tpex_warrant_daily_quts", QuoteType.DAILY);
    }

    public List<TpexWarrantQuote> fetchMonthlyQuotes() {
        return fetchQuotes("/tpex_warrant_monthly_quts", QuoteType.MONTHLY);
    }

    private List<TpexWarrantQuote> fetchQuotes(String path, QuoteType type) {
        try {
            String url = buildUrl(path);
            String body = restClient.get().uri(url)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                log.warn("TPEx warrant market response empty: url={}", url);
                return List.of();
            }
            JsonNode root = objectMapper.readTree(body);
            List<JsonNode> items = extractItems(root);
            if (items.isEmpty()) {
                log.warn("TPEx warrant market response has no items: url={}, sample={}",
                        url, summarize(body));
                return List.of();
            }

            List<TpexWarrantQuote> results = new ArrayList<>();
            for (JsonNode node : items) {
                String code = readTextAny(node, "Code", "代號", "證券代號");
                if (code == null || code.isBlank()) {
                    continue;
                }
                String normalizedCode = normalizeCode(code);
                if (normalizedCode == null) {
                    continue;
                }
                LocalDate date = type == QuoteType.MONTHLY
                        ? parseMonth(readTextAny(node, "Date", "日期"))
                        : parseDate(readTextAny(node, "Date", "日期"));
                BigDecimal open = parseDecimal(readTextAny(node, "Open", "開盤價"));
                BigDecimal high = parseDecimal(readTextAny(node, "High", "最高價"));
                BigDecimal low = parseDecimal(readTextAny(node, "Low", "最低價"));
                BigDecimal close = parseDecimal(readTextAny(node, "Close", "收盤價"));
                BigDecimal change = parseDecimal(readTextAny(node, "Change", "漲跌"));
                Long volume = parseLong(readTextAny(node, "TradeVol.", "成交股數", "成交量"));

                if (date == null) {
                    continue;
                }
                results.add(new TpexWarrantQuote(normalizedCode, date, open, high, low, close, change, volume));
            }

            if (results.isEmpty()) {
                log.warn("TPEx warrant market parsed empty results: url={}, sample={}",
                        buildUrl(path), summarize(body));
            }
            return results;
        } catch (RestClientResponseException ex) {
            log.error("Failed to fetch TPEx warrant market: status={}, body={}",
                    ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "TPEx warrant market fetch failed");
        } catch (Exception ex) {
            log.error("Failed to fetch TPEx warrant market", ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "TPEx warrant market fetch failed");
        }
    }

    private String buildUrl(String path) {
        String base = properties.getTpex().getBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/openapi")) {
            base = base + "/v1";
        }
        return base + path;
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
        if (!data.isEmpty() && data.get(0).isObject()) {
            List<JsonNode> list = new ArrayList<>();
            data.forEach(list::add);
            return list;
        }
        if (root.has("fields") && root.get("fields").isArray() && !data.isEmpty()
                && data.get(0).isArray()) {
            List<String> fields = new ArrayList<>();
            root.get("fields").forEach(node -> fields.add(node.asText()));
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

    private String normalizeCode(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().replaceAll("\\s+", "");
        if (CODE_PATTERN.matcher(normalized).matches()) {
            return normalized;
        }
        Matcher matcher = CODE_EXTRACT_PATTERN.matcher(normalized);
        if (matcher.find()) {
            return matcher.group(0);
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
        return null;
    }

    private LocalDate parseMonth(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        for (DateTimeFormatter formatter : MONTH_FORMATS) {
            try {
                YearMonth ym = YearMonth.parse(normalized, formatter);
                return ym.atDay(1);
            } catch (Exception ignore) {
                // try next
            }
        }
        LocalDate fallback = parseDate(normalized);
        return fallback != null ? fallback.withDayOfMonth(1) : null;
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim()
                .replace(",", "")
                .replace("—", "")
                .replace("--", "")
                .replace("N/A", "")
                .replace("n/a", "");
        if (normalized.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(normalized);
        } catch (Exception ignore) {
            return null;
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim()
                .replace(",", "")
                .replace("—", "")
                .replace("--", "")
                .replace("N/A", "")
                .replace("n/a", "");
        if (normalized.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(normalized);
        } catch (Exception ignore) {
            return null;
        }
    }

    private String summarize(String body) {
        if (body == null) {
            return "";
        }
        String normalized = body.replaceAll("\\s+", " ").trim();
        int limit = Math.min(normalized.length(), 300);
        return normalized.substring(0, limit);
    }

    private enum QuoteType {
        DAILY,
        MONTHLY
    }

    public record TpexWarrantQuote(
            String code,
            LocalDate date,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            BigDecimal change,
            Long volume) {
    }
}
