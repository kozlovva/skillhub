package com.skillhub.application.service;

import com.skillhub.application.dto.SearchQuery;
import com.skillhub.application.dto.SearchQueryResult;
import com.skillhub.application.dto.SortBy;
import com.skillhub.application.dto.SortOrder;
import com.skillhub.domain.model.User;
import com.skillhub.domain.port.SearchPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SearchUseCase {

    private final SearchPort searchPort;

    public SearchUseCase(SearchPort searchPort) {
        this.searchPort = searchPort;
    }

    @Transactional(readOnly = true)
    public SearchQueryResult search(String q, String type, String category,
                                    SortBy sortBy, SortOrder sortOrder,
                                    User viewer, int limit, int offset) {
        UUID userId = viewer == null
            ? UUID.nameUUIDFromBytes("anonymous".getBytes())
            : viewer.getId();
        boolean admin = viewer != null && viewer.isAdmin();
        return searchPort.search(new SearchQuery(q, type, category, userId, admin, limit, offset,
            sortBy, sortOrder));
    }
}
