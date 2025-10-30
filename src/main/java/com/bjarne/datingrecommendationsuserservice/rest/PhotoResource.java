package com.bjarne.datingrecommendationsuserservice.rest;

import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.service.SupabaseStorageService;
import com.bjarne.datingrecommendationsuserservice.service.UserService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/photos")
public class PhotoResource {

    private final SupabaseStorageService supabaseStorageService;
    private final UserService userService;

    public PhotoResource(SupabaseStorageService supabaseStorageService, UserService userService) {
        this.supabaseStorageService = supabaseStorageService;
        this.userService = userService;
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

        String extension = getExtension(file.getOriginalFilename());
        String objectUrl = "avatars/" + referenceId + (extension != null ? "." + extension : "");

        // upload to supabase
        String url = supabaseStorageService.upload(null, objectUrl, file);
        userService.savePhoto(user, url);

        return ResponseEntity.ok(Map.of("url", url));
    }

    private String getExtension(String filename) {
        if (filename == null) return null;
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return null;
        return filename.substring(dot + 1);
    }
}
