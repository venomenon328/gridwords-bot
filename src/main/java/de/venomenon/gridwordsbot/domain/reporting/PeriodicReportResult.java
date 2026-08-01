package de.venomenon.gridwordsbot.domain.reporting;

/** The transport-neutral result of assembling one completed periodic report. */
public sealed interface PeriodicReportResult permits PeriodicReport, PeriodicReportNoOp {}
