package de.venomenon.gridwordsbot.domain.streak;
public record StreakSummary(int personalActivity,int personalComplete,int personalGridWordsSolved,int personalQuadWordsSolved,int personalPerfect,int sharedComplete,int sharedPerfect) {
 public StreakSummary { if (personalActivity<0||personalComplete<0||personalGridWordsSolved<0||personalQuadWordsSolved<0||personalPerfect<0||sharedComplete<0||sharedPerfect<0) throw new IllegalArgumentException("negative streak"); }
}
