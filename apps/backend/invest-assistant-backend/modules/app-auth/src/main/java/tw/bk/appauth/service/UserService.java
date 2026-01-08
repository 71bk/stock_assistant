package tw.bk.appauth.service;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.apppersistence.entity.UserEntity;
import tw.bk.apppersistence.repository.UserRepository;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserEntity upsertGoogleUser(String googleSub, String email, String displayName) {
        Optional<UserEntity> existing = userRepository.findByGoogleSub(googleSub);
        UserEntity user = existing.orElseGet(UserEntity::new);
        user.setGoogleSub(googleSub);
        user.setEmail(email);
        user.setDisplayName(displayName);
        if (user.getStatus() == null) {
            user.setStatus("ACTIVE");
        }
        return userRepository.save(user);
    }

    public Optional<UserEntity> findById(Long userId) {
        return userRepository.findById(userId);
    }
}
