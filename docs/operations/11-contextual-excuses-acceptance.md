# Abnahmeprotokoll Inkrement 11

Dieses Protokoll bündelt die Gesamtregression und die reale Discord-Abnahme für Issue #42. Es ergänzt Requirements und ADR 0017, ersetzt sie aber nicht.

## Freigaberegel

Die reale Abnahme erfolgt ausschließlich mit einer separaten Discord-Testanwendung, einem separaten Testserver/-channel und einer isolierten PostgreSQL-Datenbank. `EXCUSES_ENABLED=true` ist nur in dieser nicht versionierten Testkonfiguration zulässig; der produktive Default bleibt `false`.

Keine Tokens, konkreten Server-, Channel- oder Message-IDs sowie keine Rohshares werden hier, in Issues oder Pull Requests festgehalten. Vor dem Merge von PR #46 und dem Schließen von Issue #42 müssen alle Punkte dieses Dokuments als bestanden dokumentiert sein.

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
| Unveränderte Basisfunktionen | Parser-, Ergebnis-, Serien-, Status-, Reminder- und Reporttests im Standard- und PostgreSQL-Profil |

Auf dem finalen Head ausführen:

```powershell
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

Erst wenn diese automatisierten Prüfungen **und** die untenstehende reale Discord-Abnahme dokumentiert bestanden sind, den Pull Request auf **Ready for review** setzen. Dann den vorhandenen GitHub-Workflow für Image, Compose, Backup, Restore, Resume und Rollback abwarten; auch dieser ist vor dem Merge verpflichtend grün.

### Aktueller automatisierter Nachweis

Am Paket-8B-Stand wurden `mvn --batch-mode --no-transfer-progress clean verify` mit 579 Tests, das vollständige PostgreSQL-Profil mit zusätzlich 171 Integrationstests sowie die 72 fokussierten Codec-, JDA-, Interaction- und Refresh-Handoff-Tests erfolgreich ausgeführt. Das lokale Produktionsimage bestand die Nicht-Root- und Secretfreiheitsprüfung.

Der vollständige Bash-Betriebsworkflow konnte auf diesem Windows-Host nicht lokal gestartet werden, weil keine Linux-Bash installiert ist. Er bleibt als verpflichtender GitHub-Actions-Nachweis vor Merge vorgesehen; der Fehler ist eine Hostvoraussetzung, kein abgebrochener Produkt- oder Container-Test.

## Reale Discord-Abnahme

**Status:** ausstehend
**Testumgebung:** separate Testanwendung und isolierte Testdatenbank; keine Produktionsdaten

- [ ] Qualifizierendes GridWords-Ergebnis zeigt einen öffentlichen Button.
- [ ] Qualifizierendes QuadWords-Ergebnis mit Boards zeigt einen passenden Flow; ein boardloses QuadWords-Ergebnis behauptet keinen Boardkontext.
- [ ] Öffnen liefert genau drei ephemere Vorschläge mit Stilnamen.
- [ ] Ein Stil-Neuwurf liefert die persistierte Stilrunde genau einmal.
- [ ] Auswahl ergänzt nur den Text ohne Stil in derselben kanonischen Message-ID.
- [ ] Verzicht erzeugt keine öffentliche Zusatznachricht und entfernt den Button per kanonischem Refresh.
- [ ] Ablauf entfernt den Button per kanonischem Refresh.
- [ ] Fremder Nutzer, manipulierte Component und Doppelklick ändern keinen Zustand und keine öffentliche Nachricht zusätzlich.
- [ ] Korrektur und Boardanreicherung bewahren oder invalidieren den Snapshot gemäß persistiertem Kontext.
- [ ] Neustart nach persistierter Terminalentscheidung aktualisiert dieselbe kanonische Message-ID.
- [ ] Externe Löschung wird durch die vorhandene kanonische Recovery-Pipeline repariert; ein Retirement-Fence verhindert Wiederveröffentlichung.

## Stabilitätsprotokoll

| Vergleich | Erwarteter Nachweis | Ergebnis |
|---|---|---|
| Auswahl, Verzicht, Ablauf, Invalidierung, Restart | dieselbe kanonische Message-ID, solange keine externe Löschung vorliegt | ausstehend |
| Externe Löschung | kontrolliertes Recreate mit persistierter neuer kanonischer ID | ausstehend |
| Ergebnis, Serien, Tagesstatus, Reminder, Berichte | unverändert außerhalb des optionalen Ausredenanteils | ausstehend |

Ein bestandener Live-Test wird ohne IDs, Tokens, Rohshares oder Personenbezug als Datum, geprüfter Commit und zusammengefasste Beobachtung ergänzt. Erst dann dürfen Paket 8B als abgenommen, PR #46 gemergt und Issue #42 geschlossen werden.
