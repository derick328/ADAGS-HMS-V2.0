package com.adags.modules.billing.infrastructure;

import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.PriceList;
import com.adags.modules.billing.domain.PriceListRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcPriceListRepository implements PriceListRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcPriceListRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<PriceList> findByPayerCategory(PayerCategory payerCategory) {
        List<PriceList> results = jdbcTemplate.query(
                "SELECT id, payer_category, name, is_active FROM price_list WHERE payer_category = :payerCategory",
                new MapSqlParameterSource("payerCategory", payerCategory.name()),
                this::mapRow);
        return results.stream().findFirst();
    }

    private PriceList mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new PriceList(
                (UUID) rs.getObject("id"),
                PayerCategory.valueOf(rs.getString("payer_category")),
                rs.getString("name"),
                rs.getBoolean("is_active"));
    }
}
