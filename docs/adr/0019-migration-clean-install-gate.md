# ADR 0019: Reproduzierbarer Migration-Clean-Install-Gate

- Status: akzeptiert
- Datum: 2026-08-07
- Kontext: Issue #83, PR #84

## Kontext

Der Standardbuild kennt keine PostgreSQL-Integration. Das bestehende Profil
`database-integration` prüft die vollständige Persistenzmatrix. Ein verlangter
separater Migration-Clean-Install-Aufruf ohne zugehöriges Maven-Profil würde
hingegen mit einer Maven-Warnung nur den Standardbuild ausführen und keinen
Migrationsnachweis liefern.

## Entscheidung

Das Maven-Profil `migration-clean-install` bindet die bestehenden
`src/integration-test/java/**/*MigrationIT.java`-Tests mit denselben
Testcontainers-Abhängigkeiten wie `database-integration` ein. Der Aufruf

```text
mvn --batch-mode --no-transfer-progress -Pmigration-clean-install verify
```

umfasst weiterhin den Standardbuild und führt anschließend die
PostgreSQL-Migrations-Integrationstests aus. Die Tests validieren leer
installierte Schemata sowie die vorhandenen produktionsrelevanten Upgradepfade.

`migration-clean-install` ist ein gezielter Diagnose- und Abnahme-Gate. Er
ersetzt weder `database-integration` noch dessen vollständige
Konkurrenz-, Recovery- und Delivery-Matrix.

## Folgen

- Der verlangte Befehl ist ein echter, fehlertoleranzloser PostgreSQL-Testlauf.
- Migrationsregressionen bleiben schneller isolierbar als im Vollprofil.
- Testcontainers und Docker bleiben nur für das explizite Profil erforderlich;
  der Standardbuild bleibt infrastrukturfrei.
