# ADR 0002: Persistierter und idempotenter Discord-Nachrichtenersetzungsablauf

- **Status:** akzeptiert
- **Datum:** 2026-07-28

## Kontext

Der Bot soll vom Nutzer gepostete Gridgames-Ergebnisse bereinigen. Dazu muss er eine kanonische Bot-Nachricht veröffentlichen und anschließend die Originalnachricht löschen.

Discord und PostgreSQL unterstützen keine gemeinsame atomare Transaktion. Fehler können unter anderem auftreten:

- nach dem Speichern, aber vor dem Bot-Post,
- nach dem Bot-Post, aber vor dem Speichern seiner Message-ID,
- nach dem Speichern der Bot-Message-ID, aber vor dem Löschen des Originals,
- durch erneut zugestellte Gateway-Events,
- durch Neustart während eines Zwischenschritts.

Ein einfaches „senden und danach löschen“ ohne persistierten Zustand könnte Ergebnisse verlieren oder doppelte Bot-Nachrichten erzeugen.

## Entscheidung

Die Verarbeitung wird als **persistierter, idempotenter Ablauf** modelliert.

Verbindliche Reihenfolge:

1. Quellereignis anhand der Discord-Message-ID registrieren.
2. Nachricht parsen und vollständig validieren.
3. Fachliches Ergebnis idempotent speichern beziehungsweise aktualisieren.
4. Verarbeitungszustand `RESULT_STORED` persistieren.
5. Kanonische Bot-Nachricht veröffentlichen.
6. Bot-Message-ID persistieren und Zustand `CANONICAL_MESSAGE_PUBLISHED` setzen.
7. Erst jetzt Originalnachricht löschen.
8. Löschung und Abschluss persistieren.
9. Abgeleitete Tagesstatusnachricht aktualisieren.

Discord-Aufrufe erfolgen nicht innerhalb einer langen Datenbanktransaktion.

Mindestens folgende Eindeutigkeiten gelten:

- Quell-Message-ID eindeutig
- Spieler + Spieltyp + Spieltag eindeutig

Ein erneuter Lauf liest den persistierten Zustand und setzt beim ersten unvollständigen Schritt fort.

## Fehlerregeln

- Schlägt Parse oder Validierung fehl, wird nichts gelöscht.
- Schlägt Ergebnisspeicherung fehl, wird nichts veröffentlicht oder gelöscht.
- Schlägt Veröffentlichung fehl, bleibt das Original bestehen.
- Schlägt das Persistieren der Bot-Message-ID fehl, darf das Original nicht gelöscht werden.
- Schlägt nur die Originallöschung fehl, bleiben Ergebnis und kanonische Bot-Nachricht bestehen; die Löschung kann erneut versucht werden.
- Schlägt die Tagesstatusaktualisierung fehl, wird das Ergebnis nicht zurückgerollt.

## Doppelte kanonische Nachricht nach unklarem Publish-Ausgang

Ein Netzwerkfehler kann theoretisch eintreten, nachdem Discord die Bot-Nachricht akzeptiert hat, aber bevor die Anwendung die ID sicher erhalten und gespeichert hat.

Für Version 1 gilt:

- Der Discord-Adapter soll erfolgreiche Antworten vollständig abwarten und timeouts klar behandeln.
- Vor einem erneuten Publish darf nach Möglichkeit anhand eines stabilen Korrelationsmerkmals beziehungsweise gespeicherter Message-ID geprüft werden.
- Eine seltene doppelte Bot-Nachricht ist sicherer als das Löschen des Originals bei unklarem Zustand.
- Automatisches Löschen des Originals bleibt gesperrt, solange die kanonische Bot-Nachricht nicht eindeutig persistiert ist.

Eine komplexe externe Outbox oder Discord-Message-Suche wird erst eingeführt, falls reale Tests zeigen, dass die einfache persistierte Zustandsmaschine nicht ausreicht.

## Konsequenzen

### Positiv

- Kein Original wird vor sicherer Wiederveröffentlichung gelöscht.
- Neustarts und doppelte Events sind beherrschbar.
- Fehlerzustände sind diagnostizierbar.
- Der Ablauf kann mit Fake-Ports vollständig getestet werden.

### Negativ

- Mehr Persistenzzustände und Übergangstests.
- Mehrere kurze Datenbanktransaktionen pro Ergebnis.
- Discord und Datenbank bleiben grundsätzlich nur „eventually consistent“.

## Verworfene Alternativen

### Original sofort löschen und danach Bot-Nachricht senden

Unzulässig, weil ein Discord- oder Prozessfehler das Nutzerergebnis dauerhaft verlieren kann.

### Eine Datenbanktransaktion über Discord-Aufrufe offen halten

Discord ist nicht Teil der Transaktion. Lange Transaktionen würden Sperren und Fehlerfolgen verschärfen, ohne Atomarität herzustellen.

### Externer Message Broker/Outbox-Service

Für den aktuellen Umfang unnötige Infrastruktur. Der persistierte lokale Workflow deckt die relevanten Fehlerfälle ausreichend ab.