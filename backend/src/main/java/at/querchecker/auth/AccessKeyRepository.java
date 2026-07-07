package at.querchecker.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccessKeyRepository extends JpaRepository<AccessKey, Long> {
    Optional<AccessKey> findBySecretKeyHash(String secretKeyHash);
}
