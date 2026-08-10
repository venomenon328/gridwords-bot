# Produktionsarchitektur

## Zielumgebung

Die Produktion läuft containerisiert auf einem Debian-13-VPS. Docker Engine und Docker Compose betreiben den Bot und PostgreSQL in einem privaten Compose-Netz. PostgreSQL- und Managementports werden nicht öffentlich veröffentlicht; administrativer Zugang erfolgt über SSH.

## Images und Laufzeit

Das Anwendungsimage wird mehrstufig gebaut und enthält zur Laufzeit weder Quellen noch Maven-Cache. Der Prozess läuft als Nicht-Root-Benutzer. Basisimages und PostgreSQL sind auf überprüfte Versionen und Digests gepinnt. Die produktive Compose-Konfiguration verwendet PostgreSQL 16.

Deployments referenzieren unveränderliche GHCR-Tags, bevorzugt Commit-SHA und ergänzend freigegebene SemVer-Tags. `latest` ist keine reproduzierbare Produktionsreferenz. Das Repository führt kein automatisches Produktionsdeployment aus GitHub Actions durch.

## Secrets und Netzwerk

Produktive Tokens, Passwörter und Schlüssel existieren nur serverseitig, werden über eine restriktiv berechtigte Runtime-Environment-Datei bereitgestellt und weder in Image noch Repository aufgenommen. Anwendungs-, Datenbank- und Managementzugriffe sind auf das notwendige interne Netz beschränkt.

## Daten und Backups

PostgreSQL-Daten liegen auf einem dauerhaften Volume. Vor jedem Deployment wird ein atomar erzeugtes und validiertes Datenbankbackup erstellt. Aufbewahrung und Wiederherstellung folgen den aktiven Runbooks. Anwendung-Rollback und Datenbank-Restore sind getrennte Entscheidungen; ein älteres Image impliziert keinen automatischen Schema-Rollback.

## Deployment und Verifikation

Das Deployment validiert Eingaben und Compose-Konfiguration, zieht den expliziten Image-Tag, startet die Services und prüft Health sowie Logs. Fehler führen nicht zu stillem Weiterlaufen. Der produktive Ablauf und sichere Rückwege stehen in [`../operations/deployment.md`](../operations/deployment.md), [`../operations/backup-restore.md`](../operations/backup-restore.md) und [`../operations/troubleshooting.md`](../operations/troubleshooting.md).
