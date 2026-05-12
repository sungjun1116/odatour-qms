package com.odatour.waiting.repository;

import com.odatour.waiting.domain.WaitingEntry;
import com.odatour.waiting.domain.WaitingStatus;
import com.odatour.waiting.service.WaitingNotFoundException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class WaitingRepository {

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;

    public WaitingRepository(JdbcClient jdbcClient, JdbcTemplate jdbcTemplate) {
        this.jdbcClient = jdbcClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    public WaitingEntry save(String phoneNumber, boolean consentAgreed, WaitingStatus status, LocalDateTime now) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    insert into waiting_entry (
                        phone_number,
                        consent_agreed,
                        status,
                        created_at,
                        updated_at
                    )
                    values (?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setString(1, phoneNumber);
            statement.setBoolean(2, consentAgreed);
            statement.setString(3, status.name());
            statement.setObject(4, now);
            statement.setObject(5, now);
            return statement;
        }, keyHolder);

        Long id = keyHolder.getKey().longValue();

        return findById(id).orElseThrow(() -> new WaitingNotFoundException(id));
    }

    public Optional<WaitingEntry> findById(Long id) {
        return jdbcClient.sql("""
                        select *
                        from waiting_entry
                        where id = :id
                        """)
                .param("id", id)
                .query(this::map)
                .optional();
    }

    public Optional<WaitingEntry> findByIdForUpdate(Long id) {
        return jdbcClient.sql("""
                        select *
                        from waiting_entry
                        where id = :id
                        for update
                        """)
                .param("id", id)
                .query(this::map)
                .optional();
    }

    public Optional<WaitingEntry> findFirstByPhoneNumberAndStatuses(String phoneNumber, Collection<WaitingStatus> statuses) {
        return jdbcClient.sql("""
                        select *
                        from waiting_entry
                        where phone_number = :phoneNumber
                          and status in (:statuses)
                        order by created_at, id
                        limit 1
                        """)
                .param("phoneNumber", phoneNumber)
                .param("statuses", statuses.stream().map(WaitingStatus::name).toList())
                .query(this::map)
                .optional();
    }

    public List<WaitingEntry> findByStatuses(Collection<WaitingStatus> statuses) {
        return jdbcClient.sql("""
                        select *
                        from waiting_entry
                        where status in (:statuses)
                        order by created_at, id
                        """)
                .param("statuses", statuses.stream().map(WaitingStatus::name).toList())
                .query(this::map)
                .list();
    }

    public List<WaitingEntry> findEntered() {
        return jdbcClient.sql("""
                        select *
                        from waiting_entry
                        where status = :status
                        order by entered_at desc, created_at desc, id desc
                        """)
                .param("status", WaitingStatus.ENTERED.name())
                .query(this::map)
                .list();
    }

    public List<WaitingEntry> findCompleted() {
        return jdbcClient.sql("""
                        select *
                        from waiting_entry
                        where status in (:statuses)
                        order by coalesce(entered_at, no_show_at, updated_at) desc, created_at desc, id desc
                        """)
                .param("statuses", List.of(WaitingStatus.ENTERED.name(), WaitingStatus.NO_SHOWED.name()))
                .query(this::map)
                .list();
    }

    public int updateStatus(Long id, Collection<WaitingStatus> expectedStatuses, WaitingStatus nextStatus,
                            LocalDateTime now) {
        String timestampColumn = switch (nextStatus) {
            case ARRIVED -> "arrived_at";
            case ENTERED -> "entered_at";
            case NO_SHOWED -> "no_show_at";
            case CANCELED -> "canceled_at";
            default -> throw new IllegalArgumentException("Unsupported status transition: " + nextStatus);
        };

        return jdbcClient.sql("""
                        update waiting_entry
                        set status = :nextStatus,
                            updated_at = :updatedAt,
                            %s = :eventAt
                        where id = :id
                          and status in (:expectedStatuses)
                        """.formatted(timestampColumn))
                .param("nextStatus", nextStatus.name())
                .param("updatedAt", now)
                .param("eventAt", now)
                .param("id", id)
                .param("expectedStatuses", expectedStatuses.stream().map(WaitingStatus::name).toList())
                .update();
    }

    public int markNotified(Long id, LocalDateTime now) {
        return jdbcClient.sql("""
                        update waiting_entry
                        set status = :nextStatus,
                            notified_at = :notifiedAt,
                            updated_at = :updatedAt
                        where id = :id
                          and status = :expectedStatus
                          and notified_at is null
                        """)
                .param("nextStatus", WaitingStatus.CALLED.name())
                .param("notifiedAt", now)
                .param("updatedAt", now)
                .param("id", id)
                .param("expectedStatus", WaitingStatus.WAITING.name())
                .update();
    }

    public int revertStatus(Long id, WaitingStatus expectedStatus, WaitingStatus previousStatus, LocalDateTime now) {
        String clearedColumns = switch (previousStatus) {
            case WAITING -> """
                            notified_at = null,
                            arrived_at = null,
                            entered_at = null,
                            no_show_at = null
                    """;
            case CALLED -> """
                            arrived_at = null,
                            entered_at = null,
                            no_show_at = null
                    """;
            case ARRIVED -> """
                            entered_at = null,
                            no_show_at = null
                    """;
            default -> throw new IllegalArgumentException("Unsupported revert target status: " + previousStatus);
        };

        return jdbcClient.sql("""
                        update waiting_entry
                        set status = :previousStatus,
                            updated_at = :updatedAt,
                            %s
                        where id = :id
                          and status = :expectedStatus
                        """.formatted(clearedColumns))
                .param("previousStatus", previousStatus.name())
                .param("updatedAt", now)
                .param("id", id)
                .param("expectedStatus", expectedStatus.name())
                .update();
    }

    private WaitingEntry map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new WaitingEntry(
                rs.getLong("id"),
                rs.getString("phone_number"),
                rs.getBoolean("consent_agreed"),
                WaitingStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("notified_at") == null ? null : rs.getTimestamp("notified_at").toLocalDateTime(),
                rs.getTimestamp("arrived_at") == null ? null : rs.getTimestamp("arrived_at").toLocalDateTime(),
                rs.getTimestamp("entered_at") == null ? null : rs.getTimestamp("entered_at").toLocalDateTime(),
                rs.getTimestamp("no_show_at") == null ? null : rs.getTimestamp("no_show_at").toLocalDateTime(),
                rs.getTimestamp("canceled_at") == null ? null : rs.getTimestamp("canceled_at").toLocalDateTime(),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }
}
