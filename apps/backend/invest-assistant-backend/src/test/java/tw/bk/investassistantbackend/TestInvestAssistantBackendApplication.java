package tw.bk.investassistantbackend;

import org.springframework.boot.SpringApplication;

public class TestInvestAssistantBackendApplication {

    public static void main(String[] args) {
        SpringApplication.from(InvestAssistantBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
