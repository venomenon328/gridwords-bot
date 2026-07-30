# Inkrement 8: Tagesstatus, vollständige Serien und Erinnerungen

**Status:** automatisierte Abnahme vollständig; Korrekturen aus dem ersten realen Smoke-Test umgesetzt; gezielter Discord-Nachtest offen

**Issue:** #21  
**Branch:** `feature/daily-status-reminders`  
**Draft-PR:** #22

## Ergebnis

Inkrement 8 implementiert den vollständigen täglichen Nutzungszyklus auf Basis der historisch wirksamen Teilnehmermenge:

- genau eine persistente, editierbare Tagesstatusnachricht je Guild, Channel und Spieltag,
- GridWords- und QuadWords-Zustand sowie alle fünf persönlichen Serien je aktivem Spieler,
- gemeinsame Komplett- und Perfektserie ohne gemeinsame Aktivitätsserie,
- vorläufige Semantik ausschließlich für den aktuellen Berlin-Tag und endgültige Semantik für historische Tage,
- Reminder um 18:00 und 23:00 Uhr mit konkret fehlenden Spielen,
- Reminder-Opt-out bei neuer beziehungsweise erneuter Aktivierung,
- reine Text-Reminder mit verlinkten Spielnamen, vollständiger Klartextübersicht und ausschließlich ID-basierten Mentions für Opt-ins,
- persistente Claims, Leases, Retry-Backoff, permanente Fehlerzustände, No-op, Supersession, Ablauf und Crash-Reconciliation.

Verbindliche Grundlagen sind [`../requirements/series-model.md`](../requirements/series-model.md), [`../requirements/dynamic-player-model.md`](../requirements/dynamic-player-model.md), [`../requirements/daily-status-reminders.md`](../requirements/daily-status-reminders.md), Issue #21 sowie ADR 0012.

## Fachliche Umsetzung

Die Statusprojektion verwendet den angeforderten Spieltag als Serien-Stichtag. Fehlende Einreichungen werden nur dann vorläufig behandelt, wenn dieser Stichtag dem aktuellen Tag der injizierten `Clock` in `Europe/Berlin` entspricht. Gestern und alle anderen historischen Tage werden endgültig berechnet. Ein Vortagsnachtrag rekonstruiert persönliche und gemeinsame Serien aus den persistierten Ergebnissen und den je Tag wirksamen Teilnahmezeiträumen.

Ein gültiges Ergebnis, eine Korrektur oder ein zulässiger Nachtrag darf den Status des Spieltags erzeugen beziehungsweise aktualisieren. Eine heute wirksame Aktivierung aktualisiert ausschließlich eine bereits vorhandene Statusnachricht und erzeugt morgens vor dem ersten Ergebnis oder Reminder keinen verfrühten Status. Ein Fehler dieses entkoppelten Refresh-Hooks rollt weder Ergebnis noch Teilnahmeänderung zurück. Prospektive Deaktivierungen ab morgen verändern den heutigen Status nicht.

Der Statusrenderer erzeugt eine Discord-Nachricht mit bei Bedarf mehreren Embeds, enthält alle aktiven Spieler, deaktiviert Mentions und prüft Feld-, Embed- und Gesamtlimits vollständig. Eine nicht vollständig darstellbare Nachricht wird kontrolliert abgelehnt. Der technische Statusschlüssel ist nicht mehr sichtbar; Recovery verwendet den eindeutigen Titel des Tagesstatus.

## Reminder-Opt-out und Darstellung

Ein unbekannter oder inaktiver Spieler, der durch ein gültiges Ergebnis, `/participation join` oder eine Admin-Aktivierung aktiv wird, erhält Reminder standardmäßig eingeschaltet. Eine ausdrückliche Abschaltung mit `/reminders off` bleibt bei weiteren Ergebnissen eines bereits aktiven Spielers erhalten. Nach tatsächlicher Inaktivität schaltet eine spätere Reaktivierung Reminder erneut ein.

Der Reminder enthält kein Embed. Er besteht aus einem kurzen Einleitungstext und je einer bei Bedarf vorhandenen Zeile für GridWords und QuadWords. Die Spielnamen sind verlinkt. Pro Spielzeile werden alle aktiven Spieler aufgeführt, denen dieses Spiel fehlt:

- Opt-ins als echte ID-basierte Discord-Mention,
- Opt-outs als entschärfter serverbezogener Klartextname.

Allowed Mentions enthalten exakt die Opt-in-User-IDs. Rollen-, `@everyone`- und `@here`-Mentions sind ausgeschlossen. Der technische, stufenspezifische Delivery-Schlüssel liegt ausschließlich in einem nicht dargestellten URL-Fragment der Spiel-Links und bleibt für Crash-Reconciliation nutzbar.

## Betrieb und Recovery

`daily_status_message` und `reminder_delivery` werden durch Liquibase angelegt und besitzen fachliche Unique Constraints. Claims sind tokengebunden und über Leases übernehmbar. Discord-I/O findet außerhalb der kurzen Datenbankoperationen statt.

Statusinhalte erhalten einen SHA-256-Fingerabdruck. Unveränderte Zustände prüfen nur die Existenz der Discord-Nachricht; geänderte Inhalte editieren dieselbe Message-ID. Nach externer Löschung wird genau ein Ersatz erzeugt. Reminder-Duplikate werden über den nicht sichtbaren Delivery-Schlüssel deterministisch reconciled.

Reminder-Audience und fehlende Spiele werden bei jedem tatsächlichen Sendeversuch neu ermittelt. `X/6` und `X/9` gelten als eingereicht. Fehlt keinem aktiven Spieler ein Spiel, wird die Stufe persistent als No-op abgeschlossen. Bestehen ausschließlich Opt-outs mit fehlenden Spielen, wird die Klartextübersicht ohne echte Mentions gesendet. Beim Start nach 23 Uhr wird ausschließlich Stufe 2 gesendet und die frühere offene Stufe superseded; bereits erfolgreich abgeschlossene Stufe 1 bleibt terminal. Offene Stufen vergangener Tage laufen ab. Retryfähige Fehler erhalten Backoff, permanente Fehler erzeugen keinen Hot-Loop.

Startup und Minutentakt verwenden dieselben idempotenten Use Cases. Der aktuelle Tag wird vor dem ersten fälligen Reminder nur aktualisiert, wenn bereits ein Status existiert. Der Vortag wird auch nach einem vollständigen Bot-Ausfall neu rekonstruiert, sofern für ihn aktive Teilnehmer existierten. Zeitberechnung und DST-Grenzen verwenden die konfigurierte `ZoneId` und eine injizierte `Clock`.

## Automatisierte Abnahme

Abgedeckt sind insbesondere:

1. Status-Create, Edit derselben Message-ID und Korrektur;
2. Ersatz nach externer Löschung und Duplikatbereinigung;
3. zwei, drei, wechselnde, inaktive und reine Reminder-Profile;
4. alle fünf persönlichen und beide gemeinsamen Serien;
5. vorläufiger heutiger Tag und endgültiger historischer Tag;
6. Tageswechsel, Vortagsnachtrag und Serienrekonstruktion;
7. entkoppelte Refresh-Fehler ohne Rollback;
8. Audience und konkret fehlende Spiele einschließlich `X/6` und `X/9`;
9. Reminder-Opt-out bei implizitem Join, Reaktivierung und bewahrtem aktivem Opt-out;
10. reine Textausgabe, Spiellinks, Klartextnamen und strikt begrenzte Allowed Mentions;
11. zweite Stufe mit erneuter Audience-Bewertung;
12. persistenter No-op ohne Discord-Nachricht;
13. Startup vor, zwischen und nach den Reminderzeiten;
14. Supersession nach 23 Uhr und Ablauf vergangener Stufen;
15. konkurrierende Claims für Status und Reminder;
16. retryfähige und permanente Fehler ohne Hot-Loop;
17. Sommerzeitlücke und Winterzeitüberlappung in `Europe/Berlin`;
18. Discord-Feld-, Embed-, Nachrichten- und Gesamtlimits;
19. Liquibase, Constraints, Lease-Übernahme und Recovery gegen echtes PostgreSQL;
20. vollständige GridWords-/QuadWords-, Publish-, Edit-, Delete-, Parser-, Renderer- und Architekturregression.

## Testnachweis

Finaler Code-Stand nach den Smoke-Test-Korrekturen:

- `mvn --batch-mode --no-transfer-progress clean verify`: **249 Tests**, 0 Fehler, 0 übersprungen.
- `mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify`: **249 Standardtests plus 76 PostgreSQL-Integrationstests**, jeweils 0 Fehler und 0 übersprungen.

Die Standardtests öffnen keine Datenbank-, Container- oder Discord-Verbindung und benötigen keinen Token. Das Profil `database-integration` verwendet PostgreSQL 16.6 über Testcontainers und führt alle realen Liquibase-Migrationen aus.

## Nicht-Ziele

Unverändert außerhalb dieses Inkrements liegen Wochen-/Monatsberichte, Statistik-Commands, konfigurierbare Reminderzeiten per Slash-Command, regelbasierte Kommentare, Mehrserverbetrieb und Änderungen an Share- oder Bildparsern.

## Noch offene manuelle Abnahme

Der erste reale Discord-/PostgreSQL-Smoke-Test bestätigte Statusprojektion, Ergebnispublish und Reminder-Scheduling und führte zu den drei umgesetzten UX-Korrekturen. Offen ist nur ein gezielter Nachtest dieser Korrekturen:

- impliziter Join und Reaktivierung schalten Reminder ein,
- der Tagesstatus enthält keinen sichtbaren technischen Schlüssel,
- der Reminder ist reiner Text mit verlinkten Spielnamen,
- alle unvollständigen aktiven Spieler werden angezeigt,
- ausschließlich Opt-ins erhalten echte Discord-Mentions.

PR #22 bleibt bis zu diesem Nachtest Draft und ungemergt.
