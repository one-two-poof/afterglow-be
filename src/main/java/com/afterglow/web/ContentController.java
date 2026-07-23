package com.afterglow.web;

import com.afterglow.service.ContentService;
import com.afterglow.web.dto.ContentItemResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/items")
public class ContentController {

    private final ContentService contentService;

    public ContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    @GetMapping
    public List<ContentItemResponse> list(
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return contentService.listFromDatabase(includeArchived);
    }

    @GetMapping("/{notionPageId}")
    public ResponseEntity<ContentItemResponse> get(
            @PathVariable String notionPageId,
            @RequestParam(defaultValue = "false") boolean fresh) {
        if (fresh) {
            return ResponseEntity.ok(contentService.getFreshFromNotion(notionPageId));
        }
        return contentService
                .getFromDatabase(notionPageId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
