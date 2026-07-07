# Berechtigungskonzept — Implementierungs-Prompt P1

> Scope: Nur Key-Erzeugung (Superuser) + Übersicht. Login/Session, PATCH/Revoke, Angular-UI folgen in P2+.
> Grundlage: `berechtigungen-konzept.md`, Kapitel 1–3 (Drei-Zustands-Modell: GUEST/USER/SUPERUSER).

---

## Ziel dieses Schritts

1. `Role`-Enum (`USER`, `SUPERUSER`)
2. `AccessKey`-Entity + Flyway-Migration
3. `AccessKeyRepository`
4. Endpoint `POST /api/auth/generate-key` (Superuser-only) — erzeugt Key, gibt Klartext **einmalig** zurück
5. Endpoint `GET /api/auth/keys` (Superuser-only) — Übersicht aller Keys, **ohne** Secret/Hash
6. Unit-Tests

**Explizit außerhalb des Scopes (spätere Prompts):**
- `login-with-key` + Session-Cookie (P2)
- `PATCH` / `revoke` / `unrevoke` (P2)
- Angular-UI (P3)
- `AccessKeyUsage` / Kontingent-Zählung (P4)

---

## 1. Package-Struktur

Neues Package `at.querchecker.auth` (analog zur bestehenden Modul-Struktur).

## 2. `Role`-Enum

```java
package at.querchecker.auth;

public enum Role {
    USER,      // Kontingent = AccessKey.quotaLimit, DB entscheidet (Konzept Kap. 4)
    SUPERUSER  // kein Kontingent-Check, Settings-Spezialteile erlaubt
}
```

**Modell-Entscheidung (Konzept Kap. 1):** Es gibt genau zwei Prüf-Stellen — AI-Zugriff („hat gültige Session") und Settings-Spezialteile („ist SUPERUSER"). Das Kontingent hängt allein am Key (`quotaLimit`), nicht an der Rolle — deshalb trägt das Enum keinerlei Werte. `GUEST` (= keine Session) und `LOCAL` (= Spring-Profil, läuft als SUPERUSER) sind bewusst keine Enum-Werte.

## 3. `AccessKey`-Entity

```java
package at.querchecker.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "access_key")
@Getter @Setter
@NoArgsConstructor
public class AccessKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String secretKeyHash; // SHA-256 Hash, NIE der Klartext-Key

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private int quotaLimit; // Tages-Kontingent dieses Keys; bei SUPERUSER ohne Wirkung (kein Check)

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant lastUsedAt;

    @Column(nullable = false)
    private boolean used = false;

    @Column(nullable = false)
    private boolean revoked = false;
}
```

## 4. Flyway-Migration

Nächste freie Version im Repo prüfen (Stand zuletzt bekannt: `V30` belegt → vermutlich `V31__create_access_key.sql`):

```sql
CREATE TABLE access_key (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    secret_key_hash VARCHAR(64) NOT NULL UNIQUE,
    role VARCHAR(20) NOT NULL,
    quota_limit INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    last_used_at TIMESTAMP,
    used BOOLEAN NOT NULL DEFAULT false,
    revoked BOOLEAN NOT NULL DEFAULT false
);
```

## 5. Repository

```java
package at.querchecker.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AccessKeyRepository extends JpaRepository<AccessKey, Long> {
    Optional<AccessKey> findBySecretKeyHash(String secretKeyHash);
}
```

## 6. DTOs

```java
package at.querchecker.auth.dto;

import at.querchecker.auth.Role;
import java.time.Instant;

// Response bei Generierung — Klartext-Key NUR hier enthalten, nie wieder abrufbar
public record AccessKeyCreatedDto(
    Long id,
    String secretKey, // Klartext, einmalig
    Role role,
    int quotaLimit,
    Instant createdAt
) {}

// Response für die Übersicht — KEIN Secret/Hash enthalten
public record AccessKeyOverviewDto(
    Long id,
    Role role,
    int quotaLimit,
    Instant createdAt,
    Instant lastUsedAt,
    boolean revoked
) {}
```

## 7. Service

```java
package at.querchecker.auth;

import at.querchecker.auth.dto.AccessKeyCreatedDto;
import at.querchecker.auth.dto.AccessKeyOverviewDto;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccessKeyService {

    private final AccessKeyRepository repository;

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
}
```

**Hinweis Dependency:** `commons-codec` (für `DigestUtils`) prüfen, ob bereits vorhanden — falls nicht, `pom.xml` ergänzen.

## 8. Controller

```java
package at.querchecker.auth;

import at.querchecker.auth.dto.AccessKeyCreatedDto;
import at.querchecker.auth.dto.AccessKeyOverviewDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AccessKeyController {

    private final AccessKeyService service;

    public record GenerateKeyRequest(Role role, int quotaLimit) {}

    @PostMapping("/generate-key")
    @PreAuthorize("hasRole('SUPERUSER')")
    public AccessKeyCreatedDto generateKey(@RequestBody GenerateKeyRequest request) {
        return service.generateKey(request.role(), request.quotaLimit());
    }

    @GetMapping("/keys")
    @PreAuthorize("hasRole('SUPERUSER')")
    public List<AccessKeyOverviewDto> listKeys() {
        return service.listKeys();
    }
}
```

**Achtung — Security-Config:** `@PreAuthorize` setzt `@EnableMethodSecurity` voraus. Die Filter-Kette, die `ROLE_SUPERUSER` tatsächlich vergibt (Local-Profil/IP/Session), kommt erst in P2. Für P1: Annotation als Vorbereitung setzen; Endpoint übergangsweise über die bestehende HTTP-Basic-Auth erreichbar lassen und als TODO markieren.

## 9. Tests (JUnit 5 + Mockito, analog bestehendem Test-Stil)

- `AccessKeyServiceTest`:
  - `generateKey` liefert Klartext-Key in der Response, aber `repository.save()` erhält NUR den Hash (kein Klartext im Entity)
  - Zwei aufeinanderfolgende Aufrufe erzeugen unterschiedliche Hashes
  - `quotaLimit` wird unverändert übernommen
  - `listKeys`-DTO enthält keinen Hash/Klartext (Record hat kein solches Feld — Kompilier-Check genügt)
- `AccessKeyControllerTest` (`@WebMvcTest`/`MockMvc`):
  - `POST /api/auth/generate-key` → 200 + erwartetes JSON-Schema
  - `GET /api/auth/keys` → Liste ohne Secret-Feld

---

## Nach diesem Schritt (P2 Ausblick, nicht jetzt umsetzen)

- `login-with-key` → Session-Cookie (HttpOnly, Hash in `user_session`-Tabelle) — **kein** JWT, bewusste Entscheidung (Konzept Kap. 3)
- `QuerCheckerPrincipal` + drei Security-Filter (Local-Profil → SUPERUSER ohne Key; IP-Allowlist → SUPERUSER ohne Key; Session-Cookie → Rolle+`accessKeyId` aus DB)
- `PATCH` / `revoke` / `unrevoke` auf `/api/auth/keys/{id}`
- Ablösung der bestehenden HTTP-Basic-Auth
