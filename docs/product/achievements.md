# Achievements

## Modell und Definitionsversion

Die aktive Definitionsversion `achievements-v1` enthält genau 60 stabile Definitionen. Jede besitzt einen unveränderlichen Schlüssel, Kategorie, Scope, Namen, Text und Emoji. Kategorien sind `EXPERIENCE`, `RELIABILITY`, `PERFORMANCE` und `SPECIAL`; Scopes sind `GRIDWORDS`, `QUADWORDS`, `CROSS_GAME` und `GLOBAL`.

Kanonische Quellen sind gültige Spielergebnisse, deren ursprünglicher Empfangszeitpunkt, historisch wirksame Teilnahme, QuadWords-Boarddaten sowie daraus abgeleitete Tages- und Serienzustände. `earned_on` bezeichnet den fachlichen Erreichungstag; `detected_at` den getrennten technischen Erkennungszeitpunkt.

## Verbindlicher Katalog

Die folgenden kompakten Tabellen sind vollständig. Schwellen bedeuten jeweils „mindestens“, sofern nicht ausdrücklich `exakt` angegeben.

### Teilnahme (`EXPERIENCE`)

| Scope | Schlüssel und Schwellen |
|---|---|
| GridWords | `participation.{1,10,25,50,100}.gridwords`: 1, 10, 25, 50 oder 100 gültige GridWords-Ergebnisse |
| QuadWords | `participation.{1,10,25,50,100}.quadwords`: 1, 10, 25, 50 oder 100 gültige QuadWords-Ergebnisse |

### Teilnahmeserien (`RELIABILITY`)

| Scope | Schlüssel und Schwellen |
|---|---|
| GridWords | `streak.participation.{10,25,50,100}.gridwords`: lückenlose spielbezogene Teilnahmeserie von 10, 25, 50 oder 100 Tagen |
| QuadWords | `streak.participation.{10,25,50,100}.quadwords`: lückenlose spielbezogene Teilnahmeserie von 10, 25, 50 oder 100 Tagen |

### Erfolgsserien (`PERFORMANCE`)

| Scope | Schlüssel und Schwellen |
|---|---|
| GridWords | `streak.success.{1,10,25,50,100}.gridwords`: lückenlose GridWords-Erfolgsserie von 1, 10, 25, 50 oder 100 Tagen |
| QuadWords | `streak.success.{1,10,25,50,100}.quadwords`: lückenlose QuadWords-Erfolgsserie von 1, 10, 25, 50 oder 100 Tagen |

Teilnahmeserien verwenden aufeinanderfolgende Tage historischer Spielteilnahme mit gültigem Ergebnis. Erfolgsserien verlangen zusätzlich eine Lösung. Ein nicht anwendbarer Tag bildet eine Grenze; nach Teilnahme ohne Ergebnis oder mit `X` ist die jeweilige Serie beendet.

### Exakte Versuchsergebnisse (`PERFORMANCE`)

| Schlüssel | Bedingung |
|---|---|
| `performance.solve.1.gridwords` | GridWords mindestens einmal exakt `1/6` gelöst |
| `performance.solve.2.gridwords` | GridWords mindestens einmal exakt `2/6` gelöst |
| `performance.solve.3.gridwords` | GridWords mindestens einmal exakt `3/6` gelöst |
| `performance.solve.4.quadwords` | QuadWords mindestens einmal exakt `4/9` gelöst |
| `performance.solve.5.quadwords` | QuadWords mindestens einmal exakt `5/9` gelöst |
| `performance.solve.6.quadwords` | QuadWords mindestens einmal exakt `6/9` gelöst |

### Spielübergreifende Tage

| Kategorie | Schlüssel und Schwellen |
|---|---|
| `RELIABILITY` | `crossgame.participation.{1,10,25,50,100}`: 1, 10, 25, 50 oder 100 verschiedene Tage mit gültigem Ergebnis in beiden Spielen |
| `PERFORMANCE` | `crossgame.success.{1,10,25,50,100}`: 1, 10, 25, 50 oder 100 verschiedene Tage, an denen beide Spiele gelöst wurden |

### Gesamterfahrung (`EXPERIENCE`)

| Schlüssel | Bedingung |
|---|---|
| `experience.total.100` | 100 gültige Ergebnisse über beide Spiele |
| `experience.total.200` | 200 gültige Ergebnisse über beide Spiele |
| `experience.total.300` | 300 gültige Ergebnisse über beide Spiele |

### Besondere Situationen (`SPECIAL`)

| Schlüssel | Bedingung |
|---|---|
| `situational.last_chance.gridwords` | GridWords exakt `6/6` gelöst |
| `situational.last_chance.quadwords` | QuadWords exakt `9/9` gelöst |
| `situational.quadwords.consecutive_board_attempts` | vier vorhandene gelöste Boardwerte ergeben sortiert exakt `n, n+1, n+2, n+3` |
| `situational.quadwords.outlier_board` | vier vorhandene gelöste Boardwerte; größter minus zweitgrößter Wert mindestens 3 |
| `situational.crossgame.equal_final_score` | beide Spiele am selben Tag gelöst und ihre kanonischen Endwerte `n` sind gleich |
| `situational.crossgame.double_last_chance` | am selben Tag GridWords `6/6` und QuadWords `9/9`, beide gelöst |
| `situational.deja_vu.gridwords` | drei unmittelbar aufeinanderfolgende gültige gelöste GridWords-Ergebnisse mit identischem Endwert |
| `situational.deja_vu.quadwords` | drei unmittelbar aufeinanderfolgende gültige gelöste QuadWords-Ergebnisse mit identischem `n/9`; Boarddaten sind irrelevant |
| `timing.before_0700` | ein ursprünglicher gültiger Share vor 07:00 Uhr lokaler Zeit |
| `timing.after_2300` | ein ursprünglicher gültiger Share ab 23:00 Uhr lokaler Zeit |
| `situational.crossgame.perfect_double` | am selben Tag GridWords `1/6` und QuadWords `4/9`, beide gelöst |
| `situational.failure_run.3.gridwords` | drei unmittelbar aufeinanderfolgende gültige erfolglose GridWords-Ergebnisse |
| `situational.failure_run.3.quadwords` | drei unmittelbar aufeinanderfolgende gültige erfolglose QuadWords-Ergebnisse |

Nur die beiden Boarddefinitionen benötigen Boarddaten; historische boardlose Ergebnisse bleiben für alle anderen Definitionen auswertbar.

### Anzeigenamen und Emojis

Diese Metadaten gehören zur stabilen Definition; die Bedingungen ergeben sich aus den Tabellen oben.

| Schlüssel | Emoji | Anzeigename |
|---|---|---|
| `participation.1.gridwords` | 👋 | GW: Dabei! |
| `participation.10.gridwords` | 🔟 | GW: Warmgespielt |
| `participation.25.gridwords` | 🏠 | GW: Stammgast |
| `participation.50.gridwords` | 📌 | GW: Feste Größe |
| `participation.100.gridwords` | 💯 | GW: Hundertprozentig dabei |
| `participation.1.quadwords` | 👋 | QW: Dabei! |
| `participation.10.quadwords` | 🔟 | QW: Warmgespielt |
| `participation.25.quadwords` | 🏠 | QW: Stammgast |
| `participation.50.quadwords` | 📌 | QW: Feste Größe |
| `participation.100.quadwords` | 💯 | QW: Hundertprozentig dabei |
| `streak.participation.10.gridwords` | 🔟 | GW: Zehn am Stück |
| `streak.participation.25.gridwords` | 🧱 | GW: Drangeblieben |
| `streak.participation.50.gridwords` | 🏃 | GW: Dauerläufer |
| `streak.participation.100.gridwords` | 🛡️ | GW: Unverwüstlich |
| `streak.participation.10.quadwords` | 🔟 | QW: Zehn am Stück |
| `streak.participation.25.quadwords` | 🧱 | QW: Drangeblieben |
| `streak.participation.50.quadwords` | 🏃 | QW: Dauerläufer |
| `streak.participation.100.quadwords` | 🛡️ | QW: Unverwüstlich |
| `streak.success.1.gridwords` | ✅ | GW: Geschafft! |
| `streak.success.10.gridwords` | 🔥 | GW: Heiß gelaufen |
| `streak.success.25.gridwords` | 🏅 | GW: Siegesserie |
| `streak.success.50.gridwords` | 🚀 | GW: Nicht zu stoppen |
| `streak.success.100.gridwords` | 👑 | GW: Hundertfach geliefert |
| `streak.success.1.quadwords` | ✅ | QW: Geschafft! |
| `streak.success.10.quadwords` | 🔥 | QW: Heiß gelaufen |
| `streak.success.25.quadwords` | 🏅 | QW: Siegesserie |
| `streak.success.50.quadwords` | 🚀 | QW: Nicht zu stoppen |
| `streak.success.100.quadwords` | 👑 | QW: Hundertfach geliefert |
| `performance.solve.1.gridwords` | 🎯 | GW: Volltreffer |
| `performance.solve.2.gridwords` | ✌️ | GW: Zweiter sitzt |
| `performance.solve.3.gridwords` | 3️⃣ | GW: Aller guten Dinge |
| `performance.solve.4.quadwords` | 4️⃣ | QW: Vier gewinnt |
| `performance.solve.5.quadwords` | ✋ | QW: High Five |
| `performance.solve.6.quadwords` | 6️⃣ | QW: Saubere Sechs |
| `crossgame.participation.1` | 🎮 | GW+QW: Doppelschicht |
| `crossgame.participation.10` | 🔁 | GW+QW: Doppelroutine |
| `crossgame.participation.25` | 🏠 | GW+QW: Doppelstammgast |
| `crossgame.participation.50` | 📦 | GW+QW: Im Doppelpack |
| `crossgame.participation.100` | 💯 | GW+QW: Doppelhundert |
| `crossgame.success.1` | ✌️ | GW+QW: Doppelsieg |
| `crossgame.success.10` | 🔥 | GW+QW: Doppelform |
| `crossgame.success.25` | ⚡ | GW+QW: Doppelt stark |
| `crossgame.success.50` | 🏅 | GW+QW: Zweifach souverän |
| `crossgame.success.100` | 👑 | GW+QW: Doppelkrone |
| `experience.total.100` | 💯 | Hundertsassa |
| `experience.total.200` | 🛤️ | Langstrecke |
| `experience.total.300` | 📚 | Lebendes Archiv |
| `situational.last_chance.gridwords` | ⏳ | GW: Auf den letzten Drücker |
| `situational.last_chance.quadwords` | ⏳ | QW: Auf den letzten Drücker |
| `situational.quadwords.consecutive_board_attempts` | 🚂 | QW: Durchmarsch |
| `situational.quadwords.outlier_board` | 👹 | QW: Endgegner |
| `situational.crossgame.equal_final_score` | 🎯 | GW+QW: Punktlandung |
| `situational.crossgame.double_last_chance` | 💓 | GW+QW: Doppeltes Herzschlagfinale |
| `situational.deja_vu.gridwords` | 🔁 | GW: Déjà-vu |
| `situational.deja_vu.quadwords` | 🔁 | QW: Déjà-vu |
| `timing.before_0700` | 🌅 | Frühaufsteher |
| `timing.after_2300` | 🦉 | Nachteule |
| `situational.crossgame.perfect_double` | ✨ | GW+QW: Perfekter Doppelschlag |
| `situational.failure_run.3.gridwords` | 🫠 | GW: Pleiten-Hattrick |
| `situational.failure_run.3.quadwords` | 🫠 | QW: Pleiten-Hattrick |

## Reconciliation und Persistenz

Der aktuelle Award-Zustand ist materialisiert, das Achievement-Ereignis append-only und die Discord-Ankündigung eine getrennte Delivery-Projektion. Reconciliation berechnet den Sollzustand aus den Quellen und führt abhängig vom bisherigen Zustand Unlock, No-op, Reaktivierung oder Invalidierung aus. Korrekturen dürfen eine Vergabe invalidieren oder wieder aktivieren; eine öffentliche Aberkennungsnachricht gibt es nicht.

Historischer Bootstrap ist öffentlich still, speichert aber rückwirkende Vergaben. Vor erfolgreichem Bootstrap der aktiven Definitionsversion sind Live-Ankündigungen gesperrt. Nach Einführung erhält jeder relevante Teilnehmer höchstens eine zusammengefasste Einführung; neue Live-Unlocks werden pro Ursache aggregiert veröffentlicht.

## Commands und Darstellungen

`/achievements` zeigt ephemer aktive Vergaben für den eigenen oder einen gewählten Nutzer, optional gefiltert nach Spiel und Erreichungsdatum. `/achievement-list` ist eine persönliche, ephemere Checkliste aller 60 Definitionen mit binärem Status; Filter sind Spiel/Scope, Kategorie und `Freigeschaltet`/`Offen`. Sie zeigt keinen Fortschrittszähler.

Ergebnisdetails zeigen aktive Vergaben mit `earned_on` am betrachteten Spieltag. Berichtshighlights verwenden ausschließlich dafür freigegebene, gültige Achievement-Ereignisse.
