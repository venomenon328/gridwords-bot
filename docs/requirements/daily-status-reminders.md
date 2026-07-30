# Verbindliches Modell für Tagesstatus und Erinnerungen

**Status:** fachlich vorbereitet  
**Stand:** 30. Juli 2026  
**Gültig ab:** Inkrement 8

Dieses Dokument ergänzt die Anforderungsspezifikation, das Serienmodell und das dynamische Spielermodell. Bei widersprüchlichen älteren Formulierungen zu genau zwei Spielern, statischen Spielerlisten, Tagesstatus oder Reminder-Auslieferung gilt dieses Dokument.

Verbindliche Grundlagen:

- [`series-model.md`](series-model.md)
- [`dynamic-player-model.md`](dynamic-player-model.md)
- Issue #21

## 1. Fachlicher Spieltag

Alle Status-, Serien- und Reminderentscheidungen verwenden den fachlichen Spieltag in der konfigurierten Zeitzone, standardmäßig `Europe/Berlin`.

- Ergebnisse sind weiterhin nur für heute oder gestern zulässig.
- Ein laufender heutiger Tag verwendet die vorläufige Seriensemantik.
- Nach dem Tageswechsel wird gestern finalisiert; fehlende Ergebnisse sind dann echte Lücken.
- Startup und Scheduler gleichen mindestens heute und gestern ab.

## 2. Kanonischer Tagesstatus

### 2.1 Eindeutigkeit

Pro Guild, Channel und fachlichem Spieltag existiert höchstens eine aktive kanonische Tagesstatusnachricht.

Sie wird:

- beim ersten vollständig gültigen Ergebnis des Spieltags erzeugt,
- spätestens beim ersten fälligen Reminder-Lauf erzeugt, sofern mindestens ein aktiver Teilnehmer existiert,
- nach weiteren Ergebnissen oder Korrekturen editiert,
- nach für heute wirksamen Teilnahmeänderungen aktualisiert,
- bei einem zulässigen Vortagsnachtrag für gestern aktualisiert,
- nach dem Tageswechsel für gestern finalisiert.

Wird die gespeicherte Discord-Nachricht extern gelöscht, erzeugt die Recovery kontrolliert genau einen Ersatz und übernimmt dessen ID atomar.

### 2.2 Teilnehmermenge

Der Status enthält genau die Spieler, deren Teilnahmezeitraum den dargestellten Spieltag umfasst. Reminder-Opt-in beeinflusst die Statusdarstellung nicht.

Deterministische Reihenfolge:

1. serverbezogener Anzeigename, Unicode-fallunabhängig,
2. Discord-User-ID.

Namen werden dargestellt, aber im Status nicht erwähnt.

### 2.3 Inhalt pro Spieler

Pro aktivem Spieler werden gezeigt:

- GridWords-Status,
- QuadWords-Status,
- Aktivitätsserie,
- Komplettserie,
- GridWords-Lösungsserie,
- QuadWords-Lösungsserie,
- Perfektserie.

Statussymbole:

- `✅`: gültig eingereicht und gelöst,
- `❌`: gültig eingereicht, aber nicht gelöst,
- `⬜`: noch nicht eingereicht.

Eingereichte Spiele zeigen Ergebnis und Dauer kompakt. `X/6` und `X/9` zählen als eingereicht.

### 2.4 Gemeinsame Werte

Am Ende werden angezeigt:

- gemeinsame Komplettserie,
- gemeinsame Perfektserie.

Die Berechnung verwendet für jeden historischen Tag die damals aktive Teilnehmermenge. Ein gemeinsamer Serientag erfordert mindestens zwei aktive Spieler. Eine gemeinsame Aktivitätsserie existiert nicht.

### 2.5 Laufender und abgeschlossener Tag

Am aktuellen Tag beendet ein noch fehlendes Ergebnis eine bis gestern laufende Serie nicht vorzeitig. Ein bereits eingereichtes, aber nicht gelöstes Ergebnis beendet die betroffene Lösungs- und Perfektserie sofort.

Nach Mitternacht wird der Vortag mit endgültiger Semantik neu berechnet und seine Statusnachricht aktualisiert. Ein später zulässiger Vortagsnachtrag löst erneut eine vollständige Berechnung aus.

### 2.6 Discord-Grenzen

Der Tagesstatus bleibt eine einzelne Discord-Nachricht. Mehrere Embeds in dieser Nachricht sind zulässig.

- Alle aktiven Spieler müssen enthalten sein.
- Feld-, Embed-, Nachrichten- und Gesamtzeichenlimits werden vor Discord-I/O validiert.
- Eine unvollständige Teilnachricht darf nicht als erfolgreicher Status gespeichert werden.
- Ein nicht darstellbarer Vollstatus wird kontrolliert retryfähig beziehungsweise nach klarer Klassifikation permanent fehlerhaft behandelt.

## 3. Reminder-Kandidaten

Ein Spieler ist Kandidat, wenn er am Spieltag:

- aktiv ist,
- `reminder_opt_in=true` besitzt,
- mindestens eines der beiden Spiele noch nicht gültig eingereicht hat.

Je Kandidat werden die konkret fehlenden Spieltypen geliefert. Vollständige Spieler, inaktive Opt-ins und aktive Spieler ohne Opt-in werden nicht erwähnt.

Ein gültiges `X/6` oder `X/9` gilt als erledigt und darf keine weitere Erinnerung für dieses Spiel erzeugen.

## 4. Reminder-Ausgabe

### 4.1 Stufen

Standardmäßig täglich:

```text
Stufe 1: 18:00
Stufe 2: 23:00
Zeitzone: Europe/Berlin
```

Die konfigurierten Zeiten müssen vorhanden, verschieden und aufsteigend sein.

### 4.2 Nachricht

Pro Spieltag und Stufe wird höchstens eine aggregierte Erinnerungsnachricht gesendet.

- Jede Zeile nennt einen Spieler und dessen konkret fehlende Spiele.
- Mentions werden als `<@DISCORD_USER_ID>` erzeugt.
- Allowed Mentions enthalten ausschließlich die tatsächlich ausgewählten User-IDs.
- Rollenmentions, `@everyone`, `@here` und aus Anzeigenamen konstruierte Mentions sind ausgeschlossen.
- Tagesstatus und Reminder sind getrennte Nachrichten.
- Erfolgreich versandte Reminder werden nach späteren Ergebnissen nicht editiert oder gelöscht.

### 4.3 Keine Kandidaten

Sind keine Kandidaten vorhanden, wird keine Discord-Nachricht gesendet. Die Stufe wird dennoch persistent als erfolgreicher No-op abgeschlossen, damit sie bei einem späteren Schedulerlauf nicht erneut versendet wird.

### 4.4 Catch-up

- Heute fällige, nicht abgeschlossene Stufen werden nach Neustart nachgeholt.
- Sind mehrere heutige Stufen bereits fällig und keine wurde abgeschlossen, wird nur die späteste fällige Stufe gesendet; frühere Stufen werden als superseded abgeschlossen.
- Wurde Stufe 1 bereits abgeschlossen, darf Stufe 2 später unabhängig davon senden.
- Reminder vergangener Spieltage werden nach Mitternacht nicht nachgesendet und als abgelaufen abgeschlossen.
- Kandidaten werden bei jedem tatsächlichen Sendeversuch neu ermittelt.

## 5. Scheduler und Zeitmodell

- Application- und Domainlogik verwenden eine injizierte `Clock` und konfigurierte `ZoneId`.
- Nächste Ausführungszeitpunkte werden zoniert berechnet; die Betriebssystem-Zeitzone ist unerheblich.
- Sommer- und Winterzeitgrenzen müssen deterministisch behandelt werden.
- Startup-Reconciliation und reguläre Schedulerläufe verwenden dieselben idempotenten Use Cases.
- Mindestens geplant werden Reminder-Stufen, Tageswechsel-Finalisierung und Startup-Reconciliation für heute und gestern.

## 6. Persistente Delivery-Idempotenz

### 6.1 Tagesstatus

Eine persistente Statusentität enthält mindestens:

- Guild-ID,
- Channel-ID,
- Spieltag,
- optionale Discord-Message-ID,
- Delivery-/Refresh-Zustand,
- Claim/Lease oder gleichwertige Konkurrenzsicherung,
- Inhaltsfingerabdruck oder Version,
- Fehler- und Zeitstempeldaten.

Guild, Channel und Spieltag sind fachlich eindeutig.

### 6.2 Reminder

Eine persistente Reminder-Delivery enthält mindestens:

- Guild-ID,
- Channel-ID,
- Spieltag,
- Stufe,
- geplante lokale Zeit,
- Zustand für erfolgreich gesendet, keine Kandidaten, superseded, abgelaufen sowie technische Fehler,
- optionale Discord-Message-ID,
- Claim/Lease oder gleichwertige Konkurrenzsicherung,
- Fehler- und Zeitstempeldaten.

Guild, Channel, Spieltag und Stufe sind fachlich eindeutig.

### 6.3 Garantien

- Keine Datenbanktransaktion wartet auf Discord-I/O.
- Discord-Erfolg wird erst nach dem Netzwerkaufruf persistiert.
- Retry, Neustart und parallele Instanzen erzeugen keine Duplikate.
- Unklarer Discord-Ausgang wird über persistente IDs und Zustände reconciled.
- Permanente Fehler erzeugen keinen Hot-Loop.
- Statusfehler rollen gültige Ergebnisse nicht zurück.
- Die bestehende Publish-/Edit-/Delete-Zustandsmaschine der Ergebnisnachrichten bleibt unverändert.

## 7. Refresh-Auslöser

Ein Status-Refresh wird mindestens ausgelöst durch:

- erste gültige Einreichung,
- weitere Einreichung oder Korrektur,
- zulässigen Vortagsnachtrag,
- Self-Service- oder Admin-Aktivierung mit Wirksamkeit heute,
- Tageswechsel beziehungsweise Startup-Finalisierung,
- fälligen Reminder-Lauf.

Eine Deaktivierung ab morgen verändert den heutigen Status nicht.

## 8. Nicht Bestandteil

- Wochen- und Monatsberichte,
- allgemeine Statistik-Commands,
- Änderung von Schedulerzeiten per Slash-Command,
- regelbasierte Kommentare,
- mehrere Guilds oder Channels,
- Direktnachrichten,
- automatische Deaktivierung beim Serveraustritt,
- Änderungen an Share-Parsern oder QuadWords-Bildparser,
- fachliche Änderung der kanonischen Ergebnisdarstellung.
