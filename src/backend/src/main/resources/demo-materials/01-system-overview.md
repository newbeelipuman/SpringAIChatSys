# 系统概览（SpringAIChatSys）

本项目是一个用于答辩展示的轻量级 RAG（Retrieval-Augmented Generation，检索增强生成）演示系统。

## 组件

- 后端：Spring Boot + Spring AI
- 向量库：当前使用 `memory` 模式（进程内内存列表），用于快速演示
- Embedding：可使用本地 demo embedding 或外部模型提供的 embedding
- 前端：Vue（调用后端 API 并展示 answer / citations / retrievedChunks）

## 核心接口（demo）

- `POST /demo/ingest`：写入原始文本（分块 -> embedding -> 写入向量库）
- `POST /demo/search`：语义检索 TopK（返回 retrievedChunks）
- `POST /demo/chat`：RAG 问答（返回 answer + citations + retrievedChunks）
- `GET /demo/health`：查看向量库模式、chunk 数量等信息

## 展示要点

- **重启后需要重新 ingest**：因为 `memory` 向量库不持久化（仅用于答辩演示）
- **可追溯引用**：retrievedChunks 中包含 chunkId/docId/source，前端可用它做引用展示

