# RAG 流程说明

RAG 的目标：在大模型生成答案时，先从“知识库”中检索相关内容，再把检索结果作为上下文提供给模型，从而提升事实性与可控性。

## 数据写入（Ingest）

1. 接收原始文本（例如论文摘要、系统说明、接口文档）
2. 分块（chunking）：把长文本切成多个片段（chunks），并可设置 overlap
3. 生成向量（embedding）：每个 chunk 变成一个向量
4. 写入向量库：保存 `chunkId/docId/documentName/content/source/embedding`

## 语义检索（Search）

1. 对用户问题生成 query embedding
2. 向量相似度检索 TopK chunks
3. 返回 `retrievedChunks`（用于前端展示与调试）

## 问答（Chat）

1. 先检索得到 retrievedChunks
2. 构造 prompt（把 chunks 作为 Context）
3. 调用 LLM 生成 answer
4. 返回：
   - `answer`：模型回答
   - `citations`：引用元信息（chunkId/docId/source/score）
   - `retrievedChunks`：检索到的完整片段（带 content）

答辩展示时，可以用同一个问题同时演示 `/demo/search` 与 `/demo/chat` 的差异。

