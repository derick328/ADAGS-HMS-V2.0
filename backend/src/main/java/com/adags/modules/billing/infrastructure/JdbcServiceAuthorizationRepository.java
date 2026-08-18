package com.adags.modules.billing.infrastructure;

import com.adags.modules.billing.domain.ServiceAuthorization;
import com.adags.modules.billing.domain.ServiceAuthorizationRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class JdbcServiceAuthorizationRepository implements ServiceAuthorizationRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcServiceAuthorizationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ServiceAuthorization insert(ServiceAuthorization authorization) {
        String sql = """
                INSERT INTO service_authorization (invoice_line_id, authorization_type, authorized_by, reference_id)
                VALUES (:invoiceLineId, :authorizationType, :authorizedBy, :referenceId)
                RETURNING id, authorized_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("invoiceLineId", authorization.invoiceLineId())
                .addValue("authorizationType", authorization.authorizationType().name())
                .addValue("authorizedBy", authorization.authorizedBy())
                .addValue("referenceId", authorization.referenceId());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new ServiceAuthorization(
                (UUID) rs.getObject("id"), authorization.invoiceLineId(), authorization.authorizationType(),
                rs.getTimestamp("authorized_at").toInstant(), authorization.authorizedBy(),
                authorization.referenceId()));
    }
}
