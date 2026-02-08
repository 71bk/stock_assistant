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
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appstocks.config.StockMarketProperties;

@Slf4j
@Component
@RequiredArgsConstructor
public class TwseIsinClient {
    private static final Pattern CODE_NAME_PATTERN = Pattern.compile("^([0-9A-Za-z\\.\\-]+)\\s+(.+)$");
    private static final Pattern CODE_PATTERN = Pattern.compile("^\\d{6}$");
    private static final Pattern CODE_EXTRACT_PATTERN = Pattern.compile("\\d{6}");

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
            Elements tables = doc.select("table");
            if (tables == null || tables.isEmpty()) {
                log.warn("TWSE ISIN table not found: url={}", url);
                return List.of();
            }

            for (Element table : tables) {
                List<TwseIsinItem> results = new ArrayList<>();
                Elements rows = table.select("tr");
                for (Element row : rows) {
                    Elements tds = row.select("td");
                    if (tds.size() < 2) {
                        continue;
                    }

                    String code = null;
                    String name = null;

                    for (int i = 0; i < tds.size(); i++) {
                        String cell = normalizeSpace(tds.get(i).text());
                        if (cell == null || cell.isBlank()) {
                            continue;
                        }
                        Matcher matcher = CODE_NAME_PATTERN.matcher(cell);
                        if (matcher.find() && CODE_PATTERN.matcher(matcher.group(1)).matches()) {
                            code = matcher.group(1);
                            name = matcher.group(2);
                            break;
                        }
                        Matcher codeMatcher = CODE_EXTRACT_PATTERN.matcher(cell);
                        if (codeMatcher.find()) {
                            code = codeMatcher.group(0);
                            String maybeName = cell.replace(code, "").trim();
                            if (!maybeName.isBlank()) {
                                name = maybeName;
                            } else if (i + 1 < tds.size()) {
                                name = normalizeSpace(tds.get(i + 1).text());
                            }
                            break;
                        }
                    }

                    if (code == null || code.isBlank()) {
                        continue;
                    }
                    String normalizedCode = code.trim();
                    if (!CODE_PATTERN.matcher(normalizedCode).matches()) {
                        continue;
                    }
                    results.add(new TwseIsinItem(normalizedCode, trimOrNull(name)));
                }
                if (!results.isEmpty()) {
                    return results;
                }
            }

            log.warn("TWSE ISIN returned empty results: url={}", url);
            return List.of();
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
