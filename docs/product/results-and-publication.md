# Ergebnisse und Veröffentlichung

## Annahmebereich

Der Bot verarbeitet im konfigurierten Server und Channel ausschließlich Nachrichten menschlicher Nutzer. Ein vollständig gültiges GridWords- oder QuadWords-Share aktiviert den Autor ab dem Spieltag für genau diesen Spieltyp und kann anschließend als Ergebnis verarbeitet werden.

Parser sind deterministisch und unterscheiden zwischen nicht anwendbarer Nachricht, gültigem Ergebnis und ungültigem Kandidaten. Sie greifen weder auf Datenbank noch Discord oder Netzwerk zu.

## GridWords

GridWords wird aus dem textuellen Unicode-Share gelesen. Das Share liefert Spieltag, gelöst/nicht gelöst, Versuche und Dauer sowie optional die Gridgames-Flammenserie. Das normalisierte Raster enthält pro Zeile genau fünf kanonische Zellen und muss zum Ergebnis passen. Format- und Plausibilitätsfehler führen nicht zu einer teilweise übernommenen Lösung.

## QuadWords und optionale Boardbilder

Ein vollständig gültiger QuadWords-Share-Text ist auch **ohne Bildanhang** ein vollständiges fachliches Ergebnis. Gelöststatus, Versuchswert, Spieltag und Dauer stammen aus dem Share-Text. Ein aktuelles boardloses Ergebnis wird mit Parser-Version `quadwords-share-v2` gespeichert und zählt uneingeschränkt für Teilnahme, Status, Serien, Rekorde, Achievements und Berichte, soweit die jeweilige Funktion keine Boarddaten voraussetzt.

Ein Bildanhang ist eine optionale Darstellungserweiterung. Bei genau einem plausiblen Bild wird dieser kontrolliert geladen und deterministisch ausgewertet. Unterstützt werden PNG und JPEG bis 8 MiB, höchstens 4096 Pixel je Kante und höchstens 12 Millionen Pixel. Die Auswertung verwendet Java ImageIO und deterministische Bildmerkmale; OCR, externe Bilddienste oder maschinelles Lernen werden nicht eingesetzt.

Eine erfolgreiche Bildauswertung ergänzt genau vier Boards in der Reihenfolge oben links, oben rechts, unten links, unten rechts und verwendet Parser-Version `quadwords-image-v2`. Für die Persistenz sind genau null oder vier Boardwerte zulässig; ein teilweise befüllter Satz ist ungültig.

Fehlt ein Bild, entsteht keine Warnung. Ein nicht nutzbarer optionaler Anhang darf einen ansonsten gültigen Share-Text nicht dauerhaft als fachlich ungültig behandeln; ein vorübergehend nicht ladbarer plausibler Anhang bleibt technisch retryfähig. Ein späteres gültiges bebildertes Share darf denselben Ergebnisdatensatz und dieselbe kanonische Message-ID um Boarddaten anreichern. Ein späteres boardloses Replay entfernt bereits vorhandene Boards nicht und setzt deren Parser-Version nicht zurück.

Historische `quadwords-share-v1`-Ergebnisse ohne Boards bleiben lesbar. Boardabhängige Ausreden oder Achievements sind für ein Ergebnis ohne die benötigten Boarddaten nicht anwendbar; alle übrigen fachlichen Ableitungen verwenden weiterhin den kanonischen Share-Endwert.

## Sichere Ersetzung einer Share-Nachricht

Eine fremde Originalnachricht wird niemals vorzeitig gelöscht. Die wiederaufnehmbare Reihenfolge ist:

1. Share parsen und vollständig validieren,
2. Ergebnis idempotent persistieren,
3. kanonische Bot-Nachricht erfolgreich veröffentlichen,
4. deren Discord-Message-ID persistieren,
5. Originalnachricht löschen,
6. Verarbeitung als abgeschlossen persistieren.

Claims und Leases verhindern parallele Bearbeitung. Wiederholte Events, Timeouts und Prozessneustarts setzen am persistierten Schritt fort. Bei mehreren noch nicht terminalen Kandidaten für dasselbe Ergebnis gewinnt deterministisch der neuere Empfangszeitpunkt, bei Gleichstand die Source-Message-ID. Ein vollständig abgeschlossener Vorgang bleibt eine idempotente Wiederholung.

## Korrekturen

Ein späteres zulässiges Share desselben Spielers, Spieltyps und Spieltags korrigiert das vorhandene Ergebnis, statt ein zweites anzulegen. Die kanonische Nachricht wird auf den neuen Zustand gebracht, sofern sie nicht bereits absichtlich pensioniert wurde. Davon abhängige Ausreden-, Rekord-, Achievement-, Status- und Delivery-Projektionen werden nach ihren eigenen Regeln reconciled.

## Kanonische Ausgabe

Die öffentliche Ergebnisnachricht ist die kanonische Discord-Darstellung des aktuellen Ergebnisses, solange sie im Channel aktiv ist. Sie enthält die kanonischen Ergebnisdaten und, soweit vorhanden, den gewählten Ausredentext. Stilbezeichnungen oder interne Zustände erscheinen dort nicht. Embed und Action Rows werden gemeinsam erzeugt oder editiert, damit kein veralteter Button zurückbleibt.

Nach sicherer Finalisierung des Tagesstatus werden die individuellen Ergebnisnachrichten des abgeschlossenen Tages gemäß [`daily-status-and-reminders.md`](daily-status-and-reminders.md) pensioniert. Die fachlichen Ergebnisdaten bleiben erhalten und sind anschließend über den Tagesstatus beziehungsweise dessen ephemere Detailansicht erreichbar. Diese zeigt das Ergebnis beziehungsweise die Boards, einen gewählten Ausredentext, aktuell vom Ergebnis gehaltene Rekorde und am Spieltag erworbene aktive Achievements.

## Fehler und Beobachtbarkeit

Erwartbare Eingabeprobleme werden fachlich verständlich behandelt. Technische Download-, Veröffentlichungs-, Lösch- und Persistenzfehler bleiben entsprechend ihrer Kategorie retryfähig oder dauerhaft sichtbar und werden ohne Token oder unnötigen fremden Nachrichteninhalt protokolliert. Discord- und Attachment-Operationen laufen nicht auf dem JDA-Event-Thread und nicht innerhalb fachlicher Datenbanktransaktionen.
