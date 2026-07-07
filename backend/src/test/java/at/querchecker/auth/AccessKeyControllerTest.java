package at.querchecker.auth;

import at.querchecker.auth.dto.AccessKeyCreatedDto;
import at.querchecker.auth.dto.AccessKeyOverviewDto;
import at.querchecker.config.SecurityConfig;
import at.querchecker.config.UserAgentHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// "prod"-Profil aktiv, damit LocalProfileAuthFilter (@Profile("!prod")) nicht automatisch jede
// Anfrage zu SUPERUSER macht — sonst wären die *_withoutSuperuserRole_isForbidden-Tests sinnlos.
@WebMvcTest(AccessKeyController.class)
@Import({SecurityConfig.class, AuthProperties.class})
@ActiveProfiles("prod")
class AccessKeyControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AccessKeyService service;
    @MockBean UserAgentHolder userAgentHolder; // satisfies UserAgentFilter, pulled into every @WebMvcTest slice
    @MockBean UserSessionRepository userSessionRepository; // satisfies real SessionCookieAuthFilter, pulled into every @WebMvcTest slice
    @MockBean AccessKeyRepository accessKeyRepository; // dito

    @Test
    @WithMockUser(roles = "SUPERUSER")
    void generateKey_returnsCreatedDtoSchema() throws Exception {
        when(service.generateKey(Role.USER, 10)).thenReturn(
            new AccessKeyCreatedDto(1L, "raw-secret-key", Role.USER, 10, Instant.now())
        );

        mockMvc.perform(post("/api/auth/generate-key")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new AccessKeyController.GenerateKeyRequest(Role.USER, 10))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.secretKey").value("raw-secret-key"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.quotaLimit").value(10));
    }

    @Test
    void generateKey_withoutSuperuserRole_isForbidden() throws Exception {
        mockMvc.perform(post("/api/auth/generate-key")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new AccessKeyController.GenerateKeyRequest(Role.USER, 10))))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPERUSER")
    void listKeys_returnsListWithoutSecretField() throws Exception {
        when(service.listKeys()).thenReturn(List.of(
            new AccessKeyOverviewDto(1L, Role.USER, 10, Instant.now(), null, false)
        ));

        mockMvc.perform(get("/api/auth/keys"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].revoked").value(false))
            .andExpect(jsonPath("$[0].secretKey").doesNotExist())
            .andExpect(jsonPath("$[0].secretKeyHash").doesNotExist());
    }

    @Test
    void listKeys_withoutSuperuserRole_isForbidden() throws Exception {
        mockMvc.perform(get("/api/auth/keys"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPERUSER")
    void updateKey_returnsUpdatedOverviewDto() throws Exception {
        when(service.updateKey(1L, Role.SUPERUSER, 5)).thenReturn(
            new AccessKeyOverviewDto(1L, Role.SUPERUSER, 5, Instant.now(), null, false)
        );

        mockMvc.perform(patch("/api/auth/keys/1")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new AccessKeyController.UpdateKeyRequest(Role.SUPERUSER, 5))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("SUPERUSER"))
            .andExpect(jsonPath("$.quotaLimit").value(5));
    }

    @Test
    void updateKey_withoutSuperuserRole_isForbidden() throws Exception {
        mockMvc.perform(patch("/api/auth/keys/1")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new AccessKeyController.UpdateKeyRequest(Role.SUPERUSER, 5))))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPERUSER")
    void revoke_setsRevokedTrue() throws Exception {
        when(service.revoke(1L)).thenReturn(
            new AccessKeyOverviewDto(1L, Role.USER, 10, Instant.now(), null, true)
        );

        mockMvc.perform(post("/api/auth/keys/1/revoke"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.revoked").value(true));
    }

    @Test
    @WithMockUser(roles = "SUPERUSER")
    void unrevoke_setsRevokedFalse() throws Exception {
        when(service.unrevoke(1L)).thenReturn(
            new AccessKeyOverviewDto(1L, Role.USER, 10, Instant.now(), null, false)
        );

        mockMvc.perform(post("/api/auth/keys/1/unrevoke"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.revoked").value(false));
    }

    @Test
    void revoke_withoutSuperuserRole_isForbidden() throws Exception {
        mockMvc.perform(post("/api/auth/keys/1/revoke"))
            .andExpect(status().isForbidden());
    }
}
