# ADR 0006: Eigentümergebundene kanonische Veröffentlichungs-Claims

- **Status:** akzeptiert
- **Datum:** 2026-07-29

## Kontext

Eine Discord-Veröffentlichung und ihre Datenbankbestätigung sind nicht atomar. Ein Prozess kann nach erfolgreichem Send vor dem Persistieren abstürzen. Ein zeitlich begrenzter Claim ohne Eigentümer kann nach Ablauf von einem zweiten Worker übernommen werden, während der erste Worker noch läuft und anschließend eine fremde Veröffentlichung abschließt.

## Entscheidung

- Jeder Publish-Claim erhält einen zufälligen, persistenten Ownership-Token und eine Lease-Ablaufzeit.
- Abschluss, Freigabe und Fehlerbehandlung akzeptieren den Claim nur mit genau diesem Token.
- Recovery erkennt noch aktive Leases und plant einen erneuten Versuch nach deren Ablauf; es bleibt nicht bei einem einmaligen Startup-Scan.
- Die Discord-Wiedererkennung paginiert die Bot-Nachrichten des Zielchannels anhand der stabilen Footer-Kennung, statt nur ein festes jüngstes Fenster zu prüfen.
- Eine erfolgreiche Recovery setzt die bestätigende Reaktion am Original nach.

## Konsequenzen

Der Persistenzadapter erhält explizite Claim-/Abschlussoperationen mit Token. Die zusätzliche Zustandsinformation ist notwendig, um Mehrfachsendungen und fremde Abschlüsse bei langsamen oder abgestürzten Workern zu verhindern.
