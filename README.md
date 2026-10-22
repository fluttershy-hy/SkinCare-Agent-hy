# SkinCare Copilot

皮肤健康日常信息整理演示项目。当前完成 Day 1～3：网页聊天、用户主动确认的画像，以及按用户和会话隔离的近期对话。它不提供诊断、处方、药物剂量或保证有效的治疗方案。请只用虚构用户和演示输入。

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

近期对话由 LangChain4j `MessageWindowChatMemory` 保留最近 20 条消息，只存在于当前后端进程，重启后消失；已确认画像保存在 H2 文件中，重启后仍可查询。记忆键同时包含用户 ID 和会话 ID，避免不同用户或会话共用上下文。页面在当前标签页的 `sessionStorage` 保留会话 ID；刷新页面会沿用会话，点击“开始新会话”会换 ID。当前没有过期清理或跨进程共享记忆。用户 ID 只是演示标识，尚无登录认证，请勿放入真实个人资料。事实性护肤及饮食问题还没有知识库支持，不能把此时的模型回答视为已核实资料。

当前版本遇到常见餐食请求会返回 HTTP 501，不生成未经检索和过敏原校验的方案。这是正式餐食功能完成前的临时边界；它只识别常见表达，不能当作完整的意图路由或安全校验。
