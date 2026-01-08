package tw.bk.appbootstrap.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tw.bk.appcommon.id.IdGenerator;
import tw.bk.appcommon.id.UuidIdGenerator;
import tw.bk.appcommon.time.ClockProvider;
import tw.bk.appcommon.time.SystemClockProvider;

@Configuration
public class CommonBeansConfig {

    @Bean
    public ClockProvider clockProvider() {
        return new SystemClockProvider();
    }

    @Bean
    public IdGenerator idGenerator() {
        return new UuidIdGenerator();
    }
}
