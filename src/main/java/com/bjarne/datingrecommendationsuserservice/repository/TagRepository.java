package com.bjarne.datingrecommendationsuserservice.repository;

import com.bjarne.datingrecommendationsuserservice.entity.Tag;
import com.bjarne.datingrecommendationsuserservice.entity.TagCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {
    List<Tag> findAllByOrderByCategoryAscLabelAsc();
    List<Tag> findAllByCategoryOrderByLabelAsc(TagCategory category);
}
