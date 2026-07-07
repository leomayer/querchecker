package at.querchecker.auth;

import at.querchecker.auth.dto.AccessKeyCreatedDto;
import at.querchecker.auth.dto.AccessKeyOverviewDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessKeyServiceTest {

    @Mock AccessKeyRepository repository;
    @Mock UserSessionRepository userSessionRepository;
    @InjectMocks AccessKeyService service;

    private AccessKey saved(Long id, Role role, int quotaLimit, String hash) {
        AccessKey key = new AccessKey();
        key.setId(id);
        key.setRole(role);
        key.setQuotaLimit(quotaLimit);
        key.setSecretKeyHash(hash);
        key.setCreatedAt(Instant.now());
        return key;
    }

    @Test
    void generateKey_returnsRawKeyButPersistsOnlyHash() {
        ArgumentCaptor<AccessKey> captor = ArgumentCaptor.forClass(AccessKey.class);
        when(repository.save(captor.capture()))
            .thenAnswer(invocation -> saved(1L, Role.USER, 10, invocation.<AccessKey>getArgument(0).getSecretKeyHash()));

        AccessKeyCreatedDto result = service.generateKey(Role.USER, 10);

        AccessKey persisted = captor.getValue();
        assertThat(persisted.getSecretKeyHash()).isNotEqualTo(result.secretKey());
        assertThat(persisted.getSecretKeyHash()).hasSize(64); // SHA-256 hex
        assertThat(result.secretKey()).isNotBlank();
    }

    @Test
    void generateKey_producesDifferentHashesOnSuccessiveCalls() {
        when(repository.save(any(AccessKey.class)))
            .thenAnswer(invocation -> saved(1L, Role.USER, 10, invocation.<AccessKey>getArgument(0).getSecretKeyHash()));

        AccessKeyCreatedDto first = service.generateKey(Role.USER, 10);
        AccessKeyCreatedDto second = service.generateKey(Role.USER, 10);

        assertThat(first.secretKey()).isNotEqualTo(second.secretKey());
    }

    @Test
    void generateKey_passesQuotaLimitUnchanged() {
        ArgumentCaptor<AccessKey> captor = ArgumentCaptor.forClass(AccessKey.class);
        when(repository.save(captor.capture()))
            .thenAnswer(invocation -> saved(1L, Role.USER, 42, invocation.<AccessKey>getArgument(0).getSecretKeyHash()));

        AccessKeyCreatedDto result = service.generateKey(Role.USER, 42);

        assertThat(captor.getValue().getQuotaLimit()).isEqualTo(42);
        assertThat(result.quotaLimit()).isEqualTo(42);
    }

    @Test
    void listKeys_mapsToOverviewDtoWithoutSecret() {
        AccessKey key = saved(1L, Role.SUPERUSER, 0, "somehash");
        key.setRevoked(true);
        when(repository.findAll()).thenReturn(List.of(key));

        List<AccessKeyOverviewDto> result = service.listKeys();

        assertThat(result).hasSize(1);
        AccessKeyOverviewDto dto = result.get(0);
        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.role()).isEqualTo(Role.SUPERUSER);
        assertThat(dto.revoked()).isTrue();
        // AccessKeyOverviewDto has no secret/hash field by design — compile-time guarantee
    }

    @Test
    void updateKey_appliesOnlyProvidedFields() {
        AccessKey key = saved(1L, Role.USER, 10, "hash");
        when(repository.findById(1L)).thenReturn(java.util.Optional.of(key));
        when(repository.save(any(AccessKey.class))).thenAnswer(inv -> inv.getArgument(0));

        AccessKeyOverviewDto result = service.updateKey(1L, null, 99);

        assertThat(result.role()).isEqualTo(Role.USER); // unchanged, role was null
        assertThat(result.quotaLimit()).isEqualTo(99);
    }

    @Test
    void updateKey_unknownId_throwsNotFound() {
        when(repository.findById(1L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.updateKey(1L, Role.USER, 5))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void revoke_setsRevokedAndDeletesSessions() {
        AccessKey key = saved(1L, Role.USER, 10, "hash");
        when(repository.findById(1L)).thenReturn(java.util.Optional.of(key));
        when(repository.save(any(AccessKey.class))).thenAnswer(inv -> inv.getArgument(0));

        AccessKeyOverviewDto result = service.revoke(1L);

        assertThat(result.revoked()).isTrue();
        verify(userSessionRepository).deleteByAccessKeyId(1L);
    }

    @Test
    void unrevoke_clearsRevokedFlag_doesNotTouchSessions() {
        AccessKey key = saved(1L, Role.USER, 10, "hash");
        key.setRevoked(true);
        when(repository.findById(1L)).thenReturn(java.util.Optional.of(key));
        when(repository.save(any(AccessKey.class))).thenAnswer(inv -> inv.getArgument(0));

        AccessKeyOverviewDto result = service.unrevoke(1L);

        assertThat(result.revoked()).isFalse();
        verify(userSessionRepository, org.mockito.Mockito.never()).deleteByAccessKeyId(any());
    }
}
