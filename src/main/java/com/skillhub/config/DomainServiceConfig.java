package com.skillhub.config;

import com.skillhub.domain.port.TeamMembershipPort;
import com.skillhub.domain.service.AccessService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceConfig {

    @Bean
    public AccessService accessService(TeamMembershipPort membership) {
        return new AccessService(membership);
    }
}
