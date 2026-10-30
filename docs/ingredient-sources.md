# 成分模块的小型资料记录

查阅日期：2026-10-24。仅复用仓库 Day 4 已人工核对的 AAD 原文；对应原文、译文及章节见 `src/main/resources/knowledge/entries.json`。本次未核实三亩大叔、温唯的具体文章，所以没有将其观点或文章写入事实知识库。

| 名称 | 演示别名映射 | 可支持的结论 | 局限 | 来源标题 / URL |
| --- | --- | --- | --- | --- |
| 香精（FRAGRANCE） | 香精、香料、Parfum、Fragrance | AAD 针对玫瑰痤疮易受刺激的皮肤建议选择 fragrance-free 产品，以降低刺激可能。 | 不能证明含香精产品一定刺激；不能诊断个人过敏。别名映射是本项目的小型标准化规则，不是 AAD 原文中的别名清单。 | [7 rosacea skin care tips dermatologists recommend](https://www.aad.org/public/diseases/rosacea/triggers/tips) |
| 接触后的刺激或过敏反应 | 无成分别名 | 接触性皮炎可能由刺激或过敏反应引起，两者不同。 | 不能从一张成分表判断个人致敏成分，也不能把泛红直接认定为过敏。 | [Eczema types: Contact dermatitis overview](https://www.aad.org/public/diseases/eczema/types/contact-dermatitis) |

烟酰胺/Niacinamide 与甘油/丙三醇/Glycerin 目前仅用于示例名称标准化，没有纳入功效事实条目；回答不得据此声称浓度、功效或适合程度。若要补充成分功效，需要先核实专业原文及适用范围，再增加知识条目和测试。

## 温唯文章（科普阅读层，仅作问题线索）

2026-10-30 打开用户提供的[成分文章目录](https://miriammibao.com/blog-articles/skincare/ingredients/)，并逐篇打开以下原文。日期来自文章页 `article:published_time`。只保存书目信息与自己归纳的常见问题，不复制正文。文章中存在一些广泛的致痘、刺激及浓度断言，当前没有逐项专业证据支撑，所以未将这些断言导入事实向量索引。

| 文章与原文 | 作者 / 发布日期 | 本项目记录的问题 | 不采用的内容 |
| --- | --- | --- | --- |
| [10種該害怕與不該害怕的化妝保養品成分,小心別掉入廣告陷阱](https://miriammibao.com/ingredient/) | 温唯 / 2018-09-30 | 只看成分表能否知道浓度及实际效果？ | 文中未经本项目专业来源核实的具体浓度和风险概括 |
| [匡列200多種致粉刺成分,油痘肌請小心這些成分的彩妝保養品](https://miriammibao.com/comedones-ingredients-in-skin-care/) | 温唯 / 2020-07-07 | 网站的致粉刺评分能否直接预测整款产品或个人反应？ | “200 多种”风险名单及对个人致痘性的断言 |

两篇文章的资料层级是 C（科普话题/表达）；AAD/NICE 条目仍是模块的事实资料层级 B。完整书目元数据在 `src/main/resources/knowledge/creator-articles.json`。
