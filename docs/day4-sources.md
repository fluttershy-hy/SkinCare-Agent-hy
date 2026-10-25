# Day 4 来源核实记录

2026-10-24 从 AAD、NICE 官网获取页面正文，并逐条核对 `src/main/resources/knowledge/entries.json` 中的英文短摘录与官网文字。初始检出 `ab92fe1869b15be2a03729fe0145a153e5623f54` 与远端 `main` 一致，开始 Day 4 前工作区干净。只整理少量条目，没有复制网页全文。

| ID | 主题与适用范围 | 官方页面与章节 | 核实结果 |
| --- | --- | --- | --- |
| `acne-diet-evidence` | 痤疮饮食研究，证据不一致 | AAD, [Can the right diet get rid of acne?](https://www.aad.org/public/diseases/acne/causes/diet), “A low-glycemic diet may lead to fewer pimples” | 原文短摘录逐字匹配；保留“需要更多研究”的不确定性 |
| `rosacea-triggers-vary` | 已确诊玫瑰痤疮的诱因因人而异 | AAD, [Triggers could be causing your rosacea flare-ups](https://www.aad.org/public/diseases/rosacea/triggers/find), “Important to know what triggers your rosacea” | 原文短摘录逐字匹配；不把泛红等同于玫瑰痤疮 |
| `rosacea-fragrance-free` | 玫瑰痤疮易刺激皮肤的产品选择 | AAD, [7 rosacea skin care tips dermatologists recommend](https://www.aad.org/public/diseases/rosacea/triggers/tips), “Go fragrance free” | 原文短摘录逐字匹配；区分 fragrance-free 与 unscented |
| `contact-dermatitis-irritant-allergic` | 接触性皮炎可涉及刺激或过敏反应 | AAD, [Eczema types: Contact dermatitis overview](https://www.aad.org/public/diseases/eczema/types/contact-dermatitis), “What is contact dermatitis?” | 原文短摘录逐字匹配；不凭泛红确认过敏 |
| `eczema-child-triggers` | **12 岁以下**特应性湿疹患儿的诱因评估 | NICE, [Atopic eczema in under 12s: diagnosis and management](https://www.nice.org.uk/guidance/cg57/chapter/Recommendations), 1.4.1.1 | 原文短摘录逐字匹配；正文同一建议列出刺激物、感染和接触性过敏原 |
| `eczema-child-emollients` | **12 岁以下**特应性湿疹患儿的日常保湿 | NICE, [Atopic eczema in under 12s: diagnosis and management](https://www.nice.org.uk/guidance/cg57/chapter/Recommendations), 1.5.1.4 | 原文短摘录逐字匹配；范围仅为儿童特应性湿疹 |

“泛红”“过敏”“敏感”不是同一个诊断。资料库中包含与泛红相关的玫瑰痤疮内容，以及与接触性过敏原相关的儿童湿疹建议，但检索命中只表示文本相关，不会替用户确认疾病或过敏史。与原定痤疮主题相比，此次扩展了资料范围；后续如继续扩库，仍应逐项核对原文、适用人群和不确定性。

## 本日功能边界

- 启动时导入仓库中的人工整理条目，按来源独立切分，用本地 `bge-small-zh-v1.5-q` 模型生成 512 维向量，索引放在内存中。
- 检索端点与网页展示片段、分数、标题、机构、章节、链接、核实日期及对应英文摘录。分数只表示向量相似性，不代表医学证据强度。
- 目前没有自动爬取、自动验证原文或资料更新检测。英文摘录是人工核对后保存，JSON 格式校验不能证明事实正确。
- 聊天尚未接入 RAG 生成和引用；这是 Day 5 的任务。本日没有加入意图路由、餐食生成和过敏原校验。
