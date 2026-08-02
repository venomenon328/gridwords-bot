# Verbindliches Modell für Tagesstatus und Erinnerungen

**Status:** fachlich abgenommen  
**Stand:** 2. August 2026
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

### 2.1 Eindeutigkeit und Lifecycle

Pro Guild, Channel und fachlichem Spieltag existiert höchstens eine aktive kanonische Tagesstatusnachricht.

Sie wird:

- beim ersten vollständig gültigen Ergebnis des Spieltags erzeugt,
- spätestens beim ersten fälligen Reminder-Lauf erzeugt, sofern mindestens ein aktiver Teilnehmer existiert,
- nach weiteren Ergebnissen oder Korrekturen editiert,
- nach für heute wirksamen Teilnahmeänderungen nur dann aktualisiert, wenn sie bereits existiert,
- bei einem zulässigen Vortagsnachtrag für gestern aktualisiert,
- nach dem Tageswechsel für gestern finalisiert.

Wird die gespeicherte Discord-Nachricht extern gelöscht, erzeugt die Recovery kontrolliert genau einen Ersatz und übernimmt dessen ID atomar. Zur Discord-Reconciliation wird der deterministische sichtbare Titel verwendet; technische Delivery-Schlüssel dürfen weder als Footer noch als zusätzliche sichtbare Textzeile erscheinen.

### 2.2 Teilnehmermenge

Der Status enthält genau die Spieler, deren Teilnahmezeitraum den dargestellten Spieltag umfasst. Reminderstatus beeinflusst die Statusdarstellung nicht.

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
- Allowed Mentions sind vollständig deaktiviert.

## 3. Reminder-Audience

Ein Spieler wird in der Reminder-Zusammenfassung aufgeführt, wenn er am Spieltag:

- aktiv ist und
- mindestens eines der beiden Spiele noch nicht gültig eingereicht hat.

Je Spieler werden die konkret fehlenden Spieltypen geliefert. Vollständige und inaktive Spieler werden nicht aufgeführt. Ein gültiges `X/6` oder `X/9` gilt als erledigt und darf keine weitere Erinnerung für dieses Spiel erzeugen.

Der Reminderstatus bestimmt ausschließlich die Darstellung:

- Reminder an: ID-basierte Discord-Mention `<@DISCORD_USER_ID>`,
- Reminder aus: serverbezogener Anzeigename als entschärfter Klartext ohne auslösbare Mention.

Damit bleibt die Übersicht vollständig, ohne einen Opt-out-Nutzer aktiv zu benachrichtigen.

## 4. Reminder-Ausgabe

### 4.1 Stufen

Standardmäßig täglich:

```text
Stufe 1: 16:00
Stufe 2: 22:00
Tagesabschluss: 06:00
Zeitzone: Europe/Berlin
```

Die konfigurierten Zeiten müssen vorhanden, verschieden und aufsteigend sein.

### 4.2 Nachricht

Pro Spieltag und Stufe wird höchstens eine aggregierte Erinnerungsnachricht als reiner Discord-Text ohne Embed gesendet.

Die Struktur ist:

```text
Denkt bitte noch an eure Wortspiele:
GridWords: <alle Spieler, denen GridWords fehlt>
QuadWords: <alle Spieler, denen QuadWords fehlt>
```

Dabei gelten folgende Regeln:

- `GridWords` und `QuadWords` sind jeweils fett als Link zum entsprechenden Spiel formatiert.
- Discord-Linkvorschauen werden fuer Reminder unterdrueckt.
- Eine Spielzeile entfällt, wenn niemandem dieses Spiel fehlt.
- Aktive Opt-ins werden mit `<@DISCORD_USER_ID>` adressiert.
- Aktive Opt-outs werden nur als serverbezogener Klartextname angezeigt.
- Allowed Mentions enthalten exakt die tatsächlich erwähnten Opt-in-User-IDs.
- Rollenmentions, `@everyone`, `@here` und aus Anzeigenamen konstruierte Mentions sind ausgeschlossen.
- Tagesstatus und Reminder sind getrennte Nachrichten.
- Erfolgreich versandte Reminder werden nach späteren Ergebnissen nicht editiert oder gelöscht.
- Technische Reconciliation-Metadaten dürfen nicht als sichtbare Zeile oder Embed-Footer erscheinen; ein nicht gerendertes URL-Fragment der Spiel-Links darf als stabiler Delivery-Schlüssel dienen.

### 4.3 Keine fehlenden Spiele

Fehlt keinem aktiven Spieler ein Spiel, wird keine Discord-Nachricht gesendet. Die Stufe wird dennoch persistent als erfolgreicher No-op abgeschlossen, damit sie bei einem späteren Schedulerlauf nicht erneut versendet wird.

Bestehen ausschließlich Opt-outs mit fehlenden Spielen, wird die Zusammenfassung trotzdem gesendet, jedoch ohne irgendeine echte User-Mention.

### 4.4 Catch-up

- Heute fällige, nicht abgeschlossene Stufen werden nach Neustart nachgeholt.
- Sind mehrere heutige Stufen bereits fällig und keine wurde abgeschlossen, wird nur die späteste fällige Stufe gesendet; frühere Stufen werden als superseded abgeschlossen.
- Wurde Stufe 1 bereits abgeschlossen, darf Stufe 2 später unabhängig davon senden.
- Reminder vergangener Spieltage werden nach Mitternacht nicht nachgesendet und als abgelaufen abgeschlossen.
- Audience, fehlende Spiele und Mentionstatus werden bei jedem tatsächlichen Sendeversuch neu ermittelt.

## 5. Scheduler und Zeitmodell

- Application- und Domainlogik verwenden eine injizierte `Clock` und konfigurierte `ZoneId`.
- Nächste Ausführungszeitpunkte werden zoniert berechnet; die Betriebssystem-Zeitzone ist unerheblich.
- Sommer- und Winterzeitgrenzen werden deterministisch behandelt.
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
- Zustand für erfolgreich gesendet, keine fehlenden Spiele, superseded, abgelaufen sowie technische Fehler,
- optionale Discord-Message-ID,
- Claim/Lease oder gleichwertige Konkurrenzsicherung,
- Fehler- und Zeitstempeldaten.

Guild, Channel, Spieltag und Stufe sind fachlich eindeutig.

### 6.3 Garantien

- Keine Datenbanktransaktion wartet auf Discord-I/O.
- Discord-Erfolg wird erst nach dem Netzwerkaufruf persistiert.
- Retry, Neustart und parallele Instanzen erzeugen keine Duplikate.
- Unklarer Discord-Ausgang wird über persistente IDs, Zustände und nicht sichtbare Delivery-Schlüssel reconciled.
- Permanente Fehler erzeugen keinen Hot-Loop.
- Statusfehler rollen gültige Ergebnisse nicht zurück.
- Die bestehende Publish-/Edit-/Delete-Zustandsmaschine der Ergebnisnachrichten bleibt unverändert.

## 7. Aktivierung und Refresh-Auslöser

Ein Status-Refresh wird mindestens ausgelöst durch:

- erste gültige Einreichung,
- weitere Einreichung oder Korrektur,
- zulässigen Vortagsnachtrag,
- Self-Service- oder Admin-Aktivierung mit Wirksamkeit heute,
- Tageswechsel beziehungsweise Startup-Finalisierung,
- fälligen Reminder-Lauf.

Aktiviert ein gültiges Ergebnis einen unbekannten oder inaktiven Spieler, werden Teilnahmezeitraum, Reminderstatus `true` und Ergebnis atomar gespeichert. Ein bereits aktiver ausdrücklicher Opt-out bleibt bei weiteren Ergebnissen erhalten. Eine Deaktivierung ab morgen verändert den heutigen Status nicht.

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

## Zwischeninkrement 10.4: Retention und Tagesabschluss

Diese Regelungen ersetzen ab Zwischeninkrement 10.4 entgegenstehende Aussagen dieses Dokuments.

- Eine sichtbare Stufe 1 wird erst nach dem dauerhaft gespeicherten Abschluss von Stufe 2 als `SENT` oder `NO_CANDIDATES` pensioniert; `RETRYABLE` und `PERMANENT` erhalten sie.
- Reminder vergangener Tage werden ab 06:00 Uhr nicht nur abgelaufen markiert, sondern getrennt persistent pensioniert und geloescht.
- Der Cleanup finalisiert zuerst den gestrigen Status, entfernt danach alte kanonische Ergebnisnachrichten, dann alte Reminder, und erstellt anschliessend den heutigen Statusanker.
- Ergebnis- und Reminder-Retirement haben getrennte Claims, Leases, Backoff, `RETIRED` und `PERMANENT`; Discord-I/O liegt ausserhalb von Transaktionen.
- Eine unbekannte Discord-Nachricht ist beim Retirement ein idempotenter Erfolg, und ein nicht aktiver Ergebnis-Retirement-Zustand sperrt Publication und Recovery.
