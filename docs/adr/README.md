# Architecture Decision Records

ADRs dokumentieren getroffene Architekturentscheidungen in ihrem damaligen Kontext. Sie werden bei späteren Änderungen nicht rückwirkend umgeschrieben; Status und Verweise machen Ablösungen sichtbar. Aktuelle Systemarchitektur steht unter [`../architecture/`](../architecture/overview.md).

| ADR | Entscheidung | Status |
|---|---|---|
| [0001](0001-modular-monolith-with-ports-and-adapters.md) | Modularer Monolith mit Ports und Adaptern | akzeptiert |
| [0002](0002-idempotent-message-replacement.md) | Persistierter, idempotenter Nachrichtenersetzungsablauf | akzeptiert |
| [0003](0003-time-and-scheduling.md) | Explizite Uhr, Europe/Berlin und persistierte Auslieferungen | akzeptiert |
| [0004](0004-docker-optional-local-development.md) | Docker-optionale lokale Entwicklung | **teilweise ersetzt durch ADR 0010** |
| [0005](0005-postgresql-persistence-model.md) | PostgreSQL-Persistenzmodell und konfliktfeste Schreibzugriffe | akzeptiert |
| [0006](0006-owned-canonical-publication-claims.md) | Eigentümergebundene Veröffentlichungs-Claims | akzeptiert |
| [0007](0007-submission-publication-context.md) | Persistierter Auslösekontext für Serienhinweise | akzeptiert, später ergänzt |
| [0008](0008-submission-supersession.md) | Deterministische Submission-Supersession | akzeptiert |
| [0009](0009-gridwords-source-deletion-recovery.md) | Persistierte Quelllöschung mit Recovery | akzeptiert |
| [0010](0010-docker-available-local-development.md) | Docker-verfügbare lokale Entwicklung | akzeptiert; ersetzt 0004 teilweise |
| [0011](0011-transport-neutral-quadwords-image-parsing.md) | Transportneutrale QuadWords-Bildanalyse | akzeptiert |
| [0012](0012-daily-status-reminder-delivery.md) | Persistente Tagesstatus- und Reminder-Delivery | akzeptiert |
| [0013](0013-netcup-container-production.md) | Containerbetrieb auf Netcup VPS | akzeptiert |
| [0014](0014-persistent-periodic-report-delivery.md) | Persistente periodische Report-Delivery | akzeptiert |
| [0015](0015-persistent-channel-retention-and-day-close.md) | Channel-Retention und Tagesabschluss | akzeptiert |
| [0016](0016-game-specific-participation.md) | Spielbezogene historische Teilnahme | akzeptiert |
| [0017](0017-persistent-excuse-selection.md) | Persistente Ausredenauswahl und kanonischer Refresh | akzeptiert |
| [0018](0018-record-state-events-and-reconciled-announcements.md) | Rekordzustand, Auditereignisse und reconciliierte Meldungen | akzeptiert |
| [0019](0019-migration-clean-install-gate.md) | Reproduzierbarer Migration-Clean-Install-Gate | akzeptiert |
| [0020](0020-achievement-state-reconciliation-and-delivery.md) | Achievement-Reconciliation und idempotente Delivery | akzeptiert |
| [0021](0021-targeted-parser-rejection-recovery.md) | Gezielte Recovery persistierter Parser-Ablehnungen | akzeptiert |
| [0022](0022-achievements-v2-silent-catalog-upgrade.md) | Stilles `achievements-v2`-Katalogupgrade | akzeptiert |
