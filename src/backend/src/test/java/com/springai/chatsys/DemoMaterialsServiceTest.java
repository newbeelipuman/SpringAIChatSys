package com.springai.chatsys;

import com.springai.chatsys.dto.IngestMaterialsResponse;
import com.springai.chatsys.service.DemoMaterialsService;
import com.springai.chatsys.service.MilvusService;
import com.springai.chatsys.service.RetrievalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DemoMaterialsServiceTest {

    @Autowired
    private DemoMaterialsService demoMaterialsService;

    @Autowired
    private MilvusService milvusService;

    @Autowired
    private RetrievalService retrievalService;

    @Test
    void shouldListAndIngestDemoMaterials() {
        assertThat(demoMaterialsService.listMaterials()).isNotEmpty();

        milvusService.clear();
        IngestMaterialsResponse response = demoMaterialsService.ingestAll();

        assertThat(response.successCount()).isGreaterThan(0);
        assertThat(response.totalChunks()).isGreaterThan(0);
        assertThat(milvusService.count()).isGreaterThan(0);
        assertThat(retrievalService.search("前端页面需要展示哪些字段？", 1)).isNotEmpty();
        assertThat(retrievalService.search("实习", 1)).isEmpty();
    }
}
