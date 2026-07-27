# GridWords Bot

Discord-Bot für das tägliche gemeinsame Spielen von GridWords und QuadWords.

Die fachliche Grundlage steht in [`docs/anforderungsspezifikation.md`](docs/anforderungsspezifikation.md).

## Aktueller Stand

Dieser Branch enthält das technische Grundgerüst:

- Java 21
- Spring Boot
- JDA
- PostgreSQL über Docker Compose
- Liquibase
- externe Konfiguration
- optionale Discord-Gateway-Verbindung

Die eigentliche Ergebnisverarbeitung wird in den nächsten Inkrementen umgesetzt.

## Lokale Voraussetzungen

- JDK 21
- Maven 3.9 oder neuer
- Docker Desktop beziehungsweise Docker Engine mit Compose
- Git

## Lokaler Start

1. Repository klonen und den gewünschten Branch auschecken.
2. Lokale Konfiguration erzeugen:

   ```powershell
   Copy-Item .env.example .env
   ```

3. In `.env` den echten Bot-Token eintragen. Die Datei wird nicht versioniert.
4. PostgreSQL starten:

   ```powershell
   docker compose up -d postgres
   ```

5. Zunächst ohne Discord-Verbindung bauen:

   ```powershell
   mvn clean verify
   ```

6. In `.env` setzen:

   ```text
   DISCORD_ENABLED=true
   ```

7. Anwendung starten:

   ```powershell
   mvn spring-boot:run
   ```

Im Log muss anschließend die erfolgreiche Discord-Verbindung mit Bot-Name und Bot-ID erscheinen. Der Bot wird in Discord als online angezeigt.

## Stoppen

Die Anwendung mit `Ctrl+C` beenden. PostgreSQL bei Bedarf stoppen:

```powershell
docker compose down
```

Die Datenbankdaten bleiben im Docker-Volume erhalten. Zum vollständigen lokalen Zurücksetzen:

```powershell
docker compose down -v
```

## Geheimnisse

Der Discord-Bot-Token darf niemals in Git, einen Chat, einen Screenshot oder einen Codex-Prompt gelangen. Er gehört ausschließlich in die lokale `.env`-Datei beziehungsweise später in den Secret Store des Hosts.
