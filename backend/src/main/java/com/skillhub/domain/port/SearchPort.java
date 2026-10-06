package com.skillhub.domain.port;

import com.skillhub.application.dto.SearchQuery;
import com.skillhub.application.dto.SearchQueryResult;

public interface SearchPort {
    SearchQueryResult search(SearchQuery query);
}
