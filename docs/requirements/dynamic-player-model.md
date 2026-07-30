# Verbindliches Modell für dynamische Spieler und Reminder-Teilnahme

**Status:** fachlich abgenommen  
**Stand:** 30. Juli 2026  
**Gültig ab:** Zwischeninkrement 7.2

Dieses Dokument ergänzt die Anforderungsspezifikation und das Serienmodell. Bei widersprüchlichen älteren Formulierungen zu „Tobias und Georgia“, „zwei Spielern“, einer statischen Spielerliste oder statisch konfigurierten Spieler-IDs gilt dieses Dokument.

## 1. Geltungsbereich

Der Bot bleibt auf genau einen konfigurierten Discord-Server und einen dedizierten Textkanal beschränkt. Innerhalb dieses Channels ist die Zahl der Spieler jedoch nicht mehr fest konfiguriert.

Verarbeitet werden weiterhin ausschließlich:

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
- globales Reminder-Opt-in,
- Erstellungs- und Änderungszeitpunkt.

### 2.1 Anzeigename

Als Anzeigename gilt:

1. `Member#getEffectiveName` im konfigurierten Server,
2. falls kein Guild-Member verfügbar ist, der globale Discord-Username.

Der gespeicherte Anzeigename wird synchronisiert, wenn bei einem gültigen Share oder einem relevanten Slash-Command ein anderer aktueller serverbezogener Name vorliegt. Eine spätere Reminder-Ausgabe darf den Guild-Member erneut auflösen und den Namen vor der Ausgabe synchronisieren.

Erwähnungen werden niemals aus dem Anzeigenamen konstruiert, sondern immer ID-basiert als `<@DISCORD_USER_ID>`.

### 2.2 Administratoren

Administratoren werden weiterhin ausschließlich über die extern konfigurierte Liste `gridwords.discord.admin-user-ids` bestimmt. Eine dynamische Spielerregistrierung verleiht keine administrativen Rechte.

## 3. Automatische Spielerregistrierung

Ein unbekannter Discord-Nutzer wird erst dann als aktiver Spieler angelegt, wenn er ein vollständig gültiges GridWords- oder QuadWords-Ergebnis einreicht.

Verbindliche Reihenfolge:

1. Server, Channel, Bot- und Webhookstatus filtern,
2. Spieltyp und Share syntaktisch erkennen,
3. Spieltag validieren,
4. GridWords-Raster beziehungsweise QuadWords-Bild vollständig validieren,
5. erst danach Spielerprofil anlegen oder aktualisieren,
6. Teilnahmezeitraum konfliktfest anlegen beziehungsweise reaktivieren,
7. Ergebnis und Submission speichern,
8. bestehende sichere Publish-/Edit-/Delete-Folge ausführen.

Folgen:

- normaler Text erzeugt kein Spielerprofil,
- ein erkennbarer, aber ungültiger Share erhält weiterhin `⚠️`, erzeugt jedoch kein Spielerprofil und keinen Teilnahmezeitraum,
- ein technischer QuadWords-Downloadfehler erzeugt kein halbfertiges Spielerprofil,
- ein gültiger Share eines unbekannten oder inaktiven Nutzers aktiviert ihn ab dem fachlichen Spieltag des Shares,
- ein gültiger Vortagsnachtrag kann deshalb einen Teilnahmezeitraum ab gestern beginnen lassen.

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
- ein späterer Wiedereintritt erzeugt einen neuen Zeitraum.

### 4.1 Automatische Aktivierung durch Share

Ein vollständig gültiges Share aktiviert den Spieler ab dessen fachlichem `game_date`, sofern für diesen Tag kein Teilnahmezeitraum besteht. Das gilt für heute und den zulässigen Vortag.

### 4.2 Self-Service-Commands

```text
/participation join
/participation leave
/participation status
```

- `join` aktiviert ab dem aktuellen Kalendertag in `Europe/Berlin`.
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
- Aktivierung gilt ab dem aktuellen Berlin-Tag,
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

## 6. Reminder-Opt-in

Reminder-Teilnahme und Aktivstatus sind unabhängig.

```text
/reminders on
/reminders off
/reminders status
```

Regeln:

- ein globales Opt-in für beide Spiele,
- Standardwert `false`,
- unbekannte Nutzer dürfen dadurch als inaktives Profil ohne Teilnahmezeitraum angelegt werden,
- Commands sind idempotent,
- Antworten sind ephemer,
- ein Opt-in aktiviert den Spieler nicht für gemeinsame Serien.

## 7. Reminder-Kandidaten für Inkrement 8

Zwischeninkrement 7.2 implementiert die persistente Grundlage und einen transportneutralen Query-Port. Zeitplanung und tatsächlicher Versand folgen erst in Inkrement 8.

Ein Reminder-Kandidat muss:

- am betreffenden Spieltag aktiv sein,
- `reminder_opt_in=true` besitzen,
- mindestens eines der beiden Spiele noch nicht eingereicht haben.

Das Ergebnisobjekt enthält:

- Discord-User-ID,
- aktuellen Anzeigenamen,
- konkret fehlende Spieltypen.

Vollständige Spieler werden nicht erwähnt. Die spätere Discord-Nachricht verwendet `<@USER_ID>` und schränkt Allowed Mentions auf genau die ausgewählten User-IDs ein. Rollen-, `@everyone`- und `@here`-Mentions sind verboten.

## 8. Persistenz

### 8.1 `player`

Mindestens:

- `discord_user_id`,
- `display_name`,
- `active`,
- `administrator`,
- `reminder_opt_in BOOLEAN NOT NULL DEFAULT FALSE`,
- Zeitstempel.

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
- bestehende Ergebnisse, Adminflags und IDs bleiben erhalten.

submission.author_player_id bleibt fuer erkannte, aber ungueltige Shares bewusst nullable: Vor einer vollstaendigen Fachvalidierung wird kein Spielerprofil angelegt. Ein gueltiges Ergebnis speichert Profil, Teilnahmezeitraum und Spielergebnis zusammen im Persistenzschritt.

## 9. Konfiguration

Nach Umsetzung entfallen:

```text
gridwords.players.first
gridwords.players.second
PLAYER_1_USER_ID
PLAYER_1_DISPLAY_NAME
PLAYER_2_USER_ID
PLAYER_2_DISPLAY_NAME
```

Weiterhin extern konfiguriert bleiben:

- Guild-ID,
- Channel-ID,
- Admin-User-IDs,
- Zeitzone,
- Reminder- und Berichtzeiten,
- Datenbank- und Speicherparameter.

Startup und Application-Services dürfen keine genau zwei Spieler mehr synchronisieren oder voraussetzen.

## 10. Nicht Bestandteil dieses Zwischeninkrements

- zeitgesteuerter Reminder-Versand,
- Tagesstatusnachricht,
- Wochen- und Monatsberichte,
- allgemeine Statistik-Commands,
- automatische Deaktivierung beim Verlassen des Servers,
- mehrere Server oder Channels,
- Änderungen an Parser-, Renderer- oder sicherer Publish-/Delete-Logik außer zwingenden dynamischen Player-/Serien-Schnittstellen.
