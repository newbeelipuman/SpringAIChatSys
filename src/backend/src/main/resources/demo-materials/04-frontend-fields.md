# 前端展示字段（answer / citations / retrievedChunks）

## answer

- `answer` 是模型生成的回答文本
- 建议在答辩时强调：回答是基于检索到的 Context 生成

## citations

- `citations` 仅包含引用元信息（chunkId/docId/documentName/source/score）
- 用途：在 UI 上显示“引用来自哪些文档/片段”

## retrievedChunks

- `retrievedChunks` 包含完整片段文本（content）
- 用途：
  - 展示“模型看到了什么”
  - 解释为什么给出这个 answer
  - 帮助调试 chunking 与 topK

## 答辩小技巧

可以演示：

- 调整 TopK，观察 citations/retrievedChunks 的变化
- 先 reset 再一键 ingest 演示素材，然后提问验证链路可用

