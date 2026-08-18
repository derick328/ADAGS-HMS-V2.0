package com.adags.modules.inventory.infrastructure;

import com.adags.modules.inventory.domain.LabStockRequest;
import com.adags.modules.inventory.domain.LabStockRequestRepository;
import com.adags.modules.inventory.domain.LabStockRequestStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcLabStockRequestRepository implements LabStockRequestRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, item_id, requested_quantity, released_quantity, requesting_store_id, requested_by,
                   requested_at, status, response_notes, reviewed_by, reviewed_at
            FROM lab_stock_request
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcLabStockRequestRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public LabStockRequest insert(LabStockRequest request) {
        String sql = """
                INSERT INTO lab_stock_request (id, item_id, requested_quantity, requesting_store_id, requested_by)
                VALUES (COALESCE(:id, gen_random_uuid()), :itemId, :requestedQuantity, :requestingStoreId, :requestedBy)
                RETURNING id, requested_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", request.id(), Types.OTHER)
                .addValue("itemId", request.itemId())
                .addValue("requestedQuantity", request.requestedQuantity())
                .addValue("requestingStoreId", request.requestingStoreId())
                .addValue("requestedBy", request.requestedBy());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new LabStockRequest(
                (UUID) rs.getObject("id"), request.itemId(), request.requestedQuantity(), 0,
                request.requestingStoreId(), request.requestedBy(), rs.getTimestamp("requested_at").toInstant(),
                LabStockRequestStatus.PENDING, null, null, null));
    }

    @Override
    public Optional<LabStockRequest> findById(UUID id) {
        List<LabStockRequest> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id", new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public LabStockRequest update(LabStockRequest request) {
        String sql = """
                UPDATE lab_stock_request
                SET released_quantity = :releasedQuantity, status = :status, response_notes = :responseNotes,
                    reviewed_by = :reviewedBy, reviewed_at = :reviewedAt
                WHERE id = :id
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", request.id())
                .addValue("releasedQuantity", request.releasedQuantity())
                .addValue("status", request.status().name())
                .addValue("responseNotes", request.responseNotes(), Types.VARCHAR)
                .addValue("reviewedBy", request.reviewedBy(), Types.OTHER)
                .addValue("reviewedAt", request.reviewedAt() == null ? null : Timestamp.from(request.reviewedAt())));
        return request;
    }

    private LabStockRequest mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp reviewedAt = rs.getTimestamp("reviewed_at");
        return new LabStockRequest(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("item_id"),
                rs.getInt("requested_quantity"),
                rs.getInt("released_quantity"),
                (UUID) rs.getObject("requesting_store_id"),
                (UUID) rs.getObject("requested_by"),
                rs.getTimestamp("requested_at").toInstant(),
                LabStockRequestStatus.valueOf(rs.getString("status")),
                rs.getString("response_notes"),
                (UUID) rs.getObject("reviewed_by"),
                reviewedAt == null ? null : reviewedAt.toInstant());
    }
}
