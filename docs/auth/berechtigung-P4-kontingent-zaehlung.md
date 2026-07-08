# Berechtigungskonzept — Implementierungs-Prompt P4 (Kontingent-Zählung)

> Scope: Ebene-2-Kontingent (Key-Kontingent) aus dem Konzept (Kap. 4) tatsächlich implementieren — bisher nur `quotaLimit`-Feld am Key, kein Verbrauchszähler.
> Grundlage: `berechtigungen-konzept.md`, Kap. 4 (SQL-Beispiel dort) + Kap. 8 (offene Fragen, teils hier geklärt).
> Baut auf P1–P3 auf (Role, AccessKey, Session-Cookie-Auth, Angular-UI müssen bereits stehen).

---

## Geklärt (2026-07-08, Nutzer-Entscheidung)

**„Eine AI-Anfrage" = nur Spec-Lookup.** `ProductLookupService.lookup()` (item-research-Suchfeld) zählt als 1 Aktion gegen das Tageskontingent. DL-Extraktion (automatisches Produktname-Pre-Fetch beim Öffnen eines Inserats, `WhItemService.openDetail()`) zählt **nicht** — keine bewusste User-Aktion, sondern Vorbereitung.

---

## 1. Entity + Migration

```java
package at.querchecker.auth;

@Entity
@Table(name = "access_key_usage")
@Getter @Setter
@NoArgsConstructor
public class AccessKeyUsage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "access_key_id", nullable = false)
    private Long accessKeyId;

    @Column(name = "period_date", nullable = false)
    private LocalDate periodDate;

    @Column(name = "consumed_count", nullable = false)
    private int consumedCount;
}
```

Migration `V44__create_access_key_usage.sql` (Version prüfen — aktuell V43 = user_session):

```sql
CREATE TABLE access_key_usage (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    access_key_id BIGINT NOT NULL REFERENCES access_key(id),
    period_date DATE NOT NULL,
    consumed_count INTEGER NOT NULL DEFAULT 0,
    UNIQUE (access_key_id, period_date)
);
```

Repository: `AccessKeyUsageRepository extends JpaRepository<AccessKeyUsage, Long>` — kein Standard-`findBy` nötig, Zugriff läuft über native/JPQL-Query im Service (s.u.).

## 2. Service: atomarer Check + Increment

**Check (vor der Provider-Pipeline):**

```sql
SELECT k.quota_limit - COALESCE(u.consumed_count, 0) AS remaining
FROM access_key k
LEFT JOIN access_key_usage u
       ON u.access_key_id = k.id AND u.period_date = CURRENT_DATE
WHERE k.id = :accessKeyId
```
`remaining <= 0` → wirft `QuotaExceededException` (neue Exception-Klasse, `auth`-Package) → Controller fängt sie, mappt auf bestehenden `QUOTA_EXCEEDED`-Lookup-Status (Frontend-Branch existiert bereits in `item-research.html`).

**Skip-Regel:** `role == SUPERUSER` → Check komplett übersprungen (Aufrufer prüft das, nicht der Service selbst — Service bekommt nur `accessKeyId`, das bei SUPERUSER ggf. `null` ist; `null` → Service macht nichts).

**Increment (nach erfolgreichem Abschluss des Lookups, nicht am Anfang):**
Atomares Upsert, kein Read-Modify-Write in Java:

```sql
INSERT INTO access_key_usage (access_key_id, period_date, consumed_count)
VALUES (:accessKeyId, CURRENT_DATE, 1)
ON CONFLICT (access_key_id, period_date)
DO UPDATE SET consumed_count = access_key_usage.consumed_count + 1
```

Als `@Modifying @Query(nativeQuery = true)` im Repository.

## 3. Wo einhängen — `ProductLookupService.lookup()`

1. **Vor** Cache-Check/Provider-Pipeline: `accessKeyUsageService.checkQuota(accessKeyId)` (skip bei SUPERUSER/`null`)
2. **Cache-Hit zählt nicht** — bestehender Cache-Check-Pfad bleibt vor dem Increment
3. **Provider-Erschöpfung zählt nicht gegen den User** — Increment nur im Erfolgspfad, nicht in den Error-/Rate-Limit-Handlern
4. Nach erfolgreichem Abschluss: `accessKeyUsageService.consume(accessKeyId)`

`accessKeyId` kommt aus dem `QuerCheckerPrincipal` der aktuellen Session (`SecurityContextHolder`, analog zum Muster in `WhItemService.openDetail()` — dort schon als Referenz vorhanden für „gibt es eine Session").

## 4. Frontend — Kontingent-Anzeige

- `GET /api/auth/me` (`AuthStatusDto`) um `quotaRemaining: Integer | null` erweitern (nur bei `role == USER` befüllt, sonst `null`)
- Settings: Anzeige „X/Y heute" neben dem Abmelden-Button, nur wenn `role === 'USER'` (Superuser: kein Kontingent, nichts anzeigen)
- `QUOTA_EXCEEDED`-Darstellung in `item-research.html` existiert schon (Lookup-Status-Branch) — keine Änderung nötig dort

## 5. Cleanup / DSGVO (Kap. 7)

Bestehenden Session-Cleanup-Cronjob um `AccessKeyUsage`-Retention erweitern (z.B. 90 Tage, `AppConfig`-Key analog anderen Retention-Werten):
```java
accessKeyUsageRepository.deleteByPeriodDateBefore(cutoffDate);
```

## 6. Tests

- `AccessKeyUsageServiceTest`: Check unter/über Limit, Skip bei SUPERUSER/`null` accessKeyId, Increment idempotent bei parallelen Calls (falls sinnvoll simulierbar)
- `ProductLookupServiceTest`: Erweitern um Quota-Check-Aufruf vor Pipeline, Increment nur im Erfolgspfad, kein Increment bei Cache-Hit/Provider-Fehler
- Kein `@SpringBootTest` nötig (Konvention aus P2 — Mockito/`@WebMvcTest` reicht)

---

## Nicht in diesem Schritt

- Dynamische Drosselung bei hohem Traffic (Kap. 5) — zurückgestellt
- Nächtlicher Rollup/Reporting (Kap. 6) — erst bei Bedarf
