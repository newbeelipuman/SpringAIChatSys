# 向量库模式：memory vs Milvus（答辩口径）

## 当前演示方案：memory

- `rag.vector-store.mode=memory`
- 向量数据存在 JVM 内存中
- 优点：零依赖、启动快、方便答辩演示
- 缺点：**后端重启数据会丢失，需要重新 ingest**

## 真实落地方案：Milvus（后续）

- Milvus 是常见的向量数据库/向量检索引擎
- 典型用途：持久化保存向量、支持大规模向量检索、索引加速、水平扩展
- 在本项目中可以把 `memory` 替换成 Milvus，实现“写入后可跨重启复用”

## 演示建议

答辩时可以明确说明：

- 本次重点展示完整 RAG 链路（ingest -> search -> chat）
- 持久化（Milvus）属于工程落地增强点，后续可无缝切换

