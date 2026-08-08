package de.venomenon.gridwordsbot.domain.achievement;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Vollständiger, deterministischer und hart validierter codebasierter Achievement-Katalog. */
public final class AchievementDefinitionCatalog {
    private static final int ACHIEVEMENTS_V1_DEFINITION_COUNT = 60;
    private static final AchievementDefinitionCatalog ACHIEVEMENTS_V1 = createAchievementsV1();

    private final AchievementDefinitionVersion version;
    private final List<AchievementDefinition> definitions;
    private final Map<AchievementKey, AchievementDefinition> byKey;

    private AchievementDefinitionCatalog(
            AchievementDefinitionVersion version,
            List<AchievementDefinition> definitions,
            boolean requireCompleteAchievementsV1) {
        this.version = Objects.requireNonNull(version, "version");
        this.definitions = List.copyOf(Objects.requireNonNull(definitions, "definitions"));
        if (this.definitions.isEmpty()) {
            throw new IllegalArgumentException("definitions must not be empty");
        }

        LinkedHashMap<AchievementKey, AchievementDefinition> indexed = new LinkedHashMap<>();
        Set<String> displayNames = new LinkedHashSet<>();
        for (AchievementDefinition definition : this.definitions) {
            Objects.requireNonNull(definition, "definition");
            if (!version.equals(definition.definitionVersion())) {
                throw new IllegalArgumentException("definition version does not match catalog version");
            }
            if (indexed.putIfAbsent(definition.key(), definition) != null) {
                throw new IllegalArgumentException("duplicate achievement key: " + definition.key());
            }
            if (!displayNames.add(definition.displayName())) {
                throw new IllegalArgumentException("duplicate achievement display name: " + definition.displayName());
            }
        }
        this.byKey = Map.copyOf(indexed);

        if (requireCompleteAchievementsV1) {
            validateAchievementsV1Completeness();
        }
    }

    public static AchievementDefinitionCatalog achievementsV1() {
        return ACHIEVEMENTS_V1;
    }

    public static AchievementDefinitionCatalog of(
            AchievementDefinitionVersion version, List<AchievementDefinition> definitions) {
        return new AchievementDefinitionCatalog(version, definitions, false);
    }

    public AchievementDefinitionVersion version() {
        return version;
    }

    public List<AchievementDefinition> definitions() {
        return definitions;
    }

    public Optional<AchievementDefinition> find(AchievementKey key) {
        return Optional.ofNullable(byKey.get(Objects.requireNonNull(key, "key")));
    }

    private static AchievementDefinitionCatalog createAchievementsV1() {
        List<AchievementDefinition> definitions = new ArrayList<>();
        add(definitions, "participation.1.gridwords", AchievementCategory.EXPERIENCE, AchievementScope.GRIDWORDS, "👋", "GW: Dabei!", "Dein erstes gültiges GridWords-Ergebnis.", new AchievementRule.ParticipationCount(GameType.GRIDWORDS, 1));
        add(definitions, "participation.10.gridwords", AchievementCategory.EXPERIENCE, AchievementScope.GRIDWORDS, "🔟", "GW: Warmgespielt", "Zehnmal bei GridWords dabei.", new AchievementRule.ParticipationCount(GameType.GRIDWORDS, 10));
        add(definitions, "participation.25.gridwords", AchievementCategory.EXPERIENCE, AchievementScope.GRIDWORDS, "🏠", "GW: Stammgast", "GridWords gehört zur Routine.", new AchievementRule.ParticipationCount(GameType.GRIDWORDS, 25));
        add(definitions, "participation.50.gridwords", AchievementCategory.EXPERIENCE, AchievementScope.GRIDWORDS, "📌", "GW: Feste Größe", "Fünfzig GridWords-Ergebnisse sprechen für sich.", new AchievementRule.ParticipationCount(GameType.GRIDWORDS, 50));
        add(definitions, "participation.100.gridwords", AchievementCategory.EXPERIENCE, AchievementScope.GRIDWORDS, "💯", "GW: Hundertprozentig dabei", "Dreistellige GridWords-Erfahrung.", new AchievementRule.ParticipationCount(GameType.GRIDWORDS, 100));
        add(definitions, "participation.1.quadwords", AchievementCategory.EXPERIENCE, AchievementScope.QUADWORDS, "👋", "QW: Dabei!", "Dein erstes gültiges QuadWords-Ergebnis.", new AchievementRule.ParticipationCount(GameType.QUADWORDS, 1));
        add(definitions, "participation.10.quadwords", AchievementCategory.EXPERIENCE, AchievementScope.QUADWORDS, "🔟", "QW: Warmgespielt", "Zehnmal bei QuadWords dabei.", new AchievementRule.ParticipationCount(GameType.QUADWORDS, 10));
        add(definitions, "participation.25.quadwords", AchievementCategory.EXPERIENCE, AchievementScope.QUADWORDS, "🏠", "QW: Stammgast", "QuadWords gehört zur Routine.", new AchievementRule.ParticipationCount(GameType.QUADWORDS, 25));
        add(definitions, "participation.50.quadwords", AchievementCategory.EXPERIENCE, AchievementScope.QUADWORDS, "📌", "QW: Feste Größe", "Fünfzig QuadWords-Ergebnisse sprechen für sich.", new AchievementRule.ParticipationCount(GameType.QUADWORDS, 50));
        add(definitions, "participation.100.quadwords", AchievementCategory.EXPERIENCE, AchievementScope.QUADWORDS, "💯", "QW: Hundertprozentig dabei", "Dreistellige QuadWords-Erfahrung.", new AchievementRule.ParticipationCount(GameType.QUADWORDS, 100));
        add(definitions, "streak.participation.10.gridwords", AchievementCategory.RELIABILITY, AchievementScope.GRIDWORDS, "🔟", "GW: Zehn am Stück", "Zehn GridWords-Tage ohne Lücke.", new AchievementRule.ParticipationStreak(GameType.GRIDWORDS, 10));
        add(definitions, "streak.participation.25.gridwords", AchievementCategory.RELIABILITY, AchievementScope.GRIDWORDS, "🧱", "GW: Drangeblieben", "25 Tage konsequent mitgemacht.", new AchievementRule.ParticipationStreak(GameType.GRIDWORDS, 25));
        add(definitions, "streak.participation.50.gridwords", AchievementCategory.RELIABILITY, AchievementScope.GRIDWORDS, "🏃", "GW: Dauerläufer", "Fünfzig GridWords-Tage hintereinander.", new AchievementRule.ParticipationStreak(GameType.GRIDWORDS, 50));
        add(definitions, "streak.participation.100.gridwords", AchievementCategory.RELIABILITY, AchievementScope.GRIDWORDS, "🛡️", "GW: Unverwüstlich", "Hundert GridWords-Tage am Stück dabei.", new AchievementRule.ParticipationStreak(GameType.GRIDWORDS, 100));
        add(definitions, "streak.participation.10.quadwords", AchievementCategory.RELIABILITY, AchievementScope.QUADWORDS, "🔟", "QW: Zehn am Stück", "Zehn QuadWords-Tage ohne Lücke.", new AchievementRule.ParticipationStreak(GameType.QUADWORDS, 10));
        add(definitions, "streak.participation.25.quadwords", AchievementCategory.RELIABILITY, AchievementScope.QUADWORDS, "🧱", "QW: Drangeblieben", "25 Tage konsequent mitgemacht.", new AchievementRule.ParticipationStreak(GameType.QUADWORDS, 25));
        add(definitions, "streak.participation.50.quadwords", AchievementCategory.RELIABILITY, AchievementScope.QUADWORDS, "🏃", "QW: Dauerläufer", "Fünfzig QuadWords-Tage hintereinander.", new AchievementRule.ParticipationStreak(GameType.QUADWORDS, 50));
        add(definitions, "streak.participation.100.quadwords", AchievementCategory.RELIABILITY, AchievementScope.QUADWORDS, "🛡️", "QW: Unverwüstlich", "Hundert QuadWords-Tage am Stück dabei.", new AchievementRule.ParticipationStreak(GameType.QUADWORDS, 100));
        add(definitions, "streak.success.1.gridwords", AchievementCategory.PERFORMANCE, AchievementScope.GRIDWORDS, "✅", "GW: Geschafft!", "Dein erster erfolgreicher GridWords-Abschluss.", new AchievementRule.SuccessStreak(GameType.GRIDWORDS, 1));
        add(definitions, "streak.success.10.gridwords", AchievementCategory.PERFORMANCE, AchievementScope.GRIDWORDS, "🔥", "GW: Heiß gelaufen", "Zehn erfolgreiche GridWords-Tage hintereinander.", new AchievementRule.SuccessStreak(GameType.GRIDWORDS, 10));
        add(definitions, "streak.success.25.gridwords", AchievementCategory.PERFORMANCE, AchievementScope.GRIDWORDS, "🏅", "GW: Siegesserie", "25 GridWords-Erfolge ohne Unterbrechung.", new AchievementRule.SuccessStreak(GameType.GRIDWORDS, 25));
        add(definitions, "streak.success.50.gridwords", AchievementCategory.PERFORMANCE, AchievementScope.GRIDWORDS, "🚀", "GW: Nicht zu stoppen", "Fünfzig erfolgreiche GridWords-Tage am Stück.", new AchievementRule.SuccessStreak(GameType.GRIDWORDS, 50));
        add(definitions, "streak.success.100.gridwords", AchievementCategory.PERFORMANCE, AchievementScope.GRIDWORDS, "👑", "GW: Hundertfach geliefert", "Hundert GridWords-Erfolge hintereinander.", new AchievementRule.SuccessStreak(GameType.GRIDWORDS, 100));
        add(definitions, "streak.success.1.quadwords", AchievementCategory.PERFORMANCE, AchievementScope.QUADWORDS, "✅", "QW: Geschafft!", "Dein erster erfolgreicher QuadWords-Abschluss.", new AchievementRule.SuccessStreak(GameType.QUADWORDS, 1));
        add(definitions, "streak.success.10.quadwords", AchievementCategory.PERFORMANCE, AchievementScope.QUADWORDS, "🔥", "QW: Heiß gelaufen", "Zehn erfolgreiche QuadWords-Tage hintereinander.", new AchievementRule.SuccessStreak(GameType.QUADWORDS, 10));
        add(definitions, "streak.success.25.quadwords", AchievementCategory.PERFORMANCE, AchievementScope.QUADWORDS, "🏅", "QW: Siegesserie", "25 QuadWords-Erfolge ohne Unterbrechung.", new AchievementRule.SuccessStreak(GameType.QUADWORDS, 25));
        add(definitions, "streak.success.50.quadwords", AchievementCategory.PERFORMANCE, AchievementScope.QUADWORDS, "🚀", "QW: Nicht zu stoppen", "Fünfzig erfolgreiche QuadWords-Tage am Stück.", new AchievementRule.SuccessStreak(GameType.QUADWORDS, 50));
        add(definitions, "streak.success.100.quadwords", AchievementCategory.PERFORMANCE, AchievementScope.QUADWORDS, "👑", "QW: Hundertfach geliefert", "Hundert QuadWords-Erfolge hintereinander.", new AchievementRule.SuccessStreak(GameType.QUADWORDS, 100));
        add(definitions, "performance.solve.1.gridwords", AchievementCategory.PERFORMANCE, AchievementScope.GRIDWORDS, "🎯", "GW: Volltreffer", "GridWords ohne Umwege.", new AchievementRule.ExactSolvedAttempts(GameType.GRIDWORDS, 1));
        add(definitions, "performance.solve.2.gridwords", AchievementCategory.PERFORMANCE, AchievementScope.GRIDWORDS, "✌️", "GW: Zweiter sitzt", "Nur einen Versuch Anlauf gebraucht.", new AchievementRule.ExactSolvedAttempts(GameType.GRIDWORDS, 2));
        add(definitions, "performance.solve.3.gridwords", AchievementCategory.PERFORMANCE, AchievementScope.GRIDWORDS, "3️⃣", "GW: Aller guten Dinge", "Beim dritten Versuch war Schluss.", new AchievementRule.ExactSolvedAttempts(GameType.GRIDWORDS, 3));
        add(definitions, "performance.solve.4.quadwords", AchievementCategory.PERFORMANCE, AchievementScope.QUADWORDS, "4️⃣", "QW: Vier gewinnt", "QuadWords mit dem theoretischen Minimum.", new AchievementRule.ExactSolvedAttempts(GameType.QUADWORDS, 4));
        add(definitions, "performance.solve.5.quadwords", AchievementCategory.PERFORMANCE, AchievementScope.QUADWORDS, "✋", "QW: High Five", "Alle vier Boards nach fünf Versuchen erledigt.", new AchievementRule.ExactSolvedAttempts(GameType.QUADWORDS, 5));
        add(definitions, "performance.solve.6.quadwords", AchievementCategory.PERFORMANCE, AchievementScope.QUADWORDS, "6️⃣", "QW: Saubere Sechs", "Sechs Versuche, vier gelöste Boards.", new AchievementRule.ExactSolvedAttempts(GameType.QUADWORDS, 6));
        add(definitions, "crossgame.participation.1", AchievementCategory.RELIABILITY, AchievementScope.CROSS_GAME, "🎮", "GW+QW: Doppelschicht", "Beide Spiele an einem Tag erledigt.", new AchievementRule.CrossGameParticipationCount(1));
        add(definitions, "crossgame.participation.10", AchievementCategory.RELIABILITY, AchievementScope.CROSS_GAME, "🔁", "GW+QW: Doppelroutine", "Zehn Tage mit dem kompletten Programm.", new AchievementRule.CrossGameParticipationCount(10));
        add(definitions, "crossgame.participation.25", AchievementCategory.RELIABILITY, AchievementScope.CROSS_GAME, "🏠", "GW+QW: Doppelstammgast", "25 Tage bei beiden Spielen dabei.", new AchievementRule.CrossGameParticipationCount(25));
        add(definitions, "crossgame.participation.50", AchievementCategory.RELIABILITY, AchievementScope.CROSS_GAME, "📦", "GW+QW: Im Doppelpack", "Fünfzig komplette Doppeltage.", new AchievementRule.CrossGameParticipationCount(50));
        add(definitions, "crossgame.participation.100", AchievementCategory.RELIABILITY, AchievementScope.CROSS_GAME, "💯", "GW+QW: Doppelhundert", "Hundert Tage GridWords und QuadWords.", new AchievementRule.CrossGameParticipationCount(100));
        add(definitions, "crossgame.success.1", AchievementCategory.PERFORMANCE, AchievementScope.CROSS_GAME, "✌️", "GW+QW: Doppelsieg", "Beide Tagesrätsel erfolgreich erledigt.", new AchievementRule.CrossGameSuccessCount(1));
        add(definitions, "crossgame.success.10", AchievementCategory.PERFORMANCE, AchievementScope.CROSS_GAME, "🔥", "GW+QW: Doppelform", "Zehn erfolgreiche Doppeltage.", new AchievementRule.CrossGameSuccessCount(10));
        add(definitions, "crossgame.success.25", AchievementCategory.PERFORMANCE, AchievementScope.CROSS_GAME, "⚡", "GW+QW: Doppelt stark", "25 Tage mit zwei erfolgreichen Abschlüssen.", new AchievementRule.CrossGameSuccessCount(25));
        add(definitions, "crossgame.success.50", AchievementCategory.PERFORMANCE, AchievementScope.CROSS_GAME, "🏅", "GW+QW: Zweifach souverän", "Fünfzig erfolgreiche Doppeltage.", new AchievementRule.CrossGameSuccessCount(50));
        add(definitions, "crossgame.success.100", AchievementCategory.PERFORMANCE, AchievementScope.CROSS_GAME, "👑", "GW+QW: Doppelkrone", "Hundertmal beide Spiele am selben Tag bezwungen.", new AchievementRule.CrossGameSuccessCount(100));
        add(definitions, "experience.total.100", AchievementCategory.EXPERIENCE, AchievementScope.GLOBAL, "💯", "Hundertsassa", "Hundert Ergebnisse quer durch beide Spiele.", new AchievementRule.TotalResultCount(100));
        add(definitions, "experience.total.200", AchievementCategory.EXPERIENCE, AchievementScope.GLOBAL, "🛤️", "Langstrecke", "Zweihundert Ergebnisse später immer noch dabei.", new AchievementRule.TotalResultCount(200));
        add(definitions, "experience.total.300", AchievementCategory.EXPERIENCE, AchievementScope.GLOBAL, "📚", "Lebendes Archiv", "Dreihundert Ergebnisse Bot-Geschichte.", new AchievementRule.TotalResultCount(300));
        add(definitions, "situational.last_chance.gridwords", AchievementCategory.SPECIAL, AchievementScope.GRIDWORDS, "⏳", "GW: Auf den letzten Drücker", "Wirklich keinen Versuch verschenkt.", new AchievementRule.ExactSolvedAttempts(GameType.GRIDWORDS, 6));
        add(definitions, "situational.last_chance.quadwords", AchievementCategory.SPECIAL, AchievementScope.QUADWORDS, "⏳", "QW: Auf den letzten Drücker", "Das letzte Board fiel genau noch rechtzeitig.", new AchievementRule.ExactSolvedAttempts(GameType.QUADWORDS, 9));
        add(definitions, "situational.quadwords.consecutive_board_attempts", AchievementCategory.SPECIAL, AchievementScope.QUADWORDS, "🚂", "QW: Durchmarsch", "Vier Boards, vier aufeinanderfolgende Stationen.", new AchievementRule.QuadWordsConsecutiveBoardAttempts());
        add(definitions, "situational.quadwords.outlier_board", AchievementCategory.SPECIAL, AchievementScope.QUADWORDS, "👹", "QW: Endgegner", "Drei waren okay. Eines hatte andere Pläne.", new AchievementRule.QuadWordsOutlierBoard(3));
        add(definitions, "situational.crossgame.equal_final_score", AchievementCategory.SPECIAL, AchievementScope.CROSS_GAME, "🎯", "GW+QW: Punktlandung", "Zwei Spiele, dieselbe Zahl.", new AchievementRule.CrossGameEqualFinalScore());
        add(definitions, "situational.crossgame.double_last_chance", AchievementCategory.SPECIAL, AchievementScope.CROSS_GAME, "💓", "GW+QW: Doppeltes Herzschlagfinale", "Zweimal fast vorbei. Zweimal doch geschafft.", new AchievementRule.CrossGameExactAttempts(6, 9));
        add(definitions, "situational.deja_vu.gridwords", AchievementCategory.SPECIAL, AchievementScope.GRIDWORDS, "🔁", "GW: Déjà-vu", "Dasselbe erfolgreiche Ergebnis. Und nochmal.", new AchievementRule.ConsecutiveSameSuccessfulResults(GameType.GRIDWORDS, 3));
        add(definitions, "situational.deja_vu.quadwords", AchievementCategory.SPECIAL, AchievementScope.QUADWORDS, "🔁", "QW: Déjà-vu", "Dreimal dasselbe erfolgreiche QuadWords-Endergebnis.", new AchievementRule.ConsecutiveSameSuccessfulResults(GameType.QUADWORDS, 3));
        add(definitions, "timing.before_0700", AchievementCategory.SPECIAL, AchievementScope.GLOBAL, "🌅", "Frühaufsteher", "Wörter lösen, bevor normale Menschen wach sind.", new AchievementRule.LocalTimeBefore(LocalTime.of(7, 0)));
        add(definitions, "timing.after_2300", AchievementCategory.SPECIAL, AchievementScope.GLOBAL, "🦉", "Nachteule", "Ein Rätsel geht noch.", new AchievementRule.LocalTimeAtOrAfter(LocalTime.of(23, 0)));
        add(definitions, "situational.crossgame.perfect_double", AchievementCategory.SPECIAL, AchievementScope.CROSS_GAME, "✨", "GW+QW: Perfekter Doppelschlag", "Ein nahezu absurd guter Spieltag.", new AchievementRule.CrossGameExactAttempts(1, 4));
        add(definitions, "situational.failure_run.3.gridwords", AchievementCategory.SPECIAL, AchievementScope.GRIDWORDS, "🫠", "GW: Pleiten-Hattrick", "Drei GridWords-Ergebnisse hintereinander ohne Erfolg.", new AchievementRule.ConsecutiveFailures(GameType.GRIDWORDS, 3));
        add(definitions, "situational.failure_run.3.quadwords", AchievementCategory.SPECIAL, AchievementScope.QUADWORDS, "🫠", "QW: Pleiten-Hattrick", "Drei QuadWords-Ergebnisse hintereinander ohne Erfolg.", new AchievementRule.ConsecutiveFailures(GameType.QUADWORDS, 3));
        return new AchievementDefinitionCatalog(
                AchievementDefinitionVersion.ACHIEVEMENTS_V1, definitions, true);
    }

    private static void add(
            List<AchievementDefinition> definitions,
            String key,
            AchievementCategory category,
            AchievementScope scope,
            String fallbackEmoji,
            String displayName,
            String description,
            AchievementRule rule) {
        definitions.add(new AchievementDefinition(
                new AchievementKey(key),
                AchievementDefinitionVersion.ACHIEVEMENTS_V1,
                category,
                scope,
                fallbackEmoji,
                displayName,
                description,
                rule));
    }

    private void validateAchievementsV1Completeness() {
        if (!AchievementDefinitionVersion.ACHIEVEMENTS_V1.equals(version)) {
            throw new IllegalArgumentException(
                    "achievements-v1 catalog must use achievements-v1 definition version");
        }
        if (definitions.size() != ACHIEVEMENTS_V1_DEFINITION_COUNT) {
            throw new IllegalArgumentException("achievements-v1 must contain exactly 60 definitions");
        }

        long gridWords = definitions.stream().filter(d -> d.scope() == AchievementScope.GRIDWORDS).count();
        long quadWords = definitions.stream().filter(d -> d.scope() == AchievementScope.QUADWORDS).count();
        long crossGame = definitions.stream().filter(d -> d.scope() == AchievementScope.CROSS_GAME).count();
        long global = definitions.stream().filter(d -> d.scope() == AchievementScope.GLOBAL).count();
        if (gridWords != 20 || quadWords != 22 || crossGame != 13 || global != 5) {
            throw new IllegalArgumentException("achievements-v1 scope distribution is incomplete");
        }
    }
}
