# 品牌官网全成分提取（2026-11-02）

用户提供具体产品**全名、销售市场、包装版本标识**后，系统有两条入口：

1. 在聊天框只填产品名、市场和版本：若已配置 `SKINCARE_SEARCH_API_KEY` 与 `SKINCARE_PRODUCT_OFFICIAL_HOSTS`，`ProductSearchService` 先定位官网候选页；`OfficialIngredientService` 再逐页请求并核对。搜索摘要不参与成分提取。
2. 在页面填写同样三项及品牌官网具体产品 URL，点击“核对并提取官网全成分”：调用 `POST /api/products/official-ingredients`，成功后把成分载入原聊天窗口的成分框，并显示来源链接。

示例环境设置（PowerShell）：

```powershell
$env:SKINCARE_PRODUCT_OFFICIAL_HOSTS="www.example-brand.com,shop.example-brand.com"
$env:SKINCARE_SEARCH_API_KEY="你自己的 Tavily 密钥" # 仅按产品名自动寻找官网时需要
mvn spring-boot:run
```

`SKINCARE_PRODUCT_OFFICIAL_HOSTS` 必须由使用者逐个核实属于品牌官方，精确填写主机名，不接受模糊后缀。直接提供官网 URL 不需要 Tavily 密钥。服务只请求 HTTPS、默认 443 端口、白名单精确主机名，禁止跳转与含用户信息的 URL。响应必须为 HTML，页面标题包含产品全名，可见文本包含市场和版本，且 `<h2>`/`<h3>` 等标题明确标记“全成分 / 完整成分表 / Full Ingredients / Complete Ingredient List”，或页面模板提供 `data-full-ingredients`。提取 5～150 项、总长不超过 6000 字符后返回来源 URL、查阅日期及限制说明。任何一项不符，就不导入。

这是一种**受约束的官网页面提取**，并不等于全网所有产品、所有官网模板都可自动读取。当前云环境的 HTTP 出站策略为 restricted；其有效允许域名包含研究、GitHub 等站点，但不包含 CeraVe、理肤泉、Vanicream 官网或 Tavily。对这些品牌官网发出的请求得到代理 HTTP 403，因此尚未能把其中任一具体品牌产品作为已实测、已核实的演示配方。自动搜索还依赖允许访问 Tavily、配置搜索密钥和品牌官方域名。已有测试使用虚构品牌 HTML 固定样例覆盖正确提取、版本不符、错产品、摘要不完整、仿冒域名和本地地址；这些测试证明程序逻辑，不证明某个真实商品的配方。

成功返回的是“该官网页面明确标注的候选全成分”，使用时仍应与手中包装的地区、批次和标签核对。它不会自动保存成用户确认的包装成分表，也不能凭列表判断个人过敏原因、准确浓度或实际功效。页面不可访问或信息不够时，系统继续显示官网候选页和资料不足提示，不从搜索摘要补全成分。
