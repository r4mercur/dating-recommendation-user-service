package com.bjarne.datingrecommendationsuserservice.service;

import com.bjarne.datingrecommendationsuserservice.dto.UserRequest;
import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.entity.UserStatus;
import com.bjarne.datingrecommendationsuserservice.repository.UserRepository;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrInputDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RestTemplate restTemplate;
    private final SolrClient solrClient;
    private final String recommendationServiceUrl;
    private final boolean isRecommendationServiceEnabled;
    private final boolean isSolrEnabled;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       RestTemplate restTemplate,
                       SolrClient solrClient,
                       @Value("${recommendation.service.url}") String recommendationServiceUrl,
                       @Value("${recommendation.service.enabled}") boolean isRecommendationServiceEnabled,
                       @Value("${recommender.solr.enabled}") boolean isSolrEnabled) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.restTemplate = restTemplate;
        this.solrClient = solrClient;
        this.recommendationServiceUrl = recommendationServiceUrl;
        this.isRecommendationServiceEnabled = isRecommendationServiceEnabled;
        this.isSolrEnabled = isSolrEnabled;
    }

    public User findByReferenceId(String referenceId) {
        return userRepository.findByReferenceId(referenceId);
    }

    public List<User> findByReferenceIds(List<String> references) {
        return userRepository.findAllByReferenceIdIn(references);
    }

    public User saveUserWithDTO(UserRequest userRequest) {
        User tempUser = userRepository.findById(userRequest.id())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userRequest.id()));
        String password = passwordEncoder.encode(userRequest.password());
        User user = tempUser.fromUserRequest(userRequest, password);

        return userRepository.save(user);
    }

    public User save(User user, boolean addNotToSearchIndex) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));

		if (user.getReferenceId() == null || user.getReferenceId().isEmpty()) {
			user.setReferenceId(UUID.randomUUID().toString());
		}

        if (user.getStatus() == UserStatus.ACTIVE && !addNotToSearchIndex && isRecommendationServiceEnabled) {
            // send request to recommendation service to create profile and store in elastic search
            restTemplate.postForEntity(recommendationServiceUrl + "/users/import/user", user, String.class);
        }

        if (isSolrEnabled) {
            try {
                SolrInputDocument solrDoc = createSolrDocument(user);
                solrClient.add(solrDoc);
            } catch (SolrServerException | IOException e) {
                throw new IllegalStateException("Failed to index user in Solr", e);
            }
        }

        return userRepository.save(user);
    }

    public void savePhoto(User user, String url) {
        user.setPhoto(url);
        userRepository.save(user);
    }

    public User authenticateUser(String email, String password) {
        User user = userRepository.findByEmail(email);
        if (user != null && passwordEncoder.matches(password, user.getPassword())) {
            return user;
        }
        return null;
    }

    public Integer countUsers() {
        return Math.toIntExact(userRepository.count());
    }

    private SolrInputDocument createSolrDocument(User user) {
        SolrInputDocument solrDoc = new SolrInputDocument();
        solrDoc.addField("referenceId", user.getReferenceId());
        solrDoc.addField("name", user.getName());
        solrDoc.addField("interests", user.getInterests());
        solrDoc.addField("hobbies", user.getHobbies());
        solrDoc.addField("age", user.getAge());
        solrDoc.addField("gender", user.getGender());
        solrDoc.addField("country", user.getAddress().getCountry());
        solrDoc.addField("city", user.getAddress().getCity());
        solrDoc.addField("street", user.getAddress().getStreet());
        solrDoc.addField("zipCode", user.getAddress().getZipCode());
        return solrDoc;
    }
}
