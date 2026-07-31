# Verbindliches Modell für dynamische Spieler und Reminder-Teilnahme

**Status:** fachlich abgenommen  
**Stand:** 31. Juli 2026  
**Gültig ab:** Zwischeninkrement 7.2; Reminder-Opt-out präzisiert in Inkrement 8

Dieses Dokument ergänzt die Anforderungsspezifikation und das Serienmodell. Bei widersprüchlichen älteren Formulierungen zu „Tobias und Georgia“, „zwei Spielern“, einer statischen Spielerliste oder statisch konfigurierten Spieler-IDs gilt dieses Dokument.

## 1. Geltungsbereich

Der Bot bleibt auf genau einen konfigurierten Discord-Server und einen dedizierten Textkanal beschränkt. Innerhalb dieses Channels ist die Zahl der Spieler jedoch nicht fest konfiguriert.

Verarbeitet werden ausschließlich:

- menschliche Discord-Nutzer,
- keine Bot-Nutzer,
- keine Webhooks,
- Nachrichten im konfigurierten Server und Channel.

Normale, nicht anwendbare Nachrichten werden ohne Datenbankzugriff und ohne Reaktion ignoriert.

## 2. Spielerprofil

Ein Spielerprofil wird eindeutig durch die Discord-User-ID identifiziert und enthält mindestens:

- Discord-User-ID,
- serverbezogenen Anzeigenamen,
- aktuellen Aktivstatus,
- Administratorstatus,
- globalen Reminderstatus,
- Erstellungs- und Änderungszeitpunkt.

### 2.1 Anzeigename

Als Anzeigename gilt:

1. `Member#getEffectiveName` im konfigurierten Server,
2. falls kein Guild-Member verfügbar ist, der globale Discord-Username.

Der gespeicherte Anzeigename wird bei gültigen Shares und relevanten Slash-Commands synchronisiert. Mentions werden niemals aus dem Anzeigenamen konstruiert, sondern ausschließlich ID-basiert als `<@DISCORD_USER_ID>`.

### 2.2 Administratoren

Administratoren werden ausschließlich über die extern konfigurierte Liste `gridwords.discord.admin-user-ids` bestimmt. Eine dynamische Spielerregistrierung verleiht keine administrativen Rechte.

## 3. Automatische Spielerregistrierung

Ein unbekannter Discord-Nutzer wird erst dann als aktiver Spieler angelegt, wenn er ein vollständig gültiges GridWords- oder QuadWords-Ergebnis einreicht.

Verbindliche Reihenfolge:

1. Server, Channel, Bot- und Webhookstatus filtern,
2. Spieltyp und Share syntaktisch erkennen,
3. Spieltag validieren,
4. GridWords-Raster beziehungsweise QuadWords-Bild vollständig validieren,
5. erst danach Spielerprofil anlegen oder aktualisieren,
6. Teilnahmezeitraum konfliktfest anlegen beziehungsweise reaktivieren,
7. bei neuer oder erneuter Aktivierung Reminder standardmäßig einschalten,
8. Ergebnis und Submission zusammen mit Profil und Teilnahmeänderung speichern,
9. bestehende sichere Publish-/Edit-/Delete-Folge ausführen.

Folgen:

- normaler Text erzeugt kein Spielerprofil,
- ein erkennbarer, aber ungültiger Share erhält weiterhin `⚠️`, erzeugt jedoch kein Spielerprofil und keinen Teilnahmezeitraum,
- ein technischer QuadWords-Downloadfehler erzeugt kein halbfertiges Spielerprofil,
- ein gültiger Share eines unbekannten oder inaktiven Nutzers aktiviert ihn ab dem fachlichen Spieltag des Shares und setzt den Reminderstatus auf an,
- ein gültiger Vortagsnachtrag kann deshalb einen Teilnahmezeitraum ab gestern beginnen lassen,
- ein weiteres gültiges Ergebnis eines bereits aktiven Nutzers verändert dessen ausdrücklich gewählten Reminderstatus nicht.

Ein gültiger Share eines bekannten Spielers synchronisiert mindestens Anzeigename und Administratorstatus.

## 4. Aktivstatus und Teilnahmezeiträume

`player.active` beschreibt ausschließlich den aktuellen Aktivstatus. Historische gemeinsame Serien werden nicht aus diesem Boolean rückwirkend berechnet.

Dafür existieren nicht überlappende Teilnahmezeiträume je Spieler:

```text
active_from    inklusiv
inactive_from  exklusiv, NULL bei laufender Teilnahme
```

Regeln:

- höchstens ein offener Zeitraum pro Spieler,
- keine überlappenden Zeiträume,
- `player.active=true` erfordert einen offenen Zeitraum,
- `player.active=false` erfordert, dass kein offener Zeitraum den aktuellen Berlin-Tag umfasst,
- Aktivierungen und Deaktivierungen sind idempotent,
- frühere abgeschlossene Zeiträume werden nicht nachträglich verändert,
- ein späterer Wiedereintritt erzeugt einen neuen Zeitraum und schaltet Reminder erneut standardmäßig ein.

### 4.1 Automatische Aktivierung durch Share

Ein vollständig gültiges Share aktiviert den Spieler ab dessen fachlichem `game_date`, sofern für diesen Tag kein Teilnahmezeitraum besteht. Das gilt für heute und den zulässigen Vortag. Eine solche neue oder erneute Aktivierung verwendet Reminder-Opt-out-Semantik: Reminder sind zunächst an, bis der Nutzer sie ausdrücklich ausschaltet.

### 4.2 Self-Service-Commands

```text
/participation join
/participation leave
/participation status
```

- `join` aktiviert ab dem aktuellen Kalendertag in `Europe/Berlin` und schaltet bei einer neuen oder erneuten Aktivierung Reminder ein.
- `leave` beendet die Teilnahme prospektiv ab dem folgenden Kalendertag; der laufende heutige Tag wird nicht rückwirkend aus gemeinsamen Serien entfernt.
- `status` antwortet ephemer mit Aktivstatus, Wirksamkeitsdatum und Reminderstatus.
- unbekannte Nutzer werden bei Bedarf mit aktuellem serverbezogenem Anzeigenamen angelegt.

### 4.3 Admin-Commands

```text
/player activate user:<Discord-User>
/player deactivate user:<Discord-User>
/player status user:<Discord-User>
```

- nur konfigurierte Administrator-IDs dürfen diese Commands ausführen,
- Aktivierung gilt ab dem aktuellen Berlin-Tag und schaltet bei einer neuen oder erneuten Aktivierung Reminder ein,
- Deaktivierung gilt ab dem folgenden Berlin-Tag,
- unbekannte Zielnutzer dürfen angelegt werden,
- fehlende Autorisierung erzeugt nur eine ephemere Fehlermeldung und keine Zustandsänderung.

Ein späteres gültiges Share darf einen inaktiven Spieler entsprechend dem fachlichen Spieltag wieder aktivieren.

## 5. Gemeinsame Serien mit dynamischer Teilnehmermenge

Persönliche Serien bleiben unverändert pro Spieler.

Für jeden Spieltag wird die aktive Teilnehmermenge aus den Teilnahmezeiträumen dieses Tages ermittelt. Ein Spieler gehört dazu, wenn:

```text
active_from <= spieltag
und
inactive_from IS NULL oder spieltag < inactive_from
```

Ein gemeinsamer Serientag entsteht nur bei mindestens zwei aktiven Spielern.

### 5.1 Gemeinsam kompletter Tag

Ein Spieltag ist gemeinsam komplett, wenn mindestens zwei Spieler aktiv sind und jeder an diesem Tag aktive Spieler GridWords und QuadWords gültig eingereicht hat.

### 5.2 Gemeinsam perfekter Tag

Ein Spieltag ist gemeinsam perfekt, wenn mindestens zwei Spieler aktiv sind und jeder an diesem Tag aktive Spieler beide Spiele gelöst hat.

### 5.3 Historische Stabilität

- Ein Beitritt verändert keine Tage vor `active_from`.
- Ein Austritt verändert keine Tage vor `inactive_from`.
- Ein zulässiger Vortagsnachtrag verwendet die für gestern gültige Teilnehmermenge.
- PublicationContext, Tagesstatus, Berichte und Statistiken dürfen keine feste Liste aus genau zwei Spieler-IDs erhalten.
- Es gibt weiterhin keine gemeinsame Aktivitätsserie.

## 6. Reminder-Opt-out

Reminderstatus und Aktivstatus bleiben technisch getrennt, die Aktivierung verwendet jedoch fachlich Opt-out-Semantik.

```text
/reminders on
/reminders off
/reminders status
```

Regeln:

- ein globaler Reminderstatus für beide Spiele,
- ein unbekannter oder inaktiver Spieler erhält bei Aktivierung durch gültiges Ergebnis, `/participation join` oder Admin-Aktivierung den Reminderstatus `true`,
- `/reminders off` schaltet ausschließlich echte Discord-Erwähnungen aus und deaktiviert weder Spieler noch Teilnahmezeitraum,
- ein weiteres Ergebnis eines bereits aktiven Opt-outs schaltet Reminder nicht wieder ein,
- nach tatsächlicher Inaktivität setzt eine spätere Reaktivierung Reminder erneut auf an,
- unbekannte Nutzer dürfen durch `/reminders on|off|status` als inaktives Profil ohne Teilnahmezeitraum angelegt werden,
- Commands sind idempotent und antworten ephemer,
- Reminderstatus beeinflusst gemeinsame Serien nicht.

## 7. Reminder-Auswahl und Darstellung

Der transportneutrale Query-Port liefert alle am Spieltag aktiven Spieler, denen mindestens eines der beiden Spiele fehlt. Das Ergebnisobjekt enthält:

- Discord-User-ID,
- aktuellen Anzeigenamen,
- konkret fehlende Spieltypen,
- Reminderstatus für die Entscheidung zwischen echter Mention und Klartextname.

Vollständige und inaktive Spieler werden nicht aufgeführt. Für aktive unvollständige Spieler gilt:

- Reminderstatus an: Darstellung als `<@USER_ID>` und Aufnahme genau dieser ID in Allowed Mentions,
- Reminderstatus aus: Darstellung ausschließlich als entschärfter Klartextname ohne auslösbare Mention.

Rollen-, `@everyone`- und `@here`-Mentions sind verboten.

## 8. Persistenz

### 8.1 `player`

Mindestens:

- `discord_user_id`,
- `display_name`,
- `active`,
- `administrator`,
- `reminder_opt_in BOOLEAN NOT NULL DEFAULT FALSE`,
- Zeitstempel.

Der Datenbank-Default `false` schützt reine, inaktive Profile. Die fachliche Aktivierungsoperation setzt den Wert bei neuer beziehungsweise erneuter Aktivierung atomar auf `true`.

### 8.2 `player_participation_period`

Mindestens:

- interne ID,
- Player-FK,
- `active_from DATE NOT NULL`,
- `inactive_from DATE NULL`,
- Zeitstempel,
- Check `inactive_from IS NULL OR inactive_from > active_from`,
- höchstens ein offener Zeitraum pro Spieler,
- keine überlappenden Zeiträume.

PostgreSQL-Constraints und Adapterlogik müssen konkurrierende Erstregistrierungen und Commands konfliktfest behandeln.

### 8.3 Backfill

- bestehende aktive Spieler erhalten einen offenen Zeitraum ab ihrem frühesten vorhandenen `game_result.game_date`, ersatzweise ab dem Migrationstag,
- bestehende inaktive Spieler erhalten keinen offenen Zeitraum,
- bestehende Ergebnisse, Adminflags, Reminderstatus und IDs bleiben erhalten.

`submission.author_player_id` bleibt für erkannte, aber ungültige Shares bewusst nullable. Ein gültiges Ergebnis speichert Profil, Teilnahmezeitraum, Reminder-Aktivierung und Spielergebnis zusammen im Persistenzschritt.

## 9. Konfiguration

Nach Umsetzung entfallen statische Spielerwerte wie `gridwords.players.first`, `gridwords.players.second` und `PLAYER_1_*`/`PLAYER_2_*`.

Weiterhin extern konfiguriert bleiben Guild-ID, Channel-ID, Admin-User-IDs, Zeitzone, Reminder- und Berichtzeiten sowie Datenbank- und Speicherparameter. Startup und Application-Services dürfen keine genau zwei Spieler voraussetzen.

## 10. Nicht Bestandteil

Außerhalb des dynamischen Spielermodells liegen Wochen- und Monatsberichte, allgemeine Statistik-Commands, automatische Deaktivierung beim Serveraustritt, mehrere Server oder Channels sowie fachfremde Änderungen an Parser- und Publish-/Delete-Logik.
