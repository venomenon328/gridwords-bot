# Ergebnisse und Veröffentlichung

## Annahmebereich

Der Bot verarbeitet im konfigurierten Server und Channel ausschließlich Nachrichten menschlicher Nutzer. Ein vollständig gültiges GridWords- oder QuadWords-Share aktiviert den Autor ab dem Spieltag für genau diesen Spieltyp und kann anschließend als Ergebnis verarbeitet werden.

Parser sind deterministisch und unterscheiden zwischen nicht anwendbarer Nachricht, gültigem Ergebnis und ungültigem Kandidaten. Sie greifen weder auf Datenbank noch Discord oder Netzwerk zu.

## GridWords

GridWords wird aus dem textuellen Unicode-Share gelesen. Das Share liefert Spieltag, gelöst/nicht gelöst, Versuche und gegebenenfalls Dauer. Format- und Plausibilitätsfehler führen nicht zu einer teilweise übernommenen Lösung.

## QuadWords und Boardbilder

QuadWords kombiniert den Share-Text mit genau einem passenden, eindeutigen Bildanhang. Unterstützt werden PNG und JPEG bis 8 MiB, höchstens 4096 Pixel je Kante und höchstens 12 Millionen Pixel. Die Auswertung verwendet Java ImageIO und deterministische Bildmerkmale; OCR, externe Bilddienste oder maschinelles Lernen werden nicht eingesetzt.

Die vier Boards werden in der Reihenfolge oben links, oben rechts, unten links, unten rechts gespeichert. Aktuelle Bildauswertung trägt die Parser-Version `quadwords-image-v2`. Historische QuadWords-Ergebnisse ohne Boarddetails bleiben gültig; boardabhängige Funktionen sind für sie nicht anwendbar.

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

Ein späteres zulässiges Share desselben Spielers, Spieltyps und Spieltags korrigiert das vorhandene Ergebnis, statt ein zweites anzulegen. Die kanonische Nachricht wird auf den neuen Zustand gebracht. Davon abhängige Ausreden-, Rekord-, Achievement-, Status- und Delivery-Projektionen werden nach ihren eigenen Regeln reconciled.

## Kanonische Ausgabe

Die öffentliche Ergebnisnachricht ist die einzige dauerhaft maßgebliche Discord-Darstellung des Ergebnisses. Sie enthält die kanonischen Ergebnisdaten und, soweit vorhanden, den gewählten Ausredentext. Stilbezeichnungen oder interne Zustände erscheinen dort nicht. Embed und Action Rows werden gemeinsam erzeugt oder editiert, damit kein veralteter Button zurückbleibt.

Nach Tagesabschluss ist das Ergebnis über den Tagesstatus auffindbar; dessen Detailansicht zeigt das Ergebnis beziehungsweise die Boards, einen gewählten Ausredentext, aktuell vom Ergebnis gehaltene Rekorde und am Spieltag erworbene aktive Achievements.

## Fehler und Beobachtbarkeit

Erwartbare Eingabeprobleme werden fachlich verständlich behandelt. Technische Veröffentlichungs-, Lösch- und Persistenzfehler bleiben retryfähig und werden ohne Token oder unnötigen fremden Nachrichteninhalt protokolliert. Discord-Operationen laufen nicht auf dem JDA-Event-Thread.
