# 荔枝智能问答平台

面向荔枝种植场景的完整平台实现，包含账号体系、智能问答、知识图谱、文档管理、病害识别、评测中心，以及 MySQL/Neo4j/Milvus/Ollama 组合存储与推理链路。

## 当前能力

- 用户注册、登录、登出、登录态恢复
- 多轮问答、会话列表、历史消息回放
- 文档上传、分页检索、删除、向量检索增强
- 知识图谱可视化、实体搜索、关系查询、实体详情
- 病害图片识别接口与前端识别页面
- 评测题库、系统答案提交、人工评分、统计面板
- 技术员培训课堂、农资店快配药与用药指南
- 满意度问卷提交与统计
- 语音输入与回答朗读
- MySQL 主持久化，JSON 本地状态文件作为回退

## 技术栈

- 前端: Vue 3 + TypeScript + Pinia + Element Plus
- 后端: Spring Boot 3
- 关系存储: MySQL 8
- 图数据库: Neo4j 5
- 向量数据库: Milvus 2
- 大模型服务: Ollama
- 识别服务: Python + FastAPI 风格 HTTP 服务

## 默认账号

- `farmer / demo123`
- `technician / demo123`
- `shopkeeper / demo123`

## 本机开发

### 1. 启动依赖

至少需要准备以下服务:

- MySQL: `127.0.0.1:3306`
- Neo4j: `127.0.0.1:7687`
- Milvus: `127.0.0.1:19530`
- Ollama: `http://127.0.0.1:11434`
- 识别服务: `http://127.0.0.1:8090`

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
```

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认地址: `http://localhost:5173`

## Docker 一键启动

```bash
cp .env.example .env
docker compose up -d --build
```

启动完成后可访问:

- 前端: `http://localhost`
- 后端 API: `http://localhost:8080/api`
- Neo4j Browser: `http://localhost:7474`
- MySQL: `localhost:3306`

## 关键接口

- 认证: `/api/auth/*`
- 问答: `/api/chat`
- 会话历史: `/api/chat/history`、`/api/chat/sessions`
- 文档: `/api/document`
- 图谱: `/api/kg/visualize`、`/api/kg/search`、`/api/kg/entity/{id}`
- 识别: `/api/diagnosis`
- 评测: `/api/evaluation/*`
- 满意度: `/api/feedback`、`/api/feedback/stats`
- 系统: `/api/health`、`/api/system/overview`、`/api/system/settings`、`/api/system/init`、`/api/system/demo/bootstrap`

## 持久化说明

- MySQL 启用时，平台状态以 MySQL 为主存储
- 本地 `data/*.json` 状态文件会继续同步，作为离线与故障回退
- 上传文档原文件存放在后端本地目录或容器挂载目录
- 知识图谱存储在 Neo4j，向量索引存储在 Milvus

## 构建验证

- 后端可通过 `mvn -q -DskipTests package`
- 前端可通过 `npm run build`

## 相关文档

- [部署文档](docs/部署文档.md)
- [API 接口文档](docs/API接口文档.md)
- [数据库设计](docs/数据库设计.md)
