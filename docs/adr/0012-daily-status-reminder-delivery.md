# ADR 0012: Persistente Tagesstatus- und Reminder-Auslieferung

- Status: akzeptiert
- Datum: 2026-07-31
- Kontext: Issue #21 und Draft-PR #22

## Kontext

Tagesstatus und Reminder kombinieren fachliche Tagesprojektion, Discord-I/O, zeitgesteuerte Ausführung und konkurrierende Persistenzzugriffe. Ein Prozessabbruch kann zwischen Discord-Erfolg und Datenbankabschluss liegen. Extern gelöschte Nachrichten, wiederholte Schedulerläufe und parallele Instanzen dürfen weder dauerhafte Lücken noch Doppelzustellungen erzeugen. Historische Statusprojektionen dürfen außerdem nicht die vorläufige Semantik des aktuellen Tages erben.

## Entscheidung

Die Fachprojektion erhält einen expliziten Serien-Stichtag. Vorläufigkeit ist eine ausdrücklich übergebene Eigenschaft und gilt ausschließlich, wenn der projizierte Spieltag dem aktuellen Berlin-Tag entspricht.

PostgreSQL ist die Quelle der Delivery-Zustände. `daily_status_message` ist je Guild, Channel und Spieltag eindeutig; `reminder_delivery` zusätzlich je Stufe. Beide verwenden tokengebundene Claims mit Lease, kurze Zustandsübergänge und Discord-I/O außerhalb der Datenbanktransaktion.

Statusinhalte werden per SHA-256 fingerprinted. Nur geänderte Inhalte werden editiert. Unveränderte Inhalte prüfen die Existenz der bekannten Nachricht. Stabile Discord-Footer-Schlüssel ermöglichen Recovery nach unklarem Sendeausgang; mehrfach gefundene Zustellungen werden deterministisch auf die kleinste Snowflake-ID konsolidiert.

Reminderzustände unterscheiden erfolgreich gesendet, keine Kandidaten, superseded, expired, retryable und permanent. Kandidaten werden nach dem Claim unmittelbar vor dem Versand erneut gelesen. Retryfähige Fehler erhalten Backoff; permanente Fehler bleiben terminal, bis eine relevante Inhaltsänderung eine neue Statusauslieferung rechtfertigt. Nach Mitternacht werden offene Reminder abgelaufen, nicht nachgesendet. Sind mehrere heutige Stufen überfällig, wird nur die späteste zugestellt.

Startup-Reconciliation und der reguläre Minutentakt rufen dieselben idempotenten Application Services auf. Zeitentscheidungen basieren ausschließlich auf injizierter `Clock` und konfigurierter `ZoneId`.

## Folgen

- Absturz, Restart, parallele Instanzen und externe Statuslöschung sind kontrolliert fortsetzbar.
- Unveränderte Statusnachrichten werden nicht minütlich editiert.
- Permanente Discord-Fehler verursachen keinen Hot-Loop.
- Historische Tagesstatuswerte sind endgültig und reproduzierbar.
- Die Persistenz enthält zusätzliche Zustands-, Claim-, Fingerprint- und Retryspalten.
- Die Suche nach stabilen Delivery-Schlüsseln kann Discord-Historie lesen, wird aber nur für Recovery oder unbekannte Message-IDs benötigt.

## Alternativen

Ein rein speicherinterner Schedulerzustand wurde verworfen, weil Restart und parallele Instanzen nicht sicher beherrschbar wären. Eine einzelne lange Transaktion über Discord-I/O wurde verworfen, weil sie Locks hält und einen unklaren externen Ausgang nicht beseitigt. Blindes erneutes Senden wurde wegen Doppelzustellungen verworfen.