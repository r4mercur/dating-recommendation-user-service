package com.bjarne.datingrecommendationsuserservice.service;

import com.bjarne.datingrecommendationsuserservice.dto.RecommendationResponse;
import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.repository.UserRepository;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class RecommendationService {
    private final SolrClient solrClient;
    private final UserRepository userRepository;
    private final String collectionName;

    public RecommendationService(SolrClient solrClient,
                                 UserRepository userRepository,
                                 @Value("${recommender.solr.collection}") String collectionName) {
        this.solrClient = solrClient;
        this.userRepository = userRepository;
        this.collectionName = collectionName;
    }

    public RecommendationResponse getRecommendations(String referenceId, int numberOfRecommendations) {
        User user = userRepository.findByReferenceId(referenceId);
        if (user == null) {
            return null;
        }

        List<String> interests = normalize(user.getInterests());
        List<String> hobbies = normalize(user.getHobbies());

        if (interests.isEmpty() && hobbies.isEmpty()) {
           return new RecommendationResponse(referenceId, Collections.emptyList());
        }

        String queryString = createSolrQuery(interests, hobbies);

        SolrQuery query = new SolrQuery(queryString);
        query.setRows(numberOfRecommendations);
        query.setFields("referenceId", "score");
        query.addFilterQuery("-referenceId:" + ClientUtils.escapeQueryChars(referenceId));

        try {
            QueryResponse response = solrClient.query(query);
            List<String> recommendedIds = response.getResults().stream()
                    .map(doc -> (String) doc.getFieldValue("referenceId"))
                    .filter(Objects::nonNull)
                    .toList();

            return new RecommendationResponse(referenceId, recommendedIds);
        } catch (SolrServerException |IOException e) {
            throw new RuntimeException("Error querying Solr for recommendations", e);
        }
    }

    private static @NonNull String createSolrQuery(List<String> interests, List<String> hobbies) {
        String interestsClause = interests.stream()
                .map(v -> "interests:\"" + ClientUtils.escapeQueryChars(v) + "\"")
                .collect(Collectors.joining(" OR "));

        String hobbiesClause = hobbies.stream()
                .map(v -> "hobbies:\"" + ClientUtils.escapeQueryChars(v) + "\"")
                .collect(Collectors.joining(" OR "));

        return Stream.of(
                        interestsClause.isBlank() ? null : "(" + interestsClause + ")^4",
                        hobbiesClause.isBlank() ? null : "(" + hobbiesClause + ")^3"
                )
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" OR "));
    }

    private List<String> normalize(List<String> values) {
        if (values == null) {
            return Collections.emptyList();
        }

        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }
}
