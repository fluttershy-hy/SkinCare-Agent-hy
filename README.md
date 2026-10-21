# SkinCare Copilot

皮肤健康日常信息整理演示项目。当前只完成 Day 1：单轮模型聊天。它不提供诊断、处方、药物剂量或保证有效的治疗方案。请只用虚构用户和演示输入。

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

## Day 1 运行

要求 JDK 17+ 和 Maven 3.9+。项目以 Java 17 为编译目标，固定 Spring Boot 3.5.16、LangChain4j OpenAI 模块 1.22.0。模型应支持 OpenAI 兼容的聊天接口。

```bash
export SKINCARE_LLM_API_KEY='在终端或环境设置里填入密钥'
export SKINCARE_LLM_BASE_URL='https://api.openai.com/v1' # 使用其他兼容接口时修改
export SKINCARE_LLM_MODEL='gpt-4o-mini'                 # 按提供商支持情况修改
mvn test
mvn spring-boot:run
```

打开本机 `http://localhost:8080/`。页面发送 `POST /api/chat`，JSON 为 `{"message":"你好"}`，返回 `{"answer":"..."}`。没有密钥时接口返回 503；空消息返回 400。不要把密钥写进仓库或网页。

Day 1 不保存历史：页面会显示本次浏览器中的消息，但每次请求只向模型发送当前输入。事实性护肤及饮食问题还没有知识库支持，不能把此时的模型回答视为已核实资料。
