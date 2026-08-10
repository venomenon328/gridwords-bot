# Daten und Konsistenz

## Datenbank und Migrationen

PostgreSQL ist der dauerhafte Datenspeicher. Schemaänderungen erfolgen ausschließlich mit Liquibase; Hibernate validiert das Schema und erzeugt oder aktualisiert es nicht. Fachliche Eindeutigkeiten werden zusätzlich durch Datenbank-Constraints geschützt.

Persistenzadapter übersetzen zwischen Datenbankmodellen und fachlichen Typen. Ergebnis-, Teilnahme-, Delivery-, Rekord- und Achievement-Zustände besitzen explizite Identitäten und Zustandsübergänge. Berechnete Reportstatistiken und Serien werden nicht als zweite fachliche Wahrheit fortgeschrieben.

## Transaktionsgrenzen

Zusammengehörige fachliche Zustandsänderungen werden atomar persistiert. Dazu gehören insbesondere:

- eine `both`-Teilnahmeänderung für beide Spiele,
- Ausredenstatus und kanonischer Refresh-Auftrag,
- Rekordzustand, Auditereignis und die zugehörige Delivery-Projektion,
- Achievement-Award, Ereignis und Ankündigungsprojektion,
- Claims und der jeweils beanspruchte Arbeitszustand.

Discord ist kein Teilnehmer einer Datenbanktransaktion. Deshalb werden externe Operationen als persistierte, wiederaufnehmbare Schritte modelliert und nach unbekanntem Ausgang reconciled.

## Idempotenz und Konkurrenz

Eindeutige Schlüssel verhindern doppelte fachliche Ergebnisse und Deliveries. Optimistische Zustandsprüfungen, atomare Claims und zeitlich begrenzte Leases koordinieren konkurrierende Worker. Ein Retry prüft zuerst den persistierten Zustand und wiederholt nur einen noch nicht terminalen Schritt.

Bei Ergebnisersetzung bleibt die Reihenfolge Persistieren, Discord veröffentlichen, Message-ID persistieren, Original löschen und Abschluss speichern verbindlich. Korrekturen aktualisieren das bestehende Ergebnis und stoßen die betroffenen Projektionen erneut an.

## Zeitmodell

Fachliche Zeit verwendet einen injizierten `Clock` und `Europe/Berlin`. Fachcode ruft nicht unkontrolliert `LocalDate.now()` oder `Instant.now()` auf. Teilnahmeintervalle sind am Starttag einschließlich und am Endtag ausschließlich. Tages-, Perioden- und Bootstrapberechnungen erhalten explizite Stichtage.

Die 06:00-Grenze entscheidet über zulässige Spieltage in normalen Nutzervorgängen. Administrative Imports, Bootstrap und Reparaturen sind explizit getrennte stille Betriebswege; sie umgehen die fachlichen Regeln nicht versehentlich über denselben Use Case.

## Projektionen und Reconciliation

Rekordzustände, Achievement-Awards, Tagesstatus und Discord-Deliveries können aus ihren Quellen geprüft beziehungsweise wiederhergestellt werden. Öffentliche Historie wird nicht als Wahrheit geparst. Extern gelöschte oder nach Timeout unbekannte Nachrichten werden anhand persistierter IDs, Fingerprints und Zustände reconciled.
