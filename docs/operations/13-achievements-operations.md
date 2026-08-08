# Betrieb und Recovery – Inkrement 13 Achievements

Dieses Dokument beschreibt den Betriebs- und Recovery-Pfad von `achievements-v1`. Es ergänzt ADR 0020 und das Abnahmeprotokoll `13-achievements-acceptance.md`.

**Produktionsstatus:** Release **1.4.0** ist erfolgreich ausgerollt. Der produktive Achievement-Merge basiert auf `main`-Commit `213fe15dcc59e46856ea9be7066161fdc473353a`; der anschließende Produktiv-Smoke/Canary ist bestanden.

## 1. Betriebsmodell

Achievements sind eine materialisierte, korrekturfähige Projektion aus kanonischen Bot-Daten. Quelle der Wahrheit bleiben insbesondere:

- `game_result`,
- ursprünglicher persistierter Share-Zeitpunkt,
- historisch wirksame spielbezogene Teilnahme,
- kanonische QuadWords-Boarddetails, soweit vorhanden.

Die Achievement-Tabellen sind **keine** zweite Ergebniswahrheit. Ein technischer Reparaturpfad soll deshalb kanonische Daten erneut reconciliieren statt Award-/Event-/Announcement-Daten manuell passend zu editieren.

Persistiert werden:

- `achievement_award_state` – aktueller aktiver/invalidierter Award-Zustand,
- `achievement_event` – append-only Audit-Historie für Unlock/Invalidierung/Reaktivierung,
- `achievement_bootstrap_state` – restartfähiger Bootstrap-Claim/Lease/Status je Guild und Definitionsversion,
- `achievement_announcement` – logische öffentliche Meldung samt Delivery-Status, Claim/Lease, Retry und bestätigter Discord-Message-ID,
- `achievement_announcement_item` – geordnete Eventreferenzen der logischen Meldung.

Liquibase-Migration 024 ist die verbindliche Schemaquelle für diese Tabellen.

## 2. Startup und historischer Bootstrap

Beim Start wird der Bootstrap für `achievements-v1` registriert beziehungsweise wieder aufgenommen.

Grundregeln:

1. Claim und Lease werden persistent in PostgreSQL geführt.
2. Ein abgelaufener Claim kann von einem späteren Lauf übernommen werden.
3. Der Bootstrap darf bei Restart wieder von vorn über die Teilnehmer laufen; State-, Event- und Introduction-Idempotenz verhindern Duplikate.
4. Aktive und historisch inaktive Teilnehmer werden aus der kanonischen Historie rekonstruiert.
5. Für jeden Teilnehmer existiert genau eine logische `HISTORICAL_INTRODUCTION` pro Definitionsversion, auch wenn der Teilnehmer null rückwirkende Awards besitzt.
6. Öffentliche Achievement-Delivery bleibt bis `SUCCEEDED` gegatet.

Ein technischer Bootstrap-Fehler darf nicht als erfolgreicher Abschluss maskiert werden. Er bleibt diagnostizierbar und retrybar; normale Live-Meldungen werden dadurch weiterhin nicht freigegeben.

## 3. Normale Ergebnisverarbeitung und Recovery

Der normale Resultatpfad ist bewusst an den dauerhaften kanonischen Ergebniszustand gekoppelt:

1. Share wird geparst und validiert.
2. `game_result` und Submission-Zustand werden persistent aktualisiert.
3. Achievement-Reconciliation wird participant-weit aus der kanonischen Historie ausgeführt.
4. Award-State, append-only Event und gewünschte Announcement-Projektion werden transaktional konsistent geschrieben.
5. Erst danach läuft der kanonische Discord-I/O der Ergebnisnachricht weiter.

Wenn der Prozess nach dauerhafter Ergebnisablage, aber vor erfolgreicher Achievement-Reconciliation endet, bleibt die Submission im recoverbaren `RESULT_STORED`-Zustand. Startup-Recovery kann die Achievement-Projektion still nachziehen. Damit hängt die fachliche Vergabe nicht von einem bereits erfolgreichen Discord-Publish ab.

Ein einfacher Best-Effort-Callback nach Ergebnisablage ist ausdrücklich nicht die Durability-Garantie; maßgeblich ist der persistierte Submission-/Result-Zustand.

## 4. Reconciliation, Korrekturen und Konkurrenz

Reconciliation wertet die participant-weite kanonische Historie aus und vergleicht sie mit der materialisierten Award-Projektion:

- neu belegt → `UNLOCK`,
- weiterhin aktiv → `NO_OP`,
- nicht mehr belegt → `INVALIDATE`,
- später wieder belegt → `REACTIVATE`.

Korrekturen zählen nicht als zusätzliche Teilnahme. Invalidierungen löschen weder Award-Historie noch Events und erzeugen keine öffentliche Aberkennungsnachricht.

Die Reconciliation ist participant-bezogen in PostgreSQL serialisiert. Ein vor der Schreibtransaktion ermittelter Snapshot wird unter der Participant-Fence noch einmal gegen die aktuelle Historie validiert. Ist er inzwischen veraltet, wird nicht mit stale Daten geschrieben; stattdessen wird mit frischer Historie neu geplant.

Das verhindert insbesondere, dass eine alte solved-Auswertung eine inzwischen korrekt invalidierte Vergabe wieder reaktiviert.

## 5. Announcement-Delivery

Öffentliche Achievement-Meldungen werden außerhalb der fachlichen Datenbanktransaktion ausgeliefert.

### 5.1 Claim und Lease

- Ein Worker claimed genau eine persistierte Announcement-Projektion.
- Claim und Lease sind tokengebunden.
- Während blockierendem Discord-I/O hält ein Heartbeat die Lease aktiv.
- Ein verlorener/abgelaufener Token darf weder Message-ID noch terminalen Zustand schreiben.
- Mehrere Worker werden durch PostgreSQL-Claims und `FOR UPDATE SKIP LOCKED` gegeneinander gefenced.

### 5.2 Bootstrap- und Introduction-Gates

Eine Announcement-Projektion ist nur claimbar, wenn der passende `achievements-v1`-Bootstrap erfolgreich ist.

Für denselben Teilnehmer gilt zusätzlich:

1. historische Introduction vollständig `SYNCHRONIZED`,
2. erst danach Live-Batches.

Die Reihenfolge ist Teil der PostgreSQL-Claim-Bedingung und kein ungeschützter Check-then-act im Worker.

### 5.3 Revalidation unmittelbar vor Create

Ein geclaimter, aber noch nicht veröffentlichter Batch wird vor dem ersten Discord-Create gegen den aktuellen participant-weiten Award-State revalidiert.

- inzwischen invalidierte Items werden tokengebunden entfernt,
- Content-Fingerprint und Renderer-Version werden aktualisiert,
- ein vollständig obsoleter Live-Batch wird `SUPPRESSED`, ohne Discord-Nachricht zu erzeugen,
- fehlende persistierte Event-/Award-Fakten sind technische Invariantenverletzungen und werden nicht als „obsolet“ verschluckt.

Eine Korrektur, die erst nach dieser letzten Revalidation während des bereits begonnenen externen Discord-Calls erfolgt, führt nicht zu einer späteren öffentlichen Aberkennung: bereits veröffentlichte Unlock-Historie bleibt bestehen.

## 6. Send-vor-ACK und Restart-Recovery

Jede logische Achievement-Meldung besitzt eine stabile Publication-Identität. Beim Discord-Create wird daraus eine stabile, nicht im sichtbaren Embed-Inhalt enthaltene Nonce abgeleitet; User-Mentions sind deaktiviert. Discord liefert die Nonce im Message-Objekt zurück, sodass Discovery keine technischen Marker in der Nutzdarstellung benötigt.

### Fall A: Create erfolgreich, lokaler ACK unbekannt

1. Der externe Create kann bereits erfolgreich gewesen sein, obwohl der lokale Aufruf retrybar endet.
2. Die persistierte Announcement-Arbeit wird `RETRYABLE` und behält ihre logische Identität.
3. Beim nächsten Versuch wird gezielt anhand der stabilen Nonce nach **dieser Publication-ID** gesucht.
4. Wird die vorhandene Nachricht gefunden, wird kein neuer dauerhafter Create benötigt.
5. Bei mehreren Artefakten derselben logischen Create-Operation gewinnt deterministisch eine Nachricht; nur die Duplikatartefakte derselben Publication werden gelöscht.

Eine reine sichtbare Inhaltsgleichheit reicht nicht als Discovery-Identität. Interne Publication-IDs, Hashes oder Recovery-URLs werden nicht in den sichtbaren Embed-Inhalt geschrieben.

### Fall B: Message-ID persistiert, Prozess stirbt vor `SYNCHRONIZED`

Beim Restart wird die persistierte Message-ID geprüft. Existiert die Nachricht, wird die Projektion ohne erneutes Create synchronisiert.

### Fall C: Persistierte, noch nicht vollständig synchronisierte Nachricht extern entfernt

Die Delivery erkennt die fehlende persistierte Nachricht separat. Der Zustand bleibt diagnostizierbar; es wird nicht blind eine Kette neuer Creates erzeugt.

### Bereits `SYNCHRONIZED`

Eine synchronisierte Achievement-Meldung wird wegen einer späteren fachlichen Invalidierung nicht erneut geclaimt, editiert oder gelöscht.

## 7. Achievement-Commands im Betrieb

`/achievements` ist ein reiner Read-Pfad:

- liest `achievement_award_state` plus den codebasierten V1-Katalog,
- zeigt ausschließlich `ACTIVE`,
- löst keinen historischen Vollscan, Evaluator oder Reconciler aus,
- erzeugt keinen Player/Profile-Sync als Seiteneffekt,
- Selbst- und Fremdansicht sind ephemeral,
- Single-Game-Filter enthalten ausschließlich den jeweiligen Single-Game-Scope.

`/achievement-list` ist ebenfalls strikt read-only und self-only:

- zeigt alle 60 Definitionen des V1-Katalogs,
- `✅` bedeutet aktuell `ACTIVE`,
- `❌` bedeutet fehlend oder `INVALIDATED`,
- zeigt keine quantitativen Fortschrittswerte,
- bleibt vollständig ephemeral und mention-sicher.

Ein Fehler dieser Commands verändert keine Achievement-Persistenz.

## 8. Diagnose

Diagnose soll zunächst read-only erfolgen. Hilfreiche Abfragen sind beispielsweise:

```sql
SELECT guild_id, definition_version, bootstrap_state, claim_until, failure_category, safe_error
FROM achievement_bootstrap_state
ORDER BY guild_id, definition_version;
```

```sql
SELECT guild_id, participant_id, achievement_key, award_status, earned_on, detected_at, invalidated_at, lock_version
FROM achievement_award_state
ORDER BY guild_id, participant_id, achievement_key;
```

```sql
SELECT guild_id, participant_id, announcement_type, delivery_state,
       attempt_count, next_retry_at, claim_until, discord_message_id,
       failure_category, safe_error, created_at, updated_at
FROM achievement_announcement
ORDER BY created_at, id;
```

```sql
SELECT guild_id, participant_id, achievement_key, event_type, processing_origin,
       earned_on, detected_at, idempotency_key
FROM achievement_event
ORDER BY detected_at, event_id;
```

Diese Abfragen sind Diagnosehilfen. Persistente Zustände sollen nicht durch ad-hoc `UPDATE`/`DELETE` künstlich „repariert“ werden. Wenn die kanonische Historie korrekt ist, sind Reconciliation/Recovery die bevorzugten Reparaturmechanismen.

## 9. Backup, Restore und Rollback

Achievement-Persistenz liegt in derselben PostgreSQL-Datenbank wie die übrigen Bot-Fachdaten und wird durch den bestehenden Backup-/Restore-Prozess abgedeckt.

Der GitHub-Workflow `Container image` prüft unter anderem:

- produktionsnahes Image,
- Shell-Skripte,
- isoliertes Compose-Setup,
- Backup,
- Restore,
- Resume,
- Rollback.

Migration 024 ist additiv. Bei einem Code-Rollback sollen die Achievement-Tabellen und ihre Historie **nicht** manuell gelöscht werden. Ein späterer erneuter Start mit Achievement-Unterstützung kann auf dem persistenten Zustand und der kanonischen Historie weiterarbeiten beziehungsweise reconciliieren.

Ein DB-Rollback auf ein physisch älteres Schema darf nur über den allgemeinen, getesteten Restore-/Rollbackprozess erfolgen, nicht durch handgeschriebene Down-Migrationen im laufenden System.

## 10. Fehlerklassen und Eskalation

- bekannte transiente Discord-/Netzwerkprobleme → retrybar,
- bekannte dauerhafte Berechtigungs-/Konfigurationsfehler → persistent als permanent diagnostizierbar,
- externe Missing-Message-Fälle → eigener Recoverypfad,
- unbekannte Runtime-/JDBC-/Persistenzfehler → technisch sichtbar lassen, nicht als fachlichen `NO_OP`/Konflikt maskieren.

Bei wiederholten unbekannten Fehlern keine Datenzustände manuell passend editieren, sondern Logs, persistierte Failure-Felder, Bootstrap-/Announcement-State und kanonische Quelldaten prüfen.

## 11. Produktionsstand und weitere Releases

Release **1.4.0** ist produktiv. Der Produktivrollout wurde mit dem bestehenden unveränderlichen Image-/Backup-/Healthcheck-/Rollbackpfad durchgeführt; der anschließende Smoke-Test wurde erfolgreich bestätigt.

Für weitere Achievement-Versionen gilt weiterhin:

1. kanonische Daten bleiben Quelle der Wahrheit,
2. Schemaänderungen ausschließlich über Liquibase,
3. vor Deployment vollständige technische Gates,
4. validiertes Datenbankbackup vor App-Update,
5. unveränderliches Release-/SHA-Image,
6. kontrollierter Smoke/Canary mit klaren Rollbackkriterien,
7. keine manuellen Achievement-Tabellenänderungen zur Simulation erfolgreicher Zustände.

Der abgeschlossene Rollout-Nachweis steht in `13-achievements-live-canary.md`.
