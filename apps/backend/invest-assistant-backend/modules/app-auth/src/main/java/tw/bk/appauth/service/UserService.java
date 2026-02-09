package tw.bk.appauth.service;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.appauth.model.UserView;
import tw.bk.apppersistence.entity.UserEntity;
import tw.bk.apppersistence.repository.UserRepository;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserEntity upsertGoogleUser(String googleSub, String email, String displayName, String pictureUrl) {
        Optional<UserEntity> existing = userRepository.findByGoogleSub(googleSub);
        UserEntity user = existing.orElseGet(UserEntity::new);
        user.setGoogleSub(googleSub);
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setPictureUrl(pictureUrl);
        user.setLastLoginAt(java.time.LocalDateTime.now());
        if (user.getStatus() == null) {
            user.setStatus("ACTIVE");
        }
        return userRepository.save(user);
    }

    public Optional<UserView> findById(Long userId) {
        return userRepository.findById(userId).map(this::toView);
    }

    private UserView toView(UserEntity entity) {
        return new UserView(
                entity.getId(),
                entity.getEmail(),
                entity.getDisplayName(),
                entity.getPictureUrl());
    }
}
