# SkinCare Copilot

皮肤健康日常信息整理演示项目。当前完成 Day 1～6：Day 4 导入少量 AAD/NICE 资料，Day 5 把检索片段与真实来源接入聊天，Day 6 增加保守的四路分流。它不提供诊断、处方、药物剂量或保证有效的治疗方案。请只用虚构用户和演示输入。

## 九天路线

1. 项目骨架、单轮聊天和网页。
2. 持久画像和 LangChain4j `@Tool` 查询。
3. 按用户及会话隔离的短期记忆。
4. 核实资料、导入、切分、Embedding 和检索展示。
5. 带来源的 RAG 回答与依据不足处理。
6. 闲聊、知识、画像和综合建议路由。
7. 结构化餐食草稿和 Java 过敏原校验。
8. 最多两次纠错重试及失败降级、日志与耗时。
9. 完整演示、测试、架构与面试讲解。

只有收到“开始 Day N”后才开发该日功能。

## 运行 Day 1～3

要求 JDK 17+ 和 Maven 3.9+。项目以 Java 17 为编译目标，固定 Spring Boot 3.5.16、LangChain4j 1.22.0。默认使用 DeepSeek 的 OpenAI 兼容接口，模型需支持工具调用才能查询画像。

```bash
export SKINCARE_LLM_API_KEY='在终端或环境设置里填入密钥'
# 默认：SKINCARE_LLM_BASE_URL=https://api.deepseek.com
# 默认：SKINCARE_LLM_MODEL=deepseek-flash
mvn test
mvn spring-boot:run
```

在 VS Code 中打开项目文件夹，使用集成终端设置 `SKINCARE_LLM_API_KEY` 并运行上面的 Maven 命令。Windows PowerShell 的设置方式是 `$env:SKINCARE_LLM_API_KEY="你的密钥"`。如果改用 `deepseek-v4-pro`，再设置 `SKINCARE_LLM_MODEL=deepseek-v4-pro`。这里使用的是 OpenAI 兼容接口地址；`https://api.deepseek.com/anthropic` 是另一种协议的地址，不适用于当前 `OpenAiChatModel`。请以 DeepSeek 控制台显示的可用模型和工具调用支持情况为准。

打开本机 `http://localhost:8080/`。先为虚构用户填写并勾选确认画像，再点击“保存画像”或“查询画像”。画像写入项目目录下的 `data/`（已被 Git 忽略）；再次启动应用仍可读取。未勾选确认时，后端拒绝保存。`GET /api/profiles/{userId}` 查询，`PUT /api/profiles/{userId}` 保存，保存 JSON 例如 `{"skinType":"油性","allergens":["花生"],"confirmed":true}`。

页面聊天发送 `POST /api/chat`，JSON 为 `{"userId":"demo-user","sessionId":"session-1","message":"我的肤质是什么？"}`，返回 `{"answer":"..."}`。同一用户和会话 ID 会承接近期对话；点击“开始新会话”会换一个会话 ID。模型可主动调用只读画像工具，服务日志会出现 `tool=getConfirmedProfile`。没有密钥时聊天接口返回 503；空消息或缺少会话 ID 返回 400。不要把密钥写进仓库或网页。

近期对话由 LangChain4j `MessageWindowChatMemory` 保留最近 20 条消息，只存在于当前后端进程，重启后消失；已确认画像保存在 H2 文件中，重启后仍可查询。记忆键同时包含用户 ID 和会话 ID，避免不同用户或会话共用上下文。页面在当前标签页的 `sessionStorage` 保留会话 ID；刷新页面会沿用会话，点击“开始新会话”会换 ID。当前没有过期清理或跨进程共享记忆。用户 ID 只是演示标识，尚无登录认证，请勿放入真实个人资料。事实性护肤及饮食问题会检索当前小型知识库；命中片段不等于所有回答内容都已被人工核实，请核对显示的原文与适用范围。

当前版本遇到常见餐食请求会返回 HTTP 501，不生成未经检索和过敏原校验的方案。这是正式餐食功能完成前的临时边界；它只识别常见表达，不能当作完整的餐食安全校验。

## Day 4：独立知识片段检索

`mvn verify` 会运行原有画像/会话/聊天测试和新增导入、切分、本地中文向量检索及接口测试。不需要模型 API 密钥。新依赖为 `langchain4j-embeddings-bge-small-zh-v15-q:1.22.0-beta32`（与 LangChain4j 1.22.0 配套），模型和分词器随 Maven 依赖提供；Embedding 在本机 CPU 执行，不调用 DeepSeek，也不在启动时抓取官网。首次安装需下载 ONNX Runtime 等依赖。

启动命令仍是 `mvn spring-boot:run`。页面新增“知识片段与来源”，与现有聊天分别使用独立接口：

```bash
curl http://localhost:8080/api/knowledge/sources
curl -X POST http://localhost:8080/api/knowledge/search \
  -H 'Content-Type: application/json' \
  -d '{"query":"痤疮皮肤如何清洁？","topK":3}'
```

检索输入最多 500 字符，`topK` 默认 3、允许 1～5。返回片段、来源标题/章节/链接、人工核实日期、对应条目英文原文和相似度，不返回生成式回答。分数为 LangChain4j 的归一化余弦相似度 `(cosine + 1) / 2`，最低 0.75，只用于检索排序，不是医学可信度或证据等级；阈值尚未用正式知识条目校准。

启动时导入 `src/main/resources/knowledge/entries.json`，字段校验后按来源分别切分：最多 180 个 Unicode 码点、重叠 30 个码点、优先句末；保留确定性片段 ID 及原译文中的 UTF-16 起止位置。使用 BGE 中文量化模型生成 512 维向量，内存索引每次启动重建，正式条目随仓库保存。少量条目采用串行计算，无需向量数据库。测试夹具只在测试中使用，不会进入正式索引。

`entries.json` 现含 6 条经 AAD/NICE 官网核对的短条目，覆盖痤疮饮食研究的不确定性、玫瑰痤疮的个人诱因与护肤、12 岁以下特应性湿疹的诱因评估与保湿。原文与适用范围见 [Day 4 来源记录](docs/day4-sources.md)。网页只展示命中的片段与原文出处；“泛红”不能据此自动诊断为过敏或玫瑰痤疮。

## Day 5～6：带来源的聊天与问题路由

`POST /api/chat` 仍接收 `userId`、`sessionId`、`message`。响应增加 `route` 和 `sources`；原有 `answer` 保留。四种路由是 `SMALL_TALK`、`KNOWLEDGE`、`PROFILE`、`COMBINED`。日志记录路由结果。规则在 `ChatRouter` 中明确列出：涉及皮肤、饮食、成分等事实的问题进入检索；直接问已确认画像走画像分支；同时涉及个人情况和事实的请求走综合分支。规则是第一版可读、可测的保守匹配，不宣称能覆盖所有表述。

知识/综合分支先从 Day 4 的小型资料库检索最多 3 个片段。空结果直接说明资料不足，不调用模型；有结果时仅把命中片段提供给模型，Java 根据实际命中附上来源，网页展示出处。综合分支还读取 H2 中已确认的画像；模型仍可调用只读画像 `@Tool`。聊天中的推断不会写入画像。模型生成内容可能有遗漏或偏差，请查看附带的原文及适用范围；检索相似度阈值 0.75 是演示设置，尚未用系统评测校准。

餐食方案仍由后端返回 501，Day 7 的结构化草稿和过敏原强制校验尚未实现。真实模型回答需设置 `SKINCARE_LLM_API_KEY`，默认 DeepSeek 兼容地址与 `deepseek-flash`；无密钥可运行测试、独立检索以及知识依据不足分支。`mvn verify` 的测试用本机假模型覆盖检索片段进入提示词、来源只来自命中数据、缺少依据时不调用模型，以及容易误分的短事实问题。

## 具体护肤品成分来源查询（试验性）

Day 6 后新增独立的只读产品页面查询区，供用户输入**包装上的完整产品名**，寻找可信品牌官网的具体产品页面。`POST /api/products/search` 接收 `{"productName":"完整产品名"}`。目前接入 Tavily Search API，需在本地 PowerShell 的运行窗口设置 `SKINCARE_SEARCH_API_KEY`，另用逗号分隔的 `SKINCARE_PRODUCT_OFFICIAL_HOSTS` 指定你已核实属于该品牌官网的域名。只有 HTTPS、精确匹配所配域名且页面标题包含产品全名的搜索结果会展示；官网域名或搜索密钥缺失时返回明确提示。不要把密钥写入仓库。此接口不会发送用户 ID、画像或聊天内容到搜索服务，只发送产品名。

**搜索摘要不是完整成分表。** 当前接口只定位官网候选页，不提取或验证完整 INCI 成分，也不判断个人对哪个成分过敏。即使日后核实完整配方，也只能把成分与已确认的资料对照、指出待专业评估的线索；单凭一次皮肤反应及配方列表无法确认致敏成分。品牌名如“薇诺娜”无法确定是哪一款产品；需要完整产品名，最好有官方产品页。聊天里说“我可能对某产品过敏”时，若只给品牌名，会要求完整产品名；若给出具体产品名，会自动调用同一只读查询。它只返回官网候选链接与不足说明，绝不输出对个人致敏成分的推断。搜索仍需配置密钥和可信官网域名。

## 护肤品成分解读与双产品对比（独立模块）

网页新区域支持粘贴一款或两款完整成分表，再填写肤质、关注问题、已确认过敏的**成分**和具体问题。`POST /api/ingredients/analyze` 使用相同字段；只有 `first` 必填，`second` 可省略。需要 `SKINCARE_LLM_API_KEY` 生成自然语言分析；缺少密钥或模型调用失败时只返回成分解析、强制命中与明确的能力不足说明。此模块没有修改原有画像或餐食逻辑。

调用顺序：`IngredientController.analyze` → `IngredientService.analyze` → `IngredientNames.parse` 保留原文与顺序并用小型别名表标准化 → 读取已有画像中的肤质（本次肤质优先）→ Java 对本次明确确认的过敏**成分**进行逐项精确/已知别名匹配 → 复用 `KnowledgeService.search` 检索相关 AAD 片段 → 模型输出 JSON `answer` → Java 校验结构并在命中时强制加警示、返回匹配项和来源。画像现有 `allergens` 是一般过敏原，**不会**被自动解释为已确认过敏成分；本次确认的成分也不会持久写入画像。未勾选确认时拒绝把填写的成分当作过敏史。

当前名称表只有香精/香料/Parfum/Fragrance、烟酰胺/Niacinamide、甘油/丙三醇/Glycerin 的示例映射；其余名称只做字面标准化。相同名称的匹配也不能证明个人对实际产品的反应。已有知识库中仅 [AAD 玫瑰痤疮无香料建议](https://www.aad.org/public/diseases/rosacea/triggers/tips) 与 [AAD 接触性皮炎概述](https://www.aad.org/public/diseases/eczema/types/contact-dermatitis) 可用于相关解释，均已在 `entries.json` 记录标题、原文、链接和 2026-10-24 核实日期。前一条适用范围为玫瑰痤疮护理；后一条区分刺激和过敏。没有针对烟酰胺浓度、甘油实际效果或具体产品功效的已核实资料，回答应明确资料不足。博主文章仅作为另一个科普阅读目录，不进入事实检索索引。

这是独立于九天主线的新增功能；没有开始 Day 7 的餐食功能。成分分割依赖用户提供的逗号/顿号/分号/换行分隔文本，不识别图片、括号内复杂复合名称或未知同义词；无法由列表推算准确浓度、配方工艺及实际功效。模型的自然语言结论仍可能有错误，强制匹配和来源链接由 Java 控制。

### 温唯文章目录（科普阅读层）

`knowledge/creator-articles.json` 只收录两篇已打开核对的文章的作者、标题、原文链接、发布日期、查阅日期和文章提出的常见问题；不复制全文或 200 多种所谓致粉刺成分表。`CreatorArticleService` 独立加载并校验目录，`GET /api/ingredients/articles` 可查看，相关问题的分析响应把文章列为 `readingSuggestions`。这不是 AAD/NICE 的事实 RAG 索引，文章内容不会进入模型证据提示词，网页也明确标为“延伸阅读，非事实依据”。来源核对及取舍见 `docs/ingredient-sources.md`。
