package tw.bk.appauth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {

    @Value("${app.auth.password.argon2.salt-length:16}")
    private int saltLength;

    @Value("${app.auth.password.argon2.hash-length:32}")
    private int hashLength;

    @Value("${app.auth.password.argon2.parallelism:1}")
    private int parallelism;

    @Value("${app.auth.password.argon2.memory-kib:19456}")
    private int memoryKib;

    @Value("${app.auth.password.argon2.iterations:2}")
    private int iterations;

    @Bean
    public PasswordEncoder passwordEncoder() {
        // OWASP base minimum profile (Argon2id): m=19MiB, t=2, p=1.
        return new Argon2PasswordEncoder(
                saltLength,
                hashLength,
                parallelism,
                memoryKib,
                iterations);
    }
}

