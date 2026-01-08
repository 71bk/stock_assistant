package tw.bk.appbootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "tw.bk")
@EnableJpaRepositories(basePackages = "tw.bk.apppersistence.repository")
@EntityScan(basePackages = "tw.bk.apppersistence.entity")
public class AppBootstrapApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppBootstrapApplication.class, args);
    }

}
