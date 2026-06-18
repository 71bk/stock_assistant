package tw.bk.appocr.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OcrPdfPasswordVault {
    private final ConcurrentMap<Key, Entry> passwords = new ConcurrentHashMap<>();
    private final Duration ttl;

    public OcrPdfPasswordVault(@Value("${app.ocr.pdf-password.ttl:5m}") Duration ttl) {
        this.ttl = ttl == null || ttl.isZero() || ttl.isNegative() ? Duration.ofMinutes(5) : ttl;
    }

    public void put(Long userId, Long jobId, String password) {
        if (userId == null || jobId == null || password == null || password.isBlank()) {
            return;
        }
        passwords.put(new Key(userId, jobId), new Entry(password, Instant.now().plus(ttl)));
    }

    public Optional<String> consume(Long userId, Long jobId) {
        if (userId == null || jobId == null) {
            return Optional.empty();
        }
        Entry entry = passwords.remove(new Key(userId, jobId));
        if (entry == null || entry.isExpired()) {
            return Optional.empty();
        }
        return Optional.of(entry.password());
    }

    public boolean contains(Long userId, Long jobId) {
        if (userId == null || jobId == null) {
            return false;
        }
        Key key = new Key(userId, jobId);
        Entry entry = passwords.get(key);
        if (entry == null) {
            return false;
        }
        if (entry.isExpired()) {
            passwords.remove(key, entry);
            return false;
        }
        return true;
    }

    public void discard(Long userId, Long jobId) {
        if (userId != null && jobId != null) {
            passwords.remove(new Key(userId, jobId));
        }
    }

    private record Key(Long userId, Long jobId) {
    }

    private record Entry(String password, Instant expiresAt) {
        private boolean isExpired() {
            return !expiresAt.isAfter(Instant.now());
        }
    }
}
