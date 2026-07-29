# GridWords Bot

Discord-Bot für das tägliche gemeinsame Spielen von GridWords und QuadWords.

## Projektdokumentation

- [`docs/anforderungsspezifikation.md`](docs/anforderungsspezifikation.md) – verbindliche fachliche Grundanforderungen
- [`docs/requirements/series-model.md`](docs/requirements/series-model.md) – abgenommene Präzisierung für Aktivitäts-, Komplett-, Lösungs- und Perfektserien
- [`docs/architecture.md`](docs/architecture.md) – Zielarchitektur und Modulgrenzen
- [`docs/implementation-plan.md`](docs/implementation-plan.md) – geplante Inkremente und Reihenfolge
- [`docs/development-guide.md`](docs/development-guide.md) – lokaler Build, Tests, Secrets und Codex-Workflow
- [`docs/adr/`](docs/adr/) – akzeptierte Architekturentscheidungen
- [`AGENTS.md`](AGENTS.md) – automatisch heranzuziehende Arbeitsregeln für Codex

## Aktueller Stand

Der aktuelle Projektstand umfasst:

- Java 21, Spring Boot und JDA-Grundgerüst
- deterministische GridWords- und QuadWords-Share-Parser
- PostgreSQL-Persistenzadapter und Liquibase-Migrationen
- idempotente Spieler-, Ergebnis- und Submission-Speicherung
- wiederaufnehmbaren Submission-Zustand mit konfliktfesten Replays
- beobachtenden Discord-Inbound-Ablauf für die zwei konfigurierten Spieler
- `✅` nach erfolgreich gespeicherten Ergebnissen und `⚠️` nach persistent abgelehnten Shares
- gekapselte PostgreSQL-Integrationstests im Maven-Profil `database-integration`
- optionale Docker-Compose-Konfiguration
- externe Konfiguration und optionale Discord-Gateway-Verbindung

Die Inkremente 0 bis 2 sind abgeschlossen. Inkrement 3 befindet sich in Draft-PR #8: Der JDA-Listener filtert und beobachtet neue Shares, ohne Originalnachrichten zu verändern. Sichere Nachrichtenersetzung, Tagesstatus, Serien und Erinnerungen bleiben bewusst späteren Inkrementen vorbehalten.

Der lokale Standardbuild umfasst Unit-, Parser-, Application-, Architektur- und Discord-Adaptertests. GitHub Actions führt zusätzlich PostgreSQL-Integrationstests gegen echtes PostgreSQL aus.

Der reale Discord-Gateway-Smoke-Test wurde am 29. Juli 2026 mit lokalem Token erfolgreich **ohne Docker und ohne PostgreSQL** durchgeführt. Der Bot erschien im vorgesehenen Testserver online und die JDA-Verbindung wurde erfolgreich aufgebaut.

Die Seriensemantik wurde nachträglich präzisiert: Persönlich werden Aktivität, vollständige tägliche Erledigung, die Lösungsserien beider Spiele und perfekte Tage getrennt betrachtet; gemeinsam gibt es eine Komplett- und eine Perfektserie. Maßgeblich ist das verlinkte Serienmodell.

## Lokale Voraussetzungen

Für den normalen lokalen Build und die Discord-/Fachlogikentwicklung werden nur benötigt:

- Git
- JDK 21
- Maven 3.9 oder neuer
- VS Code beziehungsweise eine andere IDE

**Docker Desktop ist keine Projektvoraussetzung.**

Optional werden später benötigt:

- eine nativ installierte PostgreSQL-Instanz für einen manuellen lokalen Start mit Persistenz oder
- eine funktionierende Docker-/Compose-Umgebung als alternative Komfortlösung.

PostgreSQL-Integrationstests laufen im Maven-Profil `database-integration` und verpflichtend in GitHub Actions.

## Standardbuild ohne externe Systeme

Der verbindliche lokale Standardbuild lautet:

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

Er muss funktionieren ohne:

- Discord-Token,
- Discord-Netzwerkverbindung,
- PostgreSQL,
- Docker beziehungsweise eine andere Container-Runtime.

GitHub Actions führt zusätzlich die für das jeweilige Inkrement vorgesehenen vollständigen Integrationstests aus.

## Lokale Konfiguration

Die Anwendung importiert optional die lokale Datei `.env`; sie bleibt durch `.gitignore` vom Repository ausgeschlossen. Betriebssystem-Umgebungsvariablen haben Vorrang.

Unter PowerShell:

```powershell
Copy-Item .env.example .env
```

`DISCORD_BOT_TOKEN` wird ausschließlich lokal in `.env` eingetragen und niemals committed, in einen Chat kopiert oder an Codex übergeben.

## Discord-Start ohne Docker und PostgreSQL

In `.env`:

```properties
DISCORD_BOT_TOKEN=DEIN_LOKALER_TOKEN
DISCORD_ENABLED=true
```

Danach:

```powershell
$env:SPRING_PROFILES_ACTIVE = "offline"
mvn spring-boot:run
```

Das Profil `offline` deaktiviert die Datenbank-Autokonfiguration, nicht aber die über `DISCORD_ENABLED=true` ausdrücklich aktivierte Discord-Verbindung.

Bei erfolgreichem Start erscheint sinngemäß:

```text
Discord connection ready as <Bot-Name> (application user id <ID>).
```

Im Profil `offline` bleibt der Gateway-Start ohne Datenbank möglich; Ergebnisverarbeitung ist dort bewusst nicht aktiviert.

## Lokaler Start mit PostgreSQL

Für spätere Persistenzentwicklung wird eine nativ installierte PostgreSQL-Instanz unterstützt. Die Zugangsdaten werden in `.env` gesetzt:

```properties
DATABASE_URL=jdbc:postgresql://localhost:5432/gridwords
DATABASE_USERNAME=gridwords
DATABASE_PASSWORD=lokales-passwort
```

Start:

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=database
```

Discord kann dabei unabhängig über `DISCORD_ENABLED=true` aktiviert werden.

## Manueller Inbound-Smoke-Test (durch Tobias)

Der Test ist erst mit einer lokalen PostgreSQL-Instanz und lokal hinterlegtem Discord-Token im Profil `database` sinnvoll. Bei optionaler Compose-Nutzung verwendet `compose.yaml` dieselbe fest gepinnte Image-Version wie CI: `postgres:16.6-alpine`. Der Test wird nicht durch den Standardbuild ersetzt und wurde von Codex nicht ausgeführt.

1. Eine normale Nachricht von Tobias im Zielchannel senden: keine Reaktion, kein Submission-Datensatz.
2. Eine Nachricht in einem anderen Channel senden: keine Verarbeitung.
3. Ein gültiges GridWords-Share für heute oder gestern senden: erst nach Speicherung `✅`; Ergebnis und Submission prüfen.
4. Ein gültiges QuadWords-Share mit plausiblem Bildanhang senden: erst nach Speicherung `✅`; Ergebnis und Submission prüfen.
5. Ein erkennbar ungültiges Share senden: `⚠️`, Submission mit `PARSE_REJECTED`, kein Ergebnis.
6. Den Bot ohne neue Discord-Nachricht neu starten: Der Gateway stellt alte Nachrichten dabei nicht erneut zu; entsprechend dürfen keine neuen Submission-/Ergebniszeilen oder Reaktionen entstehen.
7. Eine Korrektur als **neue** gültige Discord-Nachricht für denselben Spieler, Spieltyp und Spieltag senden: neue Submission, aber weiter genau ein aktualisiertes fachliches Ergebnis.
8. Prüfen, dass alle Originalnachrichten unverändert bleiben; Inkrement 3 löscht und ersetzt keine Nachrichten. Der identische Source-Event-Replay ist automatisiert abgedeckt, nicht manuell durch einen normalen Neustart erzwingbar.

## Optionale Docker-Compose-Nutzung

`compose.yaml` bleibt als optionale Alternative erhalten und verwendet wie die CI-Integrationstests `postgres:16.6-alpine`. Auf einem Rechner mit funktionierender Container-Runtime kann PostgreSQL weiterhin so gestartet werden:

```bash
docker compose up -d postgres
```

Docker ist jedoch weder für den Standardbuild noch für den Discord-Smoke-Test verpflichtend.

## Teststrategie

- Unit-, Parser-, Domain-, Application-, Architektur- und Discord-Adaptertests laufen lokal ohne Container.
- Der normale lokale `mvn verify` startet keine Testcontainers-Umgebung.
- PostgreSQL-Integrationstests: `mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify` (Container-Runtime erforderlich).
- GitHub Actions führt dieses Profil in einer Umgebung mit verfügbarer Container-Runtime verpflichtend aus.
- Ein vollständiger manueller Persistenzstart kann lokal gegen eine native PostgreSQL-Installation erfolgen.

Die verbindliche Entscheidung steht in [`docs/adr/0004-docker-optional-local-development.md`](docs/adr/0004-docker-optional-local-development.md).

## Geheimnisse

Der Discord-Bot-Token darf niemals in Git, einen Chat, ein Issue, einen Screenshot oder einen Codex-Prompt gelangen. Er gehört ausschließlich in eine lokale, nicht versionierte Konfiguration beziehungsweise später in den Secret Store des Hosts.

## Inkrement 4: manueller Canonical-Message-Smoke-Test

Nach grünem CI und mit Profil `database`, PostgreSQL 16.6 sowie lokalem Discord-Token prüft Tobias im Zielchannel: gültiges GridWords bleibt als Original erhalten, genau ein kanonisches Embed erscheint und `✅` folgt erst danach; eine neue Korrektur bearbeitet dieses Embed; ein Neustart erzeugt keine Duplikate; QuadWords reagiert weiter nur mit `✅`; ungültige Shares weiter mit `⚠️`. Optional kann das kanonische Embed gelöscht und die kontrollierte Wiederherstellung durch eine Korrektur geprüft werden. Dieser Test wurde von Codex nicht ausgeführt.