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

    public int updateStatus(Long id, WaitingStatus expectedStatus1, WaitingStatus expectedStatus2,
                            WaitingStatus nextStatus, LocalDateTime now) {
        String timestampColumn = switch (nextStatus) {
            case ENTERED -> "entered_at";
            case NO_SHOW -> "no_show_at";
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
                .param("expectedStatuses", List.of(expectedStatus1.name(), expectedStatus2.name()))
                .update();
    }

    private WaitingEntry map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new WaitingEntry(
                rs.getLong("id"),
                rs.getString("phone_number"),
                rs.getBoolean("consent_agreed"),
                WaitingStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("notified_at") == null ? null : rs.getTimestamp("notified_at").toLocalDateTime(),
                rs.getTimestamp("entered_at") == null ? null : rs.getTimestamp("entered_at").toLocalDateTime(),
                rs.getTimestamp("no_show_at") == null ? null : rs.getTimestamp("no_show_at").toLocalDateTime(),
                rs.getTimestamp("canceled_at") == null ? null : rs.getTimestamp("canceled_at").toLocalDateTime(),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }
}
