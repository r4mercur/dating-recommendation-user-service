package com.bjarne.datingrecommendationsuserservice.service;

import com.bjarne.datingrecommendationsuserservice.entity.Tag;
import com.bjarne.datingrecommendationsuserservice.entity.TagCategory;
import com.bjarne.datingrecommendationsuserservice.exception.BadRequestException;
import com.bjarne.datingrecommendationsuserservice.repository.TagRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TagService {

    private final TagRepository tagRepository;

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    public List<Tag> getTags(TagCategory category) {
        return category == null
                ? tagRepository.findAllByOrderByCategoryAscLabelAsc()
                : tagRepository.findAllByCategoryOrderByLabelAsc(category);
    }

    /**
     * Normalizes the given values to catalog slugs ("Playing Video Games", "PLAYING_VIDEO_GAMES" -> "playing_video_games")
     * and rejects values that are not part of the catalog for this category.
     */
    public List<String> normalize(TagCategory category, List<String> values) {
        if (values == null) {
            return null;
        }

        Set<String> known = tagRepository.findAllByCategoryOrderByLabelAsc(category).stream()
                .map(Tag::getSlug)
                .collect(Collectors.toSet());

        List<String> slugs = values.stream()
                .filter(Objects::nonNull)
                .map(TagService::toSlug)
                .filter(slug -> !slug.isEmpty())
                .distinct()
                .toList();

        List<String> unknown = slugs.stream().filter(slug -> !known.contains(slug)).toList();
        if (!unknown.isEmpty()) {
            throw new BadRequestException("Unknown " + category.name().toLowerCase(Locale.ROOT) + " tags: " + unknown);
        }

        return slugs;
    }

    static String toSlug(String value) {
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s-]+", "_");
    }
}
