package com.bjarne.datingrecommendationsuserservice.search;

import com.bjarne.datingrecommendationsuserservice.service.UserService;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runs after Solr setup, postal code import and seeders: locates users that have no coordinates yet
 * (accounts from before Phase 1) and optionally rebuilds the index. There is no API for a reindex;
 * enable recommender.solr.reindex-on-startup and restart instead.
 */
@Component
@Order(40)
public class SearchIndexInitializer implements ApplicationRunner {

    private final UserService userService;
    private final SearchIndexService searchIndexService;
    private final boolean reindexOnStartup;

    public SearchIndexInitializer(UserService userService,
                                  SearchIndexService searchIndexService,
                                  @Value("${recommender.solr.reindex-on-startup:false}") boolean reindexOnStartup) {
        this.userService = userService;
        this.searchIndexService = searchIndexService;
        this.reindexOnStartup = reindexOnStartup;
    }

    @Override
    public void run(@NonNull ApplicationArguments args) {
        userService.backfillMissingCoordinates();

        if (reindexOnStartup) {
            searchIndexService.reindexAll();
        }
    }
}
