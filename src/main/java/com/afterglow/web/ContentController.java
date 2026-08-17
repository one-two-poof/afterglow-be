package com.afterglow.web;

import com.afterglow.service.ContentService;
import com.afterglow.web.dto.ContentItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/items")
@Tag(name = "Content", description = "Notion에서 동기화한 콘텐츠 아이템 조회. 인증 불필요")
public class ContentController {

    private final ContentService contentService;

    public ContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    @Operation(summary = "콘텐츠 아이템 목록", description = "로컬 DB에 동기화된 Notion 콘텐츠 목록을 조회한다.")
    @GetMapping
    public List<ContentItemResponse> list(
            @Parameter(description = "true면 보관 처리된 항목도 포함") @RequestParam(defaultValue = "false") boolean includeArchived) {
        return contentService.listFromDatabase(includeArchived);
    }

    @Operation(
            summary = "콘텐츠 아이템 상세",
            description = "notionPageId로 조회. fresh=true면 로컬 DB를 거치지 않고 Notion에서 바로 최신 값을 가져온다.")
    @GetMapping("/{notionPageId}")
    public ResponseEntity<ContentItemResponse> get(
            @PathVariable String notionPageId,
            @Parameter(description = "true면 Notion에서 실시간으로 가져온다 (로컬 DB 캐시 무시)")
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
