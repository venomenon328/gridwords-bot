# ADR 0008: Deterministische Supersession kanonischer GridWords-Veröffentlichungen

- Status: Akzeptiert
- Datum: 2026-07-29
- Kontext: Issue #9, kanonische Ergebnisnachrichten

## Kontext

Mehrere Discord-Quellnachrichten können dieselbe veränderliche `game_result`-Zeile korrigieren. Eine fehlgeschlagene ältere Veröffentlichung darf nach einer erfolgreich publizierten neueren Korrektur weder das kanonische Embed zurücksetzen noch eine verspätete Erfolgsreaktion auslösen. Der bestehende Lease-Claim verhindert nur parallele Publisher, bestimmt aber keine fachliche Reihenfolge von Quellen.

## Entscheidung

Für alle Submissions, die mit demselben `game_result` verbunden sind, ist die fachliche Reihenfolge das stabile Tupel `(received_at, source_message_id)` in aufsteigender Reihenfolge. Die spätere Submission ist maßgeblich.

Der Persistenzadapter serialisiert die Entscheidung mit einer Sperre auf der betroffenen `game_result`-Zeile:

- Beim Speichern oder unmittelbar vor einem Publish-Versuch supersediert eine neuere Submission alle älteren offenen Submissions (`RESULT_STORED`, `FAILED_RETRYABLE`).
- Erkennt eine Submission bereits eine spätere verbundene Submission, wechselt sie selbst atomar in den terminalen Zustand `SUPERSEDED`.
- Bereits erfolgreich publizierte Quellen bleiben als abgeschlossene Historie `CANONICAL_MESSAGE_PUBLISHED`; sie werden nicht erneut veröffentlicht. Nur offene Quellen werden supersediert.
- `SUPERSEDED` wird weder vom Startup-Recovery noch vom Live-Retry selektiert und erzeugt keine nachträgliche ✅-Reaktion.
- Ein Publisher mit bereits erworbenem Token kann nach einer Supersession die Abschluss-Transaktion nicht mehr abschließen; deren Änderung der Ergebniszeile wird mit der Transaktion zurückgerollt. Anschließend kann nur die aktuelle Quelle den Claim nutzen.
- Kehrt ein bereits gestarteter alter Discord-Edit erst nach einer erfolgreichen neueren Veröffentlichung zurück, erhöht der Persistenzadapter eine durable Refresh-Generation am `game_result` und markiert die Reconciliation als erforderlich. Der Application Service koalesziert Wake-ups pro `game_result_id`, merkt aber während eines laufenden Refreshs weitere Wake-ups als erneuten Durchlauf vor. Jeder Durchlauf ermittelt die aktuelle Quelle erneut nach `(received_at, source_message_id)`, rendert nur sie außerhalb der Transaktion und persistiert das Ergebnis nur, wenn Quelle und Claim weiterhin aktuell sind. Der Abschluss löscht den Refreshbedarf nur für exakt die Generation, die er gerendert hat; eine zwischenzeitlich erhöhte Generation bleibt offen. Startup-Recovery lädt solche offenen Refreshbedarfe erneut. Refreshes erzeugen keine Quellenreaktion.

Die Discord-Operationen bleiben außerhalb der Datenbanktransaktion. Der bestehende Claim-Token, die Lost-Message-Ersetzung und die idempotente Suche per Publication-Key bleiben unverändert.

## Folgen

Die neueste bekannte Korrektur bleibt nach Retry und Neustart maßgeblich. Eine in-flight ältere Discord-Operation kann nicht rückwirkend verhindert werden und kann das sichtbare Embed nach einem neueren Edit noch einmal verändern. Ihr abgewiesener Abschluss löst deshalb einen persistent vorgemerkten Refresh der aktuell maßgeblichen Quelle aus. Ein weiterer verspäteter Edit während dieses Refreshs wird weder durch In-Memory-Deduplizierung verloren noch durch dessen Abschluss gelöscht: Generation und Rerun erzwingen einen weiteren Durchlauf. Nach einem Prozessende bleibt der Refreshbedarf erhalten und wird beim Startup kontrolliert reconciled. Die Schema-Constraint akzeptiert weiterhin den zusätzlichen terminalen Submission-Zustand `SUPERSEDED`.

## Ergänzung: Write-ahead-Delivery-Fence und Duplikat-Reconciliation

Vor jedem kanonischen Discord-Create oder -Edit wird ein tokengebundener Eintrag in
`canonical_delivery_attempt` zusammen mit einer neuen Refresh-Generation persistiert. Dieser Eintrag bleibt nach
Prozessabbruch erhalten. Ein erfolgreicher tokengebundener Abschluss entfernt nur seinen eigenen Eintrag; ältere
noch offene Einträge halten den Refreshbedarf bewusst aufrecht. Ein erfolgreicher Refresh entfernt ausschließlich
Versuche bis zu seiner gerenderten Generation. Kehrt ein lebender, verspäteter Publisher danach zurück, fordert sein
Fehlerpfad erneut einen Refresh an.

Die Wiedererkennung über den stabilen Publication-Key liefert alle passenden Bot-Nachrichten. Die persistierte
`canonical_message_id` gewinnt; ohne sie gewinnt die kleinste Discord-Snowflake. Alle anderen gefundenen Bot-Nachrichten
werden außerhalb der Datenbanktransaktion gelöscht. Damit kann ein Lease-Takeover während eines langsamen ersten
Creates zwar kurzfristig zwei sichtbare Nachrichten erzeugen, aber nicht dauerhaft zwei kanonische Nachrichten
hinterlassen. Ein fehlgeschlagener Bereinigungsschritt bleibt durch den Write-ahead-Fence wiederaufnehmbar.