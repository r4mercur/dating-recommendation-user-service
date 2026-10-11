package com.bjarne.datingrecommendationsuserservice.dto;

import com.bjarne.datingrecommendationsuserservice.entity.Tag;
import com.bjarne.datingrecommendationsuserservice.entity.TagCategory;

public record TagResponse(TagCategory category, String slug, String label) {

    public static TagResponse from(Tag tag) {
        return new TagResponse(tag.getCategory(), tag.getSlug(), tag.getLabel());
    }
}
