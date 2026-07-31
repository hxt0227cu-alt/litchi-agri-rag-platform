# API 接口文档

## 1. 基本说明

- Base URL：`http://localhost:8080/api`
- 数据格式：`application/json`
- 文件上传：`multipart/form-data`
- 认证方式：`Authorization: Bearer <token>`

统一错误响应：

```json
{
  "message": "错误说明"
}
```

分页接口统一返回：

```json
{
  "total": 12,
  "page": 1,
  "size": 10,
  "items": []
}
```

## 2. 认证模块

### 2.1 注册

- `POST /auth/register`

请求：

```json
{
  "username": "demo_user",
  "password": "demo123",
  "role": "farmer"
}
```

响应：

```json
{
  "token": "token_value",
  "expiresAt": "2026-03-26T10:00:00+08:00",
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

请求：

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

响应：

```json
{
  "success": true,
  "message": "已退出登录。"
}
```

## 3. 问答与会话模块

### 3.1 发起问答

- `POST /chat`
- 需要登录

请求：

```json
{
  "sessionId": "session_001",
  "question": "荔枝雨季如何防治炭疽病？",
  "useKnowledgeGraph": true,
  "useVectorSearch": true
}
```

响应：

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
    "entities": [
      {
        "id": "entity_id",
        "label": "Disease",
        "properties": {
          "name": "炭疽病"
        }
      }
    ]
  }
}
```

### 3.2 会话历史

- `GET /chat/history?sessionId=session_001&page=1&size=20`
- 需要登录

### 3.3 会话列表

- `GET /chat/sessions?page=1&size=10`
- 需要登录

## 4. 文档模块

说明：

- 文档列表接口只要求登录。
- 文档上传和删除仅技术员可执行。

### 4.1 上传文档

- `POST /document`
- 需要技术员登录
- 表单字段：
  - `file`：必填
  - `title`：可选

响应：

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
  "ownerUsername": "technician"
}
```

### 4.2 文档列表

- `GET /document?page=1&size=10&keyword=病害`
- 需要登录

### 4.3 删除文档

- `DELETE /document/{id}`
- 需要技术员登录

响应：

```json
{
  "deleted": true,
  "message": "文档已删除。"
}
```

## 5. 知识图谱模块

当前图谱接口为公开接口，前端页面入口由角色路由限制。

### 5.1 可视化数据

- `GET /kg/visualize`
- `GET /kg/visualize?keyword=炭疽病`

响应：

```json
{
  "nodes": [
    {
      "id": "node_id",
      "label": "Disease",
      "properties": {
        "name": "炭疽病"
      }
    }
  ],
  "edges": [
    {
      "source": "node_a",
      "target": "node_b",
      "label": "TREATS"
    }
  ]
}
```

### 5.2 实体搜索

- `GET /kg/search?keyword=炭疽&type=Disease`

响应：

```json
[
  {
    "id": "entity_id",
    "label": "Disease",
    "properties": {
      "name": "炭疽病"
    }
  }
]
```

### 5.3 实体详情

- `GET /kg/entity/{id}`

响应：

```json
{
  "id": "entity_id",
  "label": "Disease",
  "name": "炭疽病",
  "properties": {
    "name": "炭疽病"
  },
  "relations": [
    {
      "type": "TREATS",
      "target": {
        "id": "target_id",
        "label": "Pesticide",
        "name": "苯醚甲环唑"
      }
    }
  ]
}
```

## 6. 病害识别模块

- `POST /diagnosis`
- 需要农户或技术员登录
- 表单字段：
  - `file`：必填

响应：

```json
{
  "disease": "霜疫霉病",
  "diseaseName": "霜疫霉病",
  "confidence": 0.95,
  "suggestions": [
    "优先做好排水和通风。",
    "雨后及时清理病果病枝。"
  ],
  "suggestion": "优先做好排水和通风。",
  "diseases": [
    {
      "name": "霜疫霉病",
      "confidence": 0.95
    }
  ],
  "engine": "ultralytics-yolo",
  "demoMode": false,
  "note": "当前使用正式模型。"
}
```

## 7. 评测模块

所有评测接口都要求技术员登录。

### 7.1 题库列表

- `GET /evaluation/questions?page=1&size=20`
- `GET /evaluation/questions?type=病害识别&evaluated=false`

### 7.2 提交系统答案

- `POST /evaluation/answer`

请求：

```json
{
  "id": 1,
  "systemAnswer": "系统生成的答案"
}
```

### 7.3 提交人工评分

- `POST /evaluation/score`

请求：

```json
{
  "id": 1,
  "humanScore": 4,
  "reviewNote": "安全提醒还可以更明确。"
}
```

### 7.4 统计信息

- `GET /evaluation/stats`

响应：

```json
{
  "total": 100,
  "evaluated": 12,
  "avgAutoScore": 68.4,
  "avgBleuScore": 0.713,
  "avgHumanScore": 4.2,
  "reviewPending": 5,
  "byType": [
    {
      "type": "品种介绍",
      "count": 16,
      "avgAutoScore": 71.2,
      "avgBleuScore": 0.702,
      "avgHumanScore": 4.3
    }
  ]
}
```

## 8. 满意度反馈模块

### 8.1 提交反馈

- `POST /feedback`
- 需要登录

请求：

```json
{
  "module": "solutions",
  "overallScore": 5,
  "accuracyScore": 4,
  "practicalityScore": 5,
  "fluencyScore": 4,
  "comment": "推荐原因解释得比较清楚。"
}
```

### 8.2 反馈统计

- `GET /feedback/stats`
- 需要技术员登录

响应：

```json
{
  "total": 10,
  "avgOverallScore": 4.5,
  "avgAccuracyScore": 4.3,
  "avgPracticalityScore": 4.4,
  "avgFluencyScore": 4.2,
  "byModule": [
    {
      "module": "chat",
      "count": 4,
      "avgOverallScore": 4.75
    }
  ],
  "recent": []
}
```

## 9. 系统模块

### 9.1 健康检查

- `GET /health`
- 公开接口

响应：

```json
{
  "status": "healthy",
  "services": {
    "neo4j": "connected",
    "milvus": "connected",
    "ollama": "connected",
    "diagnosis": "healthy"
  },
  "diagnosisDetails": {
    "engine": "ultralytics-yolo",
    "demoMode": false,
    "modelLoaded": true
  },
  "documents": {
    "total": 3,
    "indexed": 3
  },
  "timestamp": "2026-03-26T10:00:00+08:00"
}
```

### 9.2 平台总览

- `GET /system/overview`
- 公开接口

### 9.3 系统设置摘要

- `GET /system/settings`
- 需要技术员登录

### 9.4 手动初始化

- `POST /system/init?scope=all`
- 需要技术员登录
- `scope` 可选：`all`、`graph`、`vector`

### 9.5 重建平台样例

- `POST /system/demo/bootstrap`
- 需要技术员登录

## 10. 门店协同模块

### 10.1 门店资料

- `GET /shop/profile`
- `PUT /shop/profile`
- 需要门店账号登录

请求示例：

```json
{
  "shopName": "果园快配站",
  "contactName": "刘店长",
  "phone": "13900001111",
  "wechat": "orchard-service",
  "address": "荔枝大道 66 号",
  "serviceArea": "北部园区",
  "specialties": "病害快配、巡园建议",
  "rating": 4.8
}
```

### 10.2 门店方案库

- `GET /shop/plans`
- `POST /shop/plans`
- `PUT /shop/plans/{id}`
- `DELETE /shop/plans/{id}`
- 需要门店账号登录

新增/编辑请求示例：

```json
{
  "title": "炭疽病雨季处理方案",
  "diseaseTag": "炭疽病",
  "stageTag": "雨季高湿",
  "summary": "适合连续阴雨后的炭疽病场景。",
  "products": ["吡唑醚菌酯", "苯醚甲环唑"],
  "usageTips": ["优先清园", "按标签轮换用药"],
  "riskNotes": ["不可自行加量", "注意安全间隔期"],
  "inventoryStatus": "有现货",
  "active": true
}
```

### 10.3 高频病症趋势

- `GET /shop/trends`
- 需要门店账号登录

响应：

```json
[
  {
    "diseaseTag": "炭疽病",
    "totalConsultations": 8,
    "recentConsultations": 3,
    "latestAt": "2026-03-26T09:30:00+08:00"
  }
]
```

## 11. 方案推荐与求助模块

### 11.1 获取推荐方案

- `GET /plans/recommendations`
- 需要农户或技术员登录
- 查询参数：
  - `diseaseTag`：可选
  - `stageTag`：可选
  - `query`：可选

示例：

- `GET /plans/recommendations?diseaseTag=炭疽病&stageTag=雨季高湿`
- `GET /plans/recommendations?query=连续降雨后果面有褐斑`

响应：

```json
[
  {
    "planId": "plan_id",
    "shopId": "shop_id",
    "shopName": "果园快配站",
    "contactName": "刘店长",
    "phone": "13900001111",
    "wechat": "orchard-service",
    "address": "荔枝大道 66 号",
    "serviceArea": "北部园区",
    "rating": 4.8,
    "title": "炭疽病雨季处理方案",
    "diseaseTag": "炭疽病",
    "stageTag": "雨季高湿",
    "summary": "适合连续阴雨后的炭疽病场景。",
    "products": ["吡唑醚菌酯"],
    "usageTips": ["优先清园"],
    "riskNotes": ["不可自行加量"],
    "inventoryStatus": "有现货",
    "score": 86.5,
    "reasonTags": ["病症匹配", "门店评分较高"]
  }
]
```

### 11.2 提交求助

- `POST /consultations`
- 需要农户登录

请求：

```json
{
  "planId": "plan_id",
  "diseaseTag": "炭疽病",
  "stageTag": "雨季高湿",
  "question": "果面已经有扩散，想尽快确认处理方案。",
  "reasonTags": ["病症匹配", "雨季高发"]
}
```

### 11.3 我的求助

- `GET /consultations/my`
- 需要农户登录

### 11.4 门店收件箱

- `GET /consultations/inbox`
- 需要门店登录

### 11.5 更新求助状态

- `POST /consultations/{id}/status`
- 需要门店登录

请求：

```json
{
  "status": "contacted"
}
```

状态值：

- `pending`
- `contacted`
- `completed`
