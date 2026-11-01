# 脂溢性皮炎／马拉色菌文献发现

`bash scripts/discover-literature.sh /tmp/skincare-literature-candidates.json` 使用 Europe PMC 公开接口按四个窄主题批量检索，保存标题、作者、期刊、年份、DOI/PMID 与来源。它不抓全文，也不会把检索结果自动变成风险规则。2026-11-01 云环境网络放行后实跑得到 **47 条去重候选**；候选数量不是有效证据数量。脚本在离线夹具中也通过了请求循环、元数据去重和输出格式验证。

## 已核对的原始研究

| 研究 | 核对到的实验与结果 | 不能推出什么 |
| --- | --- | --- |
| [Lipid-dependent growth of Malassezia spp. in defined medium with single fatty acids](https://www.ebi.ac.uk/europepmc/articles/PMC12416284), *FEMS Yeast Research* (2025), DOI [10.1093/femsyr/foaf043](https://doi.org/10.1093/femsyr/foaf043) | 全文 Methods/Results：在定义培养基、1% NP-40 Tergitol、1 mM 单一游离脂肪酸条件下，*M. furfur* 与 *M. pachydermatis* 对棕榈酸 C16:0 生长最明显；其他脂肪酸反应不同，且菌种间有差异。 | 不能将 C11–C24 的任何成分统一标为“高风险”，更不能从培养基生长推断某面霜会让人的脂溢性皮炎加重。 |
| [Characteristics of Malassezia furfur at various pH and effects of Malassezia lipids on skin cells](https://www.ebi.ac.uk/europepmc/articles/PMC11374913), *Applied Microbiology and Biotechnology* (2024), DOI [10.1007/s00253-024-13292-2](https://doi.org/10.1007/s00253-024-13292-2) | 全文摘要与方法：使用角质形成细胞系和不同 pH 条件，观察马拉色菌转化的脂质与细胞相关转录变化；作者报告结果随实验条件变化。 | 细胞结果不能证明某具体护肤品对患者安全、有害或有效。 |
| [Long-Chain Acyl-CoA Synthetase is Associated with the Growth of Malassezia spp.](https://www.ebi.ac.uk/europepmc/articles/PMC6958399), *Journal of Fungi* (2019), DOI [10.3390/jof5040088](https://doi.org/10.3390/jof5040088) | 阅读全文：研究把马拉色菌基因放入酵母模型，比较不同链长脂肪酸及酶活性；不同菌种/酶对 C12 等底物的反应有差异。 | 基因与培养模型不能直接变成成品护肤品的个人风险标签。该篇暂未进入 RAG，供后续核验机制。 |

前两篇已作为带实验条件和局限的短条目加入 `knowledge/entries.json`，网页可检索并显示原论文链接；没有复制论文全文。此阶段**没有**创建“马拉色菌高风险成分黑名单”、致痘 0–5 分或“面部不能用、身体可以用”的规则。

继续评估候选时需记录：研究对象是菌株培养、细胞模型、动物还是真实患者；脂肪酸是游离物还是成品配方；浓度、使用部位和暴露时间；是否测量皮炎症状；结果能否跨菌种、部位及产品版本外推。只有能支撑的窄结论才进入知识条目，不能将“可被微生物利用”直接转换为“含该成分的产品一定诱发皮炎”。
