# Abnahmeprotokoll Inkrement 11

Dieses Protokoll bündelt Gesamtregression, reale Discord-Abnahme, Produktions-Gate und produktiven Rollout für Issue #42. Es ergänzt die Requirements und ADR 0017.

## Ergebnis

**Status:** bestanden und produktiv ausgerollt  
**Abnahmedatum:** 2026-08-04  
**Produktionsrelease:** 1.2.0  
**Finaler Feature-Head:** `771c7b9431b990351c7f1122126ed15e2a3c172b`  
**Main-Merge-Commit:** `cc269099f7fbcab832e263e01c2605fcb3ebb227`  
**Release-Workflow:** `30937060488`  
**Reale Testbasis:** separate Discord-Testanwendung, separater Testserver/-channel und isolierte PostgreSQL-Datenbank

Die reale Abnahme wurde nach der Korrektur des produktiven Lifecycle-Wirings und der ephemeren Interaction-UX erfolgreich bestätigt. Release 1.2.0 wurde anschließend über den vollständig grünen manuellen Containerworkflow veröffentlicht und als unveränderliches SHA-Image produktiv deployt.

Es wurden keine Tokens, Discord-IDs, Rohshares oder personenbezogenen Testdaten versioniert.

## Automatisierte Matrix

| Bereich | Nachweis |
|---|---|
| Schwellen, Spiele und Boards | `ExcuseEligibilityPolicyTest`, `QuadWordsBoardAnalysisTest`, `ExcuseResultLifecycleIntegrationIT` |
| Katalog `2026.08.04.1` | `ProductionExcuseCatalogTest`, `JsonExcuseCatalogLoaderTest` |
| Replays, Korrekturen und Boardanreicherung | `ExcuseResultLifecycleIntegrationIT`, `CanonicalGridWordsPublicationServiceTest` |
| Persistenz, Migration, Cooldown und Wiederholungsschutz | `ExcuseInteractionMigrationIT`, `PostgresExcuseStateStoreIT`, `ExcuseOpenServiceTest`, `ExcuseSelectorTest` |
| Autorisierung, Codec und ephemere Interaktionen | `ExcuseComponentCodecTest`, `ExcuseOpenInteractionListenerTest`, `ExcuseInteractionListenerTest`, `ExcuseOpenServiceTest`, `ExcuseInteractionServiceTest` |
| Ablauf, begrenzte Verarbeitung und Startup-Recovery | `ExcuseExpirationServiceTest`, `ExcuseExpirationSchedulerTest`, `DatabaseInboundStartupTest`, `PostgresCanonicalExcuseRecoveryIT` |
| Konkurrenz und atomarer Refresh-Handoff | `PostgresExcuseStateStoreIT`, `ExcuseInteractionServiceTest`, `PostgresCanonicalExcuseRecoveryIT` |
| Kanonische Create-/Edit-/Recreate-/Restart-Recovery | `CanonicalGridWordsPublicationServiceTest`, `JdaCanonicalMessageGatewayTest`, `PostgresCanonicalExcuseRecoveryIT` |
| Cleanup, Retirement und externe Nachrichtenlöschung | `ChannelMessageRetirementServiceTest`, `PostgresChannelMessageRetirementStoreIT`, `CanonicalGridWordsPublicationServiceTest` |
| Produktionswiring und Feature-Default | `ExcuseLifecycleConfigurationTest`, `EnabledExcuseLifecycleSpringWiringIT`, `DisabledExcuseLifecycleSpringWiringIT`, `GridwordsBotPropertiesTest` |
| Unveränderte Basisfunktionen | Parser-, Ergebnis-, Serien-, Status-, Reminder- und Reporttests im Standard- und PostgreSQL-Profil |

Am finalen Fachstand waren 582 Standardtests, das vollständige PostgreSQL-Profil und der gesamte GitHub-Containerworkflow grün.

## Reale Discord-Abnahme

- [x] Qualifizierende GridWords- und QuadWords-Ergebnisse zeigen den öffentlichen Öffnungsbutton nur im passenden Zustand.
- [x] Boardkontext wird nur verwendet, wenn reale QuadWords-Boards vorhanden sind.
- [x] Öffnen liefert genau drei ephemere Vorschläge mit Stilnamen.
- [x] Ein Stilwechsel ersetzt dieselbe ephemere Oberfläche und ist genau einmal zulässig.
- [x] Auswahl ergänzt ausschließlich den Ausredentext ohne Stilbezeichnung in derselben kanonischen Nachricht.
- [x] Verzicht erzeugt keine öffentliche Zusatznachricht und entfernt den Button über den kanonischen Refresh.
- [x] Terminalentscheidungen bereinigen die ephemere Oberfläche ohne zusätzliche private Nachrichten.
- [x] Fremde Nutzer, manipulierte Komponenten und Doppelklicks verändern weder Zustand noch öffentliche Nachricht unzulässig.
- [x] Korrekturen, Boardanreicherung und Neustart folgen dem persistierten Kontext und der kanonischen Recovery-Pipeline.
- [x] Externe Löschung und Retirement-Fence bleiben durch die vorhandene kanonische Recovery abgesichert.
- [x] Ergebnisverarbeitung, Serien, Tagesstatus, Reminder und Berichte blieben außerhalb des optionalen Ausredenanteils unverändert.

## Produktions-Gate

Der finale Workflow bestand:

- Standardbuild,
- PostgreSQL-Integration,
- Build und Prüfung des Produktionsimages,
- Nicht-Root- und Secretfreiheitsprüfung,
- Übergabe aller Ausredenvariablen durch `compose.production.yaml`,
- Erstdeployment,
- Backup und Restore,
- Resume nach unterbrochenem Deployment,
- absichtlich ausgelösten und verifizierten Rollback.

## Produktivrollout

Der versionierte sichere Default bleibt:

```properties
EXCUSE_GENERATOR_CONTEXTUAL_ENABLED=false
```

In der nicht versionierten Produktionskonfiguration wurde das abgenommene Feature bewusst aktiviert:

```properties
EXCUSE_GENERATOR_CONTEXTUAL_ENABLED=true
EXCUSE_OFFER_LIFETIME=PT15M
EXCUSE_EXPIRATION_PAGE_SIZE=25
EXCUSE_EXPIRATION_MAX_PAGES=4
```

Der produktive Rollout von Version 1.2.0 wurde am 4. August 2026 erfolgreich abgeschlossen. Container-Health, Liquibase-Migrationen 015 bis 017, Ausredentabellen, Laufzeitkonfiguration und unveränderliches SHA-Image wurden im Deploymentpfad geprüft.

## Abschluss

- [x] Paket 8B abgenommen
- [x] PR #46 nach `main` gemergt
- [x] Issue #42 geschlossen
- [x] Release 1.2.0 veröffentlicht
- [x] Version 1.2.0 produktiv deployt
- [x] Inkrement 11 abgeschlossen
