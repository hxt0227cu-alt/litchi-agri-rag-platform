# 荔枝智能问答平台 - GitHub参考项目分析文档

&gt; 文档生成时间: 2026年
&gt; 适用项目: 基于大模型RAG-知识图谱的荔枝智能问答Web平台
&gt; 文档定位: 本科毕业设计参考资料

---

## 项目概述

### 毕业设计基本信息
| 项目 | 内容 |
|------|------|
| **题目** | 基于大模型RAG-知识图谱的荔枝智能问答Web平台设计与实现 |
| **应用场景** | ①田间无网/4G环境；②农资店柜台；③农技站培训课堂 |
| **用户角色** | 农民：拍照识病+语音回答；农技员：数据大屏+来源PDF；农资店主：快速配药方案 |
| **交付物** | 可扫码Web系统、Docker一键部署、100组评测+BLEU、论文（查重≤30%）、演示视频 |

### 技术栈
- **前端**: Vue3 + TypeScript
- **后端**: SpringBoot + Spring AI
- **向量检索**: bge-m3 + Milvus
- **大模型**: Qwen2.5-7B (Ollama本地部署)
- **知识图谱**: Neo4j
- **特色功能**: 拍照识病(YOLOv8-litchi) + 语音回答(edge-tts)

---

## 一、核心参考项目（强烈推荐）

### 1. KBQA-for-Diagnosis ⭐⭐⭐⭐⭐
**GitHub**: `wangle1218/KBQA-for-Diagnosis`

**项目特点**:
- **领域**: 医疗诊断问答
- **技术栈匹配度**: ⭐⭐⭐⭐⭐
- 前端: Vue.js
- 后端: Python (Flask/FastAPI)
- 数据库: Neo4j + Elasticsearch/向量检索
- 深度学习: BERT意图识别 + BiLSTM-CRF实体识别

**核心架构**:
```
用户提问 → [意图识别] → [NER实体抽取] → [Cypher查询生成] → [Neo4j查询] → [答案组装]
```

**借鉴点**:
- ✅ 前后端分离架构
- ✅ 知识图谱查询逻辑
- ✅ 问答流程设计

---

### 2. QASystemOnMedicalKG ⭐⭐⭐⭐⭐
**GitHub**: `liuhuanyong/QASystemOnMedicalKG`

**项目特点**:
- 作者: 刘焕勇（中科院软件所）
- 领域: 医疗知识图谱问答
- 技术栈: Python + Neo4j + 规则模板

**知识图谱规模**:
- 实体总数: 44,111
- 关系总数: 294,149
- 实体类型: 7类（疾病、症状、药品等）
- 关系类型: 11类

**借鉴点**:
- ✅ 知识图谱构建流程
- ✅ Cypher查询模板设计
- ✅ 问答类型分类（18类）

---

### 3. tobacco-ai ⭐⭐⭐⭐⭐
**GitHub**: `Blue16-WangFudi/tobacco-ai`

**项目特点**:
- 领域: 烟草种植AI助手
- 技术栈: RAG + 视觉诊断 + 知识图谱
- 与你的项目高度相似！

**借鉴点**:
- ✅ 农业领域RAG+知识图谱结合
- ✅ 视觉诊断功能实现
- ✅ 完整的农业问答系统架构

---

### 4. Agribot ⭐⭐⭐⭐
**GitHub**: `Antusaha3/Agribot`

**项目特点**:
- 领域: 农业孟加拉语聊天机器人
- 技术栈: GraphRAG (Neo4j) + VectorRAG (pgvector)
- 结合知识图谱和向量检索

**借鉴点**:
- ✅ GraphRAG与VectorRAG融合
- ✅ 多模态检索策略

---

## 二、RAG技术参考项目

### 2.1 主流RAG项目
| 项目 | GitHub | 特点 |
|------|--------|------|
| **LightRAG** | `HKUDS/LightRAG` | 简单快速的RAG系统，EMNLP2025 |
| **GraphRAG** | `microsoft/graphrag` | 微软图RAG，将非结构化文本转知识网络 |
| **Verba** | `weaviate/Verba` | Weaviate驱动的RAG聊天机器人 |
| **RAGFlow** | `infiniflow/ragflow` | 开源RAG引擎，融合Agent能力 |
| **RAG_Techniques** | `NirDiamant/RAG_Techniques` | 各种RAG高级技术展示 |

### 2.2 GraphRAG vs 传统RAG
| 特性 | 传统RAG | GraphRAG | 你的项目方案 |
|------|---------|----------|-------------|
| 检索方式 | 纯语义相似度 | 图结构导航+语义 | RAG向量检索+知识图谱查询 ✅ |
| 知识表示 | 文本片段 | 实体-关系网络 | Neo4j知识图谱 ✅ |
| 推理能力 | 有限 | 支持多跳推理 | 大模型+图谱推理 ✅ |
| 可解释性 | 弱 | 强（可追溯路径） | 图谱可视化 ✅ |

---

## 三、YOLOv8植物病害识别参考

### 3.1 相关GitHub项目
| 项目 | GitHub | 特点 |
|------|--------|------|
| **TomaTrac** | `yuanyuan-qwq/TomaTrac` | 番茄种植AI，CNN+NLP+YOLO |
| **Plant-disease-recognition-using-YOLOv8** | `Shaad2112/Plant-disease-recognition-using-YOLOv8` | YOLOv8植物病害识别 |
| **PlantVillage-Multiclass-YOLO** | `Data-With-Anish/PlantVillage-Multiclass-YOLO` | YOLOv12-L多类植物病害检测 |
| **Comparative-Analysis-of-next-gen-YOLO-models** | `Tiyasha0811/Comparative-Analysis-of-next-gen-YOLO-models` | YOLOv8/9/11对比研究 |

### 3.2 荔枝常见病害（用于训练YOLOv8-litchi）
| 病害名称 | 高发季节 | 典型症状 |
|---------|---------|---------|
| 霜疫霉病 | 雨季 | 果实褐色病斑、白色霉层 |
| 炭疽病 | 全年 | 叶片圆形褐色斑点、凹陷 |
| 酸腐病 | 成熟期 | 果实酸臭、汁液流出 |
| 丛枝病 | 春季 | 枝叶丛生、节间缩短 |

### 3.3 数据集规模建议
- 训练集: 2000+ 张图像
- 验证集: 200-300 张图像
- 测试集: 200-300 张图像
- 每类病害: 至少200张样本

### 3.4 YOLOv8训练流程
```python
from ultralytics import YOLO

model = YOLO('yolov8n.pt')
results = model.train(
    data='litchi_disease.yaml',
    epochs=100,
    imgsz=640,
    batch=16,
    device='0'
)
model.export(format='onnx')
```

---

## 四、前后端开发参考

### 4.1 SpringBoot + Vue3全栈项目
| 项目 | GitHub | 特点 |
|------|--------|------|
| **springboot-vue** | `devopsor/springboot-vue` | Spring Boot+Vue.js CRUD示例 |
| **springboot-vue3-mysql** | `EvanJDL/springboot-vue3-mysql` | Spring Boot+Vue3+MySQL全栈demo |
| **full-stack_1** | `flowerchar/full-stack_1` | Vue3+SpringBoot增删改查系统 |

### 4.2 前端项目结构
```
src/
├── api/                    # API接口
│   ├── chat.ts            # 问答接口
│   ├── knowledgeGraph.ts  # 知识图谱接口
│   └── upload.ts          # 图片上传接口
├── components/            # 公共组件
│   ├── ChatBubble.vue     # 聊天气泡组件
│   ├── KnowledgeGraph.vue # 知识图谱可视化组件
│   └── VoiceInput.vue     # 语音输入组件
├── views/                 # 页面
│   ├── ChatView.vue       # 问答页面
│   ├── KnowledgeView.vue  # 知识库页面
│   └── DiagnosisView.vue  # 拍照识病页面
└── stores/                # Pinia状态管理
```

### 4.3 后端项目结构
```
src/main/java/com/litchi/
├── config/                # 配置类
│   ├── Neo4jConfig.java
│   ├── MilvusConfig.java
│   └── OllamaConfig.java
├── controller/            # 控制器
│   ├── ChatController.java
│   ├── KnowledgeGraphController.java
│   └── DiagnosisController.java
├── service/               # 业务逻辑
│   ├── ChatService.java
│   ├── KnowledgeGraphService.java
│   └── VectorSearchService.java
└── entity/                # 实体类
```

---

## 五、本地部署与优化

### 5.1 Ollama部署Qwen2.5-7B
```bash
# 1. 安装Ollama
# Windows: 下载安装包

# 2. 拉取Qwen2.5-7B模型
ollama pull qwen2.5:7b

# 3. 测试模型
ollama run qwen2.5:7b
```

### 5.2 低延迟优化
- 模型量化: Q4_K_M量化，显存占用减半
- KV缓存: 开启缓存，复用对话历史
- 语义缓存: 缓存常见问题的答案

---

## 六、Docker一键部署

### 6.1 Docker Compose配置
```yaml
version: '3.8'

services:
  neo4j:
    image: neo4j:5.15-community
    container_name: litchi-neo4j
    environment:
      - NEO4J_AUTH=neo4j/litchi123456
    ports:
      - "7474:7474"
      - "7687:7687"
    volumes:
      - neo4j_data:/data
    networks:
      - litchi-network

  milvus:
    image: milvusdb/milvus:v2.3.3
    container_name: litchi-milvus
    ports:
      - "19530:19530"
    networks:
      - litchi-network

  ollama:
    image: ollama/ollama:latest
    container_name: litchi-ollama
    ports:
      - "11434:11434"
    volumes:
      - ollama_data:/root/.ollama
    networks:
      - litchi-network

  backend:
    build: ./backend
    container_name: litchi-backend
    ports:
      - "8080:8080"
    depends_on:
      - neo4j
      - milvus
      - ollama
    networks:
      - litchi-network

  frontend:
    build: ./frontend
    container_name: litchi-frontend
    ports:
      - "80:80"
    depends_on:
      - backend
    networks:
      - litchi-network

volumes:
  neo4j_data:
  ollama_data:

networks:
  litchi-network:
    driver: bridge
```

---

## 七、系统评测

### 7.1 BLEU评价指标
**BLEU** (Bilingual Evaluation Understudy) 用于文本生成质量评价。

**计算公式**:
```
BLEU = BP × exp(Σ w_n × log p_n)
```

### 7.2 100组评测集设计
| 问题类型 | 数量 | 示例 |
|---------|------|------|
| 品种介绍 | 15 | "桂味荔枝有什么特点？" |
| 种植技术 | 20 | "荔枝树什么时候施肥最好？" |
| 病害识别 | 25 | "荔枝叶片有褐色斑点是什么病？" |
| 虫害防治 | 20 | "如何防治荔枝椿象？" |
| 农药使用 | 10 | "防治炭疽病用什么药？" |
| 综合问题 | 10 | "雨季荔枝如何管理？" |

---

## 八、知识图谱构建

### 8.1 实体类型设计
```
LitchiVariety (荔枝品种)
  ├── name (名称)
  ├── origin (原产地)
  ├── taste (口感)
  ├── ripeningSeason (成熟期)

Disease (病害)
  ├── name (名称)
  ├── symptom (症状)
  ├── cause (病因)
  ├── highSeason (高发期)

Pest (虫害)
  ├── name (名称)
  ├── damage (危害)
  ├── controlMethod (防治方法)

Pesticide (农药)
  ├── name (名称)
  ├── type (类型)
  ├── usage (用法用量)
```

### 8.2 关系类型设计
```
HAS_DISEASE (品种→病害)
HAS_PEST (品种→虫害)
TREATS (农药→病害/虫害)
PREVENTS (农药→病害/虫害)
```

---

## 九、项目时间线建议（本科毕设）

| 阶段 | 任务 | 预计时间 |
|-----|------|---------|
| **第1-2周** | 数据收集与知识图谱构建 | 2周 |
| **第3-6周** | 后端开发（SpringBoot + Neo4j + Milvus） | 4周 |
| **第7-8周** | 大模型集成与RAG实现 | 2周 |
| **第9-10周** | YOLOv8病害识别模型训练 | 2周 |
| **第11-13周** | 前端开发（Vue3） | 3周 |
| **第14周** | 系统集成与Docker部署 | 1周 |
| **第15周** | 评测与优化 | 1周 |
| **第16-18周** | 论文撰写 | 3周 |

---

## 十、推荐学习资源

### 10.1 GitHub项目
- microsoft/graphrag - 微软GraphRAG官方实现
- ultralytics/ultralytics - YOLOv8官方仓库
- ollama/ollama - 本地大模型部署工具

### 10.2 农业知识资源
- 中国农业推广网
- 荔枝龙眼病虫害彩色图谱
- 荔枝种植技术书籍

---

**文档更新记录**:
- 2026-03-11: 优化文档结构，新增GitHub搜索项目，精简内容符合本科毕设要求
