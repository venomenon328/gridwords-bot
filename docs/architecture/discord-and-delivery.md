# Discord und Delivery

## Eingangsadapter

JDA-Listener filtern Guild, Channel, Bots und Webhooks, kopieren unveränderliche Eingabedaten und delegieren an einen Application Service. Längere Datenbank- oder Discord-Arbeit läuft nicht auf dem Event-Thread. Fachlicher Kern und Application Services erhalten keine JDA-Typen.

Slash Commands und Komponenteninteraktionen werden serverseitig autorisiert. Abhängig vom Use Case werden Guild, Channel, Nutzer, aktuelle Message-ID, fachliche ID, Zustand, Generation, Runde, Position und Ablauf geprüft. Sichtbare Component-IDs sind niemals alleinige Autorität.

## Kanonische Ergebnisnachricht

Eine fremde Share-Nachricht wird erst nach erfolgreicher Veröffentlichung und Persistierung der kanonischen Bot-Message-ID gelöscht. Der Ablauf ist schrittweise persistiert und nach einem Neustart fortsetzbar. Embed und Action Rows werden immer gemeinsam geschrieben, damit terminale Zustände keine alten Komponenten behalten.

Korrekturen bearbeiten die bestehende kanonische Nachricht, sofern sie noch existiert. Ist eine externe Nachricht verschwunden oder der Ausgang eines Calls unbekannt, entscheidet der persistierte Zustand über Create, Edit, Delete oder No-op.

## Weitere öffentliche Projektionen

- Pro Spieltag gibt es genau eine Statusnachricht.
- Reminder werden je fälligem Slot höchstens einmal veröffentlicht.
- Wochen- und Monatsberichte sind logische, gegebenenfalls mehrseitige Deliveries mit geordneten Message-IDs.
- Rekord- und Achievement-Meldungen aggregieren zusammengehörige Fakten und besitzen eigene Delivery-Projektionen.

Delivery-Arbeit verwendet Claims, Leases, Retryzustände und Fingerprints. Ein erfolgreich veröffentlichter Bericht bleibt ein Snapshot; Ergebnis-, Status-, Rekord- und Achievement-Projektionen folgen ihren jeweiligen Reconciliation-Regeln.

## Mentions und Inhalte

Allowed Mentions werden explizit eingeschränkt. Nur Reminder dürfen Opt-in-Spieler gezielt erwähnen. Berichte, Rekord- und Achievement-Meldungen erzeugen keine Mentions. Logs enthalten keine Tokens, Passwörter, vollständige Umgebungskonfiguration oder unnötigen fremden Nachrichteninhalt.

## Transportneutralität

Renderer erhalten transportneutrale View-Modelle. Fachliche Berechnung entscheidet nicht über JDA-Embeds oder Components. Ebenso führen Interaktions-Use-Cases keine unmittelbaren, ungesicherten Discord-Edits aus, sondern persistieren den fachlichen Übergang und gegebenenfalls einen Delivery-Auftrag.
