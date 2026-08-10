# Operations-Runbooks

Diese Dokumente sind die aktiven, ausführbaren Betriebsanleitungen für die produktive Umgebung. Fachliche Regeln stehen unter [`../product/`](../product/overview.md), technische Invarianten unter [`../architecture/production.md`](../architecture/production.md). Historische Rollout- und Abnahmenachweise liegen unter [`../history/releases/`](../history/releases/).

| Runbook | Zweck |
|---|---|
| [`server-bootstrap.md`](server-bootstrap.md) | Debian-Server, Docker, Verzeichnisse und Erstkonfiguration vorbereiten |
| [`deployment.md`](deployment.md) | unveränderliches Image sicher deployen und prüfen |
| [`backup-restore.md`](backup-restore.md) | Backups erzeugen, validieren und kontrolliert wiederherstellen |
| [`troubleshooting.md`](troubleshooting.md) | allgemeine Health-, Log-, Datenbank- und Deliverydiagnose |
| [`records.md`](records.md) | Rekord-Bootstrap, Live-Auswertung, Claims, Recovery und Delivery |
| [`achievements.md`](achievements.md) | Achievement-Bootstrap, Reconciliation, Commands und Delivery |

Vor mutierenden Reparaturen immer den Zustand lesend sichern, Ziel und Rückweg bestimmen und ein validiertes Backup erstellen. Keine Claim-Tokens, Secrets, Roh-Shares oder vollständigen internen Logs in Tickets oder Chats kopieren. Application-Rollback und Datenbank-Restore bleiben getrennte Vorgänge.
