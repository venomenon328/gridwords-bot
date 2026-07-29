package de.venomenon.gridwordsbot.adapter.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresSchemaIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private JdbcTemplate jdbc;
    private final Timestamp now = Timestamp.from(Instant.parse("2026-07-29T08:00:00Z"));

    @BeforeAll void migrate() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase(); liquibase.setDataSource(ds); liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml"); liquibase.afterPropertiesSet();
        jdbc = new JdbcTemplate(ds);
    }

    @Test void liquibaseCreatesAllPersistenceTables() { assertEquals(5, jdbc.queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_name in ('player','game_result','submission','submission_attachment','daily_status_message')", Integer.class)); }
    @Test void playerRejectsNonPositiveDiscordId() { assertThrows(Exception.class, () -> jdbc.update("insert into player values (0,'x',true,false,?,?)", now, now)); }
    @Test void gameResultRejectsUnknownPlayerAndInvalidGameType() { assertThrows(Exception.class, () -> jdbc.update("insert into game_result(player_id,game_type,game_date,solved,max_attempts,duration_seconds,normalized_board,raw_share_text,parser_version,created_at,updated_at) values (999,'GRIDWORDS',current_date,false,6,0,'board','x','v',?,?)",now,now)); }
    @Test void submissionRejectsInvalidState() { insertPlayer(201); assertThrows(Exception.class, () -> jdbc.update("insert into submission(source_message_id,guild_id,channel_id,author_player_id,raw_message_content,processing_state,received_at,updated_at) values (201,1,1,201,'x','UNKNOWN',?,?)",now,now)); }
    @Test void attachmentEnforcesNonNegativeIndexAndSize() { insertPlayer(202); insertSubmission(202,202); assertThrows(Exception.class, () -> jdbc.update("insert into submission_attachment values (202,-1,'x',null,0)")); assertThrows(Exception.class, () -> jdbc.update("insert into submission_attachment values (202,0,'x',null,-1)")); }
    @Test void dailyStatusIsUniquePerGuildChannelAndDate() { jdbc.update("insert into daily_status_message(guild_id,channel_id,game_date,created_at,updated_at) values (3,4,current_date,?,?)",now,now); assertThrows(Exception.class, () -> jdbc.update("insert into daily_status_message(guild_id,channel_id,game_date,created_at,updated_at) values (3,4,current_date,?,?)",now,now)); }
    @Test void gameResultEnforcesBusinessKey() { insertPlayer(203); insertGridResult(203); assertThrows(Exception.class, () -> insertGridResult(203)); }
    @Test void gameResultChecksSolvedAttemptsAndBoardRules() { insertPlayer(204); assertThrows(Exception.class, () -> jdbc.update("insert into game_result(player_id,game_type,game_date,solved,attempts_used,max_attempts,duration_seconds,normalized_board,raw_share_text,parser_version,created_at,updated_at) values (204,'GRIDWORDS',current_date,true,7,6,0,'b','x','v',?,?)",now,now)); assertThrows(Exception.class, () -> jdbc.update("insert into game_result(player_id,game_type,game_date,solved,max_attempts,duration_seconds,raw_share_text,parser_version,created_at,updated_at) values (204,'GRIDWORDS',current_date,false,6,0,'x','v',?,?)",now,now)); }
    @Test void canonicalMessageMustBePositive() { insertPlayer(205); assertThrows(Exception.class, () -> jdbc.update("insert into game_result(player_id,game_type,game_date,solved,max_attempts,duration_seconds,normalized_board,raw_share_text,parser_version,canonical_message_id,created_at,updated_at) values (205,'GRIDWORDS',current_date,false,6,0,'b','x','v',0,?,?)",now,now)); }
    @Test void submissionPrimaryKeyIsUnique() { insertPlayer(206); insertSubmission(206,206); assertThrows(Exception.class, () -> insertSubmission(206,206)); }
    private void insertPlayer(long id) { jdbc.update("insert into player values (?, 'p'||?,true,false,?,?) on conflict do nothing",id,id,now,now); }
    private void insertSubmission(long id,long player) { jdbc.update("insert into submission(source_message_id,guild_id,channel_id,author_player_id,raw_message_content,processing_state,received_at,updated_at) values (?,1,1,?,'x','RECEIVED',?,?)",id,player,now,now); }
    private void insertGridResult(long player) { jdbc.update("insert into game_result(player_id,game_type,game_date,solved,max_attempts,duration_seconds,normalized_board,raw_share_text,parser_version,created_at,updated_at) values (?,'GRIDWORDS',current_date,false,6,0,'b','x','v',?,?)",player,now,now); }
}