# 简历素材（可直接粘贴）

## 项目名

**HuiYi AI Data Assistant · 企业经营数据智能分析助手**（开源，GitHub: huiyi-ai-data-assistant）

## 技术栈

Java 17、Spring Boot 3、SpringAI、MyBatis-Plus、MySQL、Redis、Python、FastAPI、Chroma、pandas、sqlglot、Vue3、TypeScript、Element Plus、ECharts、Docker Compose

## 项目描述（简历正文版）

面向企业经营分析场景，构建基于 Java + Python 协同架构的智能数据分析助手。系统支持 RAG 知识库问答与 Text-to-SQL 数据查询：知识问题经 Chroma 向量检索后由 LLM 生成带引用的回答；数据问题由 LLM 生成 SQL，经四道安全防线校验后以只读账号执行，并结合 pandas 完成结果画像、图表推荐与自然语言总结。前端 Vue3 提供流式问答、知识库管理与可视化分析三个页面，Docker Compose 一键编排五个服务。

## 职责与亮点

1. **设计 Java 主服务 + Python Sidecar 架构**：Java 负责业务编排、Prompt 管理、LLM 调用、Text-to-SQL、SQL 执行和结果组装；Python 负责文档解析、分块、Chroma 检索、SQL 安全校验和数据分析。统一封装 WebClient 调用（超时控制、4xx 透传、5xx 降级）。

2. **实现 RAG 知识库问答全链路**：Markdown / PDF / Word 解析、段落优先分块（可配 chunk_size/overlap、稳定 chunk_id）、向量化入库、Top-K 检索、0.5 分阈值过滤低分命中、引用来源展示与无答案兜底，杜绝口径编造。

3. **实现 Text-to-SQL 分析链路与四道安全防线**：意图层拦截数据修改动词 → sqlglot 静态校验（单语句/SELECT-only/表白名单/危险函数/自动补 LIMIT）→ 只读账号执行（5s 超时/500 行上限）→ 全量审计日志。Prompt 注入 Schema（缓存 10 分钟）与业务口径，SQL 提取支持 JSON/代码块/裸 SELECT 三级兜底。

4. **基于 pandas 实现查询结果画像与图表推荐**（行数、数值列 sum/max/min、日期/类别/占比规则推荐 line/bar/pie/table），前端 ECharts 按类型渲染，SSE 流式问答自洽事件协议（session/token/references）。

5. **工程化交付**：Docker Compose 一键编排 5 个服务；Python 覆盖率 97%（90 用例）、Java 31 用例（MockWebServer/H2 打桩）；GitHub Actions 三端 CI；浏览器自动化五场景回归脚本。

## 技术难点与 STAR 问答

### 难点 1：如何保证 LLM 生成的 SQL 绝对安全？

- **S**：Text-to-SQL 链路中，LLM 可能生成 DELETE/DROP 等危险 SQL，直接执行会造成数据事故。
- **T**：设计一套不依赖 LLM 自觉性的确定性防护体系。
- **A**：建立四道防线——①意图层：问题含修改类动词直接拒绝，不进 LLM；②sqlglot AST 静态校验：单语句、SELECT-only、表白名单、危险函数与 INTO OUTFILE 拦截、自动补 LIMIT；③数据库层：独立只读账号仅授 SELECT；④执行层：5s 超时、500 行上限、审计日志。配合 8 类攻击向量单测（含注释绕过、多语句、子查询非白名单表）。
- **R**：验收场景中「删除所有销售订单」被确定性拒绝；曾发现 LLM 用 `WHERE 1=2` 空查询规避，由此补强了意图层拦截。

### 难点 2：RAG 如何避免"看似命中实则无关"导致的幻觉引用？

- **S**：知识库未收录的问题（如"年假天数"）也能以 0.33 相似度命中无关片段，LLM 照答且挂引用，误导用户。
- **T**：让"未命中"成为确定性行为而非依赖模型判断。
- **A**：为 bge-m3 向量实测相关/无关分数分布（0.74 vs 0.33），设定 0.5 阈值过滤低分命中；无命中时短路返回固定兜底话术、清空引用；有命中时 Prompt 强约束"只根据参考资料回答"并要求标注来源文件名。
- **R**：未命中场景 100% 返回兜底文案且无引用；命中场景回答均带可溯源引用。

### 难点 3：SSE 流式问答前端不渲染的隐蔽 bug

- **S**：流式接口联调时，浏览器气泡只有光标没有文字，curl 却正常。
- **T**：定位前端状态更新断链点。
- **A**：分层排查——网络层确认 200 + text/event-stream；解析层确认事件分发；最终定位为 Vue3 响应式陷阱：Pinia 中流式追加直接改写裸对象引用，不触发 trigger()，视图永不更新。改为通过响应式代理（数组下标访问）写入。同时为 SSE 协议补充 session 首事件解决多轮续话。
- **R**：流式打字机效果流畅，五场景浏览器自动化回归通过；该排查过程沉淀为开发文档「常见坑」条目。
