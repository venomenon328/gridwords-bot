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

Die Discord-Operationen bleiben außerhalb der Datenbanktransaktion. Der bestehende Claim-Token, die Lost-Message-Ersetzung und die idempotente Suche per Publication-Key bleiben unverändert.

## Folgen

Die neueste bekannte Korrektur bleibt nach Retry und Neustart maßgeblich. Eine in-flight ältere Discord-Operation kann nicht rückwirkend verhindert werden, wird aber nicht als kanonischer Zustand persistiert; der aktuelle Retry stellt anschließend das maßgebliche Embed her. Die Schema-Constraint akzeptiert dafür den zusätzlichen terminalen Submission-Zustand `SUPERSEDED`.
