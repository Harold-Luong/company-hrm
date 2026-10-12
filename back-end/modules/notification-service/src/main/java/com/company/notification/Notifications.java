package com.company.notification;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class Notifications {
    private final JdbcTemplate jdbc;
    public Notifications(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Event(UUID eventId, String source, UUID requestId, long version, String requesterUserId,
            UUID employeeId, String actorUserId, String action, String occurredAt) {}
    public record Item(UUID id, long sequence, String source, UUID requestId, String action, Instant occurredAt, boolean read) {}
    public record Snapshot(long revision, long readCount, long unreadCount) {}
    public record Page(List<Item> content, Long nextBefore, long unreadCount) {}
    public record Actor(String userId, boolean reviewer) {}
    public static Actor actor(JwtAuthenticationToken token) {
        return new Actor(token.getName(), token.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_HR") || a.getAuthority().equals("ROLE_ADMIN")));
    }
    private static final String SCOPE = "(n.requester_user_id = ? OR ? = true)";
    private static final String VISIBLE = SCOPE + " AND n.actor_user_id <> ?";
    private Object[] args(Actor actor) { return new Object[]{actor.userId(), actor.reviewer(), actor.userId()}; }
    @Transactional
    public void accept(Event event) {
        if (event.eventId() == null || event.requestId() == null || event.employeeId() == null || event.version() < 0
                || !Set.of("LEAVE","ATTENDANCE").contains(event.source())
                || !Set.of("SUBMITTED","UPDATED","CANCELLED","APPROVED","REJECTED").contains(event.action())
                || event.requesterUserId() == null || event.requesterUserId().isBlank()
                || event.actorUserId() == null || event.actorUserId().isBlank()) throw new IllegalArgumentException("Invalid notification event");
        jdbc.update("""
            INSERT INTO notifications(event_id, source, request_id, request_version, requester_user_id,
                employee_id, actor_user_id, action, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
            """, event.eventId(), event.source(), event.requestId(), event.version(), event.requesterUserId(),
                event.employeeId(), event.actorUserId(), event.action(), java.sql.Timestamp.from(Instant.parse(event.occurredAt())));
    }
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page list(Actor actor, Long before) {
        long cursor = before == null ? Long.MAX_VALUE : before;
        var rows = jdbc.query("""
            SELECT n.*, EXISTS(SELECT 1 FROM notification_reads r WHERE r.event_id=n.event_id AND r.user_id=?) AS is_read
            FROM notifications n WHERE """ + VISIBLE + " AND n.sequence < ? ORDER BY n.sequence DESC LIMIT 21",
            (rs, i) -> new Item(rs.getObject("event_id", UUID.class), rs.getLong("sequence"), rs.getString("source"),
                rs.getObject("request_id", UUID.class), rs.getString("action"), rs.getTimestamp("occurred_at").toInstant(), rs.getBoolean("is_read")),
            actor.userId(), actor.userId(), actor.reviewer(), actor.userId(), cursor);
        boolean more = rows.size() > 20;
        var items = more ? rows.subList(0,20) : rows;
        return new Page(items, more ? items.getLast().sequence() : null, snapshot(actor).unreadCount());
    }
    public Snapshot snapshot(Actor actor) {
        // Revision includes own actions as well, so other tabs and reviewer counters refresh.
        long revision = jdbc.queryForObject("SELECT COALESCE(MAX(sequence),0) FROM notifications n WHERE " + SCOPE,
                Long.class, actor.userId(), actor.reviewer());
        var counts = jdbc.queryForObject("""
            SELECT COUNT(*) AS total, COUNT(r.event_id) AS seen FROM notifications n
            LEFT JOIN notification_reads r ON r.event_id=n.event_id AND r.user_id=?
            WHERE """ + VISIBLE, (rs,i) -> new long[]{rs.getLong("total"),rs.getLong("seen")},
            actor.userId(), actor.userId(), actor.reviewer(), actor.userId());
        return new Snapshot(revision, counts[1], counts[0] - counts[1]);
    }
    @Transactional
    public void read(UUID id, Actor actor) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM notifications n WHERE n.event_id=? AND " + VISIBLE + ")",
                Boolean.class, id, actor.userId(), actor.reviewer(), actor.userId())))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        jdbc.update("INSERT INTO notification_reads(event_id,user_id) VALUES (?,?) ON CONFLICT DO NOTHING", id, actor.userId());
    }
    @Transactional
    public void readAll(Actor actor, long through) {
        // Explicit boundary prevents marking a concurrently arriving notification read.
        jdbc.update("INSERT INTO notification_reads(event_id,user_id) SELECT n.event_id, ? FROM notifications n WHERE "
                + VISIBLE + " AND n.sequence <= ? ON CONFLICT DO NOTHING",
                actor.userId(), actor.userId(), actor.reviewer(), actor.userId(), through);
    }
}
