package com.afterglow.web;

import com.afterglow.config.NotionProperties;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final NotionProperties notionProperties;

    public HealthController(NotionProperties notionProperties) {
        this.notionProperties = notionProperties;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "notionConfigured", notionProperties.isConfigured());
    }
}
