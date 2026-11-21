# 在项目根目录执行：. ./scripts/setup-reranker.ps1
# 下载一次模型，随后为当前 PowerShell 会话设置两个路径。
$ErrorActionPreference = 'Stop'
$rerankerDir = Join-Path (Get-Location) 'data/reranker'
New-Item -ItemType Directory -Force -Path $rerankerDir | Out-Null
$modelPath = Join-Path $rerankerDir 'model.onnx'
$tokenizerPath = Join-Path $rerankerDir 'tokenizer.json'
if (-not (Test-Path $modelPath)) {
    Invoke-WebRequest 'https://huggingface.co/Xenova/bge-reranker-base/resolve/main/onnx/model_quantized.onnx' -OutFile "$modelPath.download"
    Move-Item "$modelPath.download" $modelPath
}
if (-not (Test-Path $tokenizerPath)) {
    Invoke-WebRequest 'https://huggingface.co/Xenova/bge-reranker-base/resolve/main/tokenizer.json' -OutFile "$tokenizerPath.download"
    Move-Item "$tokenizerPath.download" $tokenizerPath
}
if ((Get-FileHash -Algorithm SHA256 $modelPath).Hash.ToLowerInvariant() -ne "dd98f3e67837d23210a6b7550c08cced4f61845b940ac45be3565840a10f3244") { throw "重排模型校验失败，请删除后重新下载：$modelPath" }
if ((Get-FileHash -Algorithm SHA256 $tokenizerPath).Hash.ToLowerInvariant() -ne "48564c5c7d3fa64d85d95e65414a542385f88b0f128fd8d4163fd7a57f2be05c") { throw "tokenizer 校验失败，请删除后重新下载：$tokenizerPath" }
$env:SKINCARE_RERANK_MODEL = $modelPath
$env:SKINCARE_RERANK_TOKENIZER = $tokenizerPath
Write-Host '重排模型已配置到当前 PowerShell 会话；接下来在此窗口运行 mvn spring-boot:run。'
