# 成分模块的小型资料记录

AAD 资料查阅于 2026-10-24；温唯文章查阅于 2026-10-30。原始来源与归纳文字见 `src/main/resources/knowledge/entries.json`。三亩大叔的具体文章尚未提供链接，也没有导入。

| 名称 | 演示别名映射 | 可支持的结论 | 局限 | 来源标题 / URL |
| --- | --- | --- | --- | --- |
| 香精（FRAGRANCE） | 香精、香料、Parfum、Fragrance | AAD 针对玫瑰痤疮易受刺激的皮肤建议选择 fragrance-free 产品，以降低刺激可能。 | 不能证明含香精产品一定刺激；不能诊断个人过敏。别名映射是本项目的小型标准化规则，不是 AAD 原文中的别名清单。 | [7 rosacea skin care tips dermatologists recommend](https://www.aad.org/public/diseases/rosacea/triggers/tips) |
| 接触后的刺激或过敏反应 | 无成分别名 | 接触性皮炎可能由刺激或过敏反应引起，两者不同。 | 不能从一张成分表判断个人致敏成分，也不能把泛红直接认定为过敏。 | [Eczema types: Contact dermatitis overview](https://www.aad.org/public/diseases/eczema/types/contact-dermatitis) |

烟酰胺/Niacinamide 与甘油/丙三醇/Glycerin 目前仅用于示例名称标准化，没有纳入功效事实条目；回答不得据此声称浓度、功效或适合程度。若要补充成分功效，需要先核实专业原文及适用范围，再增加知识条目和测试。

## 温唯文章：14 篇目录文章的观点摘要

2026-10-30 打开用户提供的[成分文章目录](https://miriammibao.com/blog-articles/skincare/ingredients/)及其中全部 14 篇文章。每篇的原文链接、标题与发布日期在 `src/main/resources/knowledge/creator-articles.json`；每篇的简短中文观点摘要在 `src/main/resources/knowledge/entries.json`，已进入本地 Embedding 检索。没有复制正文、图片或文章中的成分名单。

这批条目记录的是“温唯在文章中如何讨论问题”，不是对所有成分风险或效果的独立确认。尤其是固定前五位决定功效、排在香精后就无效、某类成分必然致痘或刺激等绝对判断，不能用作项目的确定性规则。检索结果应显示作者、文章链接和观点适用边界；与 AAD/NICE 等专业资料不同层级。目录文章涉及视黄醇、VC、抗氧化、防腐剂、致粉刺评分、美白、表面活性剂、润肤剂、油保养、洁面、成分表和查询工具，具体题目见上述 JSON。
