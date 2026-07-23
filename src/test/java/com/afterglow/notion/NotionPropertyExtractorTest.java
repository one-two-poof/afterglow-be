package com.afterglow.notion;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class NotionPropertyExtractorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void extractsTitleAndSelect() throws Exception {
        String json =
                """
                {
                  "Name": {
                    "type": "title",
                    "title": [{ "plain_text": "Hello Notion" }]
                  },
                  "Status": {
                    "type": "select",
                    "select": { "name": "Published" }
                  }
                }
                """;

        var properties = objectMapper.readTree(json);

        assertThat(NotionPropertyExtractor.extractTitle(properties, "Name")).isEqualTo("Hello Notion");
        assertThat(NotionPropertyExtractor.extractSelectName(properties, "Status")).isEqualTo("Published");

        var all = NotionPropertyExtractor.extractAllAsMap(properties);
        assertThat(all).containsEntry("Name", "Hello Notion");
        assertThat(all).containsEntry("Status", "Published");
    }
}
