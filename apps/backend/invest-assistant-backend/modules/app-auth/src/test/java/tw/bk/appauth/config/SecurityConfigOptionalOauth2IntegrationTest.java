package tw.bk.appauth.config;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.bk.appauth.jwt.JwtTokenService;
import tw.bk.appauth.security.JwtAuthenticationFilter;

@SpringBootTest(
        classes = SecurityConfigOptionalOauth2IntegrationTest.TestApplication.class,
        properties = {
                "app.security.csrf.enabled=false",
                "app.cors.allowed-origins=http://localhost:5173"
        })
@AutoConfigureMockMvc
class SecurityConfigOptionalOauth2IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldAllowAuthenticatedRequestWithoutOauth2ClientRegistration() throws Exception {
        mockMvc.perform(get("/test/ping")
                        .with(user("tester")))
                .andExpect(status().isOk());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class
    })
    @Import({
            SecurityConfig.class,
            SecurityConfigOptionalOauth2IntegrationTest.TestController.class,
            SecurityConfigOptionalOauth2IntegrationTest.SecurityTestBeans.class
    })
    static class TestApplication {
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {
        @GetMapping("/ping")
        ResponseEntity<Void> ping() {
            return ResponseEntity.ok().build();
        }
    }

    @TestConfiguration
    static class SecurityTestBeans {
        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            return new JwtAuthenticationFilter(mock(JwtTokenService.class), new AuthProperties());
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
