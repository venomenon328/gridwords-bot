# ADR 0001: Modularer Monolith mit Ports und Adaptern

- **Status:** akzeptiert
- **Datum:** 2026-07-28

## Kontext

Der GridWords-Bot verarbeitet Discord-Nachrichten für zwei Nutzer, persistiert tägliche Ergebnisse, berechnet Serien und sendet geplante Erinnerungen. Spätere Versionen ergänzen Bildverarbeitung, Berichte und Slash-Commands.

Das System ist klein, besitzt aber mehrere externe Grenzen:

- Discord/JDA
- PostgreSQL
- Zeitplanung
- temporärer Bildspeicher

Die fachlichen Regeln und Parser sollen ohne echte externe Systeme testbar bleiben. Gleichzeitig soll die Architektur nicht durch unnötige Services oder generische Frameworks aufgebläht werden.

## Entscheidung

Das System wird als einzelner Spring-Boot-Prozess in Form eines **modularen Monolithen** umgesetzt.

Die Abhängigkeitsrichtung folgt Ports und Adaptern:

- Domain enthält reine fachliche Java-Typen und Regeln.
- Application orchestriert Use Cases und Transaktionsgrenzen.
- Ausgangsports abstrahieren Discord, Persistenz und Artefaktspeicher.
- Adapter implementieren diese Ports mit JDA, JPA/PostgreSQL und Dateisystem.
- Parser sind deterministische Komponenten ohne externe Zugriffe.

JDA-, Spring- und JPA-Typen dürfen nicht in die Domain eindringen. Application Services kennen keine JDA-Typen und hängen nicht von konkreten Adaptern ab.

Es wird kein externer Message Broker und kein separates Service-Deployment eingeführt.

## Konsequenzen

### Positiv

- Fachliche Logik und Parser sind schnell und isoliert testbar.
- Discord- und Datenbankdetails können geändert werden, ohne Fachregeln umzuschreiben.
- Der Bot bleibt als einzelner Prozess leicht betreibbar.
- Architekturgrenzen können später durch ArchUnit geprüft werden.

### Negativ

- Es entstehen einige zusätzliche Port- und Mapping-Typen.
- JPA-Entities und Domänenmodelle dürfen nicht bequem vermischt werden.
- Entwickler müssen die Abhängigkeitsrichtung bewusst einhalten.

## Verworfene Alternativen

### Direkte JDA-/JPA-Nutzung in allen Services

Weniger Dateien, aber deutlich schlechter testbar und hohe Kopplung an Frameworks.

### Microservices

Für zwei Nutzer und einen Bot unverhältnismäßiger Betriebs- und Fehleraufwand.

### Vollständiges Event Sourcing/CQRS

Die benötigte Idempotenz kann mit normalen Tabellen und persistierten Verarbeitungszuständen erreicht werden.

### Generisches Plugin-System für Spiele

Es existieren genau zwei bekannte Share-Formate. Eine dynamische Plugin-Architektur wäre vorauseilende Generalisierung.