# Inkrement 14 – UX- und QoL-Stärkung

**Status:** abgeschlossen, nach `main` gemergt und als Release **1.5.0** produktiv ausgerollt  
**Stand:** 10. August 2026  
**Umbrella-Issue:** #105  
**Gesamt-PR:** #111  
**Fachliche Grundlage:** [`../requirements/ux-qol.md`](../requirements/ux-qol.md)  
**Release:** 1.5.0  
**Produktiver Anwendungscode:** `e06fc8f9dfb4645c0db5cd3532c1eb7b8289f784`

## 1. Ziel

Inkrement 14 verbessert die Benutzerführung und Sichtbarkeit bereits vorhandener Bot-Funktionen. Es führt kein neues großes Fachsystem ein. Tagesstatus, Ergebnisdetails, Teilnahme-/Reminder-Antworten, Achievement-/Record-Commands, persönlicher Status und periodische Berichte werden gezielt erweitert.

Das Inkrement baut vollständig auf den produktiven Ständen der Inkremente 10–13 auf. Bestehende Fachlogik wird wiederverwendet; neue Anzeigeinformationen werden aus vorhandenen kanonischen Daten oder materialisierten Fachprojektionen gelesen.

## 2. Verbindlicher Umfang

Inkrement 14 umfasst:

- Statussymbol und Kurzinfo in den Ergebnis-Dropdowns der Tagesstatusnachricht,
- direkte Link-Buttons zu GridWords und QuadWords,
- gewählte Ausrede, aktuelle Ergebnisrekorde und Spieltag-Achievements in ephemeren Ergebnisdetails,
- verständlichere Teilnahmebestätigungen,
- klareren Reminderstatus einschließlich konfigurierter Zeiten,
- `/status` als persönliches Dashboard mit heutigem Zustand und allen fünf persönlichen Serien,
- `earnedOn` in `/achievements`,
- kombinierbare Filter für `/achievement-list`,
- Scope-Filter für `/records`,
- Achievement-Zahlen und vollständige Rekord-Highlights in Wochen-/Monatsberichten,
- End-to-End-Härtung und reale Discord-Abnahme der geänderten Pfade.

## 3. Architekturgrenzen

Für alle Pakete galt und gilt:

1. `docs/requirements/ux-qol.md` ist der verbindliche Inkrement-14-Nachtrag.
2. Bestehende Spiel-, Teilnahme-, Serien-, Ausreden-, Rekord- und Achievement-Semantik wird nicht dupliziert oder neu interpretiert.
3. Read-only Commands/Interactions dürfen keine Player-, Award-, Record-, Excuse- oder Delivery-Writes auslösen.
4. Read-Views verwenden vorhandene materialisierte Fachprojektionen beziehungsweise schmale read-only Ports; kein History-Scan oder Evaluator/Reconciler nur für eine Anzeige.
5. JDA bleibt am Adapterrand; Application/Domain kennen keine JDA-Typen.
6. Discord-I/O findet niemals innerhalb einer Datenbanktransaktion statt.
7. Keine Discord-/Announcement-Texte als fachliche Datenquelle.
8. Keine neue Persistenzwahrheit; Inkrement 14 benötigte **keine Liquibase-Migration**.
9. Vorhandene Paging-, Mention-, Idempotenz-, Retry-, Recovery- und historische Teilnahmegrenzen bleiben verbindlich.
10. Keine generischen UI-, Query-, Dashboard-, Gamification-, Event- oder Messaging-Frameworks.

Der produktive Schema-Endstand bleibt Migration **024 – Achievement Persistence**.

## 4. Branch-, PR- und Releaseverlauf

- Sammelbranch: `feature/14-ux-qol`.
- Pakete 14.1–14.4 wurden auf eigenen Arbeitsbranches umgesetzt und jeweils nach Review in den Sammelbranch gesquasht.
- Paket 14.5 härtete den kumulierten Stand ohne neuen Fachumfang ab.
- Gesamt-PR #111 wurde nach vollständiger technischer und realer Abnahme nach `main` gemergt.
- Der daraus hervorgegangene Anwendungscode `e06fc8f9dfb4645c0db5cd3532c1eb7b8289f784` wurde als Release **1.5.0** veröffentlicht und produktiv deployt.
- Der Produktiv-Smoke/Canary wurde am 10. August 2026 vom Betreiber als bestanden bestätigt.

Nachgelagerte reine Dokumentationscommits auf `main` ändern den Bezug des tatsächlich ausgerollten Images nicht.

## 5. Paketübersicht

| Paket | Issue | PR | Inhalt | Abschluss |
|---|---:|---:|---|---|
| 14.1 | #106 | #112 | Tagesstatus-Dropdowns, Spiel-Link-Buttons, angereicherte Ergebnisdetails | Squash `cb667a72cdf6fa7aabf0056938a00bf37a2b0071` |
| 14.2 | #107 | #113 | Teilnahmebestätigungen, Reminderstatus/-zeiten, `/status`-Dashboard | Squash `ee4a5e8bc639c7bfaf215c8ddc278d4d16d9e854` |
| 14.3 | #108 | #114 | `earnedOn`, `/achievement-list`-Filter, `/records`-Scopefilter | Squash `949115aa16fa4e4615bb17dbc7fb2cf95557c8ad` |
| 14.4 | #109 | #115 | Achievement-/Record-Highlights in periodischen Berichten | Squash `d4bf877a4c5bdcb00467bb1fbe3c19a3c97ef819` |
| 14.5 | #110 | #117 | Gesamthärtung, Operations-Gates und Abnahmevorbereitung | Squash `29fc6c0ab45570bcb4e7c7800ca484a7635cb0ff` |
| Gesamt | #105 | #111 | vollständiges Inkrement 14 | `main`-Merge `e06fc8f9dfb4645c0db5cd3532c1eb7b8289f784` |

## 6. Paket 14.1 – Tagesstatus und Ergebnisdetails UX

### Tagesstatus

- Statussymbol `✅`, `❌` oder `⬜` im Label,
- Description mit `n/max · Dauer`, `X/max · Dauer` oder `Noch nicht eingereicht`,
- bestehende spielbezogene Teilnehmermenge, Sortierung und Pagination unverändert,
- Präsentationsinformationen werden aus dem transportneutral vorhandenen Tagesstatus abgeleitet, ohne historische Nachrichten allein durch den Deploymentwechsel umzuschreiben.

### Spiel-Link-Buttons

- `🟩 GridWords spielen`,
- `🟦 QuadWords spielen`,
- reine Link-Buttons ohne Interaction/State,
- neue Tagesstatusnachrichten enthalten die Buttons,
- kein Sonderrefresh historischer oder unveränderter Nachrichten nur für die Buttons.

### Ergebnisdetails

Zusätzlich zur Ergebnis-/Boarddarstellung:

- SELECTED-Ausrede exakt aus persistentem Zustand,
- nur aktuelle Rekorde, deren kanonische GameResult-Quelle exakt Ergebnis-ID und Ergebnisversion entspricht,
- ACTIVE Awards des Spielers mit `earnedOn == gameDate`, nur Emoji + Anzeigename,
- leere optionale Blöcke entfallen vollständig.

Wesentliche Nachweise: `DailyStatusComponentRendererTest`, `JdaDailyStatusComponentsTest`, `DailyStatusFingerprintTest`, `DailyResultDetailsServiceTest`, `DailyResultDetailsInteractionListenerTest`, `DailyResultDetailsEmbedRendererTest`, `PostgresDailyResultDetailsQueryIT`.

## 7. Paket 14.2 – Persönlicher Status, Teilnahme und Reminder UX

- Join/Activate erklärt `ab heute aktiv` beziehungsweise idempotent `bereits aktiv`,
- Leave/Deactivate erklärt heutigen Zustand und Wirksamkeit ab morgen,
- Reminder on/off/status erklärt Mention versus Klartextname,
- `/reminders status` zeigt tatsächlich konfigurierte Zeiten,
- `/status` zeigt in dieser Reihenfolge Heute, alle fünf persönlichen Serien, Teilnahme/Reminder und letzte Einreichungen,
- `/status` ist strikt read-only und verwendet die bestehende Tagesstatus-/Seriensemantik,
- unbekannte `/status`-Aufrufer werden nicht implizit registriert.

Wesentliche Nachweise: `PlayerParticipationServiceTest`, `DiscordParticipationCommandListenerTest`, `PersonalStatusServiceTest`, `PersonalStatusEmbedRendererTest`, `PostgresPersonalStatusReadOnlyIT`.

## 8. Paket 14.3 – Achievement- und Record-Command UX

- `/achievements` zeigt `earnedOn` als fachliches Freischaltdatum,
- `/achievement-list` unterstützt kombinierbare Filter `game`, `category`, `status`,
- Global-Achievements erscheinen nur bei `game=Alle`,
- binäre ✅/❌-Semantik bleibt unverändert; kein quantitativer Progress,
- neutraler Leerzustand bei Filterkombination ohne Treffer,
- `/records` unterstützt `Alle|Persönlich|Serverweit|Gemeinsam`, kombinierbar mit bestehenden Filtern,
- bestehende Fremdansichts-Autorisierung greift vor Record-State-/Bootstrap-Zugriff,
- alle drei Commands bleiben read-only, ephemeral und mention-sicher,
- keine neuen Persistenzqueries oder Migrationen.

Wesentliche Nachweise: `AchievementsQueryServiceTest`, `AchievementsOverviewEmbedRendererTest`, `AchievementCatalogQueryServiceTest`, `AchievementCatalogEmbedRendererTest`, `DiscordAchievementCatalogCommandListenerTest`, `DiscordAchievementsCommandListenerTest`, `RecordsScopeFilterTest`, `RecordsQueryReadOnlyInvariantTest`, `DiscordRecordsCommandListenerTest`, `PostgresAchievementsQueryIT`.

## 9. Paket 14.4 – Highlights in Wochen- und Monatsberichten

### Achievements

- je Reportteilnehmer nur Anzahl ACTIVE Awards mit `earnedOn` in der Periode,
- Spieler mit 0 neuen Awards werden ausgelassen,
- keine einzelnen Achievement-Namen im Report.

### Rekorde

- Quelle sind VALID Record-Events mit fachlichem GameResult-Spieltag beziehungsweise Streak-Enddatum in der Periode,
- geeignete Eventtypen: `RESULT_RECORD_BROKEN`, `SERIES_RECORD_CROSSED`, `RECORD_SERIES_FINISHED`,
- öffentliche Eignung wird zentral über `processingOrigin.publicAnnouncementEligible()` bestimmt,
- Gleichstand, Near Miss, Initialisierung, invalidierte/supersedete Events und stille Origins werden ausgelassen,
- Crossing+Finish derselben StateKey/StreakRun-Kombination innerhalb derselben Periode werden auf Finish reduziert,
- mehrere echte Ergebnisrekordverbesserungen bleiben einzeln erhalten,
- Rekorde werden nie wegen Discord-Grenzen still gekürzt; deterministische Report-Pagination bleibt maßgeblich.

Bestehende Snapshot-/Catch-up-Semantik bleibt unverändert. Keine neue Report-Persistenzwahrheit, keine Migration.

Wesentliche Nachweise: `PeriodicReportUseCaseTest`, `PeriodicReportRendererTest`, `PeriodicReportDeliveryConfigurationTest`, bestehende `PeriodicReportDeliveryService*`-Regressionstests und PostgreSQL-Periodenreadtests.

## 10. Paket 14.5 – Gesamthärtung, Abnahme und Releasevorbereitung

Paket 14.5 führte keinen neuen Fachumfang ein. Ergebnis:

- keine weitere Produktivcode- oder Schemaänderung erforderlich,
- Dokumentation und Abnahmematrix synchronisiert,
- finaler Paket-Head `f00d56bbc22181395ad2ea9c1703bf3d77051d18`,
- CI #1674 / Run-ID `31324466419`: Standardbuild, PostgreSQL-Integration und Migration/Upgrade grün,
- Container image #511 / Run-ID `31324466410`: Produktionsimage, Nicht-Root-/Runtime-Check, Compose, Backup, Restore, Resume und Application-Rollback grün,
- realer Discord-/Report-Smoke am 9. August 2026 bestanden,
- Gesamt-PR #111 nach `main` freigegeben und gemergt.

## 11. Produktionsfreigabe und Rollout

Release **1.5.0** wurde am 10. August 2026 produktiv gesetzt.

Produktiver Codecommit:

`e06fc8f9dfb4645c0db5cd3532c1eb7b8289f784`

Der Deploymentweg verwendete weiterhin den verbindlichen Produktionspfad mit unveränderlichem SHA-Image, validiertem Pre-Deployment-Backup, Healthprüfung und App-Rollbackfähigkeit. Da Inkrement 14 keine neue Migration enthält, blieb der Schema-Endstand unverändert bei 024.

Der unmittelbare Produktiv-Canary wurde vom Betreiber als bestanden bestätigt; es wurde kein Rollbackkriterium ausgelöst.

## 12. Nicht Bestandteil des Gesamtinkrements

- quantitative Achievement-Fortschritte,
- neue Achievements oder Rekordmetriken,
- Rankings/Leaderboards/Spieler der Woche,
- Reminderstatus pro Spiel,
- Trendvergleiche in Reports,
- manuelle Report-Commands,
- generische Frameworks.