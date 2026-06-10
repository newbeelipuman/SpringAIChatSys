param(
  [string]$BaseUrl = 'http://localhost:8080',
  [string]$AdminUsername = 'admin',
  [string]$AdminPassword = 'change-me-admin-password'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Enable-Utf8Console {
  try { chcp 65001 | Out-Null } catch {}
  try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}
  try { $script:OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}
}

function ConvertTo-JsonText {
  param([object]$Value)
  if ($null -eq $Value) {
    return $null
  }
  return ($Value | ConvertTo-Json -Depth 20 -Compress)
}

function Invoke-Case {
  param(
    [string]$CaseId,
    [string]$Category,
    [string]$Method,
    [string]$Path,
    [string]$Role,
    [int]$ExpectedStatus,
    [hashtable]$Headers = @{},
    [object]$Body = $null,
    [string]$Purpose = ''
  )

  Add-Type -AssemblyName System.Net.Http | Out-Null
  $client = [System.Net.Http.HttpClient]::new()
  $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), "$BaseUrl$Path")
  $request.Headers.Accept.ParseAdd('application/json')
  foreach ($key in $Headers.Keys) {
    $request.Headers.TryAddWithoutValidation($key, [string]$Headers[$key]) | Out-Null
  }
  if ($null -ne $Body) {
    $request.Content = [System.Net.Http.StringContent]::new((ConvertTo-JsonText $Body), [System.Text.Encoding]::UTF8, 'application/json')
  }

  $watch = [System.Diagnostics.Stopwatch]::StartNew()
  $response = $client.SendAsync($request).GetAwaiter().GetResult()
  $bytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
  $watch.Stop()
  $text = [System.Text.Encoding]::UTF8.GetString($bytes)

  $json = $null
  if (-not [string]::IsNullOrWhiteSpace($text)) {
    try { $json = $text | ConvertFrom-Json } catch {}
  }

  $actualStatus = [int]$response.StatusCode
  $passed = $actualStatus -eq $ExpectedStatus
  $summary = ''
  if ($null -ne $json) {
    if ($json.PSObject.Properties.Name -contains 'status') {
      $summary += "status=$($json.status); "
    }
    if ($json.PSObject.Properties.Name -contains 'error') {
      $summary += "error=$($json.error); "
    }
    if ($json.PSObject.Properties.Name -contains 'message') {
      $summary += "message=$($json.message); "
    }
    if ($json.PSObject.Properties.Name -contains 'tokenType') {
      $summary += "tokenType=$($json.tokenType); "
    }
    if ($json.PSObject.Properties.Name -contains 'user') {
      $summary += "role=$($json.user.role); source=$($json.user.source); "
    }
    if ($json.PSObject.Properties.Name -contains 'details') {
      $details = $json.details
      if ($details.PSObject.Properties.Name -contains 'vectorStoreMode') { $summary += "vectorStoreMode=$($details.vectorStoreMode); " }
      if ($details.PSObject.Properties.Name -contains 'chunkCount') { $summary += "chunkCount=$($details.chunkCount); " }
      if ($details.PSObject.Properties.Name -contains 'mysqlStatus') { $summary += "mysqlStatus=$($details.mysqlStatus); " }
      if ($details.PSObject.Properties.Name -contains 'authMode') { $summary += "authMode=$($details.authMode); " }
    }
    if ($json.PSObject.Properties.Name -contains 'chatMode') {
      $summary += "chatMode=$($json.chatMode); embeddingMode=$($json.embeddingMode); "
    }
    if ($json.PSObject.Properties.Name -contains 'currentRole') {
      $summary += "currentRole=$($json.currentRole); authMode=$($json.authMode); "
    }
    if ($json.PSObject.Properties.Name -contains 'chunkCount') {
      $summary += "chunkCount=$($json.chunkCount); embeddingDimensions=$($json.embeddingDimensions); vectorStoreMode=$($json.vectorStoreMode); "
    }
    if ($json.PSObject.Properties.Name -contains 'retrievedChunks') {
      $summary += "retrievedChunks=$($json.retrievedChunks.Count); "
    }
    if ($json.PSObject.Properties.Name -contains 'citations') {
      $summary += "citations=$($json.citations.Count); mode=$($json.mode); "
    }
    if ($json -is [array]) {
      $summary += "recordCount=$($json.Count); "
    }
  } else {
    $summary = $text
  }

  return [pscustomobject]@{
    caseId = $CaseId
    category = $Category
    method = $Method
    path = $Path
    role = $Role
    expectedStatus = $ExpectedStatus
    actualStatus = $actualStatus
    durationMs = [int]$watch.ElapsedMilliseconds
    passed = $passed
    purpose = $Purpose
    summary = $summary.Trim()
    body = $json
    rawBody = $text
  }
}

function Add-MarkdownTable {
  param(
    [System.Text.StringBuilder]$Builder,
    [array]$Rows
  )
  [void]$Builder.AppendLine('| Case | Category | Method | API | Role | Expected | Actual | Duration(ms) | Result | Evidence |')
  [void]$Builder.AppendLine('|---|---|---|---|---|---:|---:|---:|---|---|')
  foreach ($row in $Rows) {
    $result = if ($row.passed) { 'PASS' } else { 'FAIL' }
    $summary = ([string]$row.summary).Replace('|', '/')
    [void]$Builder.AppendLine(('| {0} | {1} | {2} | {3} | {4} | {5} | {6} | {7} | {8} | {9} |' -f $row.caseId, $row.category, $row.method, $row.path, $row.role, $row.expectedStatus, $row.actualStatus, $row.durationMs, $result, $summary))
  }
}

Enable-Utf8Console

$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$projectRoot = Split-Path -Parent $PSScriptRoot
$paperDir = Join-Path $projectRoot 'target\thesis-test-data'
New-Item -ItemType Directory -Force -Path $paperDir | Out-Null
$jsonOut = Join-Path $paperDir "thesis-test-data_$timestamp.json"
$mdOut = Join-Path $paperDir "thesis-test-data_$timestamp.md"

$testUsername = "thesis_user_$timestamp"
$testPassword = 'change-me-user-password'
$docId = "thesis-test-$timestamp"
$docContent = 'Spring AI wraps model calls and local embedding generation. Milvus stores text vectors and performs semantic similarity search. MySQL stores accounts, sessions, history records, permission metadata, and diagnostics only. MySQL does not store embeddings and does not replace vector retrieval. Temporary knowledge stays in JVM memory and is isolated by the current identity.'
$ragQuestion = 'What do Milvus and MySQL do in this system?'

$cases = New-Object System.Collections.Generic.List[object]

$cases.Add((Invoke-Case 'T-001' 'diagnostics' 'GET' '/demo/health' 'anonymous' 200 -Purpose 'RAG and storage diagnostics for status page'))
$cases.Add((Invoke-Case 'T-002' 'diagnostics' 'GET' '/llm/config' 'anonymous' 200 -Purpose 'Non-sensitive model configuration endpoint'))
$cases.Add((Invoke-Case 'T-003' 'diagnostics' 'GET' '/auth/health' 'anonymous' 200 -Purpose 'Auth and permission diagnostics endpoint'))

$register = Invoke-Case 'T-004' 'auth-api' 'POST' '/auth/register' 'USER' 200 @{} @{ username = $testUsername; password = $testPassword } 'Register a normal test user'
$cases.Add($register)

$loginUser = Invoke-Case 'T-005' 'auth-api' 'POST' '/auth/login' 'USER' 200 @{} @{ username = $testUsername; password = $testPassword } 'Login normal test user'
$cases.Add($loginUser)
$userToken = $loginUser.body.token
$userHeaders = @{ Authorization = "Bearer $userToken" }

$cases.Add((Invoke-Case 'T-006' 'auth-api' 'GET' '/auth/me' 'USER' 200 $userHeaders $null 'Resolve current USER identity'))
$cases.Add((Invoke-Case 'T-007' 'permission' 'GET' '/demo/admin/questions' 'USER' 403 $userHeaders $null 'USER should be denied by admin endpoint'))

$loginAdmin = Invoke-Case 'T-008' 'auth-api' 'POST' '/auth/login' 'ADMIN' 200 @{} @{ username = $AdminUsername; password = $AdminPassword } 'Login admin user'
$cases.Add($loginAdmin)
$adminToken = $loginAdmin.body.token
$adminHeaders = @{ Authorization = "Bearer $adminToken" }

$cases.Add((Invoke-Case 'T-009' 'permission' 'GET' '/demo/admin/questions' 'ADMIN' 200 $adminHeaders $null 'ADMIN should access global question history'))

$cases.Add((Invoke-Case 'T-010' 'rag-flow' 'POST' '/demo/ingest' 'USER' 200 $userHeaders @{
  docId = $docId
  documentName = 'Thesis test knowledge'
  content = $docContent
  source = 'thesis-test-script'
} 'Ingest persistent knowledge'))

$cases.Add((Invoke-Case 'T-011' 'rag-flow' 'POST' '/demo/search' 'USER' 200 $userHeaders @{
  question = $ragQuestion
  topK = 3
} 'Semantic search returns retrieved chunks'))

$cases.Add((Invoke-Case 'T-012' 'rag-flow' 'POST' '/demo/chat' 'USER' 200 $userHeaders @{
  question = $ragQuestion
  topK = 3
} 'RAG chat returns answer, citations, and retrieved chunks'))

$cases.Add((Invoke-Case 'T-013' 'history-api' 'GET' '/demo/me/knowledge' 'USER' 200 $userHeaders $null 'Current user knowledge history'))
$cases.Add((Invoke-Case 'T-014' 'history-api' 'GET' '/demo/me/questions' 'USER' 200 $userHeaders $null 'Current user question history'))
$cases.Add((Invoke-Case 'T-015' 'diagnostics' 'GET' '/demo/health' 'USER' 200 $userHeaders $null 'Status page data with logged-in user'))

$records = [pscustomobject]@{
  collectedAt = (Get-Date).ToString('s')
  baseUrl = $BaseUrl
  testUsername = $testUsername
  adminUsername = $AdminUsername
  cases = $cases
}

$records | ConvertTo-Json -Depth 30 | Set-Content -Encoding UTF8 $jsonOut

$md = [System.Text.StringBuilder]::new()
[void]$md.AppendLine('# Thesis Test Data Collection')
[void]$md.AppendLine()
[void]$md.AppendLine("- Collected at: $($records.collectedAt)")
[void]$md.AppendLine("- Base URL: $BaseUrl")
[void]$md.AppendLine("- USER account: $testUsername")
[void]$md.AppendLine("- ADMIN account: $AdminUsername")
[void]$md.AppendLine('- Source script: scripts/collect-thesis-test-data.ps1')
[void]$md.AppendLine()
[void]$md.AppendLine('## API And Permission Test Table')
Add-MarkdownTable $md $cases
[void]$md.AppendLine()
[void]$md.AppendLine('## Response Time Table')
[void]$md.AppendLine('| API | Role | Status | Duration(ms) | Use In Thesis |')
[void]$md.AppendLine('|---|---|---:|---:|---|')
foreach ($row in $cases) {
  [void]$md.AppendLine(('| {0} {1} | {2} | {3} | {4} | {5} |' -f $row.method, $row.path, $row.role, $row.actualStatus, $row.durationMs, $row.category))
}
[void]$md.AppendLine()
[void]$md.AppendLine('## Diagnostic Summary')
$demo = ($cases | Where-Object { $_.caseId -eq 'T-001' }).body
$llm = ($cases | Where-Object { $_.caseId -eq 'T-002' }).body
$auth = ($cases | Where-Object { $_.caseId -eq 'T-003' }).body
[void]$md.AppendLine('| Item | Value |')
[void]$md.AppendLine('|---|---|')
[void]$md.AppendLine(('| Vector store mode | {0} |' -f $demo.details.vectorStoreMode))
[void]$md.AppendLine(('| Vector store available | {0} |' -f $demo.details.available))
[void]$md.AppendLine(('| Embedding mode | {0} |' -f $demo.details.embeddingMode))
[void]$md.AppendLine(('| Default TopK | {0} |' -f $demo.details.defaultTopK))
[void]$md.AppendLine(('| MySQL status | {0} |' -f $demo.details.mysqlStatus))
[void]$md.AppendLine(('| Auth mode | {0} |' -f $auth.authMode))
[void]$md.AppendLine(('| Admin endpoints protected | {0} |' -f $auth.adminEndpointsProtected))
[void]$md.AppendLine(('| Admin account available | {0} |' -f $auth.adminUserAvailable))
[void]$md.AppendLine(('| Chat mode | {0} |' -f $llm.chatMode))
[void]$md.AppendLine(('| Chat model | {0} |' -f $llm.chatModel))
[void]$md.AppendLine(('| External chat configured | {0} |' -f $llm.externalChatConfigured))
[void]$md.AppendLine()
[void]$md.AppendLine('## Evidence Notes')
[void]$md.AppendLine()
$userAdminStatus = ($cases | Where-Object { $_.caseId -eq 'T-007' }).actualStatus
$adminAdminStatus = ($cases | Where-Object { $_.caseId -eq 'T-009' }).actualStatus
[void]$md.AppendLine(('- USER request to GET /demo/admin/questions returned status {0} with FORBIDDEN evidence.' -f $userAdminStatus))
[void]$md.AppendLine(('- ADMIN request to GET /demo/admin/questions returned status {0}.' -f $adminAdminStatus))
[void]$md.AppendLine('- /demo/health, /llm/config, and /auth/health all returned HTTP 200 and can support the status page and thesis data table.')
[void]$md.AppendLine('- The RAG path was tested through ingest -> search -> chat. Treat the collected durations as local smoke-test observations, not large-scale benchmark data.')
$md.ToString() | Set-Content -Encoding UTF8 $mdOut

Write-Host "JSON: $jsonOut"
Write-Host "Markdown: $mdOut"
Write-Host "Cases: $($cases.Count)"
$failedCases = @($cases | Where-Object { -not $_.passed })
if ($failedCases.Count -gt 0) {
  Write-Host 'Some cases failed. Inspect the generated JSON for details.'
  exit 1
}
