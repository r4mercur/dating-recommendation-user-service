package com.bjarne.datingrecommendationsuserservice.rest;

import com.bjarne.datingrecommendationsuserservice.dto.UserRequest;
import com.bjarne.datingrecommendationsuserservice.dto.UserSearchRequest;
import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;


@RestController
@RequestMapping("/api/user")
public class UserResource {
    private final UserService userService;

    public UserResource(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public User getUser(@RequestParam("referenceId") String referenceId) {
        return userService.findByReferenceId(referenceId);
    }

    @PostMapping("/search")
    public List<User> getUsersByReferenceIds(@Valid @RequestBody UserSearchRequest references) {
        return userService.findByReferenceIds(references.referenceIds());
    }

	@PostMapping
	public User createUser(@Valid @RequestBody User user,
						   @RequestParam(value = "useDefaultPW", required = false) Optional<Boolean> defaultPW,
						   @RequestParam(value = "addNotToSearchIndex", required = false) Optional<Boolean> addNotToSearchIndex) {
		if (defaultPW.orElse(false)) {
			user.setPassword("password");
		}

		return userService.save(user, addNotToSearchIndex.orElse(false));
	}

    @PutMapping
    public User updateUser(@Valid @RequestBody UserRequest userRequest) {
        return userService.saveUserWithDTO(userRequest);
    }
}
