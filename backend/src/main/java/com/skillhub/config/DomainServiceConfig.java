package com.skillhub.config;

import com.skillhub.domain.port.TeamMembershipPort;
import com.skillhub.domain.service.AccessService;
import com.skillhub.domain.service.ArchiveService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceConfig {

    @Bean
    public AccessService accessService(TeamMembershipPort membership) {
        return new AccessService(membership);
    }

    @Bean
    public ArchiveService archiveService(
            @Value("${skillhub.upload.max-uncompressed-bytes:209715200}") long maxUncompressedBytes,
            @Value("${skillhub.upload.max-files:5000}") int maxFiles) {
        return new ArchiveService(maxUncompressedBytes, maxFiles);
    }
}
