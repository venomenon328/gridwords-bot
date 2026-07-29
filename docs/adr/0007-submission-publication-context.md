# ADR 0007: Persistierter Ausloesekontext fuer kanonische Serienhinweise

- **Status:** akzeptiert
- **Datum:** 2026-07-29

## Kontext

Die optionalen Komplett- und Perfektserien einer kanonischen GridWords-Nachricht duerfen nur erscheinen, wenn genau diese Einreichung den entsprechenden Tageszustand herstellt. Der spaetere Zustand von `game_result` und insbesondere eine fehlende `canonical_message_id` sind dafuer kein verlaesslicher Ersatz: ein fehlgeschlagener erster Publish kann nach einer nachfolgenden QuadWords-Einreichung wiederholt werden, und eine Korrektur kann vor der ersten erfolgreichen kanonischen Nachricht eintreffen.

Mehrere Live-Einreichungen duerfen den Vorher-/Nachhervergleich nicht gegeneinander verfaelschen. Eine abgewiesene Publish-Claim-Anfrage muss ausserdem ohne Neustart wieder aufgenommen werden, darf aber keine Erfolgsreaktion ausloesen, solange die kanonische Nachricht noch nicht bestaetigt ist.

## Entscheidung

- Beim Speichern einer GridWords-Einreichung wird unter geordneten Sperren der zwei konfigurierten Spielerzeilen der Tageszustand vor und nach dem Upsert bewertet.
- Die vier positiven Uebergaenge (persoenlich komplett, persoenlich perfekt, gemeinsam komplett, gemeinsam perfekt) werden an der Quellsubmission gespeichert. Spaetere Publish-, Edit- und Recovery-Versuche verwenden ausschliesslich diesen Kontext.
- Kontextserien werden nur angezeigt, wenn der gespeicherte Uebergang zutrifft und der berechnete Serienwert groesser als null ist.
- Jede fehlgeschlagene oder kollidierende kanonische Veroeffentlichung plant pro Quellnachricht hoechstens einen kontrollierten Retry nach Lease-Ablauf. Der Retry verwendet weiterhin den persistierten Claim-Token fuer Abschluss oder Freigabe und setzt die Erfolgsreaktion erst nach erfolgreicher Persistierung.

## Konsequenzen

Die Submission-Tabelle erhaelt vier unveraenderliche Kontextspalten. Die Speicherung einer GridWords-Einreichung sperrt kurz die konfigurierte Spielerpaarung; dadurch bleiben die Tagesuebergaenge bei konkurrierenden Einreichungen deterministisch. Der Scheduler ist keine dauerhafte Queue: bei Prozessende oder Scheduler-Abweisung bleibt der offene Persistenzzustand durch Startup-Recovery wiederaufnehmbar.