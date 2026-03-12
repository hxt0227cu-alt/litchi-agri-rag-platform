# 荔枝智能问答平台

毕业设计可演示版，当前已具备以下闭环：

- 文档上传 -> 本地存储 -> 解析/切块 -> 向量化 -> 检索 -> 问答来源展示
- 知识图谱查询与关系可视化
- 拍照识病页面与后端接口字段对齐
- 显式初始化命令与接口
- 根目录 `docker-compose.yml`、`.env.example` 和前端 `Dockerfile`

## 当前状态

- 后端可正常执行 `mvn -q -DskipTests package`
- 前端可正常执行 `npm run build`
- 文档管理已实现上传、列表、删除和本地元数据持久化
- 智能问答已返回来源片段，知识图谱页支持关键词查询
- 拍照识病当前为“演示版规则识别”，不是 YOLO 完成版

## 本机启动

### 1. 启动依赖服务

至少准备以下服务：

- Neo4j: `bolt://localhost:7687`
- Milvus: `localhost:19530`
- Ollama: `http://localhost:11434`

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
```

显式初始化二选一：

```bash
# 方式 A：启动时执行初始化
mvn spring-boot:run -Dspring-boot.run.arguments="--init=all"

# 方式 B：服务启动后调用接口
curl -X POST "http://localhost:8080/api/system/init?scope=all"
```

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认地址：

- `http://localhost:5173`

## Docker 一键部署

```bash
cp .env.example .env
docker compose up -d --build
docker compose exec backend java -jar app.jar --init=all
```

默认访问地址：

- 前端：`http://localhost`
- 后端：`http://localhost:8080/api`
- Neo4j Browser：`http://localhost:7474`

## 5 分钟演示脚本

1. 执行一次 `/api/system/init?scope=all`，确认图谱和向量库初始化完成。
2. 打开“文档管理”，上传一份 `txt` 或 `md` 知识文档，确认列表出现、状态为“已入库”。
3. 打开“智能问答”，提问与文档内容相关的问题，展示答案下方的来源卡片。
4. 打开“知识图谱”，输入病害或品种关键词，展示节点关系和属性面板。
5. 打开“拍照识病”，上传示例图片，展示病害名称、置信度和防治建议。

## 说明

- 文档解析目前优先保障 `txt / md / csv / json`，`pdf / docx` 为尽力解析。
- 向量化使用本地轻量哈希向量，结果会同时尝试同步到 Milvus。
- 若 Ollama 未启动，问答接口仍会返回失败提示，但来源检索链路可单独演示。
