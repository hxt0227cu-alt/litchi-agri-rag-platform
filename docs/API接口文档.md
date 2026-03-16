# API 接口文档

## 1. 基本说明

- Base URL: `http://localhost:8080/api`
- 数据格式: `application/json`
- 文件上传: `multipart/form-data`
- 认证方式: `Authorization: Bearer <token>`

错误响应统一为:

```json
{
  "message": "错误说明"
}
```

## 2. 认证模块

### 2.1 注册

- `POST /auth/register`

请求:

```json
{
  "username": "demo_user",
  "password": "demo123",
  "role": "farmer"
}
```

响应:

```json
{
  "token": "token_value",
  "expiresAt": "2026-03-23T10:00:00+08:00",
  "user": {
    "id": "user_id",
    "username": "demo_user",
    "role": "farmer",
    "createdAt": "2026-03-16T10:00:00+08:00"
  }
}
```

### 2.2 登录

- `POST /auth/login`

请求:

```json
{
  "username": "farmer",
  "password": "demo123"
}
```

### 2.3 当前用户

- `GET /auth/me`
- 需要登录

### 2.4 登出

- `POST /auth/logout`
- 需要登录

响应:

```json
{
  "success": true,
  "message": "已退出登录"
}
```

## 3. 问答模块

### 3.1 发起问答

- `POST /chat`
- 需要登录

请求:

```json
{
  "sessionId": "session_001",
  "question": "荔枝雨季如何防治炭疽病？",
  "useKnowledgeGraph": true,
  "useVectorSearch": true
}
```

响应:

```json
{
  "answer": "回答内容",
  "sources": [
    {
      "title": "荔枝病害手册.md",
      "content": "命中的文档片段",
      "source": "荔枝病害手册.md",
      "page": 1,
      "score": 0.91
    }
  ],
  "knowledgeGraph": {
    "nodes": [],
    "edges": []
  }
}
```

### 3.2 会话历史

- `GET /chat/history?sessionId=session_001&page=1&size=20`
- 需要登录

响应:

```json
{
  "total": 12,
  "page": 1,
  "size": 20,
  "items": [
    {
      "id": "history_id",
      "sessionId": "session_001",
      "question": "问题",
      "answer": "回答",
      "sources": [],
      "knowledgeGraph": {},
      "createdAt": "2026-03-16T10:00:00+08:00"
    }
  ]
}
```

### 3.3 会话列表

- `GET /chat/sessions?page=1&size=10`
- 需要登录

## 4. 文档模块

### 4.1 上传文档

- `POST /document`
- 需要登录
- 表单字段:
  - `file`: 必填
  - `title`: 可选

响应:

```json
{
  "id": "doc_id",
  "name": "guide.md",
  "title": "荔枝病害指南",
  "size": 1024,
  "contentType": "text/markdown",
  "uploadTime": "2026-03-16T10:00:00+08:00",
  "chunkCount": 3,
  "indexed": true,
  "statusMessage": "文档已完成切块并建立检索索引。",
  "ownerId": "user_id",
  "ownerUsername": "farmer"
}
```

### 4.2 文档列表

- `GET /document?page=1&size=10&keyword=病害`
- 需要登录

响应:

```json
{
  "total": 4,
  "page": 1,
  "size": 10,
  "items": []
}
```

### 4.3 删除文档

- `DELETE /document/{id}`
- 需要登录

响应:

```json
{
  "deleted": true,
  "message": "文档已删除。"
}
```

## 5. 知识图谱模块

### 5.1 可视化数据

- `GET /kg/visualize`
- `GET /kg/visualize?keyword=炭疽病`

### 5.2 实体搜索

- `GET /kg/search?keyword=炭疽&type=Disease`

响应:

```json
[
  {
    "id": "entity_id",
    "label": "Disease",
    "name": "炭疽病",
    "properties": {}
  }
]
```

### 5.3 实体详情

- `GET /kg/entity/{id}`

## 6. 病害识别模块

### 6.1 图片识别

- `POST /diagnosis`
- 表单字段:
  - `file`: 必填

响应:

```json
{
  "disease": "霜疫霉病",
  "diseaseName": "霜疫霉病",
  "confidence": 0.95,
  "suggestions": [
    "建议一",
    "建议二"
  ],
  "suggestion": "建议一",
  "diseases": [
    {
      "name": "霜疫霉病",
      "confidence": 0.95
    }
  ],
  "engine": "demo-rule-engine",
  "demoMode": true,
  "note": "当前为演示模式"
}
```

## 7. 评测模块

所有评测接口都需要登录。

### 7.1 题库列表

- `GET /evaluation/questions?page=1&size=20`
- `GET /evaluation/questions?type=病害识别&evaluated=false`

### 7.2 提交系统答案

- `POST /evaluation/answer`

请求:

```json
{
  "id": 1,
  "systemAnswer": "系统生成的答案"
}
```

### 7.3 提交人工评分

- `POST /evaluation/score`

请求:

```json
{
  "id": 1,
  "humanScore": 4
}
```

### 7.4 统计信息

- `GET /evaluation/stats`

响应:

```json
{
  "total": 100,
  "evaluated": 12,
  "avgBleuScore": 0.713,
  "avgHumanScore": 4.2,
  "byType": [
    {
      "type": "品种介绍",
      "count": 16,
      "avgBleuScore": 0.702,
      "avgHumanScore": 4.0
    }
  ]
}
```

## 8. 满意度模块

### 8.1 提交问卷

- `POST /feedback`

请求:

```json
{
  "module": "智能问答",
  "overallScore": 5,
  "accuracyScore": 5,
  "practicalityScore": 4,
  "fluencyScore": 5,
  "comment": "回答有来源卡片，讲解比较清楚。"
}
```

### 8.2 问卷统计

- `GET /feedback/stats`

响应:

```json
{
  "total": 3,
  "avgOverallScore": 4.67,
  "avgAccuracyScore": 4.67,
  "avgPracticalityScore": 4.33,
  "avgFluencyScore": 4.67,
  "byModule": [
    {
      "module": "智能问答",
      "count": 2,
      "avgOverallScore": 4.5
    }
  ],
  "recent": []
}
```

## 9. 系统模块

### 9.1 健康检查

- `GET /health`

### 9.2 初始化

- `POST /system/init?scope=all`

响应:

```json
{
  "scope": "all",
  "graphInitialized": true,
  "vectorInitialized": true,
  "message": "初始化完成"
}
```

### 9.3 系统概览

- `GET /system/overview`

### 9.4 系统设置

- `GET /system/settings`

### 9.5 演示数据引导

- `POST /system/demo/bootstrap`

## 10. 默认账号

- `farmer / demo123`
- `technician / demo123`
- `shopkeeper / demo123`
