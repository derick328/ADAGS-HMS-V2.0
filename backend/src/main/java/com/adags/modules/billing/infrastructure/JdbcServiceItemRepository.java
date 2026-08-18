package com.adags.modules.billing.infrastructure;

import com.adags.modules.billing.domain.ServiceItem;
import com.adags.modules.billing.domain.ServiceItemCategory;
import com.adags.modules.billing.domain.ServiceItemRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcServiceItemRepository implements ServiceItemRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcServiceItemRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ServiceItem> findByCode(String code) {
        List<ServiceItem> results = jdbcTemplate.query(
                "SELECT id, code, name, category, is_active FROM service_item WHERE code = :code",
                new MapSqlParameterSource("code", code),
                this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public Optional<ServiceItem> findById(UUID id) {
        List<ServiceItem> results = jdbcTemplate.query(
                "SELECT id, code, name, category, is_active FROM service_item WHERE id = :id",
                new MapSqlParameterSource("id", id),
                this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public ServiceItem insert(ServiceItem item) {
        String sql = """
                INSERT INTO service_item (code, name, category, is_active)
                VALUES (:code, :name, :category, :isActive)
                RETURNING id
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("code", item.code())
                .addValue("name", item.name())
                .addValue("category", item.category().name())
                .addValue("isActive", item.isActive());
        UUID id = jdbcTemplate.queryForObject(sql, params, UUID.class);
        return new ServiceItem(id, item.code(), item.name(), item.category(), item.isActive());
    }

    private ServiceItem mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ServiceItem(
                (UUID) rs.getObject("id"),
                rs.getString("code"),
                rs.getString("name"),
                ServiceItemCategory.valueOf(rs.getString("category")),
                rs.getBoolean("is_active"));
    }
}
