# Verbindliches Modell für Wochen- und Monatsberichte

**Status:** fachlich abgenommen  
**Stand:** 1. August 2026  
**Gültig ab:** Inkrement 10  
**Verbindliches Issue:** #25

Dieses Dokument ergänzt die Anforderungsspezifikation, das Serienmodell, das dynamische Spielermodell und die Anforderungen an Tagesstatus und Reminder. Bei widersprüchlichen älteren Formulierungen zu Wochen- oder Monatsberichten gilt dieses Dokument.

## 1. Ziel und Geltungsbereich

Der Bot veröffentlicht regelmäßig kompakte Wochen- und Monatsberichte aus den bereits persistierten Spielergebnissen und den historisch wirksamen Teilnahmezeiträumen.

Die Berichte:

- fassen einen vollständig abgeschlossenen Kalenderzeitraum zusammen,
- verwenden dieselben fachlichen Definitionen wie Tagesstatus und Serienmodell,
- unterstützen eine dynamische Zahl von Spielern,
- bleiben frei von Gewinnerlogik, Rankings und direkten Leistungsvergleichen,
- werden duplikatsicher und nach Neustarts fortsetzbar ausgeliefert,
- speichern keine berechneten Statistikwerte als zweite fachliche Wahrheit.

Nicht Bestandteil sind Statistik-Slash-Commands, manuelle Report-Commands, per Discord veränderbare Zeiten und regelbasierte Kommentare.

## 2. Begriffe

### 2.1 Berichtsperiode

Eine Berichtsperiode besteht aus einem inklusiven Start- und Enddatum in `Europe/Berlin`.

```text
period_start  inklusiv
period_end    inklusiv
```

Alle fachlichen Tages-, Ergebnis- und Serienabfragen sind auf `period_end` begrenzt. Daten nach dem Periodenende dürfen den Bericht nicht beeinflussen.

### 2.2 Teilnahmetag

Ein **Teilnahmetag** ist ein Kalendertag, an dem ein Spieler laut `player_participation_period` aktiv war.

Der Begriff ist ausdrücklich nicht mit **Aktivitätstag** gleichzusetzen:

- Teilnahmetag: Spieler war zur Teilnahme verpflichtet beziehungsweise berechtigt.
- Aktivitätstag: Spieler hat mindestens eines der beiden Spiele gültig eingereicht.

Ein Spieler kann daher Teilnahmetage ohne Aktivitätstage besitzen.

### 2.3 Gemeinsam möglicher Tag

Ein Tag ist **gemeinsam möglich**, wenn mindestens zwei Spieler an diesem Tag laut ihren Teilnahmezeiträumen aktiv waren.

Die Menge kann sich innerhalb einer Woche oder eines Monats ändern. Es gibt keine feste Spielerzahl und keine feste Paarzuordnung.

### 2.4 Veröffentlichungs-Snapshot

Ein erfolgreich veröffentlichter Bericht ist ein Snapshot zum tatsächlichen Veröffentlichungszeitpunkt. Nach erfolgreichem Abschluss werden spätere Ergebnisse oder Ergebniskorrekturen nicht automatisch in denselben Bericht eingearbeitet.

Eine kontrollierte Neuerzeugung nach externer Löschung innerhalb des Catch-up-Fensters ist die einzige Ausnahme. Sie wird aus dem dann vorhandenen, weiterhin auf das Periodenende begrenzten Datenbestand neu abgeleitet.

## 3. Berichtstypen und Zeiträume

### 3.1 Wochenbericht

Standardfälligkeit:

```text
Wochentag: Montag
Uhrzeit:   08:00
Zeitzone:  Europe/Berlin
```

Die Berichtsperiode ist die vollständig abgeschlossene Vorwoche:

```text
vorheriger Montag bis vorheriger Sonntag
```

Beispiel:

```text
Fälligkeit: 3. August 2026, 08:00
Periode:    27. Juli 2026 bis 2. August 2026
```

### 3.2 Monatsbericht

Standardfälligkeit:

```text
Tag:       erster Kalendertag
Uhrzeit:   08:15
Zeitzone:  Europe/Berlin
```

Die Berichtsperiode ist der vollständig abgeschlossene Vormonat.

Beispiel:

```text
Fälligkeit: 1. September 2026, 08:15
Periode:    1. August 2026 bis 31. August 2026
```

### 3.3 Konfiguration

In Inkrement 10 bleiben ausschließlich die vorhandenen externen Werte maßgeblich:

```text
WEEKLY_REPORT_TIME=08:00
MONTHLY_REPORT_TIME=08:15
TIME_ZONE=Europe/Berlin
```

Eine persistente Zeitkonfiguration oder Scheduler-Neuplanung per Slash-Command gehört nicht zu diesem Inkrement.

### 3.4 Kalender- und DST-Regeln

- Perioden werden mit `LocalDate` in der fachlichen Zeitzone bestimmt.
- Fälligkeiten werden DST-sicher aus lokalem Datum, lokaler Uhrzeit und `ZoneId` gebildet.
- Fachlicher Code verwendet eine injizierte `Clock`.
- Jahres-, Monats-, Schaltjahr- sowie Sommer-/Winterzeitgrenzen müssen deterministisch getestet werden.

## 4. Auswahl und Reihenfolge der Spieler

Ein Spieler erscheint im Bericht, wenn mindestens ein Teilnahmetag innerhalb der Berichtsperiode existiert.

Ein gültiges Ergebnis ist für die Aufnahme in den Bericht nicht erforderlich. Dadurch bleiben auch aktive Spieler ohne Einreichung sichtbar.

Die Reihenfolge ist stabil:

1. frühester Beginn des ersten Teilnahmezeitraums des Spielers,
2. bei Gleichstand numerische Discord-User-ID.

Angezeigt wird der zum Zeitpunkt der Berichtserzeugung aktuell gespeicherte serverbezogene Anzeigename. Namensänderungen dürfen die stabile Reihenfolge nicht verändern.

## 5. Individuelle Nenner und Tagesmerkmale

Für jeden Spieler wird die Zahl seiner Teilnahmetage innerhalb der Periode bestimmt.

Nur diese Tage bilden den Nenner für fehlende Einreichungen und Tagesmerkmale. Tage vor einem Beitritt, nach einem Austritt oder zwischen getrennten Teilnahmezeiträumen zählen nicht.

Pro Spieler werden ausgewiesen:

- Teilnahmetage,
- Aktivitätstage,
- komplette Tage,
- perfekte Tage.

Die Definitionen aus `series-model.md` gelten unverändert:

- Aktivitätstag: mindestens ein Spiel eingereicht,
- kompletter Tag: beide Spiele eingereicht,
- perfekter Tag: beide Spiele eingereicht und beide gelöst.

## 6. Spielbezogene Periodenstatistiken

Für GridWords und QuadWords werden getrennt berechnet:

- **eingereicht:** Anzahl gültiger Ergebnisse,
- **gelöst:** Anzahl gültiger Ergebnisse mit numerischer Versuchszahl,
- **nicht gelöst:** Anzahl gültiger Ergebnisse mit `X/6` beziehungsweise `X/9`,
- **fehlend:** Teilnahmetage minus eingereichte Ergebnisse,
- **Lösungsquote:** gelöst geteilt durch eingereicht,
- **durchschnittliche Versuche:** ausschließlich gelöste Ergebnisse,
- **durchschnittliche Lösungszeit:** ausschließlich gelöste Ergebnisse,
- **Bestzeit:** kleinste Lösungszeit eines gelösten Ergebnisses.

Invarianten:

```text
eingereicht = gelöst + nicht gelöst
fehlend = Teilnahmetage - eingereicht
0 <= eingereicht <= Teilnahmetage
```

Bei null eingereichten Ergebnissen ist die Lösungsquote fachlich nicht definiert und wird nicht als `0 %` ausgegeben.

Bei null gelösten Ergebnissen sind Durchschnittsversuche, Durchschnittszeit und Bestzeit nicht definiert. Der Renderer zeigt dafür einen neutralen Platzhalter statt einer künstlichen Null.

Der transportneutrale Kern soll Summen, Anzahlen und Minima so bereitstellen, dass Rundung erst im Renderer erfolgt. Es werden keine früh gerundeten Durchschnittswerte als fachliche Daten persistiert.

## 7. Persönliche Serienwerte

Für jeden Spieler werden für alle fünf persönlichen Serien ausgewiesen:

1. Aktivitätsserie,
2. Komplettserie,
3. GridWords-Lösungsserie,
4. QuadWords-Lösungsserie,
5. Perfektserie.

Je Serienart werden zwei Werte berechnet:

- **Stand am Periodenende:** die Serie, wie sie nach dem vollständig abgeschlossenen `period_end` gilt,
- **Allzeitrekord bis Periodenende:** die längste Serie, deren betrachtete Tage nicht nach `period_end` liegen.

Ergebnisse nach `period_end` dürfen weder den Stand noch den Rekord beeinflussen.

Da die Periode abgeschlossen ist, gilt keine vorläufige Heute-Semantik. Fehlende Bedingungen am Periodenende sind endgültige Lücken.

## 8. Gemeinsame Kennzahlen

Die täglich wirksame Teilnehmermenge wird aus allen Teilnahmezeiträumen bestimmt.

Ausgewiesen werden:

- gemeinsam mögliche Tage,
- gemeinsam komplette Tage,
- gemeinsam perfekte Tage,
- gemeinsame Komplettserie am Periodenende,
- Allzeitrekord der gemeinsamen Komplettserie bis Periodenende,
- gemeinsame Perfektserie am Periodenende,
- Allzeitrekord der gemeinsamen Perfektserie bis Periodenende.

Ein gemeinsam kompletter oder perfekter Tag setzt mindestens zwei aktive Spieler voraus und muss von allen an diesem Tag aktiven Spielern erfüllt werden.

Es gibt weiterhin keine gemeinsame Aktivitätsserie.

Bei einer Periode mit nur einem aktiven Spieler sind die gemeinsam möglichen Tage und alle gemeinsamen Periodenzähler null. Der Bericht darf dies knapp als fehlende gemeinsame Tage darstellen.

## 9. Leere und ergebnislose Perioden

### 9.1 Keine Teilnehmer

Existiert in der gesamten Periode kein Teilnahmetag eines Spielers:

- wird keine Discord-Nachricht veröffentlicht,
- wird die Delivery persistent als fachlicher `NO_OP` abgeschlossen,
- darf derselbe Bericht bei Replay oder Neustart nicht später doch erscheinen.

### 9.2 Teilnehmer ohne Ergebnisse

Existiert mindestens ein Teilnehmer, aber kein gültiges Ergebnis:

- wird ein Bericht veröffentlicht,
- Teilnahmetage und fehlende Einreichungen werden korrekt dargestellt,
- nicht definierte Quoten und Leistungsdurchschnitte erscheinen als neutrale Platzhalter.

## 10. Darstellung in Discord

Wochen- und Monatsbericht verwenden dasselbe transportneutrale Datenmodell und grundsätzlich dasselbe Layout.

Verbindlich:

- klarer Berichtstyp und vollständiger Zeitraum im Titel,
- kompakte persönliche Abschnitte,
- gemeinsamer Abschnitt,
- eindeutige Serienbezeichnungen,
- keine Ergebnisraster,
- keine echten oder maskierten User-Mentions,
- keine Allowed Mentions,
- keine Gewinnerlogik, Ranglisten, Medaillen oder direkten Leistungsvergleiche,
- keine sichtbaren technischen Delivery-Schlüssel.

Ein logischer Bericht darf wegen Discord-Grenzen aus mehreren geordneten Nachrichten beziehungsweise Embeds bestehen. Die Seitenteilung muss deterministisch sein.

Die persistierte Reihenfolge der Discord-Message-IDs entspricht der sichtbaren Seitenreihenfolge.

## 11. Snapshot-, Korrektur- und Löschsemantik

Nach erfolgreicher vollständiger Veröffentlichung gilt der Report als abgeschlossen und eingefroren.

Spätere Ereignisse lösen keinen Edit aus:

- neue Ergebnisse,
- Ergebniskorrekturen,
- spätere Namensänderungen,
- spätere Teilnahmeänderungen.

Wird eine oder mehrere Berichtsseiten extern gelöscht:

- innerhalb des Catch-up-Fensters darf der vollständige logische Bericht kontrolliert reconciled und aus aktuellen, auf `period_end` begrenzten Daten neu erzeugt werden,
- nach Ablauf des Catch-up-Fensters wird kein alter Bericht automatisch wieder in den Channel gestellt.

Eine teilweise verbliebene Mehrseiten-Ausgabe darf nicht dauerhaft als scheinbar vollständiger Bericht stehen bleiben. Innerhalb des Catch-up-Fensters wird sie deterministisch vervollständigt oder ersetzt; außerhalb des Fensters wird der persistierte Zustand als nicht mehr automatisch reparierbar abgeschlossen.

## 12. Catch-up und Ablauf

### 12.1 Wochenbericht

Der Bericht darf bis höchstens 72 Stunden nach seiner Fälligkeit nachgeholt werden.

### 12.2 Monatsbericht

Der Bericht darf bis höchstens sieben Tage nach seiner Fälligkeit nachgeholt werden.

### 12.3 Grenzregel

Das Catch-up-Fenster ist halb offen:

```text
due_at <= now < due_at + catch_up_duration
```

Ab dem exakten Ende des Fensters ist die Delivery abgelaufen.

### 12.4 Startup-Reconciliation

Beim Start und bei regelmäßigen Scheduler-Ticks wird pro Berichtstyp höchstens der jüngste noch relevante fällige Bericht betrachtet.

Es werden keine Serien alter Berichte nachträglich in den Channel gespült. Ältere versäumte Perioden werden persistent als abgelaufen behandelt.

Wochen- und Monatsbericht sind voneinander unabhängige Berichtstypen. Sind beide am selben Tag fällig, dürfen beide gemäß ihren konfigurierten Zeiten veröffentlicht werden.

## 13. Persistenz

Statistikwerte, Serienstände und Spielerzusammenfassungen bleiben abgeleitet. Sie werden nicht als dauerhaftes Report-Snapshot-Modell gespeichert.

Persistiert werden ausschließlich Delivery- und Reconciliation-Daten, mindestens:

- Berichtstyp,
- Periodenbeginn und Periodenende,
- Guild-ID und Channel-ID,
- Delivery-Zustand,
- Claim-Token und Lease-Ablauf,
- Versuchsanzahl,
- nächster Retry-Zeitpunkt,
- letzte Fehlerkategorie,
- Inhalts-Fingerprint,
- geordnete Discord-Message-IDs,
- Fälligkeit,
- Veröffentlichungs- und Änderungszeitpunkt,
- `NO_OP`- beziehungsweise Ablaufzustand.

Der fachliche Unique Key enthält mindestens:

```text
Guild-ID + Channel-ID + Berichtstyp + Periodenbeginn
```

Periodenende und Fälligkeit werden zusätzlich persistiert und validiert.

## 14. Delivery- und Recoveryregeln

Die bewährten Prinzipien aus Tagesstatus und Remindern werden gezielt wiederverwendet:

- tokengebundene Claims,
- begrenzte Leases,
- Retry mit Backoff,
- explizite permanente Fehler,
- kurze atomare Zustandsübergänge,
- keine Discord-I/O in Datenbanktransaktionen,
- Inhalts-Fingerprint gegen unnötige Wiederholungen,
- Reconciliation unklarer Discord-Ausgänge,
- deterministische Duplikatbereinigung,
- sichere Wiederaufnahme nach Prozessabsturz.

Der Scheduler ist nur ein Trigger. PostgreSQL bleibt die Quelle für fällige, laufende, erfolgreiche, no-op, abgelaufene und fehlgeschlagene Deliveries.

Die Umsetzung darf gemeinsame konkrete Delivery-Bausteine extrahieren, aber kein universelles Messaging-Framework oder generisches Plugin-System einführen.

## 15. Befehle und manuelle Auslösung

In Inkrement 10 gibt es keine öffentlichen oder administrativen Slash-Commands für:

- Reportanzeige,
- Regenerierung,
- manuelle Veröffentlichung,
- Zeitkonfiguration.

Automatisierte und manuelle Tests dürfen Application-Use-Cases beziehungsweise kontrollierte Testkonfigurationen verwenden, ohne eine dauerhafte Discord-Bedienoberfläche einzuführen.

## 16. Testanforderungen

### 16.1 Standardbuild

Ohne Discord, Token, PostgreSQL oder Container:

- Wochen- und Monatsperioden,
- Jahres-, Monats-, Schaltjahr- und DST-Grenzen,
- persönliche Spiel- und Tagesstatistiken,
- Serienstand und Rekord am Periodenende,
- gemeinsame dynamische Teilnehmermenge,
- stabile Spielerreihenfolge,
- Null- und nicht definierte Werte,
- Renderer und Pagination,
- Application-Delivery, Replay, Retry und Recovery mit Testdoubles.

### 16.2 PostgreSQL-Integration

Mit echtem PostgreSQL:

- Teilnahmezeiträume mit Beitritt, Austritt und Wiedereintritt,
- abgeleitete Statistikabfragen,
- Liquibase-Migration,
- Unique Constraints,
- Claims, Leases, Retry, Ablauf und Konkurrenz,
- geordnete Message-IDs,
- Startup-Reconciliation,
- vollständiger Spring-Kontext.

### 16.3 Regression

Unverändert grün bleiben insbesondere:

- Ergebnisparser und Bildparser,
- kanonische Ergebnisveröffentlichung und Quelllöschung,
- dynamische Spieler und Commands,
- Tagesstatus,
- alle sieben Serien,
- Reminder und Mention-Sicherheit,
- Produktionscontainer und Betriebs-Vollpfad.

### 16.4 Reale Abnahme

Vor Aufhebung des Draft-Status werden mindestens geprüft:

- ein real veröffentlichter Wochenbericht,
- ein real veröffentlichter Monatsbericht,
- visuelle Darstellung und Pagination,
- persistierte Message-IDs,
- Duplikatschutz bei Wiederholung,
- Rückkehr zu den regulären Produktionszeiten.

## 17. Nicht Bestandteil

- Gewinnerlogik und Leaderboards,
- Statistik-Slash-Commands,
- Report- oder Regenerate-Commands,
- persistente Konfiguration der Ausführungszeiten,
- Scheduler-Neuplanung ohne Neustart,
- regelbasierte Kommentare,
- generative KI,
- mehrere Guilds oder Channels,
- Speicherung berechneter Berichtssnapshots als zweite fachliche Wahrheit.
