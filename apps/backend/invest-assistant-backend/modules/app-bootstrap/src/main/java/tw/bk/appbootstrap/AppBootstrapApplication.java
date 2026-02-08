package tw.bk.appbootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication(scanBasePackages = "tw.bk")
@EnableJpaRepositories(basePackages = "tw.bk.apppersistence.repository")
@EntityScan(basePackages = "tw.bk.apppersistence.entity")
@EnableScheduling
@EnableCaching
public class AppBootstrapApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppBootstrapApplication.class, args);
    }

}
