# Configuration And Run Guide

This guide describes how to run the public showcase version locally.

## Requirements

- JDK 17+
- Maven 3.9+
- Node.js 20+
- npm 10+

Optional services:

- MySQL 8.x, only if `MYSQL_ENABLED=true`
- Milvus standalone, only if `RAG_VECTOR_STORE_MODE=milvus`
- OpenAI-compatible API provider, only if external LLM calls are enabled

The default mode does not require MySQL, Milvus, or an external API key.

## Project Layout

```text
src/backend
src/frontend
src/scripts
screenshots
diagrams_png
```

## Backend Configuration

Go to the backend directory:

```powershell
cd src/backend
```

Create a local environment file from the public template:

```powershell
copy .env.example .env.local
```

Edit `.env.local` locally. Do not commit it.

Important variables:

```text
DEEPSEEK_API_KEY=your-deepseek-api-key
DEEPSEEK_CHAT_MODEL=deepseek-v4-flash

RAG_VECTOR_STORE_MODE=memory
RAG_MILVUS_HOST=localhost
RAG_MILVUS_PORT=19530
RAG_MILVUS_COLLECTION_NAME=spring_ai_chat_chunks
RAG_MILVUS_DIMENSION=384

MYSQL_ENABLED=false
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_DATABASE=spring_ai_chat_sys
MYSQL_USERNAME=root
MYSQL_PASSWORD=
MYSQL_DDL_AUTO=none
MYSQL_HISTORY_READ_ENABLED=false

AUTH_BOOTSTRAP_ADMIN_USERNAME=
AUTH_BOOTSTRAP_ADMIN_PASSWORD=
```

Recommended first run:

```text
RAG_VECTOR_STORE_MODE=memory
MYSQL_ENABLED=false
```

This runs the demo with local memory storage and no database.

## Run Backend

From `src/backend`:

```powershell
mvn spring-boot:run
```

Default backend URL:

```text
http://localhost:8080
```

Quick health checks:

```powershell
Invoke-RestMethod http://localhost:8080/health
Invoke-RestMethod http://localhost:8080/demo/health | ConvertTo-Json -Depth 5
Invoke-RestMethod http://localhost:8080/llm/config | ConvertTo-Json -Depth 5
```

## Frontend Configuration

Go to the frontend directory:

```powershell
cd src/frontend
```

Install dependencies:

```powershell
npm install
```

The frontend dev server proxies API requests to the backend. If your backend runs on the default port, use:

```powershell
$env:VITE_API_PROXY_TARGET="http://localhost:8080"
```

## Run Frontend

From `src/frontend`:

```powershell
npm run dev
```

Open the URL printed by Vite, usually:

```text
http://localhost:5173
```

## Optional: MySQL Mode

Use MySQL only when you need persisted structured data and demo auth tables.

Example local values:

```powershell
$env:MYSQL_ENABLED="true"
$env:MYSQL_HOST="localhost"
$env:MYSQL_PORT="3306"
$env:MYSQL_DATABASE="spring_ai_chat_sys"
$env:MYSQL_USERNAME="root"
$env:MYSQL_PASSWORD="change-me-db-password"
$env:MYSQL_DDL_AUTO="update"
$env:MYSQL_HISTORY_READ_ENABLED="true"
```

Optional admin bootstrap:

```powershell
$env:AUTH_BOOTSTRAP_ADMIN_USERNAME="admin"
$env:AUTH_BOOTSTRAP_ADMIN_PASSWORD="change-me-admin-password"
```

Use your own local password values. Do not commit them.

## Optional: Milvus Mode

Use Milvus only when you need a persistent vector store.

```powershell
$env:RAG_VECTOR_STORE_MODE="milvus"
$env:RAG_MILVUS_HOST="localhost"
$env:RAG_MILVUS_PORT="19530"
$env:RAG_MILVUS_COLLECTION_NAME="spring_ai_chat_chunks"
$env:RAG_MILVUS_DIMENSION="384"
```

If Milvus is not available, keep:

```text
RAG_VECTOR_STORE_MODE=memory
```

## Optional: DeepSeek Or OpenAI-Compatible Mode

For DeepSeek:

```powershell
$env:DEEPSEEK_API_KEY="your-deepseek-api-key"
$env:DEEPSEEK_CHAT_MODEL="deepseek-v4-flash"
mvn spring-boot:run -Dspring-boot.run.profiles=deepseek
```

For OpenAI-compatible providers, configure the relevant environment variables in `application-openai.yml` or use the backend README as reference.

Never commit real API keys.

## Build Checks

Backend tests:

```powershell
cd src/backend
mvn test
```

Frontend build:

```powershell
cd src/frontend
npm run build
```

## Public Repository Safety Checklist

Before pushing, confirm these files or folders are not included:

```text
.env
.env.local
node_modules
target
dist
data
.milvus
*.log
*.docx
*.pdf
*.pptx
*.drawio
*.vsdx
*.emf
*.zip
```

