package com.adags.modules.inventory.infrastructure;

import com.adags.modules.inventory.domain.Supplier;
import com.adags.modules.inventory.domain.SupplierRepository;
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
public class JdbcSupplierRepository implements SupplierRepository {

    private static final String SELECT_COLUMNS = "SELECT id, code, name, active, created_at FROM supplier";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcSupplierRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Supplier insert(Supplier supplier) {
        String sql = """
                INSERT INTO supplier (id, code, name, active)
                VALUES (COALESCE(:id, gen_random_uuid()), :code, :name, :active)
                RETURNING id, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", supplier.id(), Types.OTHER)
                .addValue("code", supplier.code())
                .addValue("name", supplier.name())
                .addValue("active", supplier.active());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new Supplier(
                (UUID) rs.getObject("id"), supplier.code(), supplier.name(), supplier.active(),
                rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public Optional<Supplier> findById(UUID id) {
        List<Supplier> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id", new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    private Supplier mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Supplier((UUID) rs.getObject("id"), rs.getString("code"), rs.getString("name"),
                rs.getBoolean("active"), rs.getTimestamp("created_at").toInstant());
    }
}
