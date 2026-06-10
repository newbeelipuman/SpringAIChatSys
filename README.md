# SpringAIChatSys

SpringAIChatSys is a lightweight RAG question answering system built with Spring AI, Spring Boot, and Vue. It was developed as a graduation design project and includes a runnable backend, a web frontend, screenshots, and exported system diagrams.

## Features

- User login, registration, and simple role-based access control
- Persistent and temporary knowledge ingestion
- RAG-based question answering with retrieved context
- Personal question history and knowledge records
- Admin view for user and history inspection
- Runtime status page for model, vector store, and data-source state

## Tech Stack

- Backend: Spring Boot, Spring AI, Spring MVC, JPA
- Frontend: Vue 3, Vite, Element Plus
- Vector store: in-memory mode by default, optional Milvus
- Database: optional MySQL for structured data and demo auth
- LLM: local demo flow by default, optional DeepSeek / OpenAI-compatible APIs

## Repository Layout

```text
src/backend      Spring Boot backend
src/frontend     Vue + Vite frontend
src/scripts      Local test and helper scripts
screenshots      UI screenshots
diagrams_png     Exported PNG diagrams
```

## Quick Start

Backend:

```powershell
cd src/backend
copy .env.example .env.local
mvn spring-boot:run
```

Frontend:

```powershell
cd src/frontend
npm install
$env:VITE_API_PROXY_TARGET="http://localhost:8080"
npm run dev
```

Default URLs:

```text
Backend:  http://localhost:8080
Frontend: http://localhost:5173
```

For full configuration details, see [CONFIG_AND_RUN.md](CONFIG_AND_RUN.md).

## Default Mode

The first run can use the default local mode:

```text
RAG_VECTOR_STORE_MODE=memory
MYSQL_ENABLED=false
```

This mode does not require MySQL, Milvus, or an external model API key. To enable DeepSeek, MySQL, or Milvus, edit your local `.env.local` file. Do not commit private environment files.

## Notes

This repository is a public code and UI showcase. It does not include thesis documents, defense slides, private runtime data, real API keys, editable diagram sources, or local database/vector-store files.

All rights reserved. Please do not reuse this project for coursework, papers, competitions, software copyright registration, patent applications, commercial projects, or redistribution without written permission.

## 中文说明

SpringAIChatSys 是一个基于 Spring AI、Spring Boot 和 Vue 的 RAG 智能问答系统，主要用于毕业设计和作品展示。仓库中保留了可运行的前后端代码、配置说明、系统截图和 PNG 流程图；论文全文、答辩材料、真实密钥、本地运行数据和可编辑流程图源文件没有公开。

首次运行建议使用默认内存模式：

```text
RAG_VECTOR_STORE_MODE=memory
MYSQL_ENABLED=false
```

需要接入 DeepSeek、MySQL 或 Milvus 时，再按 [CONFIG_AND_RUN.md](CONFIG_AND_RUN.md) 修改本地配置。

