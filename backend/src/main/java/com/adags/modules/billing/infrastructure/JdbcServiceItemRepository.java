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

    @Override
    public List<ServiceItem> search(String query) {
        // 07-doctor.md §3 "One component, four configurations": the
        // Combobox debounces to 200ms and hits this on every keystroke —
        // the ordering has to reflect the query so the top of the
        // dropdown actually moves as the doctor narrows what they typed.
        // Plain `ORDER BY name` kept "Angioplasty" / "Appendectomy" at
        // the top regardless of query (`app`, `bio`, `laparo` all
        // rendered A-names first because the sort ignored the search
        // term) — the user reads only the first few visible rows and
        // reasonably assumes the search is broken.
        //
        // Three tiers: name starts-with wins (that's what someone
        // typing "app" is looking for), then code starts-with, then
        // anywhere-in-name/code. Break ties by name so the order is
        // stable across identical relevance rows. LIMIT is a defence
        // against a two-letter query returning every seeded item.
        String prefix = query + "%";
        String contains = "%" + query + "%";
        return jdbcTemplate.query(
                """
                SELECT id, code, name, category, is_active FROM service_item
                WHERE is_active = true AND (name ILIKE :contains OR code ILIKE :contains)
                ORDER BY
                    CASE
                        WHEN name ILIKE :prefix THEN 0
                        WHEN code ILIKE :prefix THEN 1
                        ELSE 2
                    END,
                    name
                LIMIT 50
                """,
                new MapSqlParameterSource()
                        .addValue("prefix", prefix)
                        .addValue("contains", contains),
                this::mapRow);
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
