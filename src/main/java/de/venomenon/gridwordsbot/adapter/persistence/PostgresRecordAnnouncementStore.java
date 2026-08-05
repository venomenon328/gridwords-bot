package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementKey;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementMessage;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementPhase;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementProjection;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementRegistration;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementSubject;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaim;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaimRequest;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailure;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailureCategory;
import de.venomenon.gridwordsbot.domain.record.RecordWorkState;
import de.venomenon.gridwordsbot.port.out.RecordAnnouncementStore;
import de.venomenon.gridwordsbot.port.out.RecordAnnouncementClaimConflictException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL delivery projection store.  It persists intent only and performs no external I/O. */
public final class PostgresRecordAnnouncementStore implements RecordAnnouncementStore {
    private final JdbcTemplate jdbc; private final Clock clock;
    public PostgresRecordAnnouncementStore(JdbcTemplate jdbc,Clock clock){this.jdbc=java.util.Objects.requireNonNull(jdbc,"jdbc");this.clock=java.util.Objects.requireNonNull(clock,"clock");}
    @Override @Transactional public RecordAnnouncementSnapshot registerOrUpdate(RecordAnnouncementRegistration registration) {
        java.util.Objects.requireNonNull(registration,"registration"); Instant now=clock.instant(); RecordAnnouncementKey key=registration.key();
        Optional<RecordAnnouncementSnapshot> existing = find(key);
        if (existing.isPresent()) {
            RecordAnnouncementSnapshot snapshot = existing.orElseThrow();
            if (snapshot.state() == RecordWorkState.EXTERNALLY_REMOVED || sameRegistration(snapshot.registration(), registration)) return snapshot;
            if (snapshot.state() == RecordWorkState.CLAIMED) throw new RecordAnnouncementClaimConflictException();
            int updated = jdbc.update("""
                    UPDATE record_announcement SET subject_type=?,subject_key=?,announcement_phase=?,desired_projection=?,renderer_version=?,content_fingerprint=?,
                        delivery_state='OPEN',claim_token=NULL,claim_until=NULL,next_retry_at=NULL,failure_category=NULL,safe_error=NULL,updated_at=?
                    WHERE guild_id=? AND channel_id=? AND idempotency_key=? AND delivery_state<>'CLAIMED' AND delivery_state<>'EXTERNALLY_REMOVED'
                    """, registration.subject().type().name(),registration.subject().key(),registration.phase().name(),registration.desiredProjection().name(),registration.rendererVersion(),registration.contentFingerprint(),RecordJdbcMapping.utc(now),key.guildId(),key.channelId(),key.idempotencyKey());
            if (updated != 1) throw new RecordAnnouncementClaimConflictException();
            replaceFacts(id(key), registration.eventIds());
            return find(key).orElseThrow();
        }
        int inserted = jdbc.update("""
                INSERT INTO record_announcement (guild_id,channel_id,idempotency_key,subject_type,subject_key,announcement_phase,desired_projection,renderer_version,content_fingerprint,delivery_state,attempt_count,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,'OPEN',0,?,?)
                ON CONFLICT (guild_id,channel_id,idempotency_key) DO NOTHING
                """,key.guildId(),key.channelId(),key.idempotencyKey(),registration.subject().type().name(),registration.subject().key(),registration.phase().name(),registration.desiredProjection().name(),registration.rendererVersion(),registration.contentFingerprint(),RecordJdbcMapping.utc(now),RecordJdbcMapping.utc(now));
        if (inserted == 0) return registerOrUpdate(registration);
        replaceFacts(id(key), registration.eventIds());
        return find(key).orElseThrow();
    }
    @Override public Optional<RecordAnnouncementSnapshot> find(RecordAnnouncementKey key) { return jdbc.query("SELECT * FROM record_announcement WHERE guild_id=? AND channel_id=? AND idempotency_key=?",(rs,row)->snapshot(rs),key.guildId(),key.channelId(),key.idempotencyKey()).stream().findFirst(); }
    @Override public Optional<RecordLeaseClaim> claim(RecordAnnouncementKey key,RecordLeaseClaimRequest request) {
        UUID token=UUID.randomUUID(); return jdbc.query("""
                UPDATE record_announcement SET delivery_state='CLAIMED',claim_token=?,claim_until=?,next_retry_at=NULL,attempt_count=attempt_count+1,updated_at=?
                WHERE guild_id=? AND channel_id=? AND idempotency_key=? AND (delivery_state='OPEN' OR (delivery_state='RETRYABLE' AND next_retry_at<=?) OR (delivery_state='CLAIMED' AND claim_until<=?))
                RETURNING claim_token,claim_until
                """,(rs,row)->new RecordLeaseClaim(rs.getObject("claim_token",UUID.class),RecordJdbcMapping.instant(rs,"claim_until")),token,RecordJdbcMapping.utc(request.leaseUntil()),RecordJdbcMapping.utc(request.claimedAt()),key.guildId(),key.channelId(),key.idempotencyKey(),RecordJdbcMapping.utc(request.claimedAt()),RecordJdbcMapping.utc(request.claimedAt())).stream().findFirst();
    }
    @Override public boolean renewLease(RecordAnnouncementKey key,UUID token,RecordLeaseClaimRequest request) { return jdbc.update("UPDATE record_announcement SET claim_until=?,updated_at=? WHERE guild_id=? AND channel_id=? AND idempotency_key=? AND delivery_state='CLAIMED' AND claim_token=? AND claim_until>?",RecordJdbcMapping.utc(request.leaseUntil()),RecordJdbcMapping.utc(request.claimedAt()),key.guildId(),key.channelId(),key.idempotencyKey(),token,RecordJdbcMapping.utc(request.claimedAt()))==1; }
    @Override @Transactional public boolean replaceMessages(RecordAnnouncementKey key,UUID token,List<RecordAnnouncementMessage> messages) {
        messages=List.copyOf(java.util.Objects.requireNonNull(messages,"messages")); for(int i=0;i<messages.size();i++) if(messages.get(i).position()!=i) throw new IllegalArgumentException("messages must use contiguous visible positions");
        Long id=claimedId(key,token); if(id==null) return false;
        jdbc.update("DELETE FROM record_announcement_message WHERE announcement_id=?",id);
        for(RecordAnnouncementMessage message:messages) jdbc.update("INSERT INTO record_announcement_message (announcement_id,message_position,discord_message_id,created_at) VALUES (?,?,?,?)",id,message.position(),message.messageId(),RecordJdbcMapping.utc(clock.instant()));
        return true;
    }
    @Override public boolean markSynchronized(RecordAnnouncementKey key,UUID token,Instant at) { return jdbc.update("""
            UPDATE record_announcement SET delivery_state='SYNCHRONIZED',claim_token=NULL,claim_until=NULL,next_retry_at=NULL,failure_category=NULL,safe_error=NULL,
              published_at=CASE WHEN desired_projection='CREATE' THEN ? ELSE published_at END,changed_at=CASE WHEN desired_projection='EDIT' THEN ? ELSE changed_at END,
              deleted_at=CASE WHEN desired_projection='DELETE' THEN ? ELSE deleted_at END,updated_at=?
            WHERE guild_id=? AND channel_id=? AND idempotency_key=? AND delivery_state='CLAIMED' AND claim_token=?
            """,RecordJdbcMapping.utc(at),RecordJdbcMapping.utc(at),RecordJdbcMapping.utc(at),RecordJdbcMapping.utc(at),key.guildId(),key.channelId(),key.idempotencyKey(),token)==1; }
    @Override public boolean markRetryableFailure(RecordAnnouncementKey key,UUID token,RecordWorkFailure failure,Instant nextRetryAt) { if(failure.category()==RecordWorkFailureCategory.PERMANENT) throw new IllegalArgumentException("retryable failure cannot be permanent"); return failure(key,token,"RETRYABLE",failure,nextRetryAt,null); }
    @Override public boolean markPermanentFailure(RecordAnnouncementKey key,UUID token,RecordWorkFailure failure,Instant completedAt) { if(failure.category()!=RecordWorkFailureCategory.PERMANENT) throw new IllegalArgumentException("permanent failure needs PERMANENT category"); return failure(key,token,"FAILED_PERMANENT",failure,null,completedAt); }
    @Override public boolean markExternallyRemoved(RecordAnnouncementKey key,UUID token,Instant removedAt) { return jdbc.update("UPDATE record_announcement SET delivery_state='EXTERNALLY_REMOVED',claim_token=NULL,claim_until=NULL,next_retry_at=NULL,externally_removed_at=?,updated_at=? WHERE guild_id=? AND channel_id=? AND idempotency_key=? AND delivery_state='CLAIMED' AND claim_token=?",RecordJdbcMapping.utc(removedAt),RecordJdbcMapping.utc(removedAt),key.guildId(),key.channelId(),key.idempotencyKey(),token)==1; }
    private boolean failure(RecordAnnouncementKey key,UUID token,String state,RecordWorkFailure failure,Instant retry,Instant complete) {
        Instant updatedAt = complete == null ? clock.instant() : complete;
        return jdbc.update("UPDATE record_announcement SET delivery_state=?,claim_token=NULL,claim_until=NULL,next_retry_at=?,failure_category=?,safe_error=?,updated_at=? WHERE guild_id=? AND channel_id=? AND idempotency_key=? AND delivery_state='CLAIMED' AND claim_token=?",state,retry==null?null:RecordJdbcMapping.utc(retry),failure.category().name(),failure.safeMessage(),RecordJdbcMapping.utc(updatedAt),key.guildId(),key.channelId(),key.idempotencyKey(),token)==1;
    }
    private Long id(RecordAnnouncementKey key) { return jdbc.query("SELECT id FROM record_announcement WHERE guild_id=? AND channel_id=? AND idempotency_key=?",(rs,row)->rs.getLong(1),key.guildId(),key.channelId(),key.idempotencyKey()).stream().findFirst().orElse(null); }
    private void replaceFacts(Long id, List<UUID> eventIds) {
        if (id == null) throw new IllegalStateException("registered announcement is missing");
        jdbc.update("DELETE FROM record_announcement_event WHERE announcement_id=?",id);
        for(UUID eventId:eventIds) jdbc.update("INSERT INTO record_announcement_event (announcement_id,event_id) VALUES (?,?)",id,eventId);
    }
    private static boolean sameRegistration(RecordAnnouncementRegistration left, RecordAnnouncementRegistration right) {
        return left.key().equals(right.key()) && left.subject().equals(right.subject()) && left.phase() == right.phase()
                && left.desiredProjection() == right.desiredProjection() && left.rendererVersion().equals(right.rendererVersion())
                && left.contentFingerprint().equals(right.contentFingerprint()) && new java.util.HashSet<>(left.eventIds()).equals(new java.util.HashSet<>(right.eventIds()));
    }
    private Long claimedId(RecordAnnouncementKey key,UUID token) { return jdbc.query("SELECT id FROM record_announcement WHERE guild_id=? AND channel_id=? AND idempotency_key=? AND delivery_state='CLAIMED' AND claim_token=?",(rs,row)->rs.getLong(1),key.guildId(),key.channelId(),key.idempotencyKey(),token).stream().findFirst().orElse(null); }
    private RecordAnnouncementSnapshot snapshot(ResultSet rs) throws SQLException {
        long id=rs.getLong("id"); RecordAnnouncementKey key=new RecordAnnouncementKey(rs.getLong("guild_id"),rs.getLong("channel_id"),rs.getString("idempotency_key"));
        List<UUID> events=jdbc.query("SELECT event_id FROM record_announcement_event WHERE announcement_id=? ORDER BY event_id",(event,row)->event.getObject(1,UUID.class),id);
        List<RecordAnnouncementMessage> messages=jdbc.query("SELECT message_position,discord_message_id FROM record_announcement_message WHERE announcement_id=? ORDER BY message_position",(message,row)->new RecordAnnouncementMessage(message.getInt(1),message.getLong(2)),id);
        RecordAnnouncementRegistration registration=new RecordAnnouncementRegistration(key,new RecordAnnouncementSubject(RecordAnnouncementSubject.Type.valueOf(rs.getString("subject_type")),rs.getString("subject_key")),RecordAnnouncementPhase.valueOf(rs.getString("announcement_phase")),RecordAnnouncementProjection.valueOf(rs.getString("desired_projection")),rs.getString("renderer_version"),rs.getString("content_fingerprint"),events);
        String category=rs.getString("failure_category"); return new RecordAnnouncementSnapshot(registration,RecordWorkState.valueOf(rs.getString("delivery_state")),Optional.ofNullable(rs.getObject("claim_token",UUID.class)),Optional.ofNullable(RecordJdbcMapping.instant(rs,"claim_until")),rs.getInt("attempt_count"),Optional.ofNullable(RecordJdbcMapping.instant(rs,"next_retry_at")),category==null?Optional.empty():Optional.of(new RecordWorkFailure(RecordWorkFailureCategory.valueOf(category),rs.getString("safe_error"))),messages,Optional.ofNullable(RecordJdbcMapping.instant(rs,"published_at")),Optional.ofNullable(RecordJdbcMapping.instant(rs,"changed_at")),Optional.ofNullable(RecordJdbcMapping.instant(rs,"deleted_at")),Optional.ofNullable(RecordJdbcMapping.instant(rs,"externally_removed_at")),RecordJdbcMapping.instant(rs,"created_at"),RecordJdbcMapping.instant(rs,"updated_at"));
    }
}
