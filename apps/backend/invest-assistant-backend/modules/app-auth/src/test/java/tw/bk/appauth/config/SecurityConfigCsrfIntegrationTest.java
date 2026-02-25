package tw.bk.appauth.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
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
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.bk.appauth.jwt.JwtTokenService;
import tw.bk.appauth.security.JwtAuthenticationFilter;
import tw.bk.appauth.security.OAuth2LoginSuccessHandler;

@SpringBootTest(
        classes = SecurityConfigCsrfIntegrationTest.TestApplication.class,
        properties = {
                "app.security.csrf.enabled=true",
                "app.cors.allowed-origins=http://localhost:5173"
        })
@AutoConfigureMockMvc
class SecurityConfigCsrfIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void postWithoutCsrf_shouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/test/write")
                        .with(user("tester")))
                .andExpect(status().isForbidden());
    }

    @Test
    void postWithCookieAndHeaderCsrf_shouldReturnOk() throws Exception {
        MvcResult tokenBootstrap = mockMvc.perform(get("/test/token")
                        .with(user("tester")))
                .andExpect(status().isOk())
                .andReturn();

        Cookie xsrfCookie = tokenBootstrap.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(xsrfCookie);

        mockMvc.perform(post("/test/write")
                        .with(user("tester"))
                        .cookie(xsrfCookie)
                        .header("X-XSRF-TOKEN", xsrfCookie.getValue()))
                .andExpect(status().isOk());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class
    })
    @Import({
            SecurityConfig.class,
            SecurityConfigCsrfIntegrationTest.TestWriteController.class,
            SecurityConfigCsrfIntegrationTest.SecurityTestBeans.class
    })
    static class TestApplication {
    }

    @RestController
    @RequestMapping("/test")
    static class TestWriteController {
        @GetMapping("/token")
        ResponseEntity<Void> token() {
            return ResponseEntity.ok().build();
        }

        @PostMapping("/write")
        ResponseEntity<Void> write() {
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

        @Bean
        OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler() {
            return mock(OAuth2LoginSuccessHandler.class);
        }

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return mock(ClientRegistrationRepository.class);
        }

        @Bean
        OAuth2AuthorizedClientService oAuth2AuthorizedClientService() {
            return mock(OAuth2AuthorizedClientService.class);
        }

        @Bean
        OAuth2AuthorizedClientRepository oAuth2AuthorizedClientRepository() {
            return mock(OAuth2AuthorizedClientRepository.class);
        }
    }
}
