# Verbindliche Präzisierung: Umfang und Qualität des Ausredenkatalogs

**Status:** fachlich abgenommen  
**Stand:** 4. August 2026  
**Geltungsbereich:** Inkrement 11, Paket 8A

Dieses Dokument ersetzt ausschließlich die frühere Mengenaussage in `docs/requirements/excuses.md`, nach der der erste redaktionelle Grundstock ungefähr 70 bis 90 Templates enthalten sollte. Alle übrigen Regeln des Hauptrequirements bleiben unverändert.

## Verbindlicher Startumfang

Der produktive Erstkatalog enthält **564 auswählbare Templates**:

| Familie | Anzahl |
|---|---:|
| Allgemein | 144 |
| `NOT_SOLVED` | 64 |
| `VERY_LATE_SUBMISSION` | 58 |
| `GRIDWORDS_LAST_ATTEMPT` | 56 |
| `GRIDWORDS_VERY_SLOW` | 56 |
| `QUADWORDS_VERY_SLOW` | 56 |
| `QUADWORDS_SINGLE_BOARD_COLLAPSE` | 72 |
| `CLEAR_CURRENT_DAILY_OUTLIER` | 58 |
| **Gesamt** | **564** |

## Stilabdeckung

- Jeder der acht Stile besitzt genau 18 allgemeine Texte.
- Jeder der sieben spezifischen Anlässe verwendet alle acht Stile.
- Jede normale Kombination aus Anlass und Stil besitzt mindestens sechs Texte.
- Besonders geeignete Stile besitzen zusätzliche Varianten.
- Der einzelne QuadWords-Board-Zusammenbruch differenziert universelle Texte, ein ungelöstes Board und einen gelösten deutlichen Ausreißer.

## Redaktionelle Qualität

Ein Text soll den Anlass nicht lediglich beschreiben. Er muss nach Möglichkeit eine Ursache, Verteidigung, Umdeutung, Schuldverschiebung, Relativierung oder absurde Begründung liefern.

Zulässig bleiben reine beziehungsweise fast reine Tatsachenfeststellungen, wenn die Pointe gerade aus extremer Kürze, Überhöhung oder unangemessener Stilsicherheit entsteht. Insbesondere werden bewusst knappe norddeutsche und bewusst überdramatische Texte nicht wegen ihrer Form entfernt.

Verbindlicher Detailmaßstab ist `docs/editorial/excuse-catalog-quality-guidelines.md`.

## Technische Konsequenzen

- Die stilzuerst arbeitende Auswahl verhindert, dass ein größerer Stilfundus allein wegen seiner Textanzahl häufiger gewählt wird.
- Alle Texte verwenden zunächst ein Gewicht von `100`.
- Allgemeine Texte verwenden Spezifität `0`.
- Normale Anlassfamilien verwenden Spezifität `20`.
- Universelle Board-Zusammenbruch-Texte verwenden Spezifität `30`.
- Untervariantenspezifische Board-Texte verwenden Spezifität `40`.
- Der vollständige Katalog muss den produktiven Loader, Coverage-Prüfungen, Dublettenschutz und den Standardbuild bestehen.

## Quellen

Der redaktionelle Bestand, Präzedenz und aktuelle Bearbeitungsstatus sind in `docs/editorial/excuse-catalog-manifest.md` dokumentiert.
