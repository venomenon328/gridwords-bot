# Architekturüberblick

GridWords Bot ist ein modularer Monolith auf Java 21 mit Spring Boot, JDA, PostgreSQL und Liquibase. Er läuft als ein Anwendungsprozess für genau einen konfigurierten Discord-Server und Channel. Diese Dokumentation beschreibt die produktive Architektur von Version 1.5.1.

## Schichten und Abhängigkeiten

```text
Discord / Scheduler / PostgreSQL / Konfiguration
                    ↓ Adapter
             Application Services
                    ↓ Ports
       Fachlicher Kern und Parser
```

- Der fachliche Kern enthält Modelle, Regeln und Ableitungen. Er kennt weder Spring, JPA/Hibernate noch Discord.
- Parser sind deterministisch und frei von Datenbank-, Discord- und Netzwerkzugriffen.
- Application Services koordinieren Use Cases und sprechen Infrastruktur nur über Ports an; sie kennen keine JDA-Typen.
- Adapter implementieren Discord-, Persistenz-, Scheduler-, Katalog- und Observability-Belange.
- Konfiguration wird typisiert gebunden. Infrastrukturmodelle werden nicht ungeprüft als öffentliche Domänenmodelle verwendet.

Die Paketstruktur folgt diesen Grenzen unter `de.venomenon.gridwordsbot` mit den Hauptpaketen `domain`, `application`, `port`, `adapter`, `parser` und `config`. Achievements, Rekorde, Reporting, Status, Serien, Ausreden und Submissions bleiben fachlich benannte Module innerhalb desselben Deployments; es gibt keine Microservices oder verteilte Queue.

## Kanonische Daten und Projektionen

Persistierte Spielergebnisse und historische spielbezogene Teilnahmezeiträume sind die zentralen fachlichen Quellen. Serien und Berichtsstatistiken werden daraus mit explizitem Stichtag abgeleitet. Tagesstatus, aktuelle Rekorde, Achievement-Awards sowie Discord-Nachrichten sind reproduzierbare oder reconciliable Projektionen.

Details:

- [`data-and-consistency.md`](data-and-consistency.md): Datenmodell, Transaktionen, Idempotenz und Zeit
- [`discord-and-delivery.md`](discord-and-delivery.md): Listener, kanonische Nachrichten und Interaktionen
- [`background-processing.md`](background-processing.md): Scheduler, Claims, Recovery und Bootstrap
- [`production.md`](production.md): Container- und Produktionsarchitektur

Fachliche Semantik steht unter [`../product/`](../product/overview.md), Architekturentscheidungen und ihre Historie unter [`../adr/`](../adr/README.md).
