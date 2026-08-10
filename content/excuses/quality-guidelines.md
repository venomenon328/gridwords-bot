# Redaktionelle Qualitätsregeln für Ausreden

Dieses Dokument konkretisiert den Qualitätsmaßstab für die redaktionellen Ausredenquellen. Es ergänzt die einzelnen Entwürfe und ist bei jeder Überführung in den produktiven Katalog verbindlich.

## 1. Kerneigenschaft einer Ausrede

Ein Text soll nicht bloß den bereits bekannten Anlass beschreiben. Er muss nach Möglichkeit mindestens eine der folgenden Funktionen erfüllen:

- eine Ursache außerhalb der eigenen unmittelbaren Verantwortung benennen,
- Verantwortung auf Grid, Buchstaben, Uhr, System, Verfahren, Kosmos oder ein einzelnes Board verschieben,
- das Ergebnis als Absicht, Strategie, Sorgfalt, Schonung oder langfristigen Plan umdeuten,
- die Aussagekraft, Gültigkeit oder Endgültigkeit des Ergebnisses bestreiten,
- das Ergebnis relativieren oder minimieren,
- eine absurde, aber erkennbare Begründung liefern,
- durch extreme Kürze, Überhöhung oder trockene Resignation selbst die Pointe bilden.

Eine reine Wiedergabe wie „Das Ergebnis war langsam“, „Ich liege bisher hinten“ oder „Das sechste Ergebnis war richtig“ reicht grundsätzlich nicht.

## 2. Zulässige Ausnahmen

Eine reine oder fast reine Tatsachenfeststellung darf bestehen bleiben, wenn der Witz gerade aus Form, Stil oder Fallhöhe entsteht. Das betrifft insbesondere:

- bewusst knappe norddeutsche Texte,
- bewusst überdramatische Texte,
- prägnante technische oder bürokratische Formulierungen, deren unangemessene Professionalität selbst die Ausrede ist,
- sehr starke Einzeiler, die ohne zusätzliche Erklärung besser funktionieren.

Kürze oder Überhöhung sind kein Qualitätsmangel. Ein Text wird nicht allein deshalb erweitert, weil er nur wenige Wörter besitzt.

## 3. Anlassbezug

Ein anlassbezogener Text muss den Anlass erkennbar nutzen, darf ihn aber nicht lediglich wiederholen. Die redaktionelle Leitfrage lautet:

> Warum soll dieser Anlass angeblich nicht vollständig dem Spieler zugerechnet werden – oder warum soll er anders bewertet werden, als er aussieht?

## 4. Tatsachensicherheit

- Texte dürfen ausschließlich Fakten behaupten, die durch `requiresAll` und verfügbare Platzhalter garantiert sind.
- Mehrere gleichzeitig mögliche Ursachen dürfen nicht stillschweigend auf eine konkrete Ursache verengt werden.
- Tagesausreißer bleiben ausdrücklich vorläufig.
- Eine lange Spieldauer ist keine späte Abgabe.
- Ein `6/6`-Ergebnis ist gelöst.
- Ein gelöster QuadWords-Ausreißer darf nicht als ungelöst bezeichnet werden.
- Boardtexte dürfen nur bei vier analysierbaren Boards und eindeutigem schlechtestem Board verwendet werden.

## 5. Bot-Terminologie

Im sichtbaren Text wird grundsätzlich **Grid** verwendet, nicht Raster. Technische Schlüssel und Enum-Namen bleiben unverändert.

## 6. Dubletten und Prämissen

Zwei Texte gelten redaktionell als zu ähnlich, wenn sie trotz anderer Wörter dieselbe Begründung, denselben Aufbau und dieselbe Pointe besitzen. Pro Stil und Anlass sollen verschiedene Mechaniken vorkommen, beispielsweise:

- externe Störung,
- absichtliche Strategie,
- formale Verteidigung,
- Zuständigkeitsproblem,
- kosmische Ursache,
- sportliche Pressekonferenz,
- knappe Resignation.

## 7. Abnahme vor dem produktiven Katalog

Vor Aufnahme in `src/main/resources/excuses/catalog.json` wird jeder Text geprüft auf:

1. erkennbare Ausreden-, Entschuldigungs- oder Begründungsfunktion beziehungsweise eine bewusst starke Ausnahme,
2. Stiltreue,
3. korrekten Anlass und korrekte Tatsachen,
4. eindeutige Terminologie,
5. ausreichende Abgrenzung zu anderen Texten derselben Familie,
6. passende Themenzuordnung,
7. vollständige Renderbarkeit ohne unerlaubte Mentions.
