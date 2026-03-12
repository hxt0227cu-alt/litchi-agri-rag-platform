# API 接口文档

## 接口说明

- **Base URL**: `http://localhost:8080/api`
- **数据格式**: JSON
- **字符编码**: UTF-8

---

## 1. 问答模块

### 1.1 发送问答请求

**接口**: `POST /chat`

**请求参数**:
```json
{
  "sessionId": "session_123456",
  "question": "桂味荔枝有什么特点？",
  "useKnowledgeGraph": true,
  "useVectorSearch": true
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "answer": "桂味荔枝是广东著名的荔枝品种，特点是...",
    "knowledgeGraph": {
      "nodes": [...],
      "edges": [...]
    },
    "sources": [
      {
        "title": "荔枝品种介绍.pdf",
        "content": "桂味荔枝...",
        "page": 10
      }
    ],
    "timestamp": "2026-03-11T10:30:00"
  }
}
```

---

## 2. 拍照识病模块

### 2.1 上传图片识别病害

**接口**: `POST /diagnosis`

**Content-Type**: `multipart/form-data`

**请求参数**:
| 参数名 | 类型 | 必选 | 说明 |
|--------|------|------|------|
| image | File | 是 | 图片文件 |
| userId | Long | 否 | 用户ID |

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "diseaseName": "霜疫霉病",
    "confidence": 0.95,
    "bbox": {
      "x": 100,
      "y": 150,
      "width": 200,
      "height": 180
    },
    "suggestion": "防治建议：...",
    "pesticides": [
      {
        "name": "烯酰吗啉",
        "usage": "稀释1000倍喷雾"
      }
    ]
  }
}
```

---

## 3. 知识图谱模块

### 3.1 获取知识图谱可视化数据

**接口**: `GET /kg/visualize`

**请求参数**:
| 参数名 | 类型 | 必选 | 说明 |
|--------|------|------|------|
| entityName | String | 否 | 实体名称（不传则返回全图） |
| limit | Integer | 否 | 返回节点数限制，默认50 |

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "nodes": [
      {
        "id": "1",
        "label": "LitchiVariety",
        "name": "桂味",
        "properties": {...}
      },
      {
        "id": "2",
        "label": "Disease",
        "name": "霜疫霉病",
        "properties": {...}
      }
    ],
    "edges": [
      {
        "source": "1",
        "target": "2",
        "label": "HAS_DISEASE",
        "properties": {...}
      }
    ]
  }
}
```

### 3.2 搜索实体

**接口**: `GET /kg/search`

**请求参数**:
| 参数名 | 类型 | 必选 | 说明 |
|--------|------|------|------|
| keyword | String | 是 | 搜索关键词 |
| type | String | 否 | 实体类型（LitchiVariety/Disease/Pest/Pesticide） |

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": "1",
      "label": "LitchiVariety",
      "name": "桂味",
      "properties": {...}
    }
  ]
}
```

### 3.3 获取实体详情

**接口**: `GET /kg/entity/{id}`

**路径参数**:
| 参数名 | 类型 | 说明 |
|--------|------|------|
| id | String | 实体ID |

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "1",
    "label": "Disease",
    "name": "霜疫霉病",
    "properties": {
      "symptom": "果实褐色病斑、白色霉层",
      "cause": "高湿低温",
      "highSeason": "雨季"
    },
    "relations": [
      {
        "type": "TREATS",
        "target": {
          "id": "10",
          "label": "Pesticide",
          "name": "烯酰吗啉"
        }
      }
    ]
  }
}
```

---

## 4. 文档管理模块

### 4.1 上传文档

**接口**: `POST /document`

**Content-Type**: `multipart/form-data`

**请求参数**:
| 参数名 | 类型 | 必选 | 说明 |
|--------|------|------|------|
| file | File | 是 | 文档文件（pdf/docx） |
| title | String | 否 | 文档标题（默认文件名） |
| userId | Long | 否 | 上传用户ID |

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "title": "荔枝种植技术.pdf",
    "status": "uploaded"
  }
}
```

### 4.2 获取文档列表

**接口**: `GET /document`

**请求参数**:
| 参数名 | 类型 | 必选 | 说明 |
|--------|------|------|------|
| page | Integer | 否 | 页码，默认1 |
| size | Integer | 否 | 每页数量，默认10 |
| keyword | String | 否 | 搜索关键词 |

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 50,
    "page": 1,
    "size": 10,
    "items": [
      {
        "id": 1,
        "title": "荔枝种植技术.pdf",
        "fileType": "pdf",
        "fileSize": 1048576,
        "status": "indexed",
        "createdAt": "2026-03-01T10:00:00"
      }
    ]
  }
}
```

### 4.3 删除文档

**接口**: `DELETE /document/{id}`

**路径参数**:
| 参数名 | 类型 | 说明 |
|--------|------|------|
| id | Long | 文档ID |

**响应示例**:
```json
{
  "code": 200,
  "message": "success"
}
```

---

## 5. 对话历史模块

### 5.1 获取对话历史

**接口**: `GET /chat/history`

**请求参数**:
| 参数名 | 类型 | 必选 | 说明 |
|--------|------|------|------|
| sessionId | String | 是 | 会话ID |
| page | Integer | 否 | 页码，默认1 |
| size | Integer | 否 | 每页数量，默认20 |

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 100,
    "page": 1,
    "size": 20,
    "items": [
      {
        "id": 1,
        "question": "桂味荔枝有什么特点？",
        "answer": "桂味荔枝是...",
        "createdAt": "2026-03-11T10:30:00"
      }
    ]
  }
}
```

### 5.2 获取会话列表

**接口**: `GET /chat/sessions`

**请求参数**:
| 参数名 | 类型 | 必选 | 说明 |
|--------|------|------|------|
| userId | Long | 否 | 用户ID |
| page | Integer | 否 | 页码，默认1 |
| size | Integer | 否 | 每页数量，默认10 |

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 20,
    "page": 1,
    "size": 10,
    "items": [
      {
        "sessionId": "session_123456",
        "title": "桂味荔枝咨询",
        "lastMessage": "桂味荔枝有什么特点？",
        "updatedAt": "2026-03-11T10:30:00"
      }
    ]
  }
}
```

---

## 6. 评测模块

### 6.1 获取评测问题列表

**接口**: `GET /evaluation/questions`

**请求参数**:
| 参数名 | 类型 | 必选 | 说明 |
|--------|------|------|------|
| type | String | 否 | 问题类型 |
| evaluated | Boolean | 否 | 是否已评测 |
| page | Integer | 否 | 页码，默认1 |
| size | Integer | 否 | 每页数量，默认20 |

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 100,
    "page": 1,
    "size": 20,
    "items": [
      {
        "id": 1,
        "question": "桂味荔枝有什么特点？",
        "referenceAnswer": "桂味荔枝是...",
        "systemAnswer": null,
        "bleuScore": null,
        "humanScore": null,
        "evaluated": false
      }
    ]
  }
}
```

### 6.2 提交系统答案

**接口**: `POST /evaluation/answer`

**请求参数**:
```json
{
  "id": 1,
  "systemAnswer": "桂味荔枝是..."
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "bleuScore": 0.75
  }
}
```

### 6.3 提交人工评分

**接口**: `POST /evaluation/score`

**请求参数**:
```json
{
  "id": 1,
  "humanScore": 4
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "success"
}
```

### 6.4 获取评测统计

**接口**: `GET /evaluation/stats`

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 100,
    "evaluated": 80,
    "avgBleuScore": 0.72,
    "avgHumanScore": 4.1,
    "byType": [
      {
        "type": "品种介绍",
        "count": 15,
        "avgBleuScore": 0.75
      }
    ]
  }
}
```

---

## 7. 系统模块

### 7.1 系统健康检查

**接口**: `GET /health`

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "status": "healthy",
    "services": {
      "neo4j": "connected",
      "milvus": "connected",
      "ollama": "connected"
    },
    "timestamp": "2026-03-11T10:30:00"
  }
}
```

### 7.2 初始化系统

**接口**: `POST /system/init`

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "knowledgeGraph": "initialized",
    "vectorStore": "initialized"
  }
}
```

---

## 错误码说明

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未授权 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |
| 503 | 服务不可用（如Ollama未启动） |

**错误响应示例**:
```json
{
  "code": 400,
  "message": "请求参数错误",
  "data": null
}
```
