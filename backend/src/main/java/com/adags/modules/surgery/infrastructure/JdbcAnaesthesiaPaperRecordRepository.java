package com.adags.modules.surgery.infrastructure;

import com.adags.modules.surgery.domain.AnaesthesiaPaperRecord;
import com.adags.modules.surgery.domain.AnaesthesiaPaperRecordRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcAnaesthesiaPaperRecordRepository implements AnaesthesiaPaperRecordRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, surgery_order_id, no_anaesthesia, anaesthetist_name_text, anaesthesia_type_text,
                   paper_record_reference, recorded_by, recorded_at
            FROM anaesthesia_paper_record
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcAnaesthesiaPaperRecordRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public AnaesthesiaPaperRecord insert(AnaesthesiaPaperRecord record) {
        String sql = """
                INSERT INTO anaesthesia_paper_record (
                    surgery_order_id, no_anaesthesia, anaesthetist_name_text, anaesthesia_type_text,
                    paper_record_reference, recorded_by)
                VALUES (:surgeryOrderId, :noAnaesthesia, :anaesthetistNameText, :anaesthesiaTypeText,
                        :paperRecordReference, :recordedBy)
                RETURNING id, recorded_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("surgeryOrderId", record.surgeryOrderId())
                .addValue("noAnaesthesia", record.noAnaesthesia())
                .addValue("anaesthetistNameText", record.anaesthetistNameText())
                .addValue("anaesthesiaTypeText", record.anaesthesiaTypeText())
                .addValue("paperRecordReference", record.paperRecordReference())
                .addValue("recordedBy", record.recordedBy());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new AnaesthesiaPaperRecord(
                (UUID) rs.getObject("id"), record.surgeryOrderId(), record.noAnaesthesia(),
                record.anaesthetistNameText(), record.anaesthesiaTypeText(), record.paperRecordReference(),
                record.recordedBy(), rs.getTimestamp("recorded_at").toInstant()));
    }

    @Override
    public List<AnaesthesiaPaperRecord> findBySurgeryOrderId(UUID surgeryOrderId) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE surgery_order_id = :surgeryOrderId ORDER BY recorded_at",
                new MapSqlParameterSource("surgeryOrderId", surgeryOrderId), this::mapRow);
    }

    @Override
    public boolean existsForSurgeryOrder(UUID surgeryOrderId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM anaesthesia_paper_record WHERE surgery_order_id = :surgeryOrderId",
                new MapSqlParameterSource("surgeryOrderId", surgeryOrderId), Integer.class);
        return count != null && count > 0;
    }

    private AnaesthesiaPaperRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AnaesthesiaPaperRecord(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("surgery_order_id"),
                rs.getBoolean("no_anaesthesia"),
                rs.getString("anaesthetist_name_text"),
                rs.getString("anaesthesia_type_text"),
                rs.getString("paper_record_reference"),
                (UUID) rs.getObject("recorded_by"),
                rs.getTimestamp("recorded_at").toInstant());
    }
}
