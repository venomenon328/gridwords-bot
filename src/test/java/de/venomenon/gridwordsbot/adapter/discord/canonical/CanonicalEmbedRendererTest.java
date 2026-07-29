package de.venomenon.gridwordsbot.adapter.discord.canonical;
import static org.assertj.core.api.Assertions.assertThat;
import de.venomenon.gridwordsbot.application.canonical.CanonicalResultMessage;
import de.venomenon.gridwordsbot.domain.model.*;
import de.venomenon.gridwordsbot.domain.streak.StreakSummary;
import java.time.*; import java.util.*; import org.junit.jupiter.api.Test;
class CanonicalEmbedRendererTest {
 @Test void rendersMandatoryAndContextualSeries() {
  CanonicalResultMessage message = new CanonicalResultMessage("Tobias", GameType.GRIDWORDS, LocalDate.of(2026,7,29), new ShareOutcome.Solved(3,6), Duration.ofSeconds(85), new NormalizedBoard(List.of("\u2b1c\u2b1c\u2b1c\u2b1c\u2b1c","\ud83d\udfe8\ud83d\udfe8\ud83d\udfe8\ud83d\udfe8\ud83d\udfe8","\ud83d\udfe9\ud83d\udfe9\ud83d\udfe9\ud83d\udfe9\ud83d\udfe9")), new StreakSummary(12,8,7,4,3,5,2), OptionalInt.of(8), OptionalInt.of(3), OptionalInt.of(5), OptionalInt.of(2), "gridwords-result-4");
  var embed = new CanonicalEmbedRenderer().render(message);
  assertThat(embed.getTitle()).contains("GridWords", "29. Juli 2026");
  assertThat(embed.getDescription()).contains("Tobias", "1:25", "Komplett: 8 Tage", "Perfekt: 3 Tage", "Gemeinsam komplett: 5 Tage", "Gemeinsam perfekt: 2 Tage");
  assertThat(embed.getDescription()).doesNotContain("Spielserie", "@everyone");
  assertThat(embed.getFooter().getText()).isEqualTo("gridwords-result-4");
 }
 @Test void rendersUnsolvedOutcomeAndNoRunningSeries() {
  CanonicalResultMessage message = new CanonicalResultMessage("Tobias", GameType.GRIDWORDS, LocalDate.of(2026,7,29), new ShareOutcome.Unsolved(6), Duration.ofSeconds(10), new NormalizedBoard(Collections.nCopies(6,"\u2b1c\u2b1c\u2b1c\u2b1c\u2b1c")), new StreakSummary(1,0,0,0,0,0,0), OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(), "key");
  assertThat(new CanonicalEmbedRenderer().render(message).getDescription()).contains("X/6", "keine laufende Serie");
 }
}