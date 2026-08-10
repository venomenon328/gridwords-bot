# Produktüberblick

Diese Dokumentation beschreibt den produktiven Funktionsumfang von GridWords Bot in Version 1.5.1. Sie ist die fachliche Einstiegsebene; technische Abläufe stehen unter [`../architecture/`](../architecture/overview.md).

## Zweck und Geltungsbereich

GridWords Bot begleitet eine private Discord-Spielgemeinschaft bei GridWords und QuadWords. Er übernimmt geteilte Ergebnisse, veröffentlicht eine kanonische Darstellung, führt den täglichen Status, erinnert an fehlende Ergebnisse und erzeugt Wochen- und Monatsberichte. Aus den kanonischen Ergebnissen und den historisch wirksamen Teilnahmen leitet er Serien, Rekorde und Achievements ab. Optional kann er kontextabhängige Ausreden anbieten.

Der Bot ist für genau einen konfigurierten Discord-Server und einen Channel ausgelegt. Menschliche Nutzer werden durch ein gültiges Share oder einen Teilnahme-Command dynamisch zu Spielern. Bot- und Webhook-Nachrichten werden ignoriert.

## Kanonische fachliche Quellen

- [`results-and-publication.md`](results-and-publication.md): Annahme, Korrektur und Veröffentlichung von Ergebnissen
- [`participation.md`](participation.md): dynamische und spielbezogene Teilnahme
- [`streaks.md`](streaks.md): persönliche und gemeinsame Serien
- [`daily-status-and-reminders.md`](daily-status-and-reminders.md): Tagesstatus, Details und Reminder
- [`excuses.md`](excuses.md): kontextabhängige Ausreden
- [`records.md`](records.md): Rekorddefinitionen und öffentliche Rekordmeldungen
- [`achievements.md`](achievements.md): Achievement-Katalog und Vergabe
- [`reports.md`](reports.md): Wochen- und Monatsberichte

## Gemeinsame fachliche Grundlagen

Die fachliche Zeitzone ist `Europe/Berlin`. Der Spieltag ist das im Share enthaltene Datum. Zwischen 00:00:00 und 05:59:59 Uhr sind der aktuelle und der unmittelbar vorherige Spieltag zulässig; ab 06:00 Uhr nur noch der aktuelle Spieltag. Diese Grenze gilt auch für Korrektur, Retry, Replay und Recovery eines begonnenen normalen Nutzervorgangs. Vollständig terminale Vorgänge bleiben idempotent. Administrative Backfills und Reparaturen sind davon getrennte, öffentlich stille Wartungsvorgänge.

Fachliche Ableitungen verwenden persistierte Spielergebnisse und historisch wirksame, spielbezogene Teilnahmezeiträume als Wahrheit. Aktuelle Komfortfelder, Materialisierungen und Discord-Nachrichten sind Projektionen und dürfen diese Quellen nicht ersetzen.

## Bewusste Grenzen

Das Produkt unterstützt keine beliebige Anzahl von Servern, Channels oder Spielen, keine generative KI zur Laufzeit, keine freie Regel- oder Template-Sprache, keine Ranglisten in Berichten und kein generisches Plugin-, Event- oder Messaging-Framework.
