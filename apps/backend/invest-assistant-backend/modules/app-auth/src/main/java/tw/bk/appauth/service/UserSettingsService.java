package tw.bk.appauth.service;

import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.apppersistence.entity.UserSettingsEntity;
import tw.bk.apppersistence.repository.UserSettingsRepository;

@Service
public class UserSettingsService {
    private static final String DEFAULT_BASE_CURRENCY = "TWD";
    private static final String DEFAULT_DISPLAY_TIMEZONE = "Asia/Taipei";

    private final UserSettingsRepository userSettingsRepository;

    public UserSettingsService(UserSettingsRepository userSettingsRepository) {
        this.userSettingsRepository = userSettingsRepository;
    }

    @Transactional
    public UserSettingsEntity getOrCreate(Long userId) {
        return userSettingsRepository.findById(userId)
                .orElseGet(() -> {
                    UserSettingsEntity settings = new UserSettingsEntity();
                    settings.setUserId(userId);
                    settings.setBaseCurrency(DEFAULT_BASE_CURRENCY);
                    settings.setDisplayTimezone(DEFAULT_DISPLAY_TIMEZONE);
                    return userSettingsRepository.save(settings);
                });
    }

    @Transactional
    public UserSettingsEntity update(Long userId, String baseCurrency, String displayTimezone) {
        UserSettingsEntity settings = getOrCreate(userId);
        if (baseCurrency != null && !baseCurrency.isBlank()) {
            settings.setBaseCurrency(baseCurrency.trim().toUpperCase(Locale.ROOT));
        }
        if (displayTimezone != null && !displayTimezone.isBlank()) {
            settings.setDisplayTimezone(displayTimezone.trim());
        }
        return userSettingsRepository.save(settings);
    }
}
