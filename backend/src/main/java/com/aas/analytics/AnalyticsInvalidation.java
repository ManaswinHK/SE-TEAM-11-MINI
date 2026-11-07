package com.aas.analytics;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;

/** JPA listener on the entities the dashboard is computed from: a write clears the analytics cache. */
public class AnalyticsInvalidation {

    @PostPersist
    @PostUpdate
    @PostRemove
    void changed(Object entity) {
        AnalyticsCache.invalidateAll();
    }
}
