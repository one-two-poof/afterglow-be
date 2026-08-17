package com.afterglow.web;

import com.afterglow.config.NotionProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Health", description = "서버 상태 확인용 헬스체크. 인증 불필요")
public class HealthController {

    private final NotionProperties notionProperties;

    public HealthController(NotionProperties notionProperties) {
        this.notionProperties = notionProperties;
    }

    @Operation(
            summary = "헬스체크",
            description = "배포 파이프라인·모니터링이 서버가 떠 있는지 확인하는 용도. notionConfigured로 Notion 연동 설정 여부도 같이 반환.")
    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "notionConfigured", notionProperties.isConfigured());
    }
}
