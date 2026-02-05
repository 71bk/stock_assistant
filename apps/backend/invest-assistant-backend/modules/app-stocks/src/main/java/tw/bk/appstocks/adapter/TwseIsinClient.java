package tw.bk.appstocks.adapter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import tw.bk.appcommon.error.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appstocks.config.StockMarketProperties;

@Slf4j
@Component
@RequiredArgsConstructor
public class TwseIsinClient {
    private static final Pattern CODE_NAME_PATTERN = Pattern.compile("^([0-9A-Za-z\\.\\-]+)\\s+(.+)$");
    private static final Pattern CODE_PATTERN = Pattern.compile("^\\d{6}$");

    private final StockMarketProperties properties;

    public List<TwseIsinItem> fetchWarrants() {
        String url = properties.getTwse().getIsinUrl();
        if (url == null || url.isBlank()) {
            return List.of();
        }
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .timeout(15000)
                    .get();
            Element table = doc.selectFirst("table.h4");
            if (table == null) {
                table = doc.selectFirst("table");
            }
            if (table == null) {
                return List.of();
            }
            List<TwseIsinItem> results = new ArrayList<>();
            Elements rows = table.select("tr");
            for (Element row : rows) {
                Elements tds = row.select("td");
                if (tds.size() < 2) {
                    continue;
                }
                String combined = normalizeSpace(tds.get(0).text());
                if (combined == null || combined.isBlank()) {
                    continue;
                }
                if (combined.startsWith("有價證券代號")) {
                    continue;
                }
                Matcher matcher = CODE_NAME_PATTERN.matcher(combined);
                if (!matcher.find()) {
                    continue;
                }
                String code = matcher.group(1);
                String name = matcher.group(2);
                if (code == null || code.isBlank()) {
                    continue;
                }
                String normalizedCode = code.trim();
                if (!CODE_PATTERN.matcher(normalizedCode).matches()) {
                    continue;
                }
                results.add(new TwseIsinItem(normalizedCode, trimOrNull(name)));
            }
            return results;
        } catch (IOException ex) {
            log.error("Failed to fetch TWSE ISIN page: {}", ex.getMessage(), ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "TWSE ISIN fetch failed");
        }
    }

    private String normalizeSpace(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace('\u3000', ' ').trim();
        return normalized.replaceAll("\\s+", " ");
    }

    private String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record TwseIsinItem(String code, String name) {
    }
}
