package at.querchecker.auth;

import at.querchecker.auth.dto.AccessKeyCreatedDto;
import at.querchecker.auth.dto.AccessKeyOverviewDto;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccessKeyService {

    private final AccessKeyRepository repository;
    private final UserSessionRepository userSessionRepository;

    public AccessKeyCreatedDto generateKey(Role role, int quotaLimit) {
        String rawKey = UUID.randomUUID().toString();

        AccessKey accessKey = new AccessKey();
        accessKey.setSecretKeyHash(DigestUtils.sha256Hex(rawKey));
        accessKey.setRole(role);
        accessKey.setQuotaLimit(quotaLimit);

        AccessKey saved = repository.save(accessKey);

        return new AccessKeyCreatedDto(
            saved.getId(), rawKey, saved.getRole(), saved.getQuotaLimit(), saved.getCreatedAt()
        );
    }

    public List<AccessKeyOverviewDto> listKeys() {
        return repository.findAll().stream()
            .map(k -> new AccessKeyOverviewDto(
                k.getId(), k.getRole(), k.getQuotaLimit(),
                k.getCreatedAt(), k.getLastUsedAt(), k.isRevoked()
            ))
            .toList();
    }

    public AccessKeyOverviewDto updateKey(Long id, Role role, Integer quotaLimit) {
        AccessKey key = findOrThrow(id);
        if (role != null) key.setRole(role);
        if (quotaLimit != null) key.setQuotaLimit(quotaLimit);
        AccessKey saved = repository.save(key);
        return toOverview(saved);
    }

    // Sperre wirkt sofort: bestehende Sessions dieses Keys werden gelöscht.
    public AccessKeyOverviewDto revoke(Long id) {
        AccessKey key = findOrThrow(id);
        key.setRevoked(true);
        AccessKey saved = repository.save(key);
        userSessionRepository.deleteByAccessKeyId(id);
        return toOverview(saved);
    }

    // Sessions entstehen erst beim nächsten Login neu.
    public AccessKeyOverviewDto unrevoke(Long id) {
        AccessKey key = findOrThrow(id);
        key.setRevoked(false);
        return toOverview(repository.save(key));
    }

    private AccessKey findOrThrow(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AccessKey nicht gefunden: " + id));
    }

    private AccessKeyOverviewDto toOverview(AccessKey k) {
        return new AccessKeyOverviewDto(
            k.getId(), k.getRole(), k.getQuotaLimit(),
            k.getCreatedAt(), k.getLastUsedAt(), k.isRevoked()
        );
    }
}
