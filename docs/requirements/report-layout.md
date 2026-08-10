# Verbindliches Layout für Wochen- und Monatsberichte

**Status:** fachlich abgenommen  
**Stand:** 10. August 2026  
**Gültig ab:** Release 1.5.1  
**Verbindliches Issue:** #119

Dieses Dokument konkretisiert ausschließlich die sichtbare Darstellung der bereits bestehenden Wochen- und Monatsberichte. Die fachliche Grundlage bleibt `periodic-reports.md`; Record-, Achievement-, Serien-, Snapshot-, Delivery- und Recovery-Semantik werden nicht verändert.

## 1. Ziel

Die vorhandenen Reportdaten sollen bei unverändertem Informationsgehalt deutlich schneller erfassbar sein. Dazu werden redundante Formulierungen reduziert, Spieler als klare Blöcke dargestellt und Statistik- und Highlightteil sichtbar getrennt.

Nicht Bestandteil sind neue Kennzahlen, Rankings, Gewinnerlogik, Persistenzänderungen oder neue Commands.

## 2. Unveränderte Fachsemantik

Unverändert bleiben insbesondere:

- Auswahl und stabile Reihenfolge der Spieler,
- Teilnahme-, Aktivitäts-, Komplett- und Perfekttage,
- spielbezogene Nenner,
- `submitted`, `solved`, `unsolved`, `missing`,
- Lösungsquoten und Durchschnittsbildung,
- persönliche und gemeinsame Serien,
- Achievement-Periodenzuordnung,
- Record-Event-Auswahl und Crossing/Finish-Deduplizierung,
- Snapshot-, Catch-up-, Retry-, Recovery- und Deliveryregeln,
- ein Embed pro persistierter Discord-Reportseite.

Es gibt keine Liquibase-Migration.

## 3. Berichtstitel

Der Statistikteil erhält ein `📊`-Präfix und einen kompakten, weiterhin vollständigen Zeitraum.

Beispiele:

```text
📊 Wochenbericht · 3.–9. August 2026
📊 Wochenbericht · 27. Juli–2. August 2026
📊 Wochenbericht · 29. Dezember 2025–4. Januar 2026
📊 Monatsbericht · 1.–31. August 2026
```

Monat und Jahr werden nur dort wiederholt, wo dies zur Eindeutigkeit nötig ist.

## 4. Spielerblock

Jeder Spieler bleibt ein eigenes nicht-inline Discord-Field.

Field-Name:

```text
👤 <mention- und markdown-sicherer Anzeigename>
```

Grundformat:

```text
📅 Teilnahme 7/7 · Aktiv 7 · Komplett 7 · Perfekt 6
🟩 GW 7/7 ✅ · 100 % · ØVers. 4,3 · ØZeit 0:50 · Best 0:21
🟦 QW 7/7 · 6✅ 1❌ · 85,7 % · ØVers. 8,3 · ØZeit 3:05 · Best 2:01
🔥 Serien (Stand/Rekord)
↳ Aktiv 10/10 · Komplett 10/10 · GW 10/10 · QW 2/7 · Perfekt 2/7
```

Der sichtbare Nenner in `Teilnahme x/y` ist ausschließlich Orientierung. `y` entspricht der Anzahl Kalendertage der Berichtsperiode und verändert keinen fachlichen Spielnenner.

## 5. Spielzeilen

### 5.1 Alle möglichen Ergebnisse gelöst

```text
🟩 GW 7/7 ✅ · 100 % · ØVers. 4,3 · ØZeit 0:50 · Best 0:21
```

Redundante Nullwerte für ungelöst oder fehlend entfallen.

### 5.2 Gelöst und ungelöst

```text
🟦 QW 7/7 · 6✅ 1❌ · 85,7 % · ØVers. 8,3 · ØZeit 3:05 · Best 2:01
```

### 5.3 Fehlende Einreichungen

```text
🟩 GW 6/7 · 6✅ 1⬜ · 100 % · ØVers. 4,5 · ØZeit 1:33 · Best 0:52
```

### 5.4 Ungelöst und fehlend

```text
🟦 QW 5/7 · 3✅ 2❌ 2⬜ · 60 % · ØVers. 7,7 · ØZeit 4:12 · Best 2:44
```

### 5.5 Keine Einreichung trotz Teilnahme

```text
🟩 GW 0/7 · 7⬜ · keine Einreichung
```

Nicht definierte Quote-, Durchschnitts- und Bestzeitwerte werden nicht künstlich dargestellt.

### 5.6 Keine Teilnahme am Spiel

```text
🟦 QW — keine Teilnahme
```

### 5.7 Keine gelösten Ergebnisse

```text
🟦 QW 3/7 · 3❌ 4⬜ · 0 %
```

Gelöst-basierte Durchschnitte und Bestzeit entfallen vollständig.

## 6. Zahlenformat

- Lösungsquote: maximal eine Nachkommastelle.
- Überflüssige Null entfernen: `100,0 %` → `100 %`, `75,0 %` → `75 %`.
- Nicht-ganzzahlige Werte bleiben z. B. `85,7 %`.
- Durchschnittsversuche behalten bewusst genau eine Nachkommastelle, auch `4,0`.
- Bestzeit wird als `Best 0:21` dargestellt; kein Medaillen-/Gewinnersymbol.

## 7. Serienblock

```text
🔥 Serien (Stand/Rekord)
↳ Aktiv 10/10 · Komplett 10/10 · GW 10/10 · QW 2/7 · Perfekt 2/7
```

`x/y` bleibt exakt:

```text
Stand am Periodenende / Allzeitrekord bis Periodenende
```

Alle fünf persönlichen Serien bleiben sichtbar. Fachliche Werte wie `0/0` oder `0/7` werden nicht pauschal durch `—` ersetzt.

## 8. Gemeinsamer Block

Field-Name:

```text
🤝 Gemeinsam
```

Normalfall:

```text
📅 Möglich 7 · Komplett 7 · Perfekt 5
🔥 Serien (Stand/Rekord)
↳ Komplett 7/7 · Perfekt 2/3
```

Ohne gemeinsam mögliche Tage:

```text
📅 Keine gemeinsam möglichen Tage
🔥 Serien (Stand/Rekord)
↳ Komplett 0/7 · Perfekt 0/3
```

Historische Rekordwerte bleiben sichtbar.

## 9. Eigener Highlightteil

Existiert mindestens ein Achievement- oder Record-Highlight, beginnt nach allen Statistikseiten zwingend eine neue logische Reportseite.

Titel:

```text
✨ Highlights der Woche · 3.–9. August 2026
✨ Highlights des Monats · 1.–31. August 2026
```

Existieren keine Highlights, wird keine leere Highlightseite erzeugt.

Es bleibt bei genau einem Embed pro persistierter Discord-Reportseite/Nachricht. Statistik- und Highlightteil werden nicht als mehrere Embeds in derselben Nachricht kombiniert.

## 10. Achievement-Highlights

Field-Name:

```text
🏅 Achievements
```

Beispiel:

```text
venomenon · **9** freigeschaltet
Eschi · **3** freigeschaltet
PrincessinGoldwaage · **6** freigeschaltet
```

Singular:

```text
Eschi · **1** freigeschaltet
```

Keine Namen einzelner Achievements, Kategorien oder Rankings. Reihenfolge entspricht der bestehenden Report-Teilnehmerreihenfolge.

## 11. Record-Highlights

Field-Name:

```text
🏆 Rekorde
```

Jeder Rekord ist ein atomarer Zweizeiler:

```text
**Eschi** · persönlicher Rekord
↳ Aktivitätsserie · **6 Tage**
```

```text
**Eschi** · persönlicher GridWords-Rekord
↳ GridWords-Lösungsserie · **6 Tage**
```

```text
**venomenon** · serverweiter GridWords-Rekord
↳ Bestzeit · **0:21**
```

```text
**Gemeinsam** · gemeinsamer Rekord
↳ Perfektserie · **8 Tage**
```

Zwischen zwei Record-Blöcken liegt eine Leerzeile.

Ein Rekordblock darf beim Splitten weder zwischen Fields noch zwischen Seiten auseinandergerissen werden. Eventauswahl, Reihenfolge und Crossing/Finish-Deduplizierung bleiben unverändert.

## 12. Pagination

Bestehende Discord-Grenzen bleiben verbindlich.

- Spielerfields bleiben atomar.
- `Gemeinsam` folgt nach dem letzten Spieler.
- Highlights beginnen immer auf einer neuen Seite.
- Achievement-Zeilen dürfen deterministisch über mehrere Fields verteilt werden.
- Record-Zweizeiler dürfen deterministisch über `🏆 Rekorde` und `🏆 Rekorde (Fortsetzung)` verteilt werden.
- Keine künstliche Maximalzahl an Records.
- Kein Abschneiden.
- Footer `Seite x/y` zählen Statistik- und Highlightseiten gemeinsam.
- Identischer Input erzeugt identische Seiten und denselben Fingerprint.

Beispiel bei zwei Statistikseiten und einer Highlightseite:

```text
Seite 1/3 · 📊 Wochenbericht …
Seite 2/3 · 📊 Wochenbericht …
Seite 3/3 · ✨ Highlights der Woche …
```

Es wird keine generische Section-/Layout-Engine eingeführt. Eine gezielte Rendererlogik für Statistik- und Highlightgruppe genügt.

## 13. Snapshot- und Rolloutverhalten

- Bereits erfolgreich veröffentlichte Reports werden nicht wegen des Layoutwechsels editiert oder neu veröffentlicht.
- Das neue Layout gilt für künftig erzeugte Reports.
- Eine nach bestehender Recoveryregel zulässige Neuerzeugung nach externer Löschung innerhalb des Catch-up-Fensters darf das aktuelle Rendererlayout verwenden.
- Kein Sonder-Reconcile nur wegen des Layoutwechsels.

## 14. Nicht Bestandteil

- neue Statistikdefinitionen,
- geänderte fachliche Nenner oder Werte,
- neue Seriensemantik,
- geänderte Record-/Achievement-Selektion,
- Rankings, Gewinner oder Player of the Week,
- geänderte Reportzeitpunkte,
- neue Commands,
- Persistenzänderungen,
- Liquibase-Migration,
- generische Layout-/Messaging-Plattform.
