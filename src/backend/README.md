# Spring AI Chat Sys

Lightweight RAG question answering demo for the thesis project.

## Public Repository And Privacy Notice

This repository is prepared as a public portfolio/demo project. Runtime secrets and personal local data are intentionally excluded.

- Do not commit `.env.local`, `.env`, API keys, database passwords, bearer tokens, Milvus tokens, local audit logs, generated build output, or local vector-store data.
- Use `.env.example` as the public configuration template, then create your own `.env.local` for private local runs.
- Local runtime data under `data/`, generated Java output under `target/`, frontend dependencies/build output, and `.milvus/` are ignored by Git.
- Demo usernames and passwords shown in this README are placeholders. Replace them with your own local values before running MySQL-backed auth.
- This project is a thesis/portfolio demonstration, not a production security baseline.

## Current Scope

- `GET /health`: service health.
- `GET /llm/health`: model and embedding mode.
- `GET /llm/config`: non-sensitive model configuration.
- `POST /llm/ask`: direct LLM call (with local demo fallback).
- `POST /demo/ingest`: split text, generate embeddings and store chunks.
- `POST /demo/search`: semantic TopK retrieval.
- `POST /demo/chat`: RAG question answering (answer + citations + retrievedChunks).
- `GET /demo/health`: RAG dependency status.
- `GET /demo/materials`: list built-in demo materials.
- `POST /demo/ingest/materials`: ingest built-in demo materials from classpath.
- `POST /demo/reset`: clear the active vector store.
- `GET /demo/me/knowledge`: list the current demo user's ingest history.
- `GET /demo/me/questions`: list the current demo user's question history.
- `GET /demo/admin/knowledge`: list all demo users' ingest history. Requires `ADMIN`.
- `GET /demo/admin/questions`: list all demo users' question history. Requires `ADMIN`.
- `GET /demo/admin/users/{userKey}/knowledge`: inspect one user's ingest history before deletion. Requires `ADMIN`.
- `GET /demo/admin/users/{userKey}/questions`: inspect one user's question history before deletion. Requires `ADMIN`.
- `GET /demo/admin/users/{userKey}/temporary-knowledge`: inspect one user's temporary knowledge before deletion. Requires `ADMIN`.
- `DELETE /demo/admin/users/{userKey}/data`: clear one user's demo history and temporary knowledge. Requires `ADMIN`.
- `POST /auth/register`: register a demo-level MySQL-backed user.
- `POST /auth/login`: return a demo bearer token.
- `POST /auth/logout`: revoke a demo bearer token.
- `GET /auth/me`: inspect the currently resolved identity.
- `GET /auth/health`: inspect auth and permission-table readiness.
- `GET /auth/admin/users`: list MySQL-backed auth users. Requires `ADMIN`.
- `POST /auth/admin/users/{username}/role`: assign `USER` or `ADMIN`. Requires `ADMIN`.
- `POST /auth/admin/users/{username}/password/reset`: reset another user's password. Requires `ADMIN`.

By default the project runs in local demo mode. It uses local hash embeddings and an in-memory vector store so the thesis demo can run without external services.

## Demo Users And History

The current frontend uses a login/register gate before entering the RAG workspace. The old `X-Demo-User` header remains supported by the backend for compatibility and API-level demo testing, but it is no longer the primary visible entry point in the frontend.

The project now also includes a demo-level authentication path for the thesis defense. Identity resolution is compatible with the old flow:

1. `Authorization: Bearer <token>` wins when the token maps to an enabled MySQL user session.
2. If there is no valid token, `X-Demo-User` is used.
3. If neither is present, the backend falls back to the built-in demo user.

This is a foundation for replacing `X-Demo-User`, not production security. Passwords are stored as BCrypt hashes, tokens are random demo tokens whose hashes are stored in MySQL `auth_session`, and there is no full production-grade JWT lifecycle, refresh-token rotation, lockout policy or production RBAC.

Demo roles are intentionally limited:

- `USER`: register/login, ingest persistent knowledge, ingest temporary knowledge, ask/search/chat, inspect and clear the current user's own history, and view the current user's MySQL summary/diagnostics.
- `ADMIN`: all `USER` abilities plus global admin history endpoints, user list inspection, role assignment, administrator password reset for other users, and deletion of another user's demo history / temporary knowledge.

The admin endpoints are checked on the backend through a small RBAC permission matrix. A logged-in `USER`, an unauthenticated request with `X-Demo-User`, or the built-in fallback demo user receives HTTP 403 with `error=FORBIDDEN`.

The backend records:

- knowledge ingest history: user id, document id, document name, source, vector store mode, chunk count, embedding dimensions and content preview.
- question history: user id, request type, question, answer preview, retrieved chunk count, citation chunk ids, mode and elapsed time.

Records are appended to `data/demo-audit-log.jsonl` so they survive backend restarts. The file is ignored by Git. This is suitable for thesis/demo traceability; a production system should replace it with authenticated users plus a database table.

MySQL prewarm has been added behind `MYSQL_ENABLED=false` by default. Milvus continues to own vectors and retrieval; MySQL is reserved for structured business data such as users, auth sessions, knowledge document metadata, question history, ingest history, permission relations, and suggestion cache. See `docs/mysql-prewarm.md`.

Auth tables are also behind `MYSQL_ENABLED=true`. For local schema prototyping:

```powershell
$env:MYSQL_ENABLED="true"
$env:MYSQL_PASSWORD="change-me-db-password"
$env:MYSQL_DDL_AUTO="update"
mvn spring-boot:run
```

Optional demo admin bootstrap:

```powershell
$env:AUTH_BOOTSTRAP_ADMIN_USERNAME="admin"
$env:AUTH_BOOTSTRAP_ADMIN_PASSWORD="change-me-admin-password"
```

When MySQL auth is enabled and no `ADMIN` account exists, startup creates or promotes that username as `ADMIN` with a BCrypt password hash. This is only a thesis/demo initialization path.

For stricter runs with `MYSQL_DDL_AUTO=none`, create or migrate the structured tables first. Missing auth tables do not block the old `X-Demo-User` demo path; register/login will return a clear MySQL/auth-table error until the tables exist.

## Current Frontend Scope

The Vue frontend now includes:

- standalone login/register page.
- Element Plus password inputs with `show-password`.
- authenticated RAG workspace.
- personal question/knowledge history.
- temporary knowledge and persistent knowledge ingest.
- status page with Chinese business labels instead of raw JSON.
- admin-only global history entry, diagnostic view, user management, role assignment, password reset, and user data cleanup.
- standalone `/admin` route for the admin-only user and data management view.
- `/history` remains the personal-history entry; administrators use `/admin` for global user management.

The `/admin` view can inspect role permissions and MySQL-backed auth users, then open one user's detail view before clearing data. Administrators can assign `USER` / `ADMIN`, reset another user's password, and clear another user's question history, knowledge-record history, or temporary knowledge. Global diagnostics remain on the status page. Organization management and Milvus ACL filtering remain outside this demo scope.

PowerShell examples:

```powershell
$headers = @{ "X-Demo-User" = "alice" }
Invoke-RestMethod -Headers $headers http://localhost:8080/demo/me/knowledge | ConvertTo-Json -Depth 10
Invoke-RestMethod -Headers $headers http://localhost:8080/demo/me/questions | ConvertTo-Json -Depth 10

# Admin endpoints require an ADMIN bearer token.
$adminLogin = Invoke-RestMethod -Method Post http://localhost:8080/auth/login `
  -ContentType 'application/json' `
  -Body '{"username":"admin","password":"change-me-admin-password"}'
$adminHeaders = @{ Authorization = "Bearer $($adminLogin.token)" }
Invoke-RestMethod -Headers $adminHeaders http://localhost:8080/demo/admin/knowledge | ConvertTo-Json -Depth 10
Invoke-RestMethod -Headers $adminHeaders http://localhost:8080/demo/admin/questions | ConvertTo-Json -Depth 10
```

Auth smoke example:

```powershell
$register = Invoke-RestMethod -Method Post http://localhost:8080/auth/register `
  -ContentType 'application/json' `
  -Body '{"username":"alice","password":"change-me-user-password"}'

$login = Invoke-RestMethod -Method Post http://localhost:8080/auth/login `
  -ContentType 'application/json' `
  -Body '{"username":"alice","password":"change-me-user-password"}'

$headers = @{ Authorization = "Bearer $($login.token)" }
Invoke-RestMethod -Headers $headers http://localhost:8080/auth/me | ConvertTo-Json -Depth 5
Invoke-RestMethod -Headers $headers http://localhost:8080/demo/me/questions | ConvertTo-Json -Depth 10
```

## Vector Store Modes

The RAG vector store is selected by configuration:

- `RAG_VECTOR_STORE_MODE=memory`: default local demo mode. Chunks are stored in JVM memory and disappear after restart.
- `RAG_VECTOR_STORE_MODE=milvus`: use a real Milvus collection. Chunks persist in Milvus and can be searched after backend restart.

Milvus-related settings:

```powershell
$env:RAG_VECTOR_STORE_MODE="milvus"
$env:RAG_MILVUS_HOST="localhost"
$env:RAG_MILVUS_PORT="19530"
$env:RAG_MILVUS_COLLECTION_NAME="spring_ai_chat_chunks"
$env:RAG_MILVUS_DIMENSION="384"
```

`RAG_MILVUS_DIMENSION` must match the active embedding model. The built-in local hash embedding is `384` dimensions. If you enable a real Spring AI embedding model, set the Milvus dimension to that model's vector size and use a separate collection.

Optional Milvus auth/connection overrides:

```powershell
$env:RAG_MILVUS_URI="http://localhost:19530"
$env:RAG_MILVUS_DATABASE_NAME="default"
$env:RAG_MILVUS_USERNAME=""
$env:RAG_MILVUS_PASSWORD=""
$env:RAG_MILVUS_TOKEN=""
```

At startup, Milvus mode connects to Milvus, creates the configured collection if missing, creates a COSINE vector index, and loads the collection for search. If Milvus is unavailable, the backend still starts and `/demo/health` reports `available=false` plus `lastError`; ingest/search calls will fail until Milvus is reachable.

## Run Milvus With Docker

Milvus standalone can be started with the official Docker Compose file:

```powershell
mkdir .milvus
cd .milvus
Invoke-WebRequest `
  -Uri https://github.com/milvus-io/milvus/releases/download/v2.6.14/milvus-standalone-docker-compose.yml `
  -OutFile docker-compose.yml
docker compose up -d
docker compose ps
```

Milvus listens on `localhost:19530`; its Web UI is usually available at `http://127.0.0.1:9091/webui/`.

Then start the backend in Milvus mode:

```powershell
$env:RAG_VECTOR_STORE_MODE="milvus"
$env:RAG_MILVUS_HOST="localhost"
$env:RAG_MILVUS_PORT="19530"
$env:RAG_MILVUS_COLLECTION_NAME="spring_ai_chat_chunks"
$env:RAG_MILVUS_DIMENSION="384"
mvn spring-boot:run
```

DeepSeek mode can use the same vector store settings. Put the `RAG_*` values in `.env.local` next to `DEEPSEEK_API_KEY`, then run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-deepseek.ps1
```

Verify:

```powershell
Invoke-RestMethod http://localhost:8080/demo/health | ConvertTo-Json -Depth 5
Invoke-RestMethod -Method Post http://localhost:8080/demo/ingest/materials -ContentType 'application/json' -Body '{}' | ConvertTo-Json -Depth 10
Invoke-RestMethod -Method Post http://localhost:8080/demo/search -ContentType 'application/json' -Body '{"question":"Milvus stores what?","topK":3}' | ConvertTo-Json -Depth 10
```

Expected `/demo/health` details in Milvus mode include `vectorStoreMode=milvus`, `available=true`, `collection`, `database`, `dimension`, and `chunkCount`.

## Run Backend

Local demo mode:

```powershell
mvn spring-boot:run
```

External chat model mode:

```powershell
$env:OPENAI_API_KEY="your-api-key"
$env:OPENAI_CHAT_MODEL="gpt-4o-mini"
mvn spring-boot:run -Dspring-boot.run.profiles=openai
```

OpenAI-compatible providers can be used by overriding the base URL:

```powershell
$env:OPENAI_BASE_URL="https://api.openai.com"
$env:OPENAI_CHAT_COMPLETIONS_PATH="/v1/chat/completions"
$env:OPENAI_API_KEY="your-api-key"
$env:OPENAI_CHAT_MODEL="gpt-4o-mini"
mvn spring-boot:run -Dspring-boot.run.profiles=openai
```

DeepSeek is the easiest OpenAI-compatible replacement:

```powershell
$env:DEEPSEEK_API_KEY="your-deepseek-api-key"
mvn spring-boot:run "-Dspring-boot.run.profiles=deepseek"
```

If you keep local credentials in `.env.local`, start DeepSeek mode with:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-deepseek.ps1
```

Optional DeepSeek overrides:

```powershell
$env:DEEPSEEK_BASE_URL="https://api.deepseek.com"
$env:DEEPSEEK_CHAT_COMPLETIONS_PATH="/chat/completions"
$env:DEEPSEEK_CHAT_MODEL="deepseek-v4-flash"
```

Alibaba Cloud Model Studio / Qwen is also supported:

```powershell
$env:OPENAI_BASE_URL="https://dashscope.aliyuncs.com"
$env:OPENAI_CHAT_COMPLETIONS_PATH="/compatible-mode/v1/chat/completions"
$env:OPENAI_API_KEY="your-dashscope-api-key"
$env:OPENAI_CHAT_MODEL="qwen-plus"
mvn spring-boot:run -Dspring-boot.run.profiles=openai
```

Check the active mode:

```powershell
Invoke-RestMethod http://localhost:8080/llm/config
Invoke-RestMethod http://localhost:8080/llm/health
```

On Windows PowerShell, use UTF-8 output and request bodies when testing Chinese text:

```powershell
chcp 65001
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$body = @{ question = "请用一句话说明 RAG 是什么？" } | ConvertTo-Json -Compress
Invoke-RestMethod -Method Post http://localhost:8080/llm/ask -ContentType "application/json; charset=utf-8" -Body $body | ConvertTo-Json -Depth 5
```

## Smoke Test (API)

To verify `/llm/ask`, `/demo/ingest`, `/demo/search`, `/demo/chat` (including Chinese text + citations + retrieved chunks), run:

```powershell
cd .\scripts
powershell -ExecutionPolicy Bypass -File .\smoke-api.ps1 -BaseUrl http://localhost:8080 -TopK 3
```

To test built-in demo materials endpoints (recommended for defense/demo):

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\smoke-api.ps1 -BaseUrl http://localhost:8080 -TopK 3 -IngestMaterials
```

To let the script start a backend instance automatically (optional):

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\smoke-api.ps1 -StartServer
```

DeepSeek mode example (requires `DEEPSEEK_API_KEY`):

```powershell
$env:DEEPSEEK_API_KEY="your-deepseek-api-key"
powershell -ExecutionPolicy Bypass -File .\scripts\smoke-api.ps1 -StartServer -Profiles deepseek
```

Expected mode values:

- `local-demo`: no external chat model is active.
- `spring-ai-openai`: Spring AI is calling the configured external chat model.

## Frontend (Vue)

The Vue frontend lives in `作品/frontend` and proxies API calls to the backend via `/api`.

```powershell
cd .\frontend
npm install
npm run dev
```

Open `http://localhost:5173` and use:

- “知识录入” -> `POST /demo/ingest` or `POST /demo/ingest/materials`.
- “新对话” -> `POST /demo/chat` (answer + citations + retrievedChunks); `POST /llm/ask` can be used for direct-model comparison.
- “历史总览” -> ordinary users see personal history only; administrators can switch to global history.
- “运行状态” -> ordinary users see personal sync status; administrators also see global diagnostics.

## Demo Materials (Recommended)

Built-in source materials live under:

- `src/main/resources/demo-materials/`

After backend restart in memory mode, you can restore the knowledge base quickly. In Milvus mode, this import writes into the configured Milvus collection and survives backend restarts:

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/demo/reset -ContentType 'application/json' -Body '{}' | Out-Null
Invoke-RestMethod -Method Post http://localhost:8080/demo/ingest/materials -ContentType 'application/json' -Body '{}' | ConvertTo-Json -Depth 10
```

## Quick Demo

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/demo/ingest -ContentType 'application/json' -Body '{"docId":"demo","documentName":"Demo Doc","content":"Spring AI is used to call chat and embedding models. Milvus stores text vectors for semantic retrieval.","source":"manual"}'

Invoke-RestMethod -Method Post http://localhost:8080/demo/chat -ContentType 'application/json' -Body '{"question":"What stores text vectors?","topK":3}'
```

## Thesis Demo Suggestion

It is worth showing both states briefly:

- Before external model configuration: `/llm/config` shows `chatMode=local-demo`; `/demo/chat` returns a fixed fallback answer but still shows citations and retrieved chunks.
- After external model configuration: `/llm/config` shows `chatMode=spring-ai-openai`; `/demo/chat` returns a generated natural language answer plus the same citation mechanism.

This comparison proves that the RAG retrieval chain is independent from the model provider, while Spring AI can be switched from local demo fallback to a real external model by configuration.

## Thesis Limitations And Outlook

The current implementation is suitable for thesis demonstration, but the following items should be described as limitations or future work:

- role control is a demo-level RBAC matrix over `USER` / `ADMIN`, not a production multi-tenant authorization system.
- `knowledge_permission` records metadata ownership/read relations, but Milvus retrieval is not filtered by ACL yet.
- admin pages can delete another user's demo history / temporary knowledge, reset another user's password, and assign `USER` / `ADMIN`; they do not implement organization management or persistent Milvus ACL deletion.
- JSONL and MySQL history mirroring is a local demo migration path, not a production audit pipeline.
- temporary knowledge remains JVM-memory scoped and disappears after backend restart.
- multi-format parsing, hybrid search, reranking, full Docker deployment, monitoring, and richer admin operations remain future improvements.

## Tests

Default tests keep using memory mode:

```powershell
mvn test
```

Optional Milvus smoke test, after Docker Milvus is running:

```powershell
$env:ENABLE_MILVUS_SMOKE_TEST="true"
mvn "-Dtest=MilvusSmokeTest" test
```

The smoke test uses collection `spring_ai_chat_chunks_smoke`, clears it, ingests one document, and verifies vector search through Milvus.
