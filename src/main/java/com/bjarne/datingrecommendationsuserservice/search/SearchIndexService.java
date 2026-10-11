package com.bjarne.datingrecommendationsuserservice.search;

import com.bjarne.datingrecommendationsuserservice.entity.Gender;
import com.bjarne.datingrecommendationsuserservice.entity.SearchPreferences;
import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.entity.UserStatus;
import com.bjarne.datingrecommendationsuserservice.repository.UserRepository;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

/**
 * Keeps the Solr collection in sync with the users. The Solr id is the referenceId, so indexing is idempotent.
 * Only active users are searchable; everybody else is removed from the index.
 */
@Service
public class SearchIndexService {

    static final String ID = "id";
    static final String STATUS = "status";
    static final String GENDER = "gender";
    static final String SEEKING_GENDERS = "seekingGenders";
    static final String BIRTH_DATE = "birthDate";
    static final String MIN_AGE = "minAge";
    static final String MAX_AGE = "maxAge";
    static final String MAX_DISTANCE_KM = "maxDistanceKm";
    static final String LOCATION = "location";
    static final String INTERESTS = "interests";
    static final String HOBBIES = "hobbies";

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchIndexService.class);
    private static final int COMMIT_WITHIN_MS = 1000;
    private static final int REINDEX_PAGE_SIZE = 500;

    private final SolrClient solrClient;
    private final UserRepository userRepository;
    private final boolean isSolrEnabled;

    public SearchIndexService(SolrClient solrClient,
                              UserRepository userRepository,
                              @Value("${recommender.solr.enabled}") boolean isSolrEnabled) {
        this.solrClient = solrClient;
        this.userRepository = userRepository;
        this.isSolrEnabled = isSolrEnabled;
    }

    public void index(User user) {
        if (!isSolrEnabled) {
            return;
        }

        try {
            if (user.getStatus() == UserStatus.ACTIVE) {
                solrClient.add(toDocument(user), COMMIT_WITHIN_MS);
            } else {
                solrClient.deleteById(user.getReferenceId(), COMMIT_WITHIN_MS);
            }
        } catch (SolrServerException | IOException e) {
            throw new IllegalStateException("Failed to index user " + user.getReferenceId() + " in Solr", e);
        }
    }

    public void remove(String referenceId) {
        if (!isSolrEnabled) {
            return;
        }

        try {
            solrClient.deleteById(referenceId, COMMIT_WITHIN_MS);
        } catch (SolrServerException | IOException e) {
            throw new IllegalStateException("Failed to remove user " + referenceId + " from Solr", e);
        }
    }

    /**
     * Rebuilds the whole index from the database. Also removes documents of older index formats.
     */
    public int reindexAll() {
        if (!isSolrEnabled) {
            return 0;
        }

        try {
            solrClient.deleteByQuery("*:*");

            int indexed = 0;
            Page<User> page = userRepository.findAllByStatus(UserStatus.ACTIVE, PageRequest.of(0, REINDEX_PAGE_SIZE, Sort.by("id")));
            while (true) {
                List<SolrInputDocument> documents = page.getContent().stream().map(SearchIndexService::toDocument).toList();
                if (!documents.isEmpty()) {
                    solrClient.add(documents);
                    indexed += documents.size();
                }
                if (!page.hasNext()) {
                    break;
                }
                page = userRepository.findAllByStatus(UserStatus.ACTIVE, page.nextPageable());
            }

            solrClient.commit();
            LOGGER.info("Reindexed {} active users in Solr.", indexed);
            return indexed;
        } catch (SolrServerException | IOException e) {
            throw new IllegalStateException("Failed to reindex users in Solr", e);
        }
    }

    private static SolrInputDocument toDocument(User user) {
        SolrInputDocument document = new SolrInputDocument();
        document.addField(ID, user.getReferenceId());
        document.addField("referenceId", user.getReferenceId());
        document.addField("name", user.getName());
        document.addField(STATUS, user.getStatus().name());
        document.addField(INTERESTS, user.getInterests());
        document.addField(HOBBIES, user.getHobbies());

        if (user.getGender() != null) {
            document.addField(GENDER, user.getGender().name());
        }
        if (user.getBirthDate() != null) {
            document.addField(BIRTH_DATE, Date.from(user.getBirthDate().atStartOfDay(ZoneOffset.UTC).toInstant()));
        }
        if (user.getLatitude() != null && user.getLongitude() != null) {
            document.addField(LOCATION, user.getLatitude() + "," + user.getLongitude());
        }
        if (user.getAddress() != null) {
            document.addField("city", user.getAddress().getCity());
            document.addField("country", user.getAddress().getCountry());
        }

        SearchPreferences preferences = user.getPreferences() != null ? user.getPreferences() : SearchPreferences.defaults();
        document.addField(SEEKING_GENDERS, preferences.getSeekingGenders().stream().map(Gender::name).toList());
        document.addField(MIN_AGE, preferences.getMinAge());
        document.addField(MAX_AGE, preferences.getMaxAge());
        document.addField(MAX_DISTANCE_KM, preferences.getMaxDistanceKm());

        return document;
    }
}
