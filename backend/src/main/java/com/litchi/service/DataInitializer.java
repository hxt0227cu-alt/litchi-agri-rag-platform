package com.litchi.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataInitializer {

    private final Driver neo4jDriver;
    private final VectorSearchService vectorSearchService;

    public InitResult initialize(String scope) {
        String normalizedScope = scope == null ? "all" : scope.toLowerCase(Locale.ROOT);

        boolean graphInitialized = false;
        boolean vectorInitialized = false;

        if ("all".equals(normalizedScope) || "graph".equals(normalizedScope)) {
            graphInitialized = initKnowledgeGraph();
        }

        if ("all".equals(normalizedScope) || "vector".equals(normalizedScope)) {
            vectorInitialized = initVectorStore();
        }

        String message = switch (normalizedScope) {
            case "graph" -> graphInitialized ? "Knowledge graph initialization completed." : "Knowledge graph initialization failed.";
            case "vector" -> vectorInitialized ? "Vector store initialization completed." : "Vector store initialization failed.";
            default -> (graphInitialized || vectorInitialized)
                    ? "Initialization finished."
                    : "Initialization finished with no successful actions.";
        };

        return InitResult.builder()
                .scope(normalizedScope)
                .knowledgeGraphInitialized(graphInitialized)
                .vectorStoreInitialized(vectorInitialized)
                .message(message)
                .build();
    }

    public boolean initKnowledgeGraph() {
        try (Session session = neo4jDriver.session()) {
            session.run("CREATE INDEX IF NOT EXISTS FOR (v:LitchiVariety) ON (v.name)");
            session.run("CREATE INDEX IF NOT EXISTS FOR (d:Disease) ON (d.name)");
            session.run("CREATE INDEX IF NOT EXISTS FOR (p:Pest) ON (p.name)");
            session.run("CREATE INDEX IF NOT EXISTS FOR (pe:Pesticide) ON (pe.name)");

            var result = session.run("MATCH (n) RETURN count(n) AS count");
            long count = result.single().get("count").asLong();

            if (count == 0) {
                log.info("Initializing knowledge graph with sample data...");
                initSampleData(session);
            } else {
                log.info("Knowledge graph already has {} nodes, skipping sample data initialization", count);
            }

            return true;
        } catch (Exception e) {
            log.error("Failed to initialize knowledge graph", e);
            return false;
        }
    }

    private void initSampleData(Session session) {
        String cypher = """
            CREATE (gv:LitchiVariety {name:'桂味', origin:'广东', taste:'清甜且带有桂花香', ripeningSeason:'6月下旬-7月上旬', yield:'中等', description:'优质荔枝品种，果核小，果肉厚'})
            CREATE (nz:LitchiVariety {name:'糯米糍', origin:'广东', taste:'浓甜微香', ripeningSeason:'6月下旬-7月上旬', yield:'较低', description:'知名荔枝品种，果肉柔软'})
            CREATE (fzx:LitchiVariety {name:'妃子笑', origin:'广东', taste:'清甜带微酸', ripeningSeason:'5月下旬-6月中旬', yield:'高', description:'早熟品种，果大肉厚'})
            CREATE (sy:LitchiVariety {name:'三月红', origin:'广东', taste:'酸甜', ripeningSeason:'4月下旬-5月上旬', yield:'中等', description:'成熟最早的品种'})

            CREATE (sym:Disease {name:'霜疫霉病', symptom:'果实褐色病斑与白色霉层', cause:'高湿低温', highSeason:'雨季', description:'荔枝主要病害之一'})
            CREATE (tj:Disease {name:'炭疽病', symptom:'叶片圆形病斑和果实腐烂', cause:'高温高湿', highSeason:'夏秋', description:'危害叶片和果实'})
            CREATE (sf:Disease {name:'酸腐病', symptom:'果实腐烂并伴有酸水流出', cause:'伤口感染', highSeason:'成熟期', description:'多发生于贮运期'})

            CREATE (cx:Pest {name:'蒂蛀虫', damage:'危害幼果和花穗', controlMethod:'冬季摇落虫果并集中处理', description:'荔枝常见害虫'})
            CREATE (ye:Pest {name:'尺蠖', damage:'啃食果穗和嫩叶', controlMethod:'套袋和诱捕结合', description:'幼虫取食明显'})

            CREATE (xxml:Pesticide {name:'烯酰吗啉', type:'杀菌剂', usage:'稀释1000倍喷雾', safetyInterval:7, description:'用于防治霜疫霉病'})
            CREATE (smaj:Pesticide {name:'咪鲜胺', type:'杀菌剂', usage:'稀释1500倍喷雾', safetyInterval:14, description:'用于防治炭疽病'})
            CREATE (bsmjh:Pesticide {name:'苯醚甲环唑', type:'杀菌剂', usage:'稀释1000倍喷雾', safetyInterval:14, description:'广谱杀菌剂'})

            CREATE (sfjs:CultivationTechnique {name:'施肥技术', description:'以基肥为主，按树势追肥', bestSeason:'春季'})
            CREATE (zzjs:CultivationTechnique {name:'修剪技术', description:'保持树冠通风透光，剪除病弱枝', bestSeason:'冬季'})
            CREATE (hsjs:CultivationTechnique {name:'花果管理', description:'疏花疏果，提高坐果率', bestSeason:'花期'})

            CREATE (gv)-[:HAS_DISEASE {severity:'高'}]->(sym)
            CREATE (gv)-[:HAS_DISEASE {severity:'中'}]->(tj)
            CREATE (nz)-[:HAS_DISEASE {severity:'高'}]->(sym)
            CREATE (fzx)-[:HAS_DISEASE {severity:'中'}]->(tj)
            CREATE (gv)-[:HAS_PEST {severity:'高'}]->(cx)
            CREATE (nz)-[:HAS_PEST {severity:'中'}]->(cx)

            CREATE (gv)-[:NEEDS_TECHNIQUE]->(sfjs)
            CREATE (gv)-[:NEEDS_TECHNIQUE]->(zzjs)
            CREATE (nz)-[:NEEDS_TECHNIQUE]->(hsjs)

            CREATE (xxml)-[:TREATS]->(sym)
            CREATE (smaj)-[:TREATS]->(tj)
            CREATE (bsmjh)-[:TREATS]->(tj)
            CREATE (bsmjh)-[:TREATS]->(sym)
            """;

        session.run(cypher);
        log.info("Sample knowledge graph data initialized");
    }

    public boolean initVectorStore() {
        try {
            vectorSearchService.initCollection();
            return true;
        } catch (Exception e) {
            log.error("Failed to initialize vector store", e);
            return false;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InitResult {
        private String scope;
        private boolean knowledgeGraphInitialized;
        private boolean vectorStoreInitialized;
        private String message;
    }
}
