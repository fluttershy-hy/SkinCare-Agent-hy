#!/usr/bin/env bash
set -euo pipefail

# 研究发现工具：只收集文献元数据，不把检索结果自动变成风险规则。
# 用法：bash scripts/discover-literature.sh [输出 JSON 路径]
output_path="${1:-/tmp/skincare-literature-candidates.json}"
if [[ "$output_path" != /* ]]; then
  echo '输出路径必须是绝对路径' >&2
  exit 2
fi
mkdir -p "$(dirname "$output_path")"
temp_dir="$(mktemp -d /tmp/skincare-literature.XXXXXX)"
trap 'rm -rf "$temp_dir"' EXIT

# 窄主题检索，避免把“某微生物能利用脂质”直接等同于“护肤品会加重个人皮炎”。
queries=(
  'TITLE_ABS:Malassezia AND TITLE_ABS:"fatty acid"'
  'TITLE_ABS:"seborrheic dermatitis" AND TITLE_ABS:Malassezia AND TITLE_ABS:lipid'
  'TITLE_ABS:"seborrheic dermatitis" AND TITLE_ABS:emollient'
  'TITLE_ABS:Malassezia AND TITLE_ABS:"skin cells" AND TITLE_ABS:lipid'
)

for i in "${!queries[@]}"; do
  url='https://www.ebi.ac.uk/europepmc/webservices/rest/search'
  curl --fail --silent --show-error --get --max-time 30 \
    --data-urlencode "query=${queries[$i]}" \
    --data-urlencode 'format=json' \
    --data-urlencode 'resultType=core' \
    --data-urlencode 'pageSize=20' \
    "$url" -o "$temp_dir/$i.json"
  jq -e '.resultList.result | type == "array"' "$temp_dir/$i.json" >/dev/null
done

jq -s --arg fetched "$(date -u +%F)" --argjson queries "$(printf '%s\n' "${queries[@]}" | jq -R . | jq -s .)" '
  [range(0; length) as $i | .[$i].resultList.result[]? | {
    query: $queries[$i], source: "Europe PMC", fetchedOn: $fetched,
    title: (.title // ""), author: (.authorString // ""),
    journal: (.journalTitle // .journalInfo.journal.title // ""),
    year: (.pubYear // ""), doi: (.doi // ""), pmid: (.pmid // ""),
    publicationType: (.pubTypeList.pubType // []),
    url: (if (.doi // "") != "" then "https://doi.org/" + .doi
          elif (.pmid // "") != "" then "https://pubmed.ncbi.nlm.nih.gov/" + .pmid + "/"
          else "" end)
  }]
  | unique_by(if .doi != "" then .doi elif .pmid != "" then .pmid else .title end)
  | map(select(.title != "" and .url != ""))
' "$temp_dir"/*.json > "$output_path"
echo "已保存 $(jq length "$output_path") 条候选文献：$output_path"
echo '这些只是检索候选；尚未核对实验条件、证据质量或对护肤品的适用性。'
