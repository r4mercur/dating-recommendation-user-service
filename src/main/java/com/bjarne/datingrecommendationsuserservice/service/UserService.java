package com.bjarne.datingrecommendationsuserservice.service;

import com.bjarne.datingrecommendationsuserservice.dto.UserRequest;
import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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

    public User save(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
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
