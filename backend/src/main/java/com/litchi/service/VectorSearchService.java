package com.litchi.service;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchService {

    private static final String ID_FIELD = "id";
    private static final String VECTOR_FIELD = "vector";
    private static final List<String> OUTPUT_FIELDS = List.of("title", "content", "source", "page");

    private final MilvusClientV2 milvusClient;

    @Value("${milvus.collection-name:litchi_knowledge}")
    private String collectionName;

    @Value("${app.resilience.dependency-retry-delay-ms:300000}")
    private long dependencyRetryDelayMs;

    private volatile boolean collectionLoaded;
    private volatile long retryAfterEpochMs;

    public synchronized boolean isAvailable() {
        if (System.currentTimeMillis() < retryAfterEpochMs) {
            return false;
        }
        try {
            milvusClient.listCollections();
            retryAfterEpochMs = 0L;
            return true;
        } catch (Exception e) {
            markUnavailable();
            log.debug("Milvus availability check failed", e);
            return false;
        }
    }

    public synchronized boolean initCollection() {
        if (System.currentTimeMillis() < retryAfterEpochMs) {
            return false;
        }
        if (collectionLoaded) {
            return true;
        }
        try {
            HasCollectionReq hasCollectionReq = HasCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();

            boolean exists = milvusClient.hasCollection(hasCollectionReq);
            if (!exists) {
                CreateCollectionReq createCollectionReq = CreateCollectionReq.builder()
                        .collectionName(collectionName)
                        .primaryFieldName(ID_FIELD)
                        .idType(DataType.VarChar)
                        .maxLength(64)
                        .vectorFieldName(VECTOR_FIELD)
                        .metricType(IndexParam.MetricType.COSINE.name())
                        .autoID(false)
                        .enableDynamicField(true)
                        .dimension(1024)
                        .build();
                milvusClient.createCollection(createCollectionReq);

                IndexParam indexParam = IndexParam.builder()
                        .fieldName(VECTOR_FIELD)
                        .indexName(VECTOR_FIELD + "_idx")
                        .indexType(IndexParam.IndexType.AUTOINDEX)
                        .metricType(IndexParam.MetricType.COSINE)
                        .build();

                CreateIndexReq createIndexReq = CreateIndexReq.builder()
                        .collectionName(collectionName)
                        .indexParams(List.of(indexParam))
                        .build();
                milvusClient.createIndex(createIndexReq);

                log.info("Collection {} created successfully", collectionName);
            }

            if (!collectionLoaded) {
                LoadCollectionReq loadCollectionReq = LoadCollectionReq.builder()
                        .collectionName(collectionName)
                        .build();
                milvusClient.loadCollection(loadCollectionReq);
                collectionLoaded = true;
            }
            return true;
        } catch (Exception e) {
            markUnavailable();
            log.error("Failed to init collection", e);
            return false;
        }
    }

    public List<SearchResult> search(float[] vector, int topK) {
        List<SearchResult> results = new ArrayList<>();
        try {
            if (!initCollection()) {
                return results;
            }

            SearchReq searchReq = SearchReq.builder()
                    .collectionName(collectionName)
                    .annsField(VECTOR_FIELD)
                    .outputFields(OUTPUT_FIELDS)
                    .data(Collections.singletonList(toFloatList(vector)))
                    .topK(topK)
                    .build();

            SearchResp searchResp = milvusClient.search(searchReq);
            List<List<SearchResp.SearchResult>> resultsList = searchResp.getSearchResults();

            if (resultsList != null && !resultsList.isEmpty()) {
                for (SearchResp.SearchResult result : resultsList.get(0)) {
                    SearchResult searchResult = new SearchResult();
                    searchResult.setId(result.getId());
                    searchResult.setScore(result.getDistance());
                    searchResult.setTitle((String) result.getEntity().get("title"));
                    searchResult.setContent((String) result.getEntity().get("content"));
                    searchResult.setSource((String) result.getEntity().get("source"));

                    Object pageValue = result.getEntity().get("page");
                    if (pageValue instanceof Number number) {
                        searchResult.setPage(number.intValue());
                    }

                    results.add(searchResult);
                }
            }
        } catch (Exception e) {
            markUnavailable();
            log.error("Failed to search vectors", e);
        }
        return results;
    }

    public void insertDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }

        try {
            if (!initCollection()) {
                log.warn("Skipping vector insert because Milvus collection is unavailable");
                return;
            }

            List<JSONObject> data = new ArrayList<>();
            for (Document doc : documents) {
                JSONObject entity = new JSONObject();
                entity.put(ID_FIELD, doc.getId());
                entity.put(VECTOR_FIELD, toFloatList(doc.getVector()));
                entity.put("title", doc.getTitle());
                entity.put("content", doc.getContent());
                entity.put("source", doc.getSource());
                entity.put("page", doc.getPage());
                data.add(entity);
            }

            InsertReq insertReq = InsertReq.builder()
                    .collectionName(collectionName)
                    .data(data)
                    .build();
            milvusClient.insert(insertReq);
        } catch (Exception e) {
            markUnavailable();
            log.error("Failed to insert documents", e);
        }
    }

    public void deleteDocuments(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        try {
            if (!initCollection()) {
                log.warn("Skipping vector delete because Milvus collection is unavailable");
                return;
            }

            DeleteReq deleteReq = DeleteReq.builder()
                    .collectionName(collectionName)
                    .ids(ids.stream().map(id -> (Object) id).collect(Collectors.toList()))
                    .build();
            milvusClient.delete(deleteReq);
        } catch (Exception e) {
            markUnavailable();
            log.error("Failed to delete documents from vector store", e);
        }
    }

    private void markUnavailable() {
        collectionLoaded = false;
        retryAfterEpochMs = System.currentTimeMillis() + dependencyRetryDelayMs;
    }

    public static class SearchResult {
        private Object id;
        private Float score;
        private String title;
        private String content;
        private String source;
        private Integer page;

        public Object getId() {
            return id;
        }

        public void setId(Object id) {
            this.id = id;
        }

        public Float getScore() {
            return score;
        }

        public void setScore(Float score) {
            this.score = score;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public Integer getPage() {
            return page;
        }

        public void setPage(Integer page) {
            this.page = page;
        }
    }

    private List<Float> toFloatList(float[] vector) {
        if (vector == null || vector.length == 0) {
            return List.of();
        }

        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }

    public static class Document {
        private String id;
        private float[] vector;
        private String title;
        private String content;
        private String source;
        private Integer page;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public float[] getVector() {
            return vector;
        }

        public void setVector(float[] vector) {
            this.vector = vector;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public Integer getPage() {
            return page;
        }

        public void setPage(Integer page) {
            this.page = page;
        }
    }
}
