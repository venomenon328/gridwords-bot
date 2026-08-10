# Hintergrundverarbeitung

## Grundmodell

Spring-Scheduler lösen Arbeit aus, sind aber nicht die fachliche Wahrheit für Fälligkeit oder Abschluss. Persistente Jobs und Projektionen halten Fälligkeit, Claim, Lease, Versuchszahl, Retryzeitpunkt und terminalen Zustand. Mehrere Trigger oder ein Neustart dürfen deshalb keine doppelte öffentliche Wirkung erzeugen.

## Zeitgesteuerte Abläufe

- Tagesstatus und Cleanup orientieren sich an der lokalen 06:00-Grenze.
- Reminder prüfen standardmäßig 16:00 und 22:00 Uhr.
- Wochenberichte werden montags um 08:00 Uhr fällig.
- Monatsberichte werden am ersten Kalendertag um 08:15 Uhr fällig.

Alle Uhrzeiten gelten in `Europe/Berlin` und werden über einen injizierten `Clock` ausgewertet. Catch-up verarbeitet nur den jeweils jüngsten noch relevanten Slot beziehungsweise die jüngste Periode.

## Worker und Recovery

Worker beanspruchen Arbeit atomar, verlängern oder verlieren eine Lease und speichern jeden extern relevanten Fortschritt. Technische Fehler führen zu begrenztem Retry; fachlich dauerhafte Fehler werden terminal markiert und beobachtbar gemacht. Nach Prozessstart werden unvollständige Ergebnisersetzungen, Refresh-Aufträge und Deliveries erneut geprüft.

## Bootstrap

Rekord- und Achievement-Definitionen sind versionierter Anwendungscode. Ein Bootstrap berechnet historische Projektionen aus kanonischen Quellen, bleibt öffentlich still und markiert die aktive Definitionsversion erst nach vollständigem Erfolg als bereit. Live-Ankündigungen sind bis dahin gesperrt.

## Berichte

Der Reporting-Kern ist für Woche und Monat gemeinsam und erhält eine abgeschlossene Periode mit End-Stichtag. Erstellung und Veröffentlichung sind getrennt; `NO_OP`, Snapshot, Seitenreihenfolge und Message-IDs werden nachvollziehbar gespeichert. Eine eng begrenzte Startprüfung kann den jüngsten fälligen Wochenbericht im 1.5.1-Layout anhand seines Fingerprints refreshen.

## Betriebsbeobachtung

Actuator-Health und strukturierte Logs machen Datenbank-, Scheduler-, Bootstrap- und Delivery-Zustände sichtbar. Konkrete Prüf- und Wiederherstellungsabläufe stehen unter [`../operations/`](../operations/README.md).
