# 前端说明

前端基于 Vue 3 + TypeScript + Vite，负责以下页面与交互:

- 登录页
- 平台总览
- 智能问答
- 对话历史
- 培训课堂
- 文档管理
- 知识图谱
- 用药指南
- 病害识别
- 满意度问卷
- 系统设置
- 评测中心

## 本机启动

```bash
cd frontend
npm install
npm run dev
```

默认访问地址: `http://localhost:5173`

## 生产构建

```bash
npm run build
```

## 认证说明

- 登录成功后，token 会存储在浏览器本地
- 受保护页面会自动检查登录态
- token 失效后会自动跳转回登录页

## 接口约定

- 默认请求前缀: `/api`
- 登录态通过 `Authorization: Bearer <token>` 发送
- 文档、评测、历史、问答均依赖登录态
