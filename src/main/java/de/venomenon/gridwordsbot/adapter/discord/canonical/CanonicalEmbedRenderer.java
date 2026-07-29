package de.venomenon.gridwordsbot.adapter.discord.canonical;
import de.venomenon.gridwordsbot.application.canonical.CanonicalResultMessage;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.dv8tion.jda.api.EmbedBuilder;
final class CanonicalEmbedRenderer {
 net.dv8tion.jda.api.entities.MessageEmbed render(CanonicalResultMessage m) {
  String outcome=m.outcome() instanceof ShareOutcome.Solved s ? "gel\u00f6st in "+s.attemptsUsed()+"/"+s.maxAttempts() : "nicht gel\u00f6st \u00b7 X/"+m.outcome().maxAttempts();
  String duration=String.format("%d:%02d",m.duration().toMinutes(),m.duration().toSecondsPart());
  String title="\ud83d\udfe9 GridWords \u00b7 "+m.gameDate().format(DateTimeFormatter.ofPattern("d. MMMM uuuu",Locale.GERMAN));
  StringBuilder series=new StringBuilder("\ud83d\udd25 Aktivit\u00e4t: "+days(m.streaks().personalActivity())+"\n\ud83d\udfe9 GridWords gel\u00f6st: "+daysOrNone(m.streaks().personalGridWordsSolved()));
  if(m.personalComplete().isPresent())series.append("\nKomplett: ").append(days(m.personalComplete().getAsInt()));
  if(m.personalPerfect().isPresent())series.append("\nPerfekt: ").append(days(m.personalPerfect().getAsInt()));
  return new EmbedBuilder().setTitle(title).setDescription(m.playerDisplayName()+" \u00b7 "+outcome+" \u00b7 "+duration+"\n\n"+m.board().canonicalText()+"\n\n"+series).setFooter(m.publicationKey()).build();
 }
 private String days(int x){return x+" "+(x==1?"Tag":"Tage");} private String daysOrNone(int x){return x==0?"keine laufende Serie":days(x);}
}