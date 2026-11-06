package com.adags.modules.inventory.infrastructure;

import com.adags.modules.inventory.domain.Store;
import com.adags.modules.inventory.domain.StoreRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcStoreRepository implements StoreRepository {

    private static final String SELECT_COLUMNS = "SELECT id, name, type, facility_id, active, created_at FROM store";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcStoreRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Store insert(Store store) {
        String sql = """
                INSERT INTO store (id, name, type, facility_id, active)
                VALUES (COALESCE(:id, gen_random_uuid()), :name, :type, :facilityId, :active)
                RETURNING id, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", store.id(), Types.OTHER)
                .addValue("name", store.name())
                .addValue("type", store.type())
                .addValue("facilityId", store.facilityId())
                .addValue("active", store.active());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new Store(
                (UUID) rs.getObject("id"), store.name(), store.type(), store.facilityId(), store.active(),
                rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public Optional<Store> findById(UUID id) {
        List<Store> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id", new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<Store> findAllActive() {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE active = TRUE ORDER BY name",
                new MapSqlParameterSource(), this::mapRow);
    }

    @Override
    public Optional<Store> findByFacilityIdAndType(UUID facilityId, String type) {
        List<Store> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE facility_id = :facilityId AND type = :type",
                new MapSqlParameterSource().addValue("facilityId", facilityId).addValue("type", type),
                this::mapRow);
        return results.stream().findFirst();
    }

    private Store mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Store((UUID) rs.getObject("id"), rs.getString("name"), rs.getString("type"),
                (UUID) rs.getObject("facility_id"), rs.getBoolean("active"), rs.getTimestamp("created_at").toInstant());
    }
}
