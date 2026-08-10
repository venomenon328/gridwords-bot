# Entwicklungsumgebung

## Voraussetzungen

- JDK 21
- Maven Wrapper oder Maven 3.x
- Docker Desktop mit Docker Compose für PostgreSQL-Integration und Containerprüfungen
- Git

Prüfung unter PowerShell:

```powershell
java --version
mvn --version
docker version
docker compose version
```

## Lokale Konfiguration

Geheime Werte gehören ausschließlich in lokale, ignorierte Environment-Dateien oder Betriebssystemvariablen. Ausgangspunkt ist `.env.example`; `.env` wird nicht committed. `DISCORD_BOT_TOKEN` darf weder in Logs noch Fixtures oder Testreports erscheinen. Betriebssystemvariablen haben Vorrang vor `.env`.

Die lokale Compose-Datenbank verwendet standardmäßig:

```properties
POSTGRES_DB=gridwords
POSTGRES_USER=gridwords
POSTGRES_PASSWORD=gridwords-local
POSTGRES_PORT=5432
DATABASE_URL=jdbc:postgresql://localhost:5432/gridwords
DATABASE_USERNAME=gridwords
DATABASE_PASSWORD=gridwords-local
```

## PostgreSQL starten

```powershell
docker compose up -d postgres
docker compose ps
docker compose exec postgres pg_isready -U gridwords -d gridwords
```

Die Datenbank ist anschließend mit folgendem Befehl erreichbar:

```powershell
docker compose exec postgres psql -U gridwords -d gridwords
```

`docker compose down` stoppt die Umgebung und erhält das Volume. `docker compose down -v` löscht die lokalen Daten unwiederbringlich und darf nur bewusst für einen vollständigen Neuaufbau verwendet werden.

## Anwendung starten

Der Offline-Modus öffnet keine Discord-Verbindung:

```powershell
$env:SPRING_PROFILES_ACTIVE = "offline"
mvn spring-boot:run
```

Mit lokaler Datenbank:

```powershell
docker compose up -d postgres
mvn "-Dspring-boot.run.profiles=database" spring-boot:run
```

Ein echter Discord-Start benötigt die lokal gesetzten Guild-, Channel- und Tokenwerte. Tests dürfen niemals eine echte Discord-Verbindung öffnen.
