package at.querchecker.auth;

import at.querchecker.auth.dto.AuthStatusDto;
import at.querchecker.auth.dto.LoginResponseDto;
import at.querchecker.config.SecurityConfig;
import at.querchecker.config.UserAgentHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// "prod"-Profil aktiv, damit LocalProfileAuthFilter (@Profile("!prod")) in diesem Web-Slice nicht
// automatisch mitläuft (siehe AccessKeyControllerTest für den gleichen Grund).
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, AuthProperties.class})
@ActiveProfiles("prod")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AuthService authService;
    @MockBean UserAgentHolder userAgentHolder; // satisfies UserAgentFilter, pulled into every @WebMvcTest slice
    @MockBean UserSessionRepository userSessionRepository; // satisfies real SessionCookieAuthFilter, pulled into every @WebMvcTest slice
    @MockBean AccessKeyRepository accessKeyRepository; // dito

    @Test
    void loginWithKey_withoutAuth_isReachable() throws Exception {
        when(authService.login(any(), any())).thenReturn(new LoginResponseDto(Role.USER));

        mockMvc.perform(post("/api/auth/login-with-key")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new AuthController.LoginRequest("some-key"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void logout_withoutAuth_isReachable() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
            .andExpect(status().isOk());
    }

    @Test
    void me_withoutAuth_isReachable() throws Exception {
        when(authService.me()).thenReturn(new AuthStatusDto(false, null, false, null, null, null));

        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(false));
    }
}
