# Redaktioneller Ausredenkatalog – Arbeitsstand

Dieses Dokument sichert den redaktionellen Arbeitsstand für Inkrement 11, Paket 8A. Es ist noch nicht der produktive JSON-Katalog. Texte, IDs und Metadaten können bis zur Überführung in `src/main/resources/excuses/catalog.json` redaktionell überarbeitet werden.

## Verbindliche Terminologie

- Im sichtbaren Sprachgebrauch des Bots wird vom **Grid** gesprochen, nicht vom Raster.
- Technische Schlüssel wie `GRID_CONFLICT` bleiben unverändert.
- Bewusst knappe norddeutsche und bewusst überdramatische Texte sind stilprägend und sollen nicht allein wegen ihrer Kürze oder Überhöhung entfernt werden.

## Zielumfang

| Bereich | Ziel |
|---|---:|
| Allgemeine Ausreden | 144 |
| Anlassbezogene Ausreden | mindestens 336; Ziel mit Stilboni etwa 420 |
| Gesamtkatalog | mindestens 480; aktuelles Planungsziel etwa 564 |

Für jeden der sieben spezifischen Anlässe sollen alle acht Stile mit mindestens sechs Texten vertreten sein. Besonders geeignete Stile dürfen zusätzliche Texte erhalten.

## Standardmetadaten – allgemeine Texte

```text
games: [GRIDWORDS, QUADWORDS]
specificity: 0
weight: 100
requiresAll: []
excludesAny: []
selectable: true
```

Die endgültige Zuordnung zu einem passenden `ExcuseTopic` erfolgt bei der Katalogisierung anhand der tatsächlichen Hauptprämisse.

# Allgemeine Texte

## Technisch

- `general.technical.01` – Die Buchstabenpipeline befand sich in einem nicht reproduzierbaren Fehlerzustand.
- `general.technical.02` – Das Ergebnis ist unter Produktionsbedingungen entstanden und daher nur eingeschränkt aussagekräftig.
- `general.technical.03` – Die Eingabelogik war korrekt. Die Ausgabe hatte andere Pläne.
- `general.technical.04` – Zwischen Erkenntnis und Klick ist offenbar ein Paket verloren gegangen.
- `general.technical.05` – Die heutige Grid-Instanz lief außerhalb ihrer dokumentierten Spezifikation.
- `general.technical.06` – Ein Cache hat veraltete Zuversicht ausgeliefert.
- `general.technical.07` – Die Telemetrie zeigt Aktivität, aber leider keinen belastbaren Zusammenhang.
- `general.technical.08` – Das System meldete Erfolgsaussichten, bevor sämtliche Abhängigkeiten geladen waren.
- `general.technical.09` – Die Buchstabenerkennung arbeitete heute im experimentellen Modus.
- `general.technical.10` – Der Fehler ließ sich zuverlässig reproduzieren, solange ich beteiligt war.
- `general.technical.11` – Das war kein schlechtes Ergebnis, sondern ein unfreiwilliger Lasttest.
- `general.technical.12` – Der Lösungsalgorithmus ist stabil. Nur die Realität verhielt sich inkonsistent.
- `general.technical.13` – Die Qualitätsprüfung wurde aus Performancegründen übersprungen.
- `general.technical.14` – Im kritischen Moment trat eine unerwartete Denkzeitüberschreitung auf.
- `general.technical.15` – Die interne Heuristik lief, aber ihr Endpunkt war nicht erreichbar.
- `general.technical.16` – Das Ergebnis basiert auf einer fehlerhaften Synchronisation zwischen Auge, Gehirn und Finger.
- `general.technical.17` – Ein Rollback auf den Zustand vor Spielbeginn wird geprüft.
- `general.technical.18` – Unter regulären Laborbedingungen wäre das so nicht passiert.

## Taktisch

- `general.tactical.01` – Das Ergebnis ist Teil eines langfristigen Plans, dessen Einzelheiten aktuell nicht veröffentlicht werden können.
- `general.tactical.02` – Ich habe dem Grid bewusst falsche Sicherheit vermittelt.
- `general.tactical.03` – Heute ging es primär um Informationsgewinn.
- `general.tactical.04` – Die entscheidenden Ressourcen wurden für kommende Spieltage geschont.
- `general.tactical.05` – Das war ein kontrollierter Rückzug in statistisch günstigeres Gelände.
- `general.tactical.06` – Der Matchplan sah eine frühzeitige Offenlegung meiner tatsächlichen Stärke nicht vor.
- `general.tactical.07` – Ich habe bewusst eine Linie gewählt, mit der niemand gerechnet hat. Einschließlich mir.
- `general.tactical.08` – Das Ergebnis dient der strategischen Senkung gegnerischer Erwartungen.
- `general.tactical.09` – Heute wurde bewusst in spätere Überperformance investiert.
- `general.tactical.10` – Die heutige Leistung war ein Täuschungsmanöver mit bedauerlich realistischer Ausführung.
- `general.tactical.11` – Ich habe den direkten Weg aus taktischen Gründen gemieden.
- `general.tactical.12` – Das Grid wurde nicht bekämpft, sondern langfristig gebunden.
- `general.tactical.13` – Der Plan sieht vor, dass seine Qualität erst rückblickend erkennbar wird.
- `general.tactical.14` – Ich habe alle Optionen offengehalten, bis keine mehr übrig war.
- `general.tactical.15` – Die heutige Priorität lag auf der Schonung zukünftiger Geistesblitze.
- `general.tactical.16` – Die heutige Abweichung vergrößert meinen taktischen Handlungsspielraum.
- `general.tactical.17` – Ich bin nicht gescheitert. Ich habe das Spielfeld vermessen.
- `general.tactical.18` – Die Strategie war korrekt. Der Spieltag kam nur zu früh.

## Bürokratisch

- `general.bureaucratic.01` – Ich erkenne das Ergebnis formal an, widerspreche jedoch seiner inhaltlichen Bewertung.
- `general.bureaucratic.02` – Der Vorgang befindet sich noch in der internen Prüfung.
- `general.bureaucratic.03` – Für eine abschließende Einschätzung fehlen derzeit zuständige Stellen.
- `general.bureaucratic.04` – Das Ergebnis wurde ordnungsgemäß zur Kenntnis genommen und wird nicht weiter kommentiert.
- `general.bureaucratic.05` – Die Verantwortung konnte organisatorisch noch keiner Abteilung zugeordnet werden.
- `general.bureaucratic.06` – Der Spielverlauf entsprach nicht dem genehmigten Ablaufplan.
- `general.bureaucratic.07` – Eine belastbare Bewertung ist erst nach Abschluss des Buchstabenfeststellungsverfahrens möglich.
- `general.bureaucratic.08` – Die Aktenlage ist eindeutig unübersichtlich.
- `general.bureaucratic.09` – Das Ergebnis gilt bis auf Weiteres als vorläufig endgültig.
- `general.bureaucratic.10` – Die zuständige Kommission tagt, sobald ein Termin zur Terminfindung gefunden wurde.
- `general.bureaucratic.11` – Mein Lösungsantrag wurde offenbar wegen Formmängeln zurückgestellt.
- `general.bureaucratic.12` – Die Abweichung vom Soll wurde ordnungsgemäß dokumentiert und damit praktisch behoben.
- `general.bureaucratic.13` – Eine Stellungnahme ist eingegangen, konnte aber noch keinem Vorgang zugeordnet werden.
- `general.bureaucratic.14` – Das Grid hat seine Mitwirkungspflichten nur eingeschränkt erfüllt.
- `general.bureaucratic.15` – Die heutige Leistung fällt in einen bislang ungeregelten Zuständigkeitsbereich.
- `general.bureaucratic.16` – Der Vorgang wurde ohne Anerkennung einer tatsächlichen Erkenntnis abgeschlossen.
- `general.bureaucratic.17` – Eine Neubewertung wird nach Vorlage aller nicht vorhandenen Unterlagen erwogen.
- `general.bureaucratic.18` – Ich habe das Ergebnis verwaltungstechnisch bereits deutlich verbessert.

## Dramatisch

- `general.dramatic.01` – Das Grid und ich trafen uns auf Augenhöhe. Dann sah es weg.
- `general.dramatic.02` – Die Buchstaben kamen. Die Gewissheit ging.
- `general.dramatic.03` – Ich stellte mich dem Schicksal, und das Schicksal kannte das Lösungswort.
- `general.dramatic.04` – Heute schrieb das Grid eine Tragödie mit sehr begrenztem Budget.
- `general.dramatic.05` – Zwischen mir und einem würdigen Ergebnis lag nur alles.
- `general.dramatic.06` – Ich kämpfte bis zum letzten Funken Zuversicht. Er war überraschend früh verbraucht.
- `general.dramatic.07` – Die Buchstaben standen schweigend da und sahen meinem Niedergang zu.
- `general.dramatic.08` – Es war kein Spiel. Es war eine Begegnung mit der eigenen Endlichkeit.
- `general.dramatic.09` – Das Ergebnis trägt die Handschrift eines Verrats.
- `general.dramatic.10` – Ich griff nach der Lösung, doch sie zog ihre Hand zurück.
- `general.dramatic.11` – Die Hoffnung war echt. Das macht es schlimmer.
- `general.dramatic.12` – Heute wurde aus ein paar Buchstaben ein persönliches Epos.
- `general.dramatic.13` – Ich betrat das Grid als Mensch und verließ es als Mahnung.
- `general.dramatic.14` – Manche Ergebnisse werden erzielt. Dieses wurde erlitten.
- `general.dramatic.15` – Das Schicksal hatte entschieden, bevor ich den ersten Buchstaben sah.
- `general.dramatic.16` – Ein Teil von mir blieb irgendwo zwischen Vermutung und Verzweiflung zurück.
- `general.dramatic.17` – Ich war bereit für Größe. Der Spieltag war bereit für etwas anderes.
- `general.dramatic.18` – Der Vorhang fällt. Die Fragen bleiben.

## Kosmisch

- `general.cosmic.01` – Die Vokale standen heute unter ungünstigem planetarem Einfluss.
- `general.cosmic.02` – Meine Lösung befand sich offenbar in einer benachbarten Dimension.
- `general.cosmic.03` – Die Raumzeit krümmte sich direkt zwischen Erkenntnis und Eingabe.
- `general.cosmic.04` – Das Ergebnis ist mit normaler Materie nicht vollständig erklärbar.
- `general.cosmic.05` – Eine seltene Buchstabenkonstellation hat sämtliche Vorhersagemodelle gestört.
- `general.cosmic.06` – Die Entropie war heute schneller.
- `general.cosmic.07` – Im relevanten Paralleluniversum war das eine beeindruckende Leistung.
- `general.cosmic.08` – Das Grid lag zu nah an einer kognitiven Singularität.
- `general.cosmic.09` – Kosmische Hintergrundstrahlung hat mehrere gute Ideen unbrauchbar gemacht.
- `general.cosmic.10` – Merkur war rückläufig. Mein Wortschatz leider auch.
- `general.cosmic.11` – Die Lösung umlief mich auf einer stabilen, aber unsichtbaren Umlaufbahn.
- `general.cosmic.12` – Ein lokaler Riss im Kontinuum hat den Spielverlauf verzerrt.
- `general.cosmic.13` – Die Sterne waren sich einig. Nur nicht mit mir.
- `general.cosmic.14` – Das Universum expandiert. Meine Trefferquote tat es nicht.
- `general.cosmic.15` – Dunkle Materie hat vermutlich die entscheidenden Buchstaben verdeckt.
- `general.cosmic.16` – Die Gravitation zog jede brauchbare Vermutung nach unten.
- `general.cosmic.17` – Das Ergebnis ist nur aus einer höherdimensionalen Perspektive sinnvoll.
- `general.cosmic.18` – Heute war die kosmische Ordnung gegen konstruktive Zusammenarbeit.

## Norddeutsch

- `general.northern-german.01` – War heute nicht so.
- `general.northern-german.02` – Kann passieren. Ist passiert.
- `general.northern-german.03` – Das Ergebnis steht da nun.
- `general.northern-german.04` – Hätte besser laufen können. Lief es nicht.
- `general.northern-german.05` – War Wind.
- `general.northern-german.06` – Das Grid hatte recht. Muss man nicht mögen.
- `general.northern-german.07` – Viel war da nicht zu holen.
- `general.northern-german.08` – Mehr wird dazu nicht gesagt.
- `general.northern-german.09` – Morgen gibt’s auch wieder ein Grid.
- `general.northern-german.10` – War überschaubar.
- `general.northern-german.11` – Ich war dabei. Reicht fürs Erste.
- `general.northern-german.12` – Hat nicht gepasst.
- `general.northern-german.13` – Joa. Ergebnis eben.
- `general.northern-german.14` – Die Buchstaben waren da. Ich auch.
- `general.northern-german.15` – Ging so. Eher weniger.
- `general.northern-german.16` – Ist jetzt erledigt.
- `general.northern-german.17` – Schön war’s nicht. Fertig ist es trotzdem.
- `general.northern-german.18` – Müsste man nicht wiederholen.

## Sportlich

- `general.sporting.01` – Wir müssen das analysieren, die richtigen Schlüsse ziehen und beim nächsten Grid eine Reaktion zeigen.
- `general.sporting.02` – Der Matchplan war gut. Die Umsetzung hatte leichte Kontaktprobleme.
- `general.sporting.03` – Heute fehlten in den entscheidenden Momenten die letzten Prozente.
- `general.sporting.04` – Das Ergebnis spiegelt den Spielverlauf nur teilweise und leider vollständig wider.
- `general.sporting.05` – Wir nehmen den Punkt mit, auch wenn es keinen gab.
- `general.sporting.06` – Die Trainingswoche war besser als das, was heute auf dem Platz zu sehen war.
- `general.sporting.07` – Entscheidend ist jetzt, den Kopf oben und die Buchstaben sortiert zu halten.
- `general.sporting.08` – Wir waren bemüht, Zugriff auf das Spiel zu bekommen.
- `general.sporting.09` – Das war ein gebrauchter Spieltag, aber die Saison ist lang.
- `general.sporting.10` – Die Leistung war phasenweise vorhanden.
- `general.sporting.11` – Wir haben uns für den Aufwand nicht belohnt.
- `general.sporting.12` – Im letzten Drittel fehlte die nötige Präzision.
- `general.sporting.13` – Die Mannschaft aus Auge, Gehirn und Finger war heute nicht optimal abgestimmt.
- `general.sporting.14` – Wir dürfen das Ergebnis nicht größer machen, als es bereits ist.
- `general.sporting.15` – Der Fokus liegt ab sofort auf dem nächsten Grid.
- `general.sporting.16` – Heute waren wir in den Umschaltmomenten gedanklich einen Buchstaben zu spät.
- `general.sporting.17` – Das war ein Auftritt, aus dem wir sehr konkrete und möglichst geheime Lehren ziehen.
- `general.sporting.18` – Wir wollten dominant auftreten. Das Grid wollte auch.

## Juristisch

- `general.legal.01` – Das Ergebnis ist bis zur abschließenden Prüfung ausdrücklich nicht als Schuldeingeständnis zu verstehen.
- `general.legal.02` – Ich erkenne den Spielstand an, bestreite jedoch jede persönliche Verantwortlichkeit.
- `general.legal.03` – Gegen die inhaltliche Wertung werden sämtliche verfügbaren Rechtsmittel geprüft.
- `general.legal.04` – Die Beweislage ist eindeutig, aber aus meiner Sicht nicht überzeugend.
- `general.legal.05` – Eine Haftung für spontane Fehlannahmen wird vorsorglich ausgeschlossen.
- `general.legal.06` – Das Grid hat den Sachverhalt einseitig dargestellt.
- `general.legal.07` – Das Ergebnis entfaltet keine Bindungswirkung für zukünftige Spieltage.
- `general.legal.08` – Mein Schweigen ist weder Zustimmung noch ein Hinweis darauf, dass mir nichts Besseres einfällt.
- `general.legal.09` – Die Kausalität zwischen meinen Eingaben und diesem Ausgang wird bestritten.
- `general.legal.10` – Der Grundsatz „im Zweifel für den Spieler“ wurde erkennbar nicht angewendet.
- `general.legal.11` – Sämtliche Vermutungen erfolgten nach bestem Wissen und ohne Gewähr.
- `general.legal.12` – Die Zuständigkeit meines Gehirns für diesen Vorgang ist ungeklärt.
- `general.legal.13` – Das Verfahren litt unter erheblichen Buchstaben- und Verfahrensfehlern.
- `general.legal.14` – Eine objektive Bewertung ist wegen offenkundiger Befangenheit des Grids ausgeschlossen.
- `general.legal.15` – Das Ergebnis wird unter Vorbehalt und ohne Präjudiz akzeptiert.
- `general.legal.16` – Die Darlegungs- und Beweislast liegt weiterhin beim Lösungswort.
- `general.legal.17` – Eine nachteilige Auslegung meiner heutigen Leistung ist unzulässig.
- `general.legal.18` – Rechtliche Schritte gegen die Realität werden vorbereitet.

# Anlass: nicht gelöst

## Standardmetadaten

```text
games: [GRIDWORDS, QUADWORDS]
topic: NOT_SOLVED
specificity: 20
weight: 100
requiresAll: [NOT_SOLVED]
excludesAny: []
selectable: true
```

## Technisch

- `not-solved.technical.01` – Der Lösungsprozess wurde nach Ausschöpfung sämtlicher Versuche ohne erfolgreichen Commit beendet.
- `not-solved.technical.02` – Das Lösungswort war im aktuellen Build nicht reproduzierbar.
- `not-solved.technical.03` – Der Resolver lieferte bis zum Prozessende keinen gültigen Rückgabewert.
- `not-solved.technical.04` – Alle Eingaben wurden verarbeitet. Ein Ergebnisobjekt konnte trotzdem nicht erzeugt werden.
- `not-solved.technical.05` – Die Buchstabenpipeline endete ordnungsgemäß in einem fachlich ungelösten Zustand.
- `not-solved.technical.06` – Das System verfügte über Hinweise, aber über keine lauffähige Schlussfolgerung.
- `not-solved.technical.07` – Der letzte Versuch lief in einen fachlich korrekten, praktisch nutzlosen Timeout.
- `not-solved.technical.08` – Für das Lösungswort fehlte offenbar eine zur Laufzeit nicht dokumentierte Abhängigkeit.

## Taktisch

- `not-solved.tactical.01` – Ich habe die Lösung bewusst nicht offengelegt, um das Grid in Sicherheit zu wiegen.
- `not-solved.tactical.02` – Das Lösungswort wurde für einen strategisch günstigeren Spieltag zurückgehalten.
- `not-solved.tactical.03` – Heute ging es darum, dem Grid keine verwertbaren Daten über meine tatsächliche Stärke zu liefern.
- `not-solved.tactical.04` – Der vollständige Verzicht auf eine Lösung war Teil eines radikalen Erwartungsmanagements.
- `not-solved.tactical.05` – Ich habe sämtliche Versuche geopfert, um die langfristige Gesamtlage zu verbessern.
- `not-solved.tactical.06` – Die heutige Niederlage macht den nächsten Spieltag unberechenbarer.
- `not-solved.tactical.07` – Das war kein Scheitern, sondern eine konsequente Verweigerung des offensichtlichen Weges.
- `not-solved.tactical.08` – Die Lösung blieb im Reservelager. Man weiß nie, wann man sie noch braucht.

## Bürokratisch

- `not-solved.bureaucratic.01` – Der Lösungsantrag konnte innerhalb der vorgesehenen Frist nicht abschließend bearbeitet werden.
- `not-solved.bureaucratic.02` – Das Verfahren wurde mangels feststellbarer Lösung ohne Sachentscheidung beendet.
- `not-solved.bureaucratic.03` – Eine Lösung lag möglicherweise vor, war jedoch nicht formgerecht eingereicht.
- `not-solved.bureaucratic.04` – Die zuständige Stelle konnte das Lösungswort bis zum Ablauf der Bearbeitungsfrist nicht bestätigen.
- `not-solved.bureaucratic.05` – Der Vorgang wurde nach vollständiger Ausschöpfung aller Prüfschritte ergebnislos geschlossen.
- `not-solved.bureaucratic.06` – Das Grid hat die zur Lösungsfindung erforderlichen Unterlagen nicht vollständig bereitgestellt.
- `not-solved.bureaucratic.07` – Eine abschließende Lösung scheiterte an ungeklärten Zuständigkeiten zwischen Auge, Gehirn und Finger.
- `not-solved.bureaucratic.08` – Der Bescheid lautet auf nicht gelöst; ein Widerspruchsformular wurde nicht beigefügt.

## Dramatisch

- `not-solved.dramatic.01` – Die Lösung war nah genug, um Hoffnung zu machen, und fern genug, um mich zu brechen.
- `not-solved.dramatic.02` – Am Ende blieb nur Schweigen dort, wo Gewissheit hätte sein sollen.
- `not-solved.dramatic.03` – Ich gab dem Grid jeden Versuch. Es gab mir keine Lösung zurück.
- `not-solved.dramatic.04` – Das Lösungswort sah meinen Kampf und entschied sich trotzdem gegen mich.
- `not-solved.dramatic.05` – Mit dem letzten Versuch starb auch die letzte vernünftige Vermutung.
- `not-solved.dramatic.06` – Heute endete die Geschichte nicht mit einer Lösung, sondern mit einem offenen Grab aus Buchstaben.
- `not-solved.dramatic.07` – Ich stand vor dem Ziel, bis das Ziel aufhörte, auf mich zu warten.
- `not-solved.dramatic.08` – Das Grid schloss sich. Die Wunde bleibt offen.

## Kosmisch

- `not-solved.cosmic.01` – Das Lösungswort befand sich außerhalb meines beobachtbaren Universums.
- `not-solved.cosmic.02` – Alle Versuche wurden von einem Ereignishorizont verschluckt.
- `not-solved.cosmic.03` – Die richtige Lösung existierte vermutlich, aber nicht in dieser Raumzeit.
- `not-solved.cosmic.04` – Eine kosmische Anomalie verhinderte den Kollaps der Möglichkeiten auf ein einziges Wort.
- `not-solved.cosmic.05` – Das Grid expandierte schneller, als meine Erkenntnis es durchqueren konnte.
- `not-solved.cosmic.06` – Die Lösung blieb in einer stabilen Umlaufbahn knapp außerhalb meiner Reichweite.
- `not-solved.cosmic.07` – Dunkle Energie hat das Lösungswort bis zum Ende von mir weg beschleunigt.
- `not-solved.cosmic.08` – In mindestens einem Paralleluniversum wurde das heute gelöst. Dieses gehörte nicht dazu.

## Norddeutsch

- `not-solved.northern-german.01` – Nicht gelöst. War wohl so.
- `not-solved.northern-german.02` – Kam keine Lösung bei rum.
- `not-solved.northern-german.03` – Alle Versuche weg. Wort noch da.
- `not-solved.northern-german.04` – Hat bis zum Ende nicht gepasst.
- `not-solved.northern-german.05` – Lösung blieb aus. Feierabend.
- `not-solved.northern-german.06` – Mehr Versuche gab’s nicht. Mehr Erkenntnis auch nicht.
- `not-solved.northern-german.07` – Das Grid blieb zu. Dann eben nicht.
- `not-solved.northern-german.08` – War kein Lösungswort für heute.

## Sportlich

- `not-solved.sporting.01` – Wir haben bis zum Schluss alles versucht und uns trotzdem nicht mit einer Lösung belohnt.
- `not-solved.sporting.02` – In den entscheidenden Versuchen fehlte die letzte Konsequenz.
- `not-solved.sporting.03` – Die Mannschaft hat gearbeitet, aber das Lösungswort nicht über die Linie gebracht.
- `not-solved.sporting.04` – Wir hatten Möglichkeiten, konnten sie jedoch nicht in eine Lösung umwandeln.
- `not-solved.sporting.05` – Am Ende steht ein bitteres X, das wir gemeinsam aufarbeiten müssen.
- `not-solved.sporting.06` – Der Matchplan trug uns durch alle Versuche, nur nicht bis zur Lösung.
- `not-solved.sporting.07` – Wir waren über die gesamte Spielzeit bemüht, fanden aber keinen Zugriff auf das Lösungswort.
- `not-solved.sporting.08` – Das Ergebnis tut weh, aber entscheidend ist jetzt die Reaktion im nächsten Grid.

## Juristisch

- `not-solved.legal.01` – Das Fehlen einer Lösung stellt ausdrücklich kein Anerkenntnis mangelnder Kenntnis dar.
- `not-solved.legal.02` – Die Nichtermittlung des Lösungsworts beruht auf einer unzureichenden Beweislage.
- `not-solved.legal.03` – Gegen die Feststellung „nicht gelöst“ wird fristgerecht Widerspruch geprüft.
- `not-solved.legal.04` – Das Lösungswort hat seiner Mitwirkungspflicht bis zum Verfahrensende nicht genügt.
- `not-solved.legal.05` – Mangels zweifelsfreier Identifizierung war von einer Lösung abzusehen.
- `not-solved.legal.06` – Die vollständige Ausschöpfung der Versuche begründet keine persönliche Haftung.
- `not-solved.legal.07` – Das Verfahren endete ohne Lösung, nicht jedoch mit einem Schuldeingeständnis.
- `not-solved.legal.08` – Die Beweislast für seine Auffindbarkeit verbleibt beim Lösungswort.

# Noch zu erarbeiten

- `VERY_LATE_SUBMISSION`
- `GRIDWORDS_LAST_ATTEMPT`
- `GRIDWORDS_VERY_SLOW`
- `QUADWORDS_VERY_SLOW`
- `QUADWORDS_SINGLE_BOARD_COLLAPSE`
- `CLEAR_CURRENT_DAILY_OUTLIER`

Aktueller gesicherter Stand: **208 Texte** – 144 allgemeine und 64 für `NOT_SOLVED`.
