package io.cockroachdb.workload.ledger.repository;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import io.cockroachdb.workload.Profiles;

@Repository
@Profiles.Ledger
public class JdbcMetadataRepository {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<String> getRegions() {
        return jdbcTemplate.queryForList("select region from [show regions]", String.class);
    }

    public String getGatewayRegion() {
        return jdbcTemplate
                .queryForObject("SELECT gateway_region()", String.class);
    }
}
