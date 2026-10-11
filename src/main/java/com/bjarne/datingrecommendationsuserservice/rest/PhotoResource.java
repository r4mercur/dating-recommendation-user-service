package com.bjarne.datingrecommendationsuserservice.rest;

import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.exception.BadRequestException;
import com.bjarne.datingrecommendationsuserservice.security.CurrentUser;
import com.bjarne.datingrecommendationsuserservice.service.ProfilePictureStorageService;
import com.bjarne.datingrecommendationsuserservice.service.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.Map;

@RestController
public class PhotoResource {

    private final UserService userService;
    private final ProfilePictureStorageService profilePictureStorageService;

    public PhotoResource(UserService userService, ProfilePictureStorageService profilePictureStorageService) {
        this.userService = userService;
        this.profilePictureStorageService = profilePictureStorageService;
    }

    @PostMapping(value = "/api/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> uploadAvatar(@CurrentUser String referenceId,
                                            @RequestPart("file") MultipartFile file) {
        if (file.isEmpty() || file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            throw new BadRequestException("Profile picture must be an image");
        }

        User user = userService.replacePhoto(referenceId, file);
        return Collections.singletonMap("url", profilePictureStorageService.presignedUrl(user.getPhotoKey()));
    }

    @GetMapping("/api/photos/{referenceId}")
    public ResponseEntity<Void> getAvatar(@PathVariable String referenceId) {
        String url = profilePictureStorageService.presignedUrl(userService.getVisibleProfile(referenceId).getPhotoKey());
        if (url == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, url).build();
    }
}
