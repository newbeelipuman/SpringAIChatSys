param(
  [string]$BaseUrl = 'http://localhost:8080',
  [int]$TopK = 3,
  [switch]$StartServer,
  [string]$Profiles = '',
  [switch]$IngestMaterials
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Enable-Utf8Console {
  try { chcp 65001 | Out-Null } catch {}
  try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}
  try { $script:OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}
}

function Utf8FromBase64 {
  param([string]$Base64)
  return [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($Base64))
}

function Invoke-Json {
  param(
    [ValidateSet('GET', 'POST')] [string]$Method,
    [string]$Url,
    [object]$Body = $null
  )

  Add-Type -AssemblyName System.Net.Http | Out-Null

  $client = New-Object System.Net.Http.HttpClient
  $request = New-Object System.Net.Http.HttpRequestMessage ([System.Net.Http.HttpMethod]::$Method), $Url
  $request.Headers.Accept.ParseAdd('application/json')

  if ($null -ne $Body) {
    $json = $Body | ConvertTo-Json -Compress
    $request.Content = New-Object System.Net.Http.StringContent($json, [System.Text.Encoding]::UTF8, 'application/json')
  }

  $response = $client.SendAsync($request).Result
  $bytes = $response.Content.ReadAsByteArrayAsync().Result
  $text = [System.Text.Encoding]::UTF8.GetString($bytes)

  if (-not $response.IsSuccessStatusCode) {
    throw "HTTP $([int]$response.StatusCode) $($response.ReasonPhrase): $text"
  }

  if ([string]::IsNullOrWhiteSpace($text)) {
    return $null
  }
  return $text | ConvertFrom-Json
}

function Assert-True {
  param(
    [bool]$Condition,
    [string]$Message
  )
  if (-not $Condition) {
    throw "ASSERT FAILED: $Message"
  }
}

function Wait-Backend {
  param([int]$TimeoutSeconds = 45)
  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    try {
      Invoke-Json -Method GET -Url "$BaseUrl/health" | Out-Null
      return
    } catch {
      Start-Sleep -Milliseconds 500
    }
  }
  throw "Backend not ready within $TimeoutSeconds seconds: $BaseUrl"
}

function Start-BackendIfNeeded {
  if (-not $StartServer) {
    return $null
  }

  try {
    Invoke-Json -Method GET -Url "$BaseUrl/health" | Out-Null
    Write-Host "Backend already running: $BaseUrl"
    return $null
  } catch {}

  $projectRoot = Split-Path -Parent $PSScriptRoot
  $jar = Join-Path $projectRoot 'target\spring-ai-chat-sys-0.1.0-SNAPSHOT.jar'
  if (-not (Test-Path $jar)) {
    throw "Jar not found: $jar (please run: mvn -q -DskipTests package)"
  }

  $args = @('-jar', $jar)
  if (-not [string]::IsNullOrWhiteSpace($Profiles)) {
    $args += "--spring.profiles.active=$Profiles"
  }

  $logDir = Join-Path $projectRoot 'target'
  $outLog = Join-Path $logDir 'smoke-server.log'
  $errLog = Join-Path $logDir 'smoke-server.err.log'

  Write-Host "Starting backend: java $($args -join ' ')"
  $proc = Start-Process -FilePath java -ArgumentList $args -WorkingDirectory $projectRoot -NoNewWindow -PassThru `
    -RedirectStandardOutput $outLog -RedirectStandardError $errLog

  Wait-Backend
  return $proc
}

function Stop-BackendIfStarted {
  param($Proc)
  if ($null -eq $Proc) {
    return
  }
  try {
    Stop-Process -Id $Proc.Id -Force
    Write-Host "Stopped backend PID=$($Proc.Id)"
  } catch {}
}

Enable-Utf8Console

$questionRag = Utf8FromBase64 '6K+355So5LiA5Y+l6K+d6K+05piOIFJBRyDmmK/ku4DkuYg='
$docContent = Utf8FromBase64 'U3ByaW5nIEFJIOeUqOS6juiwg+eUqOWkp+ivreiogOaooeWei+WSjCBFbWJlZGRpbmcg5qih5Z6L44CCTWlsdnVzIOeUqOS6juWtmOWCqOaWh+acrOWQkemHj++8jOW5tuaUr+aMgeivreS5ieebuOS8vOW6puajgOe0ouOAglJBRyDns7vnu5/kvJrlhYjmo4DntKLnm7jlhbPnn6Xor4bniYfmrrXvvIzlho3lsIbkuIrkuIvmlofkuqTnu5nlpKfmqKHlnovnlJ/miJDlm57nrZTjgII='
$questionMilvus = Utf8FromBase64 'TWlsdnVzIOWcqOezu+e7n+S4rei1t+S7gOS5iOS9nOeUqO+8nw=='

$proc = $null
try {
  $proc = Start-BackendIfNeeded

  Write-Host '1) GET /health'
  $health = Invoke-Json -Method GET -Url "$BaseUrl/health"
  Assert-True ($health.status -eq 'UP') '/health status should be UP'

  Write-Host '2) GET /llm/config'
  $config = Invoke-Json -Method GET -Url "$BaseUrl/llm/config"
  Assert-True (-not [string]::IsNullOrWhiteSpace($config.chatMode)) 'llm.config.chatMode should not be empty'
  Write-Host ("   chatMode={0}, chatModel={1}, baseUrl={2}" -f $config.chatMode, $config.chatModel, $config.baseUrl)

  Write-Host '3) POST /llm/ask (Chinese)'
  $ask = Invoke-Json -Method POST -Url "$BaseUrl/llm/ask" -Body @{ question = $questionRag }
  Assert-True (-not [string]::IsNullOrWhiteSpace($ask.answer)) '/llm/ask answer should not be empty'
  Assert-True ($ask.mode -eq $config.chatMode) '/llm/ask mode should match /llm/config chatMode'
  Write-Host ("   answer={0}" -f $ask.answer)

  Write-Host '4) GET /demo/health (before ingest)'
  $demoHealthBefore = Invoke-Json -Method GET -Url "$BaseUrl/demo/health"
  Write-Host ("   chunkCountBefore={0}, vectorStoreMode={1}, embeddingMode={2}" -f $demoHealthBefore.details.chunkCount, $demoHealthBefore.details.vectorStoreMode, $demoHealthBefore.details.embeddingMode)

  if ($IngestMaterials) {
    Write-Host '5) POST /demo/reset'
    Invoke-Json -Method POST -Url "$BaseUrl/demo/reset" -Body @{} | Out-Null

    Write-Host '6) POST /demo/ingest/materials'
    $materials = Invoke-Json -Method POST -Url "$BaseUrl/demo/ingest/materials" -Body @{}
    Assert-True ($materials.successCount -ge 1) '/demo/ingest/materials successCount should be >= 1'
    Assert-True ($materials.totalChunks -ge 1) '/demo/ingest/materials totalChunks should be >= 1'
    Write-Host ("   success={0}, fail={1}, totalChunks={2}" -f $materials.successCount, $materials.failCount, $materials.totalChunks)
  } else {
    Write-Host '5) POST /demo/ingest (Chinese content)'
    $ingest = Invoke-Json -Method POST -Url "$BaseUrl/demo/ingest" -Body @{
      docId        = 'demo'
      documentName = 'RAG Demo'
      content      = $docContent
      source       = 'manual'
    }
    Assert-True ($ingest.chunkCount -ge 1) '/demo/ingest chunkCount should be >= 1'
    Assert-True ($ingest.embeddingDimensions -ge 1) '/demo/ingest embeddingDimensions should be >= 1'
    Write-Host ("   chunks={0}, dims={1}, vectorStoreMode={2}" -f $ingest.chunkCount, $ingest.embeddingDimensions, $ingest.vectorStoreMode)
  }

  Write-Host '7) GET /demo/health (after ingest)'
  $demoHealthAfter = Invoke-Json -Method GET -Url "$BaseUrl/demo/health"
  Write-Host ("   chunkCountAfter={0}" -f $demoHealthAfter.details.chunkCount)

  Write-Host '8) POST /demo/search (expect retrievedChunks with Chinese)'
  $search = Invoke-Json -Method POST -Url "$BaseUrl/demo/search" -Body @{ question = $questionMilvus; topK = $TopK }
  Assert-True ($search.retrievedChunks.Count -ge 1) '/demo/search retrievedChunks should be >= 1'
  Assert-True (-not [string]::IsNullOrWhiteSpace($search.retrievedChunks[0].content)) '/demo/search first chunk content should not be empty'
  Write-Host ("   retrievedChunks={0}" -f $search.retrievedChunks.Count)
  Write-Host ("   firstChunkPreview={0}" -f ($search.retrievedChunks[0].content.Substring(0, [Math]::Min(60, $search.retrievedChunks[0].content.Length))))

  Write-Host '9) POST /demo/chat (expect citations + retrievedChunks)'
  $chat = Invoke-Json -Method POST -Url "$BaseUrl/demo/chat" -Body @{ question = $questionMilvus; topK = $TopK }
  Assert-True (-not [string]::IsNullOrWhiteSpace($chat.answer)) '/demo/chat answer should not be empty'
  Assert-True ($chat.citations.Count -ge 1) '/demo/chat citations should be >= 1'
  Assert-True ($chat.retrievedChunks.Count -ge 1) '/demo/chat retrievedChunks should be >= 1'
  Assert-True ($chat.mode -eq $config.chatMode) '/demo/chat mode should match /llm/config chatMode'
  Write-Host ("   answer={0}" -f $chat.answer)
  Write-Host ("   citations={0}, retrievedChunks={1}" -f $chat.citations.Count, $chat.retrievedChunks.Count)

  Write-Host 'OK: smoke tests passed.'
} finally {
  Stop-BackendIfStarted -Proc $proc
}
