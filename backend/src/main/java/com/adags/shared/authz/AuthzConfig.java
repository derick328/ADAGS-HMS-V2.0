package com.adags.shared.authz;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AuthzConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public AuthorizationService authorizationService(
            RoleAssignmentPort roleAssignmentPort,
            ScopeHierarchyPort scopeHierarchyPort,
            CareRelationshipPort careRelationshipPort,
            AuditRecorder auditRecorder,
            Clock clock
    ) {
        return new AuthorizationService(roleAssignmentPort, scopeHierarchyPort, careRelationshipPort, auditRecorder, clock);
    }
}
