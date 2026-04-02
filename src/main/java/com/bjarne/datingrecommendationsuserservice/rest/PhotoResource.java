package com.bjarne.datingrecommendationsuserservice.rest;

import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.service.ProfilePictureStorageService;
import com.bjarne.datingrecommendationsuserservice.service.UserService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/photos")
public class PhotoResource {

    private final UserService userService;
    private final ProfilePictureStorageService profilePictureStorageService;

    public PhotoResource(UserService userService, ProfilePictureStorageService profilePictureStorageService) {
        this.userService = userService;
        this.profilePictureStorageService = profilePictureStorageService;
    }

    @PostMapping(value = "/{referenceId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadAvatar(
            @PathVariable String referenceId,
            @RequestPart("file") MultipartFile file
    ) {
        User user = userService.findByReferenceId(referenceId);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        String url = profilePictureStorageService.upload(user, file);
        userService.savePhoto(user, url);

        return ResponseEntity.ok(Map.of("url", url));
    }

    @GetMapping("/{referenceId}")
    public ResponseEntity<byte[]> getAvatar(@PathVariable String referenceId) {
        User user = userService.findByReferenceId(referenceId);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.status(302).header("Location", user.getPhoto()).build();
    }
}
