package com.bjarne.datingrecommendationsuserservice.service;

import com.bjarne.datingrecommendationsuserservice.dto.UserRequest;
import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.entity.UserStatus;
import com.bjarne.datingrecommendationsuserservice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RestTemplate restTemplate;
    private final String recommendationServiceUrl;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, RestTemplate restTemplate,
                       @Value("${recommendation.service.url}") String recommendationServiceUrl) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.restTemplate = restTemplate;
        this.recommendationServiceUrl = recommendationServiceUrl;
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
        user.setReferenceId(UUID.randomUUID().toString());
        if (user.getStatus() == UserStatus.ACTIVE && !addNotToSearchIndex) {
            // send request to recommendation service to create profile and store in elastic search
            restTemplate.postForEntity(recommendationServiceUrl + "/users/import/user", user, String.class);
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
}
