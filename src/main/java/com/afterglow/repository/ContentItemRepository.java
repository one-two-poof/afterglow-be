package com.afterglow.repository;

import com.afterglow.domain.ContentItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentItemRepository extends JpaRepository<ContentItem, Long> {

    Optional<ContentItem> findByNotionPageId(String notionPageId);

    List<ContentItem> findByArchivedFalseOrderByNotionLastEditedAtDesc();

    List<ContentItem> findAllByOrderByNotionLastEditedAtDesc();
}
