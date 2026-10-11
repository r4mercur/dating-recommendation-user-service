package com.bjarne.datingrecommendationsuserservice.rest;

import com.bjarne.datingrecommendationsuserservice.dto.TagResponse;
import com.bjarne.datingrecommendationsuserservice.entity.TagCategory;
import com.bjarne.datingrecommendationsuserservice.service.TagService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Catalog of interests and hobbies. Public, because the registration form needs it.
 */
@RestController
@RequestMapping("/api/tags")
public class TagResource {

    private final TagService tagService;

    public TagResource(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    public List<TagResponse> getTags(@RequestParam(value = "category", required = false) TagCategory category) {
        return tagService.getTags(category).stream()
                .map(TagResponse::from)
                .toList();
    }
}
