# Betrieb und Abnahme der Rekord-Live-Auswertung

Dieses Dokument beschreibt den mit Paket 12.6 eingeführten persistenten Laufzeitpfad für neue kanonische Ergebnisse und Korrekturen. Es ergänzt die allgemeine [Produktionsdiagnose](../../../operations/troubleshooting.md).

Nicht Bestandteil dieses Pfads sind Discord-Rendering und -Delivery, der 06:00-Tagesabschluss sowie der `/records`-Command. Die Live-Auswertung erzeugt ausschließlich Rekordzustände, Auditereignisse und gewünschte logische Announcement-Projektionen.

## Ablauf und Zuständigkeiten

1. Eine Submission erreicht dauerhaft den Zustand `RESULT_STORED` und verweist auf eine kanonische `game_result`-Version.
2. Migration 020 registriert dafür idempotent eine Zeile in `record_live_evaluation`.
3. Der Scheduler claimt fällige Arbeit mit Token und Lease.
4. Der Coordinator hält die Lease per Heartbeat, klassifiziert bekannte Fehler und lässt unbekannte technische Fehler unverändert sichtbar.
5. Der Processor liest und bewertet außerhalb der kurzen Schreibtransaktion.
6. Unmittelbar vor State-, Event- und Announcement-Writes werden Claim, Ergebnisversion und fachliche Generation erneut geprüft.
7. State, Auditereignisse, Invalidierungen, Announcement-Faktzuordnung und terminaler Work-Status werden tokengebunden und atomar persistiert.

Die bestehende kanonische Ergebnisveröffentlichung, Source-Deletion und der Excuse-Pfad bleiben davon fachlich getrennt. Ein Fehler der Rekordauswertung rollt das bereits persistierte Nutzerergebnis nicht zurück.

## Konfiguration

Alle Dauern verwenden ISO-8601-Syntax.

| Variable | Standard | Validierung |
|---|---:|---|
| `RECORD_LIVE_EVALUATION_ENABLED` | `true` | schaltet ausschließlich den Scheduler ein oder aus; persistierte Arbeit bleibt erhalten |
| `RECORD_LIVE_EVALUATION_POLL_DELAY` | `PT10S` | mindestens `PT0.001S` |
| `RECORD_LIVE_EVALUATION_LEASE_DURATION` | `PT2M` | größer als null |
| `RECORD_LIVE_EVALUATION_HEARTBEAT_INTERVAL` | `PT30S` | größer als null und kleiner als die Lease-Dauer |
| `RECORD_LIVE_EVALUATION_INITIAL_RETRY_BACKOFF` | `PT10S` | größer als null |
| `RECORD_LIVE_EVALUATION_MAX_RETRY_BACKOFF` | `PT5M` | mindestens so groß wie der initiale Backoff |

Nach einer Konfigurationsänderung den Bot kontrolliert neu starten. Offene, retryfähige und geclaimte Arbeit bleibt persistent; eine abgelaufene Lease wird nach dem Restart kontrolliert übernommen.

## Persistente Zustände

`record_live_evaluation.evaluation_state` verwendet ausschließlich:

- `OPEN`: neu registriert und claimbar,
- `CLAIMED`: aktuell token- und leasegebunden in Bearbeitung,
- `RETRYABLE`: bekannter vorübergehender Fehler; erst ab `next_retry_at` erneut claimbar,
- `SUCCEEDED`: vollständig und tokengebunden abgeschlossen,
- `FAILED_PERMANENT`: bekannte dauerhafte fachliche oder Konfigurationsinvariante,
- `SUPERSEDED`: durch eine neuere kanonische Ergebnisversion ersetzt.

Eine `CLAIMED`-Zeile mit abgelaufener `claim_until` ist kein manueller Reparaturfall: Der nächste Worker darf sie mit einem neuen Token übernehmen. Der alte Worker kann danach weder fachliche Writes noch einen terminalen Work-Status persistieren.

## Metriken und Logs

Die Loopback-gebundene Managementschnittstelle stellt bereit:

```bash
$COMPOSE exec -T bot curl --fail --silent \
  http://127.0.0.1:8081/actuator/metrics/gridwords.record.live-evaluation.runs
$COMPOSE exec -T bot curl --fail --silent \
  http://127.0.0.1:8081/actuator/metrics/gridwords.record.live-evaluation.duration
```

Beide Metriken besitzen ausschließlich das niedrig kardinale Tag `outcome` mit einem der Werte:

- `completed`,
- `failed_retryable`,
- `failed_permanent`,
- `lost_lease`,
- `unknown`.

Guild-, Ergebnis-, Trigger-, Token-, Fehlertext- oder Exception-Werte dürfen nicht als Metrik-Tags ergänzt werden. `NOT_CLAIMED` und deaktiviertes Polling erzeugen keine Abschlussmetrik und keinen periodischen INFO-Spam.

Strukturierte Logs enthalten die für einen einzelnen Lauf nötigen Identifikatoren und den Versuch, aber keine Discord-Inhalte, Roh-Shares oder Claim-Tokens. Unbekannte SQL-, Mapping- und Programmierfehler werden mit Stacktrace als `unknown` sichtbar und unverändert weitergeworfen.

## Sichere Diagnoseabfragen

Die folgenden Abfragen geben ausschließlich aggregierte Zustände aus und enthalten weder Tokens noch Fehlertexte:

```bash
$COMPOSE exec -T postgres psql \
  -U "$(grep '^POSTGRES_USER=' runtime.env | cut -d= -f2-)" \
  -d "$(grep '^POSTGRES_DB=' runtime.env | cut -d= -f2-)" \
  -c "SELECT evaluation_state, count(*) FROM record_live_evaluation GROUP BY evaluation_state ORDER BY evaluation_state;"

$COMPOSE exec -T postgres psql \
  -U "$(grep '^POSTGRES_USER=' runtime.env | cut -d= -f2-)" \
  -d "$(grep '^POSTGRES_DB=' runtime.env | cut -d= -f2-)" \
  -c "SELECT processing_origin, evaluation_state, count(*) FROM record_live_evaluation GROUP BY processing_origin, evaluation_state ORDER BY processing_origin, evaluation_state;"

$COMPOSE exec -T postgres psql \
  -U "$(grep '^POSTGRES_USER=' runtime.env | cut -d= -f2-)" \
  -d "$(grep '^POSTGRES_DB=' runtime.env | cut -d= -f2-)" \
  -c "SELECT count(*) AS expired_claims FROM record_live_evaluation WHERE evaluation_state = 'CLAIMED' AND claim_until < now();"
```

Konkrete `claim_token`, `safe_error`, Result-IDs oder Triggerreferenzen nicht in öffentliche Tickets kopieren.

## Incident-Ablauf

### Viele `RETRYABLE`-Zeilen

1. Metriken und Bot-Logs auf den Beginn des Anstiegs eingrenzen.
2. Datenbank- und Readiness-Zustand prüfen.
3. Den konfigurierten Backoff respektieren; Zeilen nicht manuell auf `SUCCEEDED` setzen.
4. Nach Behebung eines Infrastrukturproblems kontrolliert neu starten. Fällige Arbeit wird ohne neue Submission fortgesetzt.

### `FAILED_PERMANENT`

Ein permanenter Status steht für eine bekannte, nicht durch blindes Retry lösbare Invariante. Sichere Serverlogs prüfen und die Ursache im Code, in der Konfiguration oder in kanonischen Daten beheben. Den Status nicht manuell umetikettieren; eine administrative Reparatur muss einen neuen fachlich gültigen Auftrag erzeugen.

### Abgelaufene `CLAIMED`-Zeilen

Einzelne abgelaufene Leases direkt nach einem Restart sind erwartbar. Der Scheduler übernimmt sie automatisch. Bleibt die Zahl dauerhaft erhöht, Scheduler-Aktivierung, Uhrzeit, Datenbankverbindung und Heartbeat-Konfiguration prüfen.

### `lost_lease`

`lost_lease` ist bei einem Takeover ein kontrolliertes Ergebnis: Der alte Worker beendet sich ohne terminale oder fachliche Writes. Wiederholte Häufung deutet auf zu kurze Leases, blockierte Datenbankzugriffe oder Prozesspausen hin. Lease und Heartbeat nur anhand konkreter Betriebsdaten anpassen.

### `unknown`

Ein unbekannter Fehler wird absichtlich nicht als Retry-, Konkurrenz- oder permanenter Fachfehler maskiert. Stacktrace und Ursache beheben. Bis dahin bleibt bereits persistierte Arbeit je nach Transaktionspunkt offen beziehungsweise durch Lease-Recovery wieder aufnehmbar; das Nutzerergebnis bleibt erhalten.

## Migration, Upgrade und Application-Rollback

Migration 020 ist nach dem Merge unveränderlich. Korrekturen am Schema erfolgen ausschließlich additiv mit einer neuen Liquibase-Migration und echten Upgrade-Tests.

Der normale Application-Rollback löscht Migration 020 nicht. Das additive Schema bleibt bestehen und wird von einer älteren Anwendung ignoriert. Der Produktionsworkflow prüft Image-Build, Compose, Backup, Restore, Resume und Application-Rollback gemeinsam. Tabellen, Trigger oder Liquibase-Metadaten niemals manuell entfernen, um einen Anwendungsrollback zu erzwingen.

## Nachweismatrix für Paket 12.6

| Risiko / Muss-Kriterium | Produktionspfad | Automatisierter Nachweis |
|---|---|---|
| Crashsichere Registrierung und Versionssupersession | Migration 020, Submission-Trigger, `PostgresRecordLiveEvaluationStore` | `RecordLiveEvaluationMigrationIT`, `PostgresRecordLiveEvaluationStoreIT` |
| Exklusives Claiming, Lease-Takeover und stale Token | `RecordLiveEvaluationCoordinator`, Store-Fencing | `RecordLiveEvaluationCoordinatorTest`, `PostgresRecordLiveEvaluationStoreIT` |
| Heartbeat einschließlich technischer Fehler und Lease-Verlust | Coordinator-Heartbeat mit Stop-and-Drain-Grenze | `RecordLiveEvaluationCoordinatorTest` |
| Retry und begrenzter exponentieller Backoff | Coordinator plus `next_retry_at` | Coordinator-Unit- und PostgreSQL-Store-Tests |
| Unbekannte technische Fehler bleiben sichtbar | Coordinator und Processor lassen unbekannte Fehler entkommen | Coordinator-Unit- und Store-Integrationstests |
| Kurze, atomare State-/Event-/Announcement-Transaktion | `RecordLiveEvaluationProcessor`, `RecordStateService`, PostgreSQL-Adapter | `PostgresRecordLiveEvaluationProcessorIT` mit Fehlpunkten nach State, Append, Invalidierung, Announcement und Terminal-Write |
| Bereits persistiertes Nutzerergebnis bleibt erhalten | Evaluationsauftrag folgt auf kanonischen Ergebnis-Commit | Processor-Rollback- und End-to-End-Integrationstests |
| Konkurrenz von Submission und Korrektur | Generationen-Re-Read, State-Snapshot und Lock-Version-CAS | Processor-ITs mit getrennten Verbindungen und Latches |
| Retry, Replay und Restart erzeugen keine Duplikate | stabile Event-/Aggregationsschlüssel und tokengebundene Abschlüsse | Processor- und Store-Integrationstests |
| Bootstrap-Readiness und stille Ursprünge | `RecordBootstrapReadService`, Origin-Regeln | Processor-Unit- und PostgreSQL-Tests |
| Korrektur, Teilreduktion, Fallback, Delete und Laufreconciliation | exakter Correction-/Repair-Pfad | `PostgresRecordLiveEvaluationProcessorIT` |
| `EXTERNALLY_REMOVED` bleibt terminal | Announcement-Reconciliation | Processor-Integrationstest für extern entfernte Fakten |
| Outcome-Tags bleiben geschlossen und niedrig kardinal | `MicrometerRecordLiveEvaluationMetrics` | `MicrometerRecordLiveEvaluationMetricsTest` |
| Produktionsimage und Operationswege bleiben funktionsfähig | `Container image`-Workflow | Image-Inhalt, Compose, Backup, Restore, Resume und Application-Rollback |

## Verbindliche Gates

```bash
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

Für den finalen PR-Head muss nach `Ready for review` zusätzlich der vollständige Workflow `Container image` erfolgreich sein. Ein Discord-Smoke-Test gehört erst zu Paket 12.8 beziehungsweise zur End-to-End-Abnahme und ist kein Bestandteil von Paket 12.6.
