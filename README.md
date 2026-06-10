# SpringAIChatSys

一个基于 Spring AI、Spring Boot 和 Vue 的 RAG 智能问答系统。项目主要用于毕业设计展示，包含后端接口、前端页面、运行配置说明、系统截图和部分流程图导出图。

## 项目内容

```text
src/backend      Spring Boot 后端
src/frontend     Vue + Vite 前端
src/scripts      本地测试和运行脚本
screenshots      系统运行效果截图
diagrams_png     流程图、架构图、ER 图等 PNG 导出图
```

这里放的是公开展示版本，不包含论文全文、答辩 PPT、真实密钥、本地运行数据、可编辑流程图源文件等私人材料。

## 技术栈

- 后端：Spring Boot、Spring AI、Spring MVC、JPA
- 前端：Vue 3、Vite、Element Plus
- 向量检索：默认内存模式，可切换 Milvus
- 结构化数据：可选 MySQL
- 模型接口：支持 DeepSeek / OpenAI 兼容接口，默认也可以用本地演示模式跑通流程

## 快速运行

后端：

```powershell
cd src/backend
copy .env.example .env.local
mvn spring-boot:run
```

前端：

```powershell
cd src/frontend
npm install
$env:VITE_API_PROXY_TARGET="http://localhost:8080"
npm run dev
```

默认后端地址：

```text
http://localhost:8080
```

默认前端地址通常是：

```text
http://localhost:5173
```

更完整的配置说明见 [CONFIG_AND_RUN.md](CONFIG_AND_RUN.md)。

## 默认运行方式

第一次运行建议使用默认配置：

```text
RAG_VECTOR_STORE_MODE=memory
MYSQL_ENABLED=false
```

这样不需要先安装 MySQL、Milvus，也不需要外部模型密钥，适合先检查系统页面和基础接口。

如果要接入 DeepSeek、MySQL 或 Milvus，再修改本地 `.env.local`。不要把 `.env.local` 提交到仓库。

## 主要功能

- 登录、注册和基础角色区分
- 知识录入、临时知识和持久知识管理
- 基于检索结果的 RAG 问答
- 问答历史和知识记录查看
- 管理员视角的用户与历史数据查看
- 系统运行状态和模型配置状态展示

## 说明

本仓库只用于项目展示和学习交流。未经作者书面许可，请勿用于论文、课程作业、竞赛、软著、专利、商业项目或二次公开发布。

