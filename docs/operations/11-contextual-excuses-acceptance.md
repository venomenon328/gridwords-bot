# Abnahmeprotokoll Inkrement 11

Dieses Protokoll bündelt die Gesamtregression, die reale Discord-Abnahme und den Produktions-Rollout-Gate für Issue #42. Es ergänzt die Requirements und ADR 0017.

## Ergebnis

**Status:** bestanden  
**Abnahmedatum:** 2026-08-04  
**Reale Testbasis:** separate Discord-Testanwendung, separater Testserver/-channel und isolierte PostgreSQL-Datenbank  
**Geprüfter Fachstand:** `065fa3a518ccf58f4099b494c380d6a341238935`

Die reale Abnahme wurde nach der Korrektur des produktiven Lifecycle-Wirings und der ephemeren Interaction-UX erfolgreich bestätigt. Es wurden keine Tokens, IDs, Rohshares oder personenbezogenen Testdaten versioniert.

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

Am abgenommenen Fachstand waren der Standardbuild mit 582 Tests sowie das vollständige PostgreSQL-Profil grün. Die echte Missing-Property-Semantik wird isoliert und unabhängig von Host-Environment-Variablen geprüft.

Der finale Merge-Head muss zusätzlich den GitHub-Workflow `Container image` vollständig grün durchlaufen. Dieser Workflow führt Standardbuild, PostgreSQL-Integration, Imageprüfung sowie Compose-, Backup-, Restore-, Resume- und Rollback-Tests aus.

## Reale Discord-Abnahme

- [x] Qualifizierende GridWords- und QuadWords-Ergebnisse zeigen den öffentlichen Öffnungsbutton nur im passenden Zustand.
- [x] Boardkontext wird nur verwendet, wenn reale QuadWords-Boards vorhanden sind.
- [x] Öffnen liefert genau drei ephemere Vorschläge mit Stilnamen.
- [x] Ein Stil-Neuwurf ersetzt dieselbe ephemere Oberfläche und ist genau einmal zulässig.
- [x] Auswahl ergänzt ausschließlich den Ausredentext ohne Stilbezeichnung in derselben kanonischen Nachricht.
- [x] Verzicht erzeugt keine öffentliche Zusatznachricht und entfernt den Button über den kanonischen Refresh.
- [x] Terminalentscheidungen bereinigen die ephemere Oberfläche ohne zusätzliche private Nachrichten.
- [x] Fremde Nutzer, manipulierte Komponenten und Doppelklicks verändern weder Zustand noch öffentliche Nachricht unzulässig.
- [x] Korrekturen, Boardanreicherung und Neustart folgen dem persistierten Kontext und der kanonischen Recovery-Pipeline.
- [x] Externe Löschung und Retirement-Fence bleiben durch die vorhandene kanonische Recovery abgesichert.
- [x] Ergebnisverarbeitung, Serien, Tagesstatus, Reminder und Berichte blieben außerhalb des optionalen Ausredenanteils unverändert.

## Produktionsfreigabe

Der versionierte Default bleibt deaktiviert:

```properties
EXCUSE_GENERATOR_CONTEXTUAL_ENABLED=false
```

`compose.production.yaml` reicht die folgenden Werte mit sicheren Defaults an den Bot-Container weiter:

```properties
EXCUSE_GENERATOR_CONTEXTUAL_ENABLED=false
EXCUSE_OFFER_LIFETIME=PT15M
EXCUSE_EXPIRATION_PAGE_SIZE=25
EXCUSE_EXPIRATION_MAX_PAGES=4
```

Die Aktivierung in Produktion ist eine bewusste Rolloutentscheidung in der nicht versionierten `/opt/gridwords-bot/runtime.env`. Vor dem Deployment muss der Betreiber den gewünschten Wert prüfen; für den produktiven Start des abgenommenen Features ist `EXCUSE_GENERATOR_CONTEXTUAL_ENABLED=true` zu setzen.

Nach grünem finalem Container-Gate dürfen der Integrations-PR nach `main` gemergt und Issue #42 geschlossen werden.
