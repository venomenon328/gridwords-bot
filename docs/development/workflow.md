# Entwicklungsworkflow

## Änderung vorbereiten

1. Aktuellen Nutzerauftrag beziehungsweise Issue und die betroffenen Dokumente unter [`../product/`](../product/overview.md), [`../architecture/`](../architecture/overview.md), [`../adr/`](../adr/README.md) und [`../operations/`](../operations/README.md) lesen.
2. Auf dem beauftragten Branch arbeiten und vorhandene Nutzeränderungen unverändert lassen.
3. Fachliche oder technische Widersprüche sichtbar machen; keine neue Entscheidung beiläufig im Code oder in einer Konsolidierung treffen.

## Implementieren

Kleine, fachlich benannte Typen und klare Kontrollflüsse sind abstrakten Frameworks vorzuziehen. Fachkern, Application Services und Adapter halten die dokumentierten Abhängigkeitsgrenzen ein. Neue Abhängigkeiten werden nur mit überprüfter stabiler Version und begründetem Nutzen eingeführt; Versionen werden nicht geraten.

Schemaänderungen erfolgen ausschließlich über Liquibase. Architektur-, Persistenz-, Delivery-, Scheduling-, Deployment- oder Technologieentscheidungen benötigen vor der Implementierung ein neues ADR. Bestehende ADRs werden bei einer Ablösung nicht rückwirkend umgeschrieben.

## Prüfen und veröffentlichen

Den Standardbuild und alle für den Änderungsbereich erforderlichen Zusatzgates aus [`testing.md`](testing.md) ausführen. Änderungen anhand von `git status` und Diff auf Umfang, Secrets, generierte Binärdateien und fremde Dateien prüfen. Logisch zusammengehörige Änderungen erhalten verständliche Commits.

Pull Requests beschreiben Nutzen, fachliche Auswirkungen, Tests und verbleibende manuelle Prüfungen. Produktions-, Backup-, Restore-, Deployment- oder Discord-Smoke-Ergebnisse dürfen nur behauptet werden, wenn sie tatsächlich durchgeführt wurden.

## Dokumentationspflege

- Aktuelle Produktsemantik gehört nach `docs/product/`.
- Aktuelle technische Struktur gehört nach `docs/architecture/`.
- Entscheidungen und Ablösungen gehören nach `docs/adr/`.
- Ausführbare Entwickler- und Betriebsabläufe gehören nach `docs/development/` beziehungsweise `docs/operations/`.
- Abgeschlossene Pläne und Abnahmen gehören nach `docs/history/` und sind nicht normativ.
- Redaktionelle Buildquellen gehören nach `content/`.

README-Dateien dienen als Einstieg und verlinken auf die kanonische Ebene, statt sie vollständig zu duplizieren.
