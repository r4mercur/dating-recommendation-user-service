package com.bjarne.datingrecommendationsuserservice.search;

import com.bjarne.datingrecommendationsuserservice.dto.ProfileCard;
import com.bjarne.datingrecommendationsuserservice.dto.ProfileMapper;
import com.bjarne.datingrecommendationsuserservice.dto.RecommendationPage;
import com.bjarne.datingrecommendationsuserservice.interaction.BlockService;
import com.bjarne.datingrecommendationsuserservice.entity.Gender;
import com.bjarne.datingrecommendationsuserservice.entity.SearchPreferences;
import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.entity.UserStatus;
import com.bjarne.datingrecommendationsuserservice.interaction.ReactionService;
import com.bjarne.datingrecommendationsuserservice.profile.CommonalityService;
import com.bjarne.datingrecommendationsuserservice.service.UserService;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.params.CursorMarkParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.bjarne.datingrecommendationsuserservice.search.SearchIndexService.*;

/**
 * Recommendations from Solr. Hard filters apply in both directions (I fit their preferences and they fit mine):
 * gender, age and distance. Ranking: shared tags first (rare tags weigh more), then closer users.
 */
@Service
public class RecommendationService {

    private static final String DISTANCE = "distance";
    private static final float INTEREST_BOOST = 4f;
    private static final float HOBBY_BOOST = 3f;
    /** Distance factor: 1.0 at 0 km, 0.5 at 100 km, 0.33 at 200 km. */
    private static final String DISTANCE_BOOST = "recip(geodist(),1,100,100)";

    private final SolrClient solrClient;
    private final UserService userService;
    private final ReactionService reactionService;
    private final BlockService blockService;
    private final ProfileMapper profileMapper;
    private final CommonalityService commonalityService;
    private final boolean isSolrEnabled;

    public RecommendationService(SolrClient solrClient,
                                 UserService userService,
                                 ReactionService reactionService,
                                 BlockService blockService,
                                 ProfileMapper profileMapper,
                                 CommonalityService commonalityService,
                                 @Value("${recommender.solr.enabled}") boolean isSolrEnabled) {
        this.solrClient = solrClient;
        this.userService = userService;
        this.reactionService = reactionService;
        this.blockService = blockService;
        this.profileMapper = profileMapper;
        this.commonalityService = commonalityService;
        this.isSolrEnabled = isSolrEnabled;
    }

    public RecommendationPage getRecommendations(String referenceId, String cursor, int size) {
        if (!isSolrEnabled) {
            return new RecommendationPage(List.of(), null);
        }

        User viewer = userService.getByReferenceId(referenceId);
        String cursorMark = cursor == null || cursor.isBlank() ? CursorMarkParams.CURSOR_MARK_START : cursor;

        try {
            QueryResponse response = solrClient.query(buildQuery(viewer, cursorMark, size), SolrRequest.METHOD.POST);
            List<SolrDocument> documents = response.getResults();

            String nextCursor = response.getNextCursorMark();
            boolean hasMore = documents.size() == size && nextCursor != null && !nextCursor.equals(cursorMark);
            return new RecommendationPage(toCards(viewer, documents), hasMore ? nextCursor : null);
        } catch (SolrServerException | IOException e) {
            throw new IllegalStateException("Error querying Solr for recommendations", e);
        }
    }

    private SolrQuery buildQuery(User viewer, String cursorMark, int size) {
        SearchPreferences preferences = viewer.getPreferences() != null ? viewer.getPreferences() : SearchPreferences.defaults();

        SolrQuery query = new SolrQuery("*:*");
        query.set("defType", "edismax");
        query.setRows(size);
        query.set(CursorMarkParams.CURSOR_MARK_PARAM, cursorMark);
        query.addSort("score", SolrQuery.ORDER.desc);
        query.addSort(ID, SolrQuery.ORDER.asc);
        query.setFields(ID, "score");

        query.addFilterQuery(STATUS + ":" + UserStatus.ACTIVE.name());
        excludeKnownUsers(query, viewer);

        // I am looking for their gender, they are looking for mine
        query.addFilterQuery(GENDER + ":(" + preferences.getSeekingGenders().stream().map(Gender::name).sorted()
                .collect(Collectors.joining(" OR ")) + ")");
        if (viewer.getGender() != null) {
            query.addFilterQuery(SEEKING_GENDERS + ":" + viewer.getGender().name());
        }

        // their age fits my range, my age fits theirs
        LocalDate today = LocalDate.now();
        LocalDate youngestBirthDate = today.minusYears(preferences.getMinAge());
        LocalDate oldestBirthDate = today.minusYears(preferences.getMaxAge() + 1L).plusDays(1);
        query.addFilterQuery(BIRTH_DATE + ":[" + oldestBirthDate + "T00:00:00Z TO " + youngestBirthDate + "T00:00:00Z]");
        Integer viewerAge = viewer.getAge();
        if (viewerAge != null) {
            query.addFilterQuery(MIN_AGE + ":[* TO " + viewerAge + "]");
            query.addFilterQuery(MAX_AGE + ":[" + viewerAge + " TO *]");
        }

        // within my distance and theirs
        if (viewer.getLatitude() != null && viewer.getLongitude() != null) {
            query.set("sfield", LOCATION);
            query.set("pt", viewer.getLatitude() + "," + viewer.getLongitude());
            query.addFilterQuery("{!geofilt d=" + preferences.getMaxDistanceKm() + "}");
            query.addFilterQuery("{!frange u=0}sub(geodist()," + MAX_DISTANCE_KM + ")");
            query.set("boost", DISTANCE_BOOST);
            query.addField(DISTANCE + ":geodist()");
        }

        String tagBoosts = Stream.of(
                        tagClause(INTERESTS, viewer.getInterests(), INTEREST_BOOST),
                        tagClause(HOBBIES, viewer.getHobbies(), HOBBY_BOOST))
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
        if (!tagBoosts.isEmpty()) {
            query.set("bq", tagBoosts);
        }

        return query;
    }

    /**
     * Excludes the viewer, everybody the viewer already reacted to and blocks in both directions.
     */
    private void excludeKnownUsers(SolrQuery query, User viewer) {
        Set<String> excluded = new TreeSet<>();
        excluded.add(viewer.getReferenceId());
        excluded.addAll(reactionService.reactedTo(viewer));
        excluded.addAll(blockService.blockedEitherWay(viewer));

        query.addFilterQuery("{!bool must=$all must_not=$excluded}");
        query.set("all", "*:*");
        query.set("excluded", "{!terms f=" + ID + "}" + String.join(",", excluded));
    }

    private static String tagClause(String field, List<String> tags, float boost) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        return field + ":(" + tags.stream().map(ClientUtils::escapeQueryChars).collect(Collectors.joining(" OR ")) + ")^" + boost;
    }

    private List<ProfileCard> toCards(User viewer, List<SolrDocument> documents) {
        List<String> ids = documents.stream().map(document -> (String) document.getFieldValue(ID)).toList();
        Map<String, User> users = userService.findVisibleProfiles(ids).stream()
                .collect(Collectors.toMap(User::getReferenceId, Function.identity()));

        Map<String, CommonalityService.CardExtras> extras = commonalityService.forCards(viewer, users.values());

        return documents.stream()
                .filter(document -> users.containsKey((String) document.getFieldValue(ID)))
                .map(document -> {
                    User candidate = users.get((String) document.getFieldValue(ID));
                    Object distance = document.getFieldValue(DISTANCE);
                    Integer distanceKm = distance instanceof Number number ? (int) Math.round(number.doubleValue()) : null;
                    return profileMapper.toProfileCard(candidate, distanceKm, compatibility(viewer, candidate),
                            extras.get(candidate.getReferenceId()));
                })
                .toList();
    }

    /**
     * Jaccard similarity of all tags in percent: |shared| / |together|.
     */
    static int compatibility(User viewer, User candidate) {
        Set<String> mine = tags(viewer);
        Set<String> theirs = tags(candidate);
        if (mine.isEmpty() && theirs.isEmpty()) {
            return 0;
        }

        Set<String> shared = new HashSet<>(mine);
        shared.retainAll(theirs);
        Set<String> together = new HashSet<>(mine);
        together.addAll(theirs);
        return (int) Math.round(100.0 * shared.size() / together.size());
    }

    private static Set<String> tags(User user) {
        Set<String> tags = new HashSet<>();
        Optional.ofNullable(user.getInterests()).orElse(List.of()).forEach(tag -> tags.add("interest:" + tag));
        Optional.ofNullable(user.getHobbies()).orElse(List.of()).forEach(tag -> tags.add("hobby:" + tag));
        return tags;
    }
}
