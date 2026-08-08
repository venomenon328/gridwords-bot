# Verbindliches Achievement-Modell

**Status:** fachlich abgenommen  
**Stand:** 8. August 2026  
**Gültig ab:** Inkrement 13  
**Verbindliches Issue:** #86  
**Definitionsversion:** `achievements-v1`

Dieses Dokument definiert die fachliche Wahrheit für Achievements. Es ergänzt insbesondere:

- [`series-model.md`](series-model.md),
- [`game-specific-participation.md`](game-specific-participation.md),
- [`records.md`](records.md),
- ADR 0016,
- ADR 0018,
- ADR 0020.

Bei widersprüchlichen älteren Formulierungen zu Achievements gilt dieses Dokument.

---

## 1. Ziel und Grundsätze

Achievements würdigen unterschiedliche Arten der Teilnahme und nicht nur besonders starke Ergebnisse. Der Katalog soll Anerkennung insbesondere auf folgende Spielertypen verteilen:

- leistungsstark,
- zuverlässig,
- ausdauernd,
- früh oder spät spielend,
- chaotisch, aber hartnäckig,
- ausschließlich bei einem Spiel aktiv,
- bei beiden Spielen aktiv.

Der erste Katalog ist bewusst kuratiert. Er enthält 60 einmalig freischaltbare Achievements. Eine große Zahl beliebiger Spaßabzeichen, wiederholbare Freischaltungen, Ranglisten nach Achievement-Anzahl oder eine Aufgabenlistenwirkung sind ausdrücklich nicht Ziel von V1.

Jedes Achievement besitzt mindestens:

1. einen stabilen technischen Schlüssel,
2. die fachliche Definitionsversion,
3. einen global eindeutigen Anzeigenamen,
4. eine Kategorie,
5. einen Spielscope,
6. ein Unicode-Fallback-Emoji,
7. eine deutsche Beschreibung,
8. eine exakt testbare Freischaltbedingung.

Name, Beschreibung und Emoji sind Darstellungsmetadaten und dürfen später geändert werden. Der technische Schlüssel ist die dauerhafte Identität und darf nicht für eine fachlich andere Bedingung wiederverwendet werden.

Inkompatible fachliche Bedeutungsänderungen erhalten einen neuen Schlüssel. Reine Fehlerkorrekturen, die lediglich die dokumentierte Semantik herstellen, dürfen unter demselben Schlüssel umgesetzt werden. Die Definitionsversion ist von technischen Lock- oder Schema-Versionen getrennt.

---

## 2. Katalogverwaltung und Namensregeln

Die Definitionen werden als expliziter versionierter Anwendungscode oder gleichwertige versionierte Ressource gepflegt. Es gibt in V1:

- keine Datenbank-DSL,
- keinen Admin-Editor für Regeln,
- keine frei konfigurierbare Achievement-Engine,
- keinen generischen internen Event-Bus oder Plugin-Mechanismus.

Der vollständige Katalog wird beim Start und in einem Katalogtest validiert. Mindestens Schlüssel und Anzeigename müssen global eindeutig sein.

Für Anzeigenamen gelten folgende Präfixe:

- GridWords-spezifisch: `GW:`,
- QuadWords-spezifisch: `QW:`,
- Bedingungen, die beide Spiele gemeinsam voraussetzen: `GW+QW:`,
- echte globale Achievements: kein künstlicher Präfix, sofern der Name global eindeutig bleibt.

Unicode-Emojis sind der V1-Fallback. Spätere eigene Discord-Emojis oder Badge-Bilder dürfen anhand des stabilen Achievement-Schlüssels aufgelöst werden, ohne Vergaben, Schlüssel oder Historie zu migrieren.

---

## 3. Kanonische Datenbasis

Achievements werden ausschließlich aus bereits kanonisch persistierten Fachdaten abgeleitet. Discord-Texte oder Attachments werden nicht im Achievement-Evaluator erneut geparst.

Maßgebliche Quellen sind insbesondere:

- gültige `game_result`-Ergebnisse,
- der fachliche Spieltag des Ergebnisses,
- der ursprüngliche persistierte Empfangszeitpunkt des Shares,
- historisch wirksame spielbezogene Teilnahmezeiträume,
- kanonisch persistierte QuadWords-Boarddetails, soweit vorhanden,
- bestehende transportneutrale Serien- und Tagesklassifikation.

Eine Korrektur ersetzt den fachlichen Ergebnisstand und zählt nicht als zusätzliche Teilnahme.

Für QuadWords gilt:

- normale Ergebnis-Achievements verwenden ausschließlich den kanonischen geposteten `n/9`-Endwert,
- der Endwert wird nicht aus Einzelboarddaten rekonstruiert,
- `QW: Durchmarsch` und `QW: Endgegner` dürfen auf die vier kanonisch gespeicherten Einzelboarddaten zugreifen,
- fehlen diese Einzelboarddaten, wird aus dieser Submission keines der beiden bildabhängigen Achievements vergeben.

Es gibt dafür in der öffentlichen oder fachlichen Achievement-Semantik keinen zusätzlichen Status „nicht auswertbar“.

---

## 4. Gültiges Ergebnis, Erfolg und Reihenfolge

Ein **gültiges Ergebnis** ist ein akzeptiertes, kanonisch persistiertes und aktuell nicht invalidiertes Ergebnis eines Teilnehmers für genau ein Spiel und einen Spieltag.

Ein **erfolgreiches Ergebnis** ist:

- GridWords: ein gelöstes Ergebnis mit numerischem `n/6`,
- QuadWords: ein vollständig gelöstes Ergebnis mit numerischem `n/9`.

Ein erfolgloses gültiges Ergebnis ist das jeweilige kanonische `X`-Ergebnis.

„Unmittelbar aufeinanderfolgende Ergebnisse“ bezieht sich auf die chronologische Folge gültiger Ergebnisse desselben Teilnehmers und Spiels. Kalendertage ohne Teilnahme dazwischen sind unerheblich. Ein anderes gültiges Ergebnis desselben Spiels unterbricht die Folge.

Bei Gleichstand des fachlichen Spieltags wird die kanonische Ergebnisidentität beziehungsweise die vorhandene stabile Persistenzreihenfolge verwendet. Pro Teilnehmer, Spiel und Spieltag existiert fachlich höchstens ein gültiger Ergebnisstand.

---

## 5. Teilnahme- und Erfolgsserien

Teilnahmeserien und Erfolgsserien sind kalenderbasierte Serien über historisch wirksame Spielteilnahme.

### 5.1 Spielbezogene Teilnahmeserie

Eine persönliche Teilnahmeserie für ein Spiel wächst an einem Spieltag genau dann, wenn:

1. der Teilnehmer an diesem Tag für das betreffende Spiel historisch aktiv ist und
2. für dieses Spiel ein gültiges Ergebnis besitzt.

Ein aktiver Tag ohne gültiges Ergebnis beendet die Serie. Eine Deaktivierung des Spiels beendet die laufende Serie ebenfalls; eine spätere Reaktivierung beginnt eine neue Serie. Inaktive Tage werden nicht rückwirkend als versäumte Teilnahme gewertet, verbinden aber auch keine getrennten Serien.

### 5.2 Spielbezogene Erfolgsserie

Eine persönliche Erfolgsserie für ein Spiel wächst an einem Spieltag genau dann, wenn:

1. der Teilnehmer an diesem Tag für das betreffende Spiel historisch aktiv ist und
2. ein erfolgreiches gültiges Ergebnis dieses Spiels besitzt.

Ein erfolgloses Ergebnis, ein aktiver Tag ohne Ergebnis oder eine Deaktivierung beendet die laufende Erfolgsserie.

Die Achievement-Auswertung muss dieselbe kanonische Tages- und Teilnahme-Semantik wie das bestehende Serienmodell verwenden und darf keine widersprüchliche zweite Serienlogik einführen.

Bereits rechtmäßig erworbene Serien-Achievements bleiben nach einem späteren normalen Serienende bestehen. Sie werden nur invalidiert, wenn eine Datenkorrektur zeigt, dass die Schwelle historisch tatsächlich nie erreicht war.

---

## 6. Spielübergreifende Tage und Gesamterfahrung

Ein **Doppeltag** ist ein fachlicher Spieltag, an dem derselbe Teilnehmer sowohl ein gültiges GridWords- als auch ein gültiges QuadWords-Ergebnis besitzt.

Ein **erfolgreicher Doppeltag** ist ein Doppeltag, an dem beide Ergebnisse erfolgreich sind.

Die Meilensteine für Doppeltage und erfolgreiche Doppeltage sind kumulativ. Sie verlangen ausdrücklich keine aufeinanderfolgenden Kalendertage.

Die Gesamterfahrung zählt gültige GridWords- und QuadWords-Ergebnisse zusammen. Ein GridWords- und ein QuadWords-Ergebnis am selben Spieltag zählen als zwei Ergebnisse.

---

## 7. Zeit-Achievements

Zeitbedingungen verwenden den ursprünglichen persistierten Empfangszeitpunkt des gültigen Shares und die fachliche Bot-Zeitzone. Standard und verbindlicher aktueller Wert ist `Europe/Berlin`; Sommer- und Winterzeit werden über `ZoneId` behandelt.

- **Frühaufsteher:** lokale Uhrzeit strikt vor `07:00:00`.
- **Nachteule:** lokale Uhrzeit ab einschließlich `23:00:00`.

Korrektur, Replay, Retry oder spätere technische Erkennung dürfen den ursprünglichen Share-Zeitpunkt nicht durch ihren eigenen Verarbeitungszeitpunkt ersetzen.

---

## 8. Kategorien und Scopes

V1 verwendet vier Darstellungskategorien:

- `EXPERIENCE` – Erfahrung und Teilnahmeumfang,
- `RELIABILITY` – Teilnahmeserien und regelmäßige Teilnahme an beiden Spielen,
- `PERFORMANCE` – Erfolge, Erfolgsserien und klar leistungsbezogene Ergebnisse,
- `SPECIAL` – besondere, ungewöhnliche oder zeitbezogene Situationen.

V1 verwendet vier Scopes:

- `GRIDWORDS`,
- `QUADWORDS`,
- `CROSS_GAME`,
- `GLOBAL`.

Kategorie und Scope sind Definitionsmetadaten und keine Bestandteile des stabilen Schlüssels.

---

## 9. Verbindlicher Katalog `achievements-v1`

### 9.1 Teilnahme – `EXPERIENCE`

| Schlüssel | Scope | Emoji | Anzeigename | Beschreibung | Exakte Bedingung |
|---|---|---|---|---|---|
| `participation.1.gridwords` | GRIDWORDS | 👋 | **GW: Dabei!** | Dein erstes gültiges GridWords-Ergebnis. | Mindestens 1 gültiges GridWords-Ergebnis insgesamt. |
| `participation.10.gridwords` | GRIDWORDS | 🔟 | **GW: Warmgespielt** | Zehnmal bei GridWords dabei. | Mindestens 10 gültige GridWords-Ergebnisse. |
| `participation.25.gridwords` | GRIDWORDS | 🏠 | **GW: Stammgast** | GridWords gehört zur Routine. | Mindestens 25 gültige GridWords-Ergebnisse. |
| `participation.50.gridwords` | GRIDWORDS | 📌 | **GW: Feste Größe** | Fünfzig GridWords-Ergebnisse sprechen für sich. | Mindestens 50 gültige GridWords-Ergebnisse. |
| `participation.100.gridwords` | GRIDWORDS | 💯 | **GW: Hundertprozentig dabei** | Dreistellige GridWords-Erfahrung. | Mindestens 100 gültige GridWords-Ergebnisse. |
| `participation.1.quadwords` | QUADWORDS | 👋 | **QW: Dabei!** | Dein erstes gültiges QuadWords-Ergebnis. | Mindestens 1 gültiges QuadWords-Ergebnis insgesamt. |
| `participation.10.quadwords` | QUADWORDS | 🔟 | **QW: Warmgespielt** | Zehnmal bei QuadWords dabei. | Mindestens 10 gültige QuadWords-Ergebnisse. |
| `participation.25.quadwords` | QUADWORDS | 🏠 | **QW: Stammgast** | QuadWords gehört zur Routine. | Mindestens 25 gültige QuadWords-Ergebnisse. |
| `participation.50.quadwords` | QUADWORDS | 📌 | **QW: Feste Größe** | Fünfzig QuadWords-Ergebnisse sprechen für sich. | Mindestens 50 gültige QuadWords-Ergebnisse. |
| `participation.100.quadwords` | QUADWORDS | 💯 | **QW: Hundertprozentig dabei** | Dreistellige QuadWords-Erfahrung. | Mindestens 100 gültige QuadWords-Ergebnisse. |

### 9.2 Teilnahmeserien – `RELIABILITY`

| Schlüssel | Scope | Emoji | Anzeigename | Beschreibung | Exakte Bedingung |
|---|---|---|---|---|---|
| `streak.participation.10.gridwords` | GRIDWORDS | 🔟 | **GW: Zehn am Stück** | Zehn GridWords-Tage ohne Lücke. | Eine GridWords-Teilnahmeserie erreicht mindestens 10 Tage. |
| `streak.participation.25.gridwords` | GRIDWORDS | 🧱 | **GW: Drangeblieben** | 25 Tage konsequent mitgemacht. | Eine GridWords-Teilnahmeserie erreicht mindestens 25 Tage. |
| `streak.participation.50.gridwords` | GRIDWORDS | 🏃 | **GW: Dauerläufer** | Fünfzig GridWords-Tage hintereinander. | Eine GridWords-Teilnahmeserie erreicht mindestens 50 Tage. |
| `streak.participation.100.gridwords` | GRIDWORDS | 🛡️ | **GW: Unverwüstlich** | Hundert GridWords-Tage am Stück dabei. | Eine GridWords-Teilnahmeserie erreicht mindestens 100 Tage. |
| `streak.participation.10.quadwords` | QUADWORDS | 🔟 | **QW: Zehn am Stück** | Zehn QuadWords-Tage ohne Lücke. | Eine QuadWords-Teilnahmeserie erreicht mindestens 10 Tage. |
| `streak.participation.25.quadwords` | QUADWORDS | 🧱 | **QW: Drangeblieben** | 25 Tage konsequent mitgemacht. | Eine QuadWords-Teilnahmeserie erreicht mindestens 25 Tage. |
| `streak.participation.50.quadwords` | QUADWORDS | 🏃 | **QW: Dauerläufer** | Fünfzig QuadWords-Tage hintereinander. | Eine QuadWords-Teilnahmeserie erreicht mindestens 50 Tage. |
| `streak.participation.100.quadwords` | QUADWORDS | 🛡️ | **QW: Unverwüstlich** | Hundert QuadWords-Tage am Stück dabei. | Eine QuadWords-Teilnahmeserie erreicht mindestens 100 Tage. |

### 9.3 Erfolgsserien – `PERFORMANCE`

| Schlüssel | Scope | Emoji | Anzeigename | Beschreibung | Exakte Bedingung |
|---|---|---|---|---|---|
| `streak.success.1.gridwords` | GRIDWORDS | ✅ | **GW: Geschafft!** | Dein erster erfolgreicher GridWords-Abschluss. | Eine GridWords-Erfolgsserie erreicht mindestens 1 Tag. |
| `streak.success.10.gridwords` | GRIDWORDS | 🔥 | **GW: Heiß gelaufen** | Zehn erfolgreiche GridWords-Tage hintereinander. | Eine GridWords-Erfolgsserie erreicht mindestens 10 Tage. |
| `streak.success.25.gridwords` | GRIDWORDS | 🏅 | **GW: Siegesserie** | 25 GridWords-Erfolge ohne Unterbrechung. | Eine GridWords-Erfolgsserie erreicht mindestens 25 Tage. |
| `streak.success.50.gridwords` | GRIDWORDS | 🚀 | **GW: Nicht zu stoppen** | Fünfzig erfolgreiche GridWords-Tage am Stück. | Eine GridWords-Erfolgsserie erreicht mindestens 50 Tage. |
| `streak.success.100.gridwords` | GRIDWORDS | 👑 | **GW: Hundertfach geliefert** | Hundert GridWords-Erfolge hintereinander. | Eine GridWords-Erfolgsserie erreicht mindestens 100 Tage. |
| `streak.success.1.quadwords` | QUADWORDS | ✅ | **QW: Geschafft!** | Dein erster erfolgreicher QuadWords-Abschluss. | Eine QuadWords-Erfolgsserie erreicht mindestens 1 Tag. |
| `streak.success.10.quadwords` | QUADWORDS | 🔥 | **QW: Heiß gelaufen** | Zehn erfolgreiche QuadWords-Tage hintereinander. | Eine QuadWords-Erfolgsserie erreicht mindestens 10 Tage. |
| `streak.success.25.quadwords` | QUADWORDS | 🏅 | **QW: Siegesserie** | 25 QuadWords-Erfolge ohne Unterbrechung. | Eine QuadWords-Erfolgsserie erreicht mindestens 25 Tage. |
| `streak.success.50.quadwords` | QUADWORDS | 🚀 | **QW: Nicht zu stoppen** | Fünfzig erfolgreiche QuadWords-Tage am Stück. | Eine QuadWords-Erfolgsserie erreicht mindestens 50 Tage. |
| `streak.success.100.quadwords` | QUADWORDS | 👑 | **QW: Hundertfach geliefert** | Hundert QuadWords-Erfolge hintereinander. | Eine QuadWords-Erfolgsserie erreicht mindestens 100 Tage. |

### 9.4 Exakte Versuchsergebnisse – `PERFORMANCE`

Die sechs Achievements sind eigenständige Bedingungen und keine hierarchischen Stufen. Ein `1/6` schaltet insbesondere nicht automatisch `2/6` oder `3/6` frei.

| Schlüssel | Scope | Emoji | Anzeigename | Beschreibung | Exakte Bedingung |
|---|---|---|---|---|---|
| `performance.solve.1.gridwords` | GRIDWORDS | 🎯 | **GW: Volltreffer** | GridWords ohne Umwege. | Mindestens einmal erfolgreich exakt `1/6`. |
| `performance.solve.2.gridwords` | GRIDWORDS | ✌️ | **GW: Zweiter sitzt** | Nur einen Versuch Anlauf gebraucht. | Mindestens einmal erfolgreich exakt `2/6`. |
| `performance.solve.3.gridwords` | GRIDWORDS | 3️⃣ | **GW: Aller guten Dinge** | Beim dritten Versuch war Schluss. | Mindestens einmal erfolgreich exakt `3/6`. |
| `performance.solve.4.quadwords` | QUADWORDS | 4️⃣ | **QW: Vier gewinnt** | QuadWords mit dem theoretischen Minimum. | Mindestens einmal erfolgreich exakt `4/9`. |
| `performance.solve.5.quadwords` | QUADWORDS | ✋ | **QW: High Five** | Alle vier Boards nach fünf Versuchen erledigt. | Mindestens einmal erfolgreich exakt `5/9`. |
| `performance.solve.6.quadwords` | QUADWORDS | 6️⃣ | **QW: Saubere Sechs** | Sechs Versuche, vier gelöste Boards. | Mindestens einmal erfolgreich exakt `6/9`. |

### 9.5 Beide Spiele gespielt – `RELIABILITY`

| Schlüssel | Scope | Emoji | Anzeigename | Beschreibung | Exakte Bedingung |
|---|---|---|---|---|---|
| `crossgame.participation.1` | CROSS_GAME | 🎮 | **GW+QW: Doppelschicht** | Beide Spiele an einem Tag erledigt. | Mindestens 1 Doppeltag. |
| `crossgame.participation.10` | CROSS_GAME | 🔁 | **GW+QW: Doppelroutine** | Zehn Tage mit dem kompletten Programm. | Mindestens 10 verschiedene Doppeltage. |
| `crossgame.participation.25` | CROSS_GAME | 🏠 | **GW+QW: Doppelstammgast** | 25 Tage bei beiden Spielen dabei. | Mindestens 25 verschiedene Doppeltage. |
| `crossgame.participation.50` | CROSS_GAME | 📦 | **GW+QW: Im Doppelpack** | Fünfzig komplette Doppeltage. | Mindestens 50 verschiedene Doppeltage. |
| `crossgame.participation.100` | CROSS_GAME | 💯 | **GW+QW: Doppelhundert** | Hundert Tage GridWords und QuadWords. | Mindestens 100 verschiedene Doppeltage. |

### 9.6 Beide Spiele erfolgreich – `PERFORMANCE`

| Schlüssel | Scope | Emoji | Anzeigename | Beschreibung | Exakte Bedingung |
|---|---|---|---|---|---|
| `crossgame.success.1` | CROSS_GAME | ✌️ | **GW+QW: Doppelsieg** | Beide Tagesrätsel erfolgreich erledigt. | Mindestens 1 erfolgreicher Doppeltag. |
| `crossgame.success.10` | CROSS_GAME | 🔥 | **GW+QW: Doppelform** | Zehn erfolgreiche Doppeltage. | Mindestens 10 verschiedene erfolgreiche Doppeltage. |
| `crossgame.success.25` | CROSS_GAME | ⚡ | **GW+QW: Doppelt stark** | 25 Tage mit zwei erfolgreichen Abschlüssen. | Mindestens 25 verschiedene erfolgreiche Doppeltage. |
| `crossgame.success.50` | CROSS_GAME | 🏅 | **GW+QW: Zweifach souverän** | Fünfzig erfolgreiche Doppeltage. | Mindestens 50 verschiedene erfolgreiche Doppeltage. |
| `crossgame.success.100` | CROSS_GAME | 👑 | **GW+QW: Doppelkrone** | Hundertmal beide Spiele am selben Tag bezwungen. | Mindestens 100 verschiedene erfolgreiche Doppeltage. |

### 9.7 Gesamterfahrung – `EXPERIENCE`

| Schlüssel | Scope | Emoji | Anzeigename | Beschreibung | Exakte Bedingung |
|---|---|---|---|---|---|
| `experience.total.100` | GLOBAL | 💯 | **Hundertsassa** | Hundert Ergebnisse quer durch beide Spiele. | Mindestens 100 gültige Ergebnisse insgesamt. |
| `experience.total.200` | GLOBAL | 🛤️ | **Langstrecke** | Zweihundert Ergebnisse später immer noch dabei. | Mindestens 200 gültige Ergebnisse insgesamt. |
| `experience.total.300` | GLOBAL | 📚 | **Lebendes Archiv** | Dreihundert Ergebnisse Bot-Geschichte. | Mindestens 300 gültige Ergebnisse insgesamt. |

### 9.8 Besondere Situationen – `SPECIAL`

| Schlüssel | Scope | Emoji | Anzeigename | Beschreibung | Exakte Bedingung |
|---|---|---|---|---|---|
| `situational.last_chance.gridwords` | GRIDWORDS | ⏳ | **GW: Auf den letzten Drücker** | Wirklich keinen Versuch verschenkt. | GridWords mindestens einmal erfolgreich exakt `6/6`. |
| `situational.last_chance.quadwords` | QUADWORDS | ⏳ | **QW: Auf den letzten Drücker** | Das letzte Board fiel genau noch rechtzeitig. | QuadWords mindestens einmal erfolgreich exakt `9/9`. |
| `situational.quadwords.consecutive_board_attempts` | QUADWORDS | 🚂 | **QW: Durchmarsch** | Vier Boards, vier aufeinanderfolgende Stationen. | Erfolgreiches QuadWords-Ergebnis mit vier vorhandenen Einzelboard-Lösungswerten; sortiert lauten sie exakt `n, n+1, n+2, n+3`. |
| `situational.quadwords.outlier_board` | QUADWORDS | 👹 | **QW: Endgegner** | Drei waren okay. Eines hatte andere Pläne. | Erfolgreiches QuadWords-Ergebnis mit vier vorhandenen Einzelboard-Lösungswerten; größter Wert minus zweitgrößter Wert ist mindestens 3. |
| `situational.crossgame.equal_final_score` | CROSS_GAME | 🎯 | **GW+QW: Punktlandung** | Zwei Spiele, dieselbe Zahl. | Am selben Spieltag GridWords und QuadWords erfolgreich; die kanonischen numerischen Endwerte `n` sind identisch. |
| `situational.crossgame.double_last_chance` | CROSS_GAME | 💓 | **GW+QW: Doppeltes Herzschlagfinale** | Zweimal fast vorbei. Zweimal doch geschafft. | Am selben Spieltag GridWords exakt `6/6` und QuadWords exakt `9/9`, beide erfolgreich. |
| `situational.deja_vu.gridwords` | GRIDWORDS | 🔁 | **GW: Déjà-vu** | Dasselbe erfolgreiche Ergebnis. Und nochmal. | Drei unmittelbar aufeinanderfolgende gültige **erfolgreiche** GridWords-Ergebnisse mit identischem kanonischen Endwert. |
| `situational.deja_vu.quadwords` | QUADWORDS | 🔁 | **QW: Déjà-vu** | Dreimal dasselbe erfolgreiche QuadWords-Endergebnis. | Drei unmittelbar aufeinanderfolgende gültige **erfolgreiche** QuadWords-Ergebnisse mit identischem kanonischen `n/9`-Endwert; Einzelboarddaten sind irrelevant. |
| `timing.before_0700` | GLOBAL | 🌅 | **Frühaufsteher** | Wörter lösen, bevor normale Menschen wach sind. | Mindestens ein gültiges Ergebnis mit ursprünglichem Share-Zeitpunkt vor `07:00` lokaler Bot-Zeit. |
| `timing.after_2300` | GLOBAL | 🦉 | **Nachteule** | Ein Rätsel geht noch. | Mindestens ein gültiges Ergebnis mit ursprünglichem Share-Zeitpunkt ab `23:00` lokaler Bot-Zeit. |
| `situational.crossgame.perfect_double` | CROSS_GAME | ✨ | **GW+QW: Perfekter Doppelschlag** | Ein nahezu absurd guter Spieltag. | Am selben Spieltag GridWords exakt `1/6` und QuadWords exakt `4/9`, beide erfolgreich. |
| `situational.failure_run.3.gridwords` | GRIDWORDS | 🫠 | **GW: Pleiten-Hattrick** | Drei GridWords-Ergebnisse hintereinander ohne Erfolg. | Drei unmittelbar aufeinanderfolgende gültige **erfolglose** GridWords-Ergebnisse. |
| `situational.failure_run.3.quadwords` | QUADWORDS | 🫠 | **QW: Pleiten-Hattrick** | Drei QuadWords-Ergebnisse hintereinander ohne Erfolg. | Drei unmittelbar aufeinanderfolgende gültige **erfolglose** QuadWords-Ergebnisse. |

Der Katalog enthält damit genau 60 Definitionen.

---

## 10. Fachliches Erreichungsdatum und Beleg

Jede aktive Vergabe besitzt ein fachliches Erreichungsdatum `earned_on` und einen nachvollziehbaren Beleg.

Das Erreichungsdatum ist grundsätzlich:

- Mengenmeilenstein: Spieltag des Ergebnisses beziehungsweise Doppeltags, mit dem die Schwelle erstmals erreicht wurde,
- Serienmeilenstein: Spieltag, an dem die betreffende Schwelle erstmals erreicht wurde,
- Einzelereignis: Spieltag des auslösenden Ergebnisses,
- Déjà-vu oder Pleiten-Hattrick: Spieltag des dritten Ergebnisses der qualifizierenden Folge,
- Zeit-Achievement: fachlicher Spieltag des qualifizierenden Shares.

Der technische Erkennungszeitpunkt `detected_at` ist davon getrennt. Ein historischer Backfill darf deshalb ein altes `earned_on` mit einem aktuellen `detected_at` erzeugen.

Belege werden transportneutral gespeichert oder referenziert. Zulässig sind insbesondere Ergebnis-ID, Spieltag, Serienlauf beziehungsweise Schwellenbezug oder ein stabiler zusammengesetzter Nachweis. Die Vergabe darf nicht von Discord-Message-Texten abhängen.

---

## 11. Dauerhafte Vergaben, Ereignisse und Korrekturen

Achievements werden nicht bei jedem `/achievements`-Aufruf vollständig neu als flüchtige Liste berechnet. Es gibt einen dauerhaften aktuellen Vergabestatus pro Teilnehmer und Achievement-Schlüssel sowie eine auditierbare Ereignishistorie.

Mindestens gilt:

- pro `(participant, achievement_key)` existiert höchstens ein aktueller Vergabestatus,
- derselbe fachliche Beleg darf bei Replay, Retry, Restart oder erneut zugestelltem Event keine zweite Vergabe erzeugen,
- eine Korrektur kann eine bisherige Vergabe invalidieren, wenn die Bedingung historisch nicht mehr belegt ist,
- invalidierte Vergaben werden nicht hart gelöscht,
- eine später erneut belegte Vergabe kann reaktiviert werden,
- eine Invalidierung erzeugt keine öffentliche Aberkennungsnachricht,
- ein bereits öffentlich angekündigtes Achievement muss nach einer späteren Reaktivierung in V1 nicht erneut öffentlich angekündigt werden.

Kanonische Ergebnisse und Teilnahmezeiträume bleiben Quelle der Wahrheit; der Vergabestatus ist eine materialisierte Projektion.

---

## 12. Reconciliation statt imperativer Unlock-Logik

Normale Live-Verarbeitung, Korrektur, Replay, Restart und historischer Backfill verwenden dieselbe fachliche Achievement-Auswertung.

Ein Auslöser bestimmt den betroffenen Teilnehmer beziehungsweise den neu zu bewertenden Bereich. Der Evaluator leitet aus der kanonischen Historie die Menge der aktuell historisch belegten Achievements und ihre Belege ab. Ein Reconciler vergleicht diese Projektion mit dem persistierten Vergabestatus:

- belegt und noch nie vorhanden → aktivieren,
- belegt und bereits aktiv → `NO_OP`,
- belegt und invalidiert → reaktivieren,
- nicht mehr belegt und bisher aktiv → invalidieren,
- nie belegt und nicht vorhanden → `NO_OP`.

Bei der aktuellen Größenordnung darf die Reconciliation bewusst den vollständigen Achievement-Zustand des betroffenen Teilnehmers auswerten. V1 benötigt keine fein granulierte Regelabhängigkeitsmatrix.

---

## 13. Öffentliche Live-Freischaltungen

Öffentliche Achievement-Freischaltungen sind in V1 standardmäßig aktiv. Ein Opt-out wird nicht eingeführt.

Mehrere gleichzeitig neu freigeschaltete Achievements werden in genau einer logischen Meldung zusammengefasst. Eine Submission darf nicht je Achievement eine eigene Nachricht erzeugen.

Eine Live-Meldung enthält mindestens:

- Anzeigename des Teilnehmers,
- Anzahl der neu freigeschalteten Achievements,
- pro Achievement Unicode- beziehungsweise aufgelöstes Custom-Emoji,
- global eindeutigen Achievement-Namen,
- deutsche Beschreibung.

Der Trigger-Channel der normalen fachlichen Verarbeitung ist der Ziel-Channel. Ein `GW+QW:`-Achievement wird dadurch typischerweise dort angekündigt, wo das zweite für die Bedingung nötige Ergebnis verarbeitet wurde.

Discord-I/O findet nicht innerhalb der fachlichen Datenbanktransaktion statt. Delivery muss Retry, Restart und unbekannte Ausgänge ohne doppelte öffentliche Meldungen behandeln.

---

## 14. Retroaktive Einführung

Bei Aktivierung von `achievements-v1` werden alle aus der vorhandenen kanonischen Historie belegbaren Achievements rückwirkend rekonstruiert. Es gilt ausdrücklich nicht die Regel „nur zukünftige Ergebnisse zählen“.

Für jeden Teilnehmer wird nach erfolgreicher Rekonstruktion **genau eine öffentliche Einführungsmeldung** als logische Discord-Nachricht erzeugt.

Diese Einführungsmeldung:

- nennt die Gesamtzahl der rückwirkend vergebenen Achievements,
- führt **jedes** rückwirkend vergebene Achievement auf,
- enthält für jedes Achievement **Name und Beschreibung**,
- verwendet eine deterministische stabile Reihenfolge,
- gruppiert die Liste **nicht** zusätzlich nach GridWords, QuadWords, `GW+QW` oder Allgemein,
- darf innerhalb derselben Discord-Nachricht mehrere Embeds beziehungsweise Darstellungsblöcke verwenden, sofern dies für Discord-Grenzen erforderlich ist,
- darf nicht in mehrere öffentliche Discord-Nachrichten pro Teilnehmer zerfallen.

Der Bootstrap beziehungsweise historische Rebuild muss idempotent und restartfähig sein. Ein stabiler semantischer Idempotenzschlüssel verhindert eine zweite Einführungsmeldung desselben Teilnehmers für dieselbe Definitionsversion.

Öffentliche normale Live-Freischaltungen dürfen erst beginnen, wenn der historische Bootstrap der aktiven Definitionsversion erfolgreich abgeschlossen ist. Dadurch werden historische und neue Vergaben nicht vermischt.

---

## 15. `/achievements`

V1 führt einen lesenden Slash-Command ein:

```text
/achievements
/achievements user:@Georgia
/achievements game:GridWords
/achievements user:@Georgia game:QuadWords
```

Regeln:

- `user` ist optional und standardmäßig der Aufrufer,
- Profile anderer Teilnehmer dürfen öffentlich abgefragt werden; dafür ist in V1 keine Administratorrolle erforderlich,
- `game` ist optional und standardmäßig `Alle`,
- `game:GridWords` zeigt ausschließlich Achievements mit Scope `GRIDWORDS`,
- `game:QuadWords` zeigt ausschließlich Achievements mit Scope `QUADWORDS`,
- `CROSS_GAME`- und `GLOBAL`-Achievements erscheinen nur bei `Alle`,
- angezeigt werden nur aktuell aktive/freigeschaltete Achievements,
- invalidierte oder gesperrte Achievements werden nicht als Aufgabenliste dargestellt,
- V1 zeigt keinen Fortschritt wie `43/50`,
- V1 führt keine Rangliste nach Achievement-Anzahl ein.

Die Ausgabe darf zur Lesbarkeit nach `EXPERIENCE`, `RELIABILITY`, `PERFORMANCE` und `SPECIAL` gruppieren und bei Bedarf paginieren. Die global eindeutigen Anzeigenamen bleiben auch innerhalb dieser Darstellung vollständig erhalten.

Der Command liest die persistierte aktuelle Vergabeprojektion. Er parst keine Discord-Nachrichten und löst keinen vollständigen historischen Rebuild pro Aufruf aus.

---

## 16. Custom Emojis und spätere Badges

V1 liefert jedes Achievement mit einem Unicode-Fallback-Emoji aus. Der Renderer darf später optional ein servereigenes Discord-Emoji oder ein anderes Badge anhand des stabilen Achievement-Schlüssels auflösen.

Eine solche Darstellungsänderung:

- ändert den technischen Achievement-Schlüssel nicht,
- migriert keine Vergaben,
- verändert keine fachliche Bedingung,
- darf auf den Unicode-Fallback zurückfallen, wenn das Custom Emoji fehlt oder nicht verfügbar ist.

Die Erzeugung eigener Badge-Bilder ist nicht Bestandteil der technischen V1-Umsetzung.

---

## 17. Nicht Bestandteil von V1

Nicht Bestandteil sind insbesondere:

- Opt-out oder individuelle Sichtbarkeitsregeln für öffentliche Achievement-Meldungen,
- frei konfigurierbare Regeln,
- Admin-Editor oder Achievement-DSL,
- Discord-Rollen pro Achievement,
- Ranglisten nach Anzahl der Achievements,
- wiederholbare Achievements,
- öffentliche Aberkennungsnachrichten,
- Fortschrittsanzeigen für gesperrte Achievements,
- versteckte Achievements mit bewusst unklaren Bedingungen,
- generisches Gamification-, Kommentar-, Event- oder Plugin-Framework,
- neue Performance-Achievements für persönliche Rekorde, Durchschnittsverbesserungen oder Schwierigkeitsgrade, solange sie nicht separat spezifiziert sind,
- automatische Generierung eigener Emoji-/Badge-Bilder.

---

## 18. Verbindliche Akzeptanzfälle

Die paketweisen Tests dürfen anders zugeschnitten sein, müssen zusammen aber mindestens folgende Fachfälle abdecken:

1. Katalog enthält exakt 60 Definitionen und keine doppelten Schlüssel.
2. Katalog enthält keine doppelten Anzeigenamen.
3. Alle spielgebundenen Namen tragen den korrekten `GW:`-/`QW:`-/`GW+QW:`-Präfix.
4. Eine erste gültige GW-Submission kann gleichzeitig `GW: Dabei!`, `GW: Geschafft!` und ein Ergebnis-Achievement freischalten.
5. `1/6` schaltet nicht `GW: Zweiter sitzt` oder `GW: Aller guten Dinge` frei.
6. `4/9`, `5/9` und `6/9` verwenden den kanonischen QuadWords-Endwert.
7. Eine Korrektur zählt nicht als zweite Teilnahme.
8. Teilnahme-Meilensteine werden exakt bei 1/10/25/50/100 je Spiel erreicht.
9. Teilnahmeserien werden exakt bei 10/25/50/100 je Spiel erreicht.
10. Erfolgsserien werden exakt bei 1/10/25/50/100 je Spiel erreicht.
11. Ein aktiver fehlender Tag bricht die passende Teilnahmeserie.
12. Ein erfolgloses Ergebnis bricht die passende Erfolgsserie.
13. Deaktivierung eines Spiels beendet die laufende Serie; Reaktivierung setzt sie nicht fort.
14. Historische Teilnahme wird nicht aus dem heutigen Aktivierungszustand abgeleitet.
15. Doppeltage zählen kumulativ und müssen nicht aufeinanderfolgen.
16. Erfolgreiche Doppeltage verlangen zwei erfolgreiche Ergebnisse am selben `game_date`.
17. Gesamterfahrung zählt GW und QW getrennt, auch am selben Tag.
18. `GW: Auf den letzten Drücker` verlangt exakt `6/6` erfolgreich.
19. `QW: Auf den letzten Drücker` verlangt exakt `9/9` erfolgreich.
20. `QW: Durchmarsch` erkennt sortiert `n,n+1,n+2,n+3` aus vier vorhandenen Board-Lösungswerten.
21. `QW: Durchmarsch` wird ohne Boarddetails nicht vergeben.
22. `QW: Endgegner` erkennt mindestens drei Versuche Abstand zwischen schlechtestem und zweitschlechtestem Board.
23. `QW: Endgegner` wird ohne Boarddetails nicht vergeben.
24. `GW+QW: Punktlandung` vergleicht ausschließlich die kanonischen numerischen Endwerte.
25. `GW+QW: Doppeltes Herzschlagfinale` verlangt `6/6` und `9/9` am selben Tag.
26. `GW+QW: Perfekter Doppelschlag` verlangt `1/6` und `4/9` am selben Tag.
27. `GW: Déjà-vu` verlangt drei aufeinanderfolgende erfolgreiche gleiche GW-Endwerte.
28. `QW: Déjà-vu` verlangt drei aufeinanderfolgende erfolgreiche gleiche `n/9`-Endwerte und ignoriert Boarddetails.
29. Kalendertage ohne Submission unterbrechen Déjà-vu nicht.
30. Ein anderes gültiges Ergebnis desselben Spiels unterbricht Déjà-vu.
31. Drei Fehlschläge erfüllen Déjà-vu nicht.
32. Drei aufeinanderfolgende gültige Fehlschläge erfüllen `GW: Pleiten-Hattrick` beziehungsweise `QW: Pleiten-Hattrick`.
33. Kalendertage ohne Submission unterbrechen den Pleiten-Hattrick nicht.
34. Frühaufsteher verwendet `< 07:00` in `Europe/Berlin`.
35. Exakt `07:00:00` erfüllt Frühaufsteher nicht.
36. Nachteule verwendet `>= 23:00` in `Europe/Berlin`.
37. Sommer-/Winterzeit wird über `ZoneId` korrekt behandelt.
38. Replay oder Retry derselben fachlichen Änderung erzeugt keine zweite Vergabe.
39. Restart nach teilweise erfolgter Verarbeitung erzeugt keine doppelte Vergabe oder Meldung.
40. Zwei konkurrierende Verarbeitungen desselben neuen Achievement-Schlüssels erzeugen höchstens einen aktiven Vergabestatus.
41. Korrektur kann eine fachlich nicht mehr belegte Vergabe invalidieren.
42. Invalidierung löscht die Historie nicht und erzeugt keine öffentliche Aberkennungsnachricht.
43. Spätere erneute Erfüllung kann eine invalidierte Vergabe reaktivieren.
44. Mehrere neue Achievements desselben Triggers werden in einer Live-Meldung aggregiert.
45. Live-Meldung enthält pro Achievement Name und Beschreibung.
46. Öffentliche Live-Meldungen bleiben bis zum erfolgreichen Bootstrap von `achievements-v1` gesperrt.
47. Historischer Bootstrap verwendet dieselben Fachregeln wie Live-Reconciliation.
48. Historischer Bootstrap bestimmt das tatsächliche `earned_on`, soweit aus kanonischen Daten ableitbar.
49. Historischer Bootstrap erzeugt pro Teilnehmer genau eine Einführungsmeldung.
50. Einführungsmeldung enthält jedes rückwirkend vergebene Achievement mit Name und Beschreibung.
51. Einführungsmeldung enthält keine zusätzliche Gruppierung nach GW/QW/GW+QW/Allgemein.
52. Wiederholung oder Restart des Bootstraps erzeugt keine zweite Einführungsmeldung.
53. `/achievements` ohne `user` zeigt den Aufrufer.
54. `/achievements user:...` darf ein anderes Teilnehmerprofil anzeigen.
55. `game:GridWords` zeigt nur Scope `GRIDWORDS`.
56. `game:QuadWords` zeigt nur Scope `QUADWORDS`.
57. `Alle` kann alle vier Scopes enthalten.
58. Invalidierte und gesperrte Achievements erscheinen nicht in der normalen Profilansicht.
59. `/achievements` löst keinen vollständigen historischen Scan oder Rebuild aus.
60. Fehlendes Custom Emoji fällt ohne fachliche Änderung auf das Unicode-Emoji zurück.

Diese 60 Akzeptanzfälle sind die Mindestmatrix für die Abnahme von Inkrement 13.