# ADR 0004: Docker-optionale lokale Entwicklung

- **Status:** akzeptiert
- **Datum:** 29. Juli 2026
- **Entscheidungsträger:** Tobias / Projektarchitektur

## Kontext

Der Bot wird unter Windows entwickelt. Docker Desktop ließ sich auf dem primären Entwicklungsrechner trotz erheblichem Einrichtungsaufwand nicht zuverlässig in Betrieb nehmen. Der Discord-Gateway-Smoke-Test wurde erfolgreich ohne Docker und ohne PostgreSQL über das Spring-Profil `offline` durchgeführt.

Docker ist für Parser, Domainlogik, Application Services, Discord-Adaptertests und den realen Discord-Gateway-Smoke-Test technisch nicht erforderlich. Erst Persistenzintegration und ein späterer vollständiger lokaler Anwendungsstart benötigen PostgreSQL.

Die lokale Entwicklungsfähigkeit darf deshalb nicht von Docker Desktop oder einer anderen Container-Runtime abhängen.

## Entscheidung

1. **Docker Desktop ist keine lokale Projektvoraussetzung.**
2. Der lokale Standardbuild

   ```bash
   mvn --batch-mode --no-transfer-progress clean verify
   ```

   muss ohne Container-Runtime, ohne PostgreSQL und ohne Discord-Token erfolgreich sein.
3. Der Discord-Gateway-Smoke-Test läuft mit dem Profil `offline`, aktiviertem Discord und lokalem Token. Eine Datenbank wird dafür nicht gestartet.
4. Parser-, Domain-, Application-, Architektur- und Discord-Adaptertests laufen lokal vollständig ohne Docker.
5. PostgreSQL-Integrationstests werden in einem eindeutig benannten Maven-Profil, vorgesehen `database-integration`, ausgeführt.
6. GitHub Actions führt dieses Profil in einer Umgebung mit verfügbarer Container-Runtime aus. Diese Tests dürfen in CI nicht unbemerkt übersprungen werden.
7. Für eine manuelle lokale Ausführung mit Persistenz wird eine **nativ installierte PostgreSQL-Instanz** unterstützt. Die Anwendung wird dabei mit dem Profil `database` gestartet und über `DATABASE_URL`, `DATABASE_USERNAME` und `DATABASE_PASSWORD` konfiguriert.
8. `compose.yaml` bleibt als optionale Komfortlösung für Entwickler oder Hosts mit funktionierender Docker-/Compose-Umgebung erhalten. Seine Verwendung ist weder für den lokalen Standardbuild noch für den Discord-Smoke-Test verpflichtend.
9. Testcontainers darf für CI-Integrationstests eingesetzt werden. Seine Nutzung darf nicht dazu führen, dass der normale lokale Build eine Container-Runtime voraussetzt.
10. Ein späteres Produktionsdeployment darf weiterhin Container verwenden; diese Entscheidung betrifft ausschließlich die lokale Entwicklungs- und Testpflicht.

## Begründung

- Der größte Teil der Anwendung ist bewusst unabhängig von Infrastruktur testbar.
- Ein lokaler Infrastrukturzwang würde die Entwicklung ohne fachlichen Nutzen blockieren.
- PostgreSQL-spezifisches Verhalten muss dennoch gegen eine echte PostgreSQL-Instanz geprüft werden; dafür ist die reproduzierbare CI-Umgebung geeignet.
- Eine native lokale PostgreSQL-Installation ermöglicht bei Bedarf manuelle Persistenztests ohne Docker Desktop.
- Die Trennung verhindert, dass Integrationstests versehentlich gar nicht ausgeführt werden: lokal sind sie explizit optional, in CI explizit verpflichtend.

## Folgen

### Positiv

- Tobias kann ohne Docker Desktop entwickeln und den Bot starten.
- Der schnelle lokale Feedbackzyklus bleibt für die meisten Inkremente vollständig erhalten.
- PostgreSQL-spezifische Migrationen, Constraints und Konfliktfälle werden weiterhin automatisiert geprüft.
- `compose.yaml` bleibt als optionale Alternative nutzbar.

### Negativ

- Der lokale Standardbuild und der vollständige CI-Build verwenden unterschiedliche Maven-Profile.
- Fehler in PostgreSQL-Integrationstests können bei einer rein lokalen Entwicklung erst in GitHub Actions sichtbar werden.
- Für einen vollständigen manuellen lokalen Persistenzbetrieb ist PostgreSQL separat zu installieren und zu pflegen.

## Umsetzungsregeln für spätere Persistenzinkremente

- Das Maven-Profil für Datenbankintegration muss klar dokumentiert sein.
- GitHub Actions muss den vollständigen Profil-Build ausführen.
- Der normale lokale Build darf keine Testcontainers-Initialisierung versuchen.
- CI muss erkennbar fehlschlagen, wenn die vorgesehenen Datenbankintegrationstests nicht ausgeführt wurden.
- Liquibase-Migrationen und Persistence-Adapter werden gegen echtes PostgreSQL getestet, nicht gegen H2.
- Manuelle lokale Datenbanktests verwenden dieselben Liquibase-Migrationen und dieselbe `application-database.yml` wie der spätere Betrieb.

## Alternativen

### Docker Desktop weiterhin zwingend voraussetzen

Verworfen, weil es den primären Entwicklungsrechner blockiert und für den Großteil der Arbeit nicht benötigt wird.

### H2 für alle lokalen und CI-Tests verwenden

Verworfen, weil H2 Unterschiede bei PostgreSQL-Datentypen, Constraints, SQL und Konfliktverhalten verschleiern kann.

### Alle Datenbanktests ausschließlich manuell durchführen

Verworfen, weil Migrationen, Eindeutigkeiten und Idempotenz automatisiert und reproduzierbar geprüft werden müssen.

### PostgreSQL lokal immer nativ voraussetzen

Verworfen als Standardvoraussetzung. Eine native Installation wird unterstützt, soll aber den schnellen Standardbuild nicht belasten.
