package com.adags.modules.billing.infrastructure;

import com.adags.modules.billing.domain.CatalogImportBatch;
import com.adags.modules.billing.domain.CatalogImportBatchRepository;
import com.adags.modules.billing.domain.CatalogImportStatus;
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
public class JdbcCatalogImportBatchRepository implements CatalogImportBatchRepository {

    private static final String COLS =
            "id, storage_key, original_filename, file_sha256, status, row_count, " +
            "new_item_count, price_change_count, metadata_change_count, unchanged_count, " +
            "uploaded_by, uploaded_at, applied_by, applied_at";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcCatalogImportBatchRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public CatalogImportBatch insert(CatalogImportBatch batch) {
        String sql = """
                INSERT INTO catalog_import_batch (
                    id, storage_key, original_filename, file_sha256, status, row_count,
                    new_item_count, price_change_count, metadata_change_count, unchanged_count,
                    uploaded_by, uploaded_at)
                VALUES (:id, :storageKey, :originalFilename, :sha256, :status, :rowCount,
                        :newItems, :priceChanges, :metadataChanges, :unchanged,
                        :uploadedBy, :uploadedAt)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", batch.id())
                .addValue("storageKey", batch.storageKey())
                .addValue("originalFilename", batch.originalFilename())
                .addValue("sha256", batch.fileSha256())
                .addValue("status", batch.status().name())
                .addValue("rowCount", batch.rowCount())
                .addValue("newItems", batch.newItemCount())
                .addValue("priceChanges", batch.priceChangeCount())
                .addValue("metadataChanges", batch.metadataChangeCount())
                .addValue("unchanged", batch.unchangedCount())
                .addValue("uploadedBy", batch.uploadedBy())
                .addValue("uploadedAt", Timestamp.from(batch.uploadedAt()));
        jdbcTemplate.update(sql, params);
        return batch;
    }

    @Override
    public Optional<CatalogImportBatch> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLS + " FROM catalog_import_batch WHERE id = :id",
                new MapSqlParameterSource("id", id),
                JdbcCatalogImportBatchRepository::mapRow).stream().findFirst();
    }

    @Override
    public Optional<CatalogImportBatch> findActiveByFileSha256(String sha256) {
        return jdbcTemplate.query(
                "SELECT " + COLS + " FROM catalog_import_batch WHERE file_sha256 = :sha AND status <> 'CANCELLED'",
                new MapSqlParameterSource("sha", sha256),
                JdbcCatalogImportBatchRepository::mapRow).stream().findFirst();
    }

    @Override
    public List<CatalogImportBatch> listRecent(int limit) {
        return jdbcTemplate.query(
                "SELECT " + COLS + " FROM catalog_import_batch ORDER BY uploaded_at DESC LIMIT :limit",
                new MapSqlParameterSource("limit", limit),
                JdbcCatalogImportBatchRepository::mapRow);
    }

    @Override
    public CatalogImportBatch markApplied(UUID batchId, UUID appliedBy, int newItemCount,
                                          int priceChangeCount, int metadataChangeCount, int unchangedCount) {
        String sql = """
                UPDATE catalog_import_batch
                   SET status = 'APPLIED',
                       applied_by = :appliedBy,
                       applied_at = now(),
                       new_item_count = :newItems,
                       price_change_count = :priceChanges,
                       metadata_change_count = :metadataChanges,
                       unchanged_count = :unchanged
                 WHERE id = :id AND status = 'PREVIEWED'
                """;
        int updated = jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", batchId)
                .addValue("appliedBy", appliedBy)
                .addValue("newItems", newItemCount)
                .addValue("priceChanges", priceChangeCount)
                .addValue("metadataChanges", metadataChangeCount)
                .addValue("unchanged", unchangedCount));
        if (updated == 0) {
            throw new IllegalStateException("Batch " + batchId + " could not be marked APPLIED (not in PREVIEWED state).");
        }
        return findById(batchId).orElseThrow();
    }

    @Override
    public CatalogImportBatch markCancelled(UUID batchId) {
        int updated = jdbcTemplate.update(
                "UPDATE catalog_import_batch SET status = 'CANCELLED' WHERE id = :id AND status = 'PREVIEWED'",
                new MapSqlParameterSource("id", batchId));
        if (updated == 0) {
            throw new IllegalStateException("Batch " + batchId + " could not be cancelled (not in PREVIEWED state).");
        }
        return findById(batchId).orElseThrow();
    }

    private static CatalogImportBatch mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp appliedAt = rs.getTimestamp("applied_at");
        return new CatalogImportBatch(
                (UUID) rs.getObject("id"),
                rs.getString("storage_key"),
                rs.getString("original_filename"),
                rs.getString("file_sha256"),
                CatalogImportStatus.valueOf(rs.getString("status")),
                rs.getInt("row_count"),
                rs.getInt("new_item_count"),
                rs.getInt("price_change_count"),
                rs.getInt("metadata_change_count"),
                rs.getInt("unchanged_count"),
                (UUID) rs.getObject("uploaded_by"),
                rs.getTimestamp("uploaded_at").toInstant(),
                (UUID) rs.getObject("applied_by"),
                appliedAt == null ? null : appliedAt.toInstant());
    }
}
