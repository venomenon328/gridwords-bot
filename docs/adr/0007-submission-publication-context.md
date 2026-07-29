# ADR 0007: Persistierter Ausloesekontext fuer kanonische Serienhinweise

- **Status:** akzeptiert, nach Smoke-Test ergaenzt
- **Datum:** 2026-07-29

## Kontext

Die optionalen Komplett- und Perfektserien einer kanonischen GridWords-Nachricht sollen beim erstmaligen Erscheinen nur dann eingeblendet werden, wenn die betreffende Einreichung den Tageszustand herstellt. Der spaetere Zustand von `game_result` und insbesondere eine fehlende `canonical_message_id` sind dafuer kein verlaesslicher Ersatz: ein fehlgeschlagener erster Publish kann nach einer nachfolgenden QuadWords-Einreichung wiederholt werden, und eine Korrektur kann vor der ersten erfolgreichen kanonischen Nachricht eintreffen.

Der manuelle Smoke-Test zeigte zugleich, dass eine spaetere Korrektur der bereits kanonisch publizierten GridWords-Nachricht die sichtbaren Hinweise nicht verlieren darf, solange der zuvor hergestellte komplette beziehungsweise perfekte Tageszustand weiterhin gilt.

Mehrere Live-Einreichungen duerfen den Vorher-/Nachhervergleich nicht gegeneinander verfaelschen. Eine abgewiesene Publish-Claim-Anfrage muss ausserdem ohne Neustart wieder aufgenommen werden, darf aber keine Erfolgsreaktion ausloesen, solange die kanonische Nachricht noch nicht bestaetigt ist.

## Entscheidung

- Beim Speichern einer GridWords-Einreichung wird unter geordneten Sperren der zwei konfigurierten Spielerzeilen der Tageszustand vor und nach dem Upsert bewertet.
- Die vier positiven Uebergaenge (persoenlich komplett, persoenlich perfekt, gemeinsam komplett, gemeinsam perfekt) werden an der Quellsubmission gespeichert. Der erste Publish verwendet diesen Kontext fuer die zusaetzlichen sichtbaren Serienhinweise.
- Beim Bearbeiten einer bereits publizierten kanonischen Nachricht bleiben vorhandene Komplett-Hinweise erhalten. Perfekt-Hinweise bleiben nur erhalten, wenn die korrigierte GridWords-Einreichung weiterhin geloest ist. Dadurch verliert eine reine Versuchs- oder Zeitkorrektur keine weiterhin gueltigen Tagesinformationen; eine Korrektur auf `X/6` entfernt dagegen Perfekt-Hinweise.
- Nicht positive beziehungsweise nicht mehr gueltige Serien werden nicht als `0 Tage` angezeigt.
- Jede fehlgeschlagene oder kollidierende kanonische Veroeffentlichung plant pro Quellnachricht hoechstens einen kontrollierten Retry nach Lease-Ablauf. Der Retry verwendet weiterhin den persistierten Claim-Token fuer Abschluss oder Freigabe und setzt die Erfolgsreaktion erst nach erfolgreicher Persistierung.

## Konsequenzen

Die Submission-Tabelle behaelt vier unveraenderliche Kontextspalten. Die Speicherung einer GridWords-Einreichung sperrt kurz die konfigurierte Spielerpaarung; dadurch bleiben die Tagesuebergaenge bei konkurrierenden Einreichungen deterministisch. Die Discord-Bearbeitung darf zuvor sichtbare, weiterhin gueltige Tageshinweise aus dem bestehenden Embed uebernehmen. Der Scheduler ist keine dauerhafte Queue: bei Prozessende oder Scheduler-Abweisung bleibt der offene Persistenzzustand durch Startup-Recovery wiederaufnehmbar.
