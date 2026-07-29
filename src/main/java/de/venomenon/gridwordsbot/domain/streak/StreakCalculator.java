package de.venomenon.gridwordsbot.domain.streak;
import de.venomenon.gridwordsbot.domain.model.*; import java.time.LocalDate; import java.util.*;
/** Pure calendar-day calculation of the seven accepted streaks. */
public final class StreakCalculator {
 public StreakSummary calculate(List<PlayerResult> rs,List<Long> players,long player,LocalDate today){ Map<Long,Map<LocalDate,Map<GameType,PlayerResult>>> all=index(rs); Map<LocalDate,Map<GameType,PlayerResult>> mine=all.getOrDefault(player,Map.of()); return new StreakSummary(run(today,d->activity(mine,d)),run(today,d->complete(mine,d)),run(today,d->solved(mine,d,GameType.GRIDWORDS)),run(today,d->solved(mine,d,GameType.QUADWORDS)),run(today,d->perfect(mine,d)),run(today,d->sharedComplete(all,players,d)),run(today,d->sharedPerfect(all,players,d))); }
 private int run(LocalDate today,java.util.function.Function<LocalDate,State> f){int n=0; LocalDate d=today; while(true){State s=f.apply(d); if(s==State.MET){n++;d=d.minusDays(1);continue;} if(d.equals(today)&&s==State.PENDING){d=d.minusDays(1);continue;} return n;}}
 private State activity(Map<LocalDate,Map<GameType,PlayerResult>> m,LocalDate d){return m.containsKey(d)?State.MET:State.PENDING;}
 private State complete(Map<LocalDate,Map<GameType,PlayerResult>> m,LocalDate d){return m.getOrDefault(d,Map.of()).size()==2?State.MET:State.PENDING;}
 private State solved(Map<LocalDate,Map<GameType,PlayerResult>> m,LocalDate d,GameType t){PlayerResult r=m.getOrDefault(d,Map.of()).get(t);return r==null?State.PENDING:r.solved()?State.MET:State.VIOLATED;}
 private State perfect(Map<LocalDate,Map<GameType,PlayerResult>> m,LocalDate d){Map<GameType,PlayerResult> g=m.getOrDefault(d,Map.of()); if(g.size()==2)return g.values().stream().allMatch(PlayerResult::solved)?State.MET:State.VIOLATED; return g.values().stream().anyMatch(r->!r.solved())?State.VIOLATED:State.PENDING;}
 private State sharedComplete(Map<Long,Map<LocalDate,Map<GameType,PlayerResult>>> a,List<Long> p,LocalDate d){return p.stream().allMatch(x->complete(a.getOrDefault(x,Map.of()),d)==State.MET)?State.MET:State.PENDING;}
 private State sharedPerfect(Map<Long,Map<LocalDate,Map<GameType,PlayerResult>>> a,List<Long> p,LocalDate d){boolean bad=p.stream().anyMatch(x->perfect(a.getOrDefault(x,Map.of()),d)==State.VIOLATED); return p.stream().allMatch(x->perfect(a.getOrDefault(x,Map.of()),d)==State.MET)?State.MET:bad?State.VIOLATED:State.PENDING;}
 private Map<Long,Map<LocalDate,Map<GameType,PlayerResult>>> index(List<PlayerResult> rs){Map<Long,Map<LocalDate,Map<GameType,PlayerResult>>> a=new HashMap<>();for(PlayerResult r:rs)a.computeIfAbsent(r.playerId(),x->new HashMap<>()).computeIfAbsent(r.result().gameDate(),x->new HashMap<>()).put(r.result().gameType(),r);return a;}
 public record PlayerResult(long playerId,ParsedGameResult result){public PlayerResult{if(playerId<=0)throw new IllegalArgumentException("player");Objects.requireNonNull(result);} boolean solved(){return result.outcome() instanceof ShareOutcome.Solved;}}
 private enum State {MET,PENDING,VIOLATED}
}
