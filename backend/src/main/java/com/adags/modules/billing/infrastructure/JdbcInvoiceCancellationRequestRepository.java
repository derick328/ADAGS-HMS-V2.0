package com.adags.modules.billing.infrastructure;

import com.adags.modules.billing.domain.CancellationRequestStatus;
import com.adags.modules.billing.domain.InvoiceAlreadyHasPendingCancellationException;
import com.adags.modules.billing.domain.InvoiceCancellationRequest;
import com.adags.modules.billing.domain.InvoiceCancellationRequestRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcInvoiceCancellationRequestRepository implements InvoiceCancellationRequestRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, invoice_id, requested_by, reason, requested_at, status, reviewed_by, reviewed_at,
                   review_note, supersedes_request_id
            FROM invoice_cancellation_request
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcInvoiceCancellationRequestRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public InvoiceCancellationRequest insert(InvoiceCancellationRequest request) {
        String sql = """
                INSERT INTO invoice_cancellation_request (invoice_id, requested_by, reason, supersedes_request_id)
                VALUES (:invoiceId, :requestedBy, :reason, :supersedesRequestId)
                RETURNING id, requested_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("invoiceId", request.invoiceId())
                .addValue("requestedBy", request.requestedBy())
                .addValue("reason", request.reason())
                .addValue("supersedesRequestId", request.supersedesRequestId());

        try {
            return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new InvoiceCancellationRequest(
                    (UUID) rs.getObject("id"), request.invoiceId(), request.requestedBy(), request.reason(),
                    rs.getTimestamp("requested_at").toInstant(), CancellationRequestStatus.PENDING, null, null, null,
                    request.supersedesRequestId()));
        } catch (DuplicateKeyException raced) {
            // The partial unique index (idx_invoice_cancellation_request_one_pending)
            // lost the race to a concurrent request against the same invoice.
            throw new InvoiceAlreadyHasPendingCancellationException(request.invoiceId());
        }
    }

    @Override
    public Optional<InvoiceCancellationRequest> findById(UUID id) {
        List<InvoiceCancellationRequest> results = jdbcTemplate.query(SELECT_COLUMNS + " WHERE id = :id",
                new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<InvoiceCancellationRequest> findPending() {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE status = 'PENDING' ORDER BY requested_at ASC",
                new MapSqlParameterSource(), this::mapRow);
    }

    @Override
    public InvoiceCancellationRequest decide(UUID id, CancellationRequestStatus status, UUID reviewedBy, String reviewNote) {
        jdbcTemplate.update("""
                        UPDATE invoice_cancellation_request
                        SET status = :status, reviewed_by = :reviewedBy, reviewed_at = now(), review_note = :reviewNote
                        WHERE id = :id
                        """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("status", status.name())
                        .addValue("reviewedBy", reviewedBy)
                        .addValue("reviewNote", reviewNote));
        return findById(id).orElseThrow();
    }

    private InvoiceCancellationRequest mapRow(ResultSet rs, int rowNum) throws SQLException {
        Object reviewedBy = rs.getObject("reviewed_by");
        Timestamp reviewedAt = rs.getTimestamp("reviewed_at");
        Object supersedesRequestId = rs.getObject("supersedes_request_id");
        return new InvoiceCancellationRequest(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("invoice_id"),
                (UUID) rs.getObject("requested_by"),
                rs.getString("reason"),
                rs.getTimestamp("requested_at").toInstant(),
                CancellationRequestStatus.valueOf(rs.getString("status")),
                reviewedBy == null ? null : (UUID) reviewedBy,
                reviewedAt == null ? null : reviewedAt.toInstant(),
                rs.getString("review_note"),
                supersedesRequestId == null ? null : (UUID) supersedesRequestId);
    }
}
