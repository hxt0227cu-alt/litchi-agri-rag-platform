package com.litchi.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemoContentServiceTest {

    private final DemoContentService demoContentService = new DemoContentService();

    @Test
    void loadsInlineAndAuthorityDemoDocuments() {
        List<DemoContentService.DemoDocument> documents = demoContentService.getDemoDocuments();

        assertThat(documents).hasSizeGreaterThanOrEqualTo(13);
        assertThat(documents)
                .extracting(DemoContentService.DemoDocument::fileName)
                .doesNotHaveDuplicates()
                .anyMatch(fileName -> fileName.contains("农业农村部"));
        assertThat(documents)
                .filteredOn(document -> document.fileName().contains("2024年全国荔枝重大病虫害发生趋势预报"))
                .singleElement()
                .satisfies(document -> {
                    assertThat(document.title()).contains("2024年全国荔枝重大病虫害发生趋势预报");
                    assertThat(document.summary()).contains("偏重发生趋势");
                    assertThat(document.content()).contains("可用于问答的知识点");
                });
    }
}
