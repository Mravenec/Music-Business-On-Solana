# EH8S Colosseum smoke - three DevNet wallets, no mocks. Program v0.10.0.
# Closed profit loop: academy + settle fees -> treasury PDA -> owner withdraw;
# settle pool -> vault -> band members by SPP weights -> per-member claim; AI owner digest.
# venue listing + booking escrow + VAULT settle, subscriptions with expiry,
# per-song royalty pools + sync licenses (80/20), NEXUS / HARMONY / ATLAS, Slack approvals,
# and (last, optional) the owner multisig that replaces direct treasury withdraws.
# academy video library (owner publishes, students watch with progress) and the
# teaching path (level-5 instructors draft courses, the owner reviews, lesson practice goes to NEXUS).
#
#   powershell -File 4.anchor/eh8s-devnet/hackathon-smoke.ps1 -Preflight   # automated checks only
#   powershell -File 4.anchor/eh8s-devnet/hackathon-smoke.ps1              # preflight + guided wallet steps
#
# The guided part pauses after every block. You sign in Phantom; this script never fakes a tx.

param([switch]$Preflight)

$ErrorActionPreference = "Stop"
$Owner = "7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC"
$Ui = "http://127.0.0.1:5173"
$Api = "http://127.0.0.1:8080"
$Rpc = if ($env:EH8S_RPC) { $env:EH8S_RPC } else { "https://api.devnet.solana.com" }
$Usdc = "4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU"
$Program = "GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG"
$ConfigPda = "GgpAKj9FZppC8gckF6SzevirNbgkfhpEbqiz1GE6oKLF"
$TreasuryPda = "DxGjk32dH8Jh5Xodx2syERsZJ6RPPaULxKsNmFmVWYWY"
$TreasuryAta = "9ptwWxWj5YQKP3pD5b4UEDksARBzkSQPtS3Kyvjnff4Z"
$Vault = "U9mzuiHMVUsCw9BLKGibq55BPZuLaxipnvKFvn76Vjq"
$GovernancePda = "3ZH4igyjF1jFPhyRTAczh1Za8bgjaTfXe2cB9v2v49XY"
$AccountTypes = @("Eh8sConfig", "MusicianProfile", "BandVault", "AgentAuthority", "Governance", "Proposal",
  "AcademySubscription", "GeographicSubscription", "VenueListing", "VenueAccessToken", "ConcertSettlement",
  "RoyaltyPool", "SyncLicense")
$ProtectedRoutes = @("/api/owner/treasury", "/api/owner/digest/latest", "/api/owner/agents", "/api/governance",
  "/api/stage/queue", "/api/vault/settle-queue", "/api/sync-deals", "/api/nexus/status",
  "/api/stage-map/pins?month=2026-10", "/api/owner-decisions",
  "/api/courses", "/api/owner/courses", "/api/teach/eligibility", "/api/owner/course-reviews")

function Pause-Step([string]$Title, [string]$Body) {
  Write-Host ""
  Write-Host "======== $Title ========" -ForegroundColor Cyan
  Write-Host $Body
  Read-Host "Press Enter when this step is done"
}

function Get-HttpStatus([string]$Url) {
  try {
    $r = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 10
    return [int]$r.StatusCode
  } catch {
    if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
    return 0
  }
}

function Get-PostStatus([string]$Url, [string]$ContentType, [string]$Body) {
  try {
    $r = Invoke-WebRequest -Uri $Url -Method Post -ContentType $ContentType -Body $Body -UseBasicParsing -TimeoutSec 10
    return [int]$r.StatusCode
  } catch {
    if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
    return 0
  }
}

function Get-RpcAccount([string]$Address) {
  $body = @{
    jsonrpc = "2.0"; id = 1; method = "getAccountInfo"
    params  = @($Address, @{ encoding = "jsonParsed"; commitment = "confirmed" })
  } | ConvertTo-Json -Depth 5
  $res = Invoke-RestMethod -Uri $Rpc -Method Post -ContentType "application/json" -Body $body -TimeoutSec 20
  return $res.result.value
}

# Anchor account discriminator: first 8 bytes of sha256("account:<Name>"), base64.
function Get-Discriminator([string]$Name) {
  $sha = [System.Security.Cryptography.SHA256]::Create()
  $hash = $sha.ComputeHash([Text.Encoding]::UTF8.GetBytes("account:$Name"))
  return [Convert]::ToBase64String($hash[0..7])
}

# One getProgramAccounts call (first 8 bytes only), grouped by account type.
# The public DevNet RPC rate-limits this method, so it retries once.
function Get-ProgramAccountCounts {
  $body = @{
    jsonrpc = "2.0"; id = 1; method = "getProgramAccounts"
    params  = @($Program, @{ commitment = "confirmed"; encoding = "base64"; dataSlice = @{ offset = 0; length = 8 } })
  } | ConvertTo-Json -Depth 6
  $res = $null
  for ($try = 0; $try -lt 2 -and $null -eq $res; $try++) {
    try { $res = Invoke-RestMethod -Uri $Rpc -Method Post -ContentType "application/json" -Body $body -TimeoutSec 30 }
    catch { if ($try -eq 1) { throw } ; Start-Sleep -Seconds 3 }
  }
  if ($res.error) { throw $res.error.message }
  $byDisc = @{}
  foreach ($name in $AccountTypes) { $byDisc[(Get-Discriminator $name)] = $name }
  $counts = [ordered]@{}
  foreach ($name in $AccountTypes) { $counts[$name] = 0 }
  foreach ($acc in @($res.result)) {
    $name = $byDisc[[string]$acc.account.data[0]]
    if ($name) { $counts[$name]++ }
  }
  return $counts
}

function Invoke-Preflight {
  $results = New-Object System.Collections.Generic.List[object]
  function Add-Check([string]$Name, [bool]$Ok, [string]$Detail) {
    $results.Add([pscustomobject]@{ Check = $Name; Ok = $Ok; Detail = $Detail })
  }

  try {
    $h = Invoke-RestMethod -Uri "$Api/health" -TimeoutSec 5
    Add-Check "Spring /health" ($h.status -eq "ok") ($h | ConvertTo-Json -Compress)
  } catch { Add-Check "Spring /health" $false $_.Exception.Message }

  try {
    $h = Invoke-RestMethod -Uri "$Ui/health" -TimeoutSec 5
    Add-Check "Vite proxy /health" ($h.status -eq "ok") "via $Ui"
  } catch { Add-Check "Vite proxy /health" $false $_.Exception.Message }

  foreach ($path in $ProtectedRoutes) {
    $code = Get-HttpStatus "$Api$path"
    Add-Check "401 without Bearer $path" ($code -eq 401) "HTTP $code"
  }

  $code = Get-PostStatus "$Api/api/slack/interactions" "application/x-www-form-urlencoded" "payload=%7B%7D"
  Add-Check "401 unsigned Slack click /api/slack/interactions" ($code -eq 401) "HTTP $code (Slack signature, not Bearer)"

  try {
    $p = Get-RpcAccount $Program
    Add-Check "Program executable" ($null -ne $p -and $p.executable) "$Program"
  } catch { Add-Check "Program executable" $false $_.Exception.Message }

  try {
    $c = Get-RpcAccount $ConfigPda
    Add-Check "Config PDA owned by program" ($null -ne $c -and $c.owner -eq $Program) "$ConfigPda"
  } catch { Add-Check "Config PDA owned by program" $false $_.Exception.Message }

  try {
    $t = Get-RpcAccount $TreasuryAta
    $info = $t.data.parsed.info
    $ok = ($null -ne $info) -and ($info.owner -eq $TreasuryPda) -and ($info.mint -eq $Usdc)
    Add-Check "Treasury ATA owned by treasury PDA" $ok "balance $($info.tokenAmount.uiAmountString) USDC"
  } catch { Add-Check "Treasury ATA owned by treasury PDA" $false $_.Exception.Message }

  try {
    $v = Get-RpcAccount $Vault
    Add-Check "Vault account exists" ($null -ne $v) "$Vault"
  } catch { Add-Check "Vault account exists" $false $_.Exception.Message }

  try {
    $g = Get-RpcAccount $GovernancePda
    if ($null -eq $g) {
      Add-Check "Governance PDA" $true "not initialized: owner withdraws directly (step 10); multisig is optional step 20"
    } else {
      Add-Check "Governance PDA" ($g.owner -eq $Program) "initialized: withdraws and agent grants go through proposals (step 20)"
    }
  } catch { Add-Check "Governance PDA" $false $_.Exception.Message }

  try {
    $counts = Get-ProgramAccountCounts
    Add-Check "Program accounts readable (one config)" ($counts["Eh8sConfig"] -eq 1) "Eh8sConfig $($counts["Eh8sConfig"])"
    $agents = $counts["AgentAuthority"]
    $agentNote = if ($agents -eq 0) { "0 - the owner signs every agent power itself" } else { "$agents granted agent wallet(s)" }
    Add-Check "AgentAuthority PDAs (owned by program)" $true $agentNote
    $detail = ($AccountTypes | Where-Object { $_ -ne "Eh8sConfig" } | ForEach-Object { "$_ $($counts[$_])" }) -join " | "
    Add-Check "Program accounts by type" $true $detail
  } catch { Add-Check "Program accounts readable (one config)" $false "$($_.Exception.Message) - rerun or set EH8S_RPC" }

  $results | Format-Table -AutoSize -Wrap | Out-String -Width 220 | Write-Host
  $failed = @($results | Where-Object { -not $_.Ok }).Count
  if ($failed -eq 0) {
    Write-Host "Preflight PASS ($($results.Count) checks)." -ForegroundColor Green
  } else {
    Write-Host "Preflight FAIL ($failed of $($results.Count) checks)." -ForegroundColor Red
  }
  return $failed
}

Write-Host "EH8S judge smoke - DevNet USDC only, no demo data."
Write-Host "Program: $Program"
Write-Host "Treasury PDA: $TreasuryPda (USDC ATA $TreasuryAta)"
Write-Host "Owner wallet (withdraws the treasury): $Owner"
Write-Host "USDC mint: $Usdc"
Write-Host "RPC: $Rpc"
Write-Host "UI: $Ui"

if ($Preflight) {
  $failed = Invoke-Preflight
  exit ([int]($failed -ne 0))
}

Pause-Step "0. Stack up (no mocks)" @"
1. python 0.database/scripts/setup_db.py
2. cd 1.backend/eh8s ; mvn spring-boot:run
3. cd 2.frontend/eh8s ; npm run dev
4. Open $Ui - Health chip must not be Network Error.
The script runs the automated preflight next.
"@

$failed = Invoke-Preflight
if ($failed -ne 0) {
  Write-Host "Fix the failed preflight checks before signing any wallet step." -ForegroundColor Yellow
  Read-Host "Press Enter to continue anyway, or Ctrl+C to stop"
}

Pause-Step "1. Login as OWNER" @"
Phantom: switch to the owner key $Owner (DevNet). Connect on $Ui
You land in the owner inbox. The owner signs vault + treasury actions only.
Disconnect when ready for the musician.
"@

Pause-Step "2. MUSICIAN applies (wallet B)" @"
Phantom: wallet B. Connect on $Ui -> Apply for a role -> musician.
Optional second member: wallet D applies as musician too (shows the split).
Disconnect.
"@

Pause-Step "3. VENUE applies (wallet C)" @"
Phantom: wallet C. Connect on $Ui -> Apply for a role -> venue. Disconnect.
"@

Pause-Step "4. OWNER approves" @"
Phantom: owner $Owner. Owner inbox -> Roles. Approve musician(s) and venue C. Disconnect.
"@

Pause-Step "5. MUSICIAN enrolls and pays academy (fee -> treasury PDA)" @"
Phantom: wallet B. Home -> Open academy.
Create the musician profile (/academy/enroll) - creates the MusicianProfile PDA
that settle credits with pending_claims_usdc.
Open a plan -> Pay with wallet. Approve in Phantom.
Explorer: 85% USDC -> treasury ATA $TreasuryAta (owner = treasury PDA),
          15% -> instructor ATA. UI status: confirmed.
"@

Pause-Step "6. MUSICIAN creates a band and adds members" @"
Still wallet B. Bands -> New band (code + name).
Open the band -> Add member (wallet B; wallet D too if it applied).
Note the band id.
"@

Pause-Step "7. OWNER activates the band vault and syncs weights" @"
Phantom: owner $Owner. Open $Ui/bands/<bandId>/vault/activate -> Activate with owner wallet
(create_band: BandVault PDA with equal member weights). Explorer: confirmed.
If an SPP cycle is closed for this band: /bands/<bandId>/vault/sync -> Sync SPP weights
(update_spp_weights). Otherwise the equal weights from activation stay.
Disconnect.
"@

Pause-Step "8. VENUE books and settles with expenses (split)" @"
Phantom: wallet C (needs DevNet USDC for the net amount). Stage -> New venue if empty.
Book a show: venue + band id + date. Open Settle a concert.
The preview reads: Gross / expenses / net / fee. Sign settle_concert.
Explorer:
  - fee USDC -> treasury ATA $TreasuryAta
  - pool USDC -> vault $Vault
  - each member MusicianProfile.pending_claims_usdc += pool x weight
The backend verifies the signature (program id, accounts, amounts) before it records the settlement.
"@

Pause-Step "9. Each MEMBER claims" @"
Phantom: wallet B. Open $Ui/stage-map/claim -> Claim royalties -> sign claim_royalties.
Explorer: USDC vault -> wallet B ATA. Repeat with wallet D if it is a member.
"@

Pause-Step "10. OWNER withdraws the treasury" @"
Phantom: owner $Owner. Owner inbox -> Treasury ($Ui/owner/treasury).
Balance is read on chain from $TreasuryAta. Enter an amount -> Withdraw to my wallet.
Explorer: withdraw_treasury, USDC treasury ATA -> owner ATA.
Activity: $Ui/owner/treasury/activity lists the confirmed withdraw.
"@

Pause-Step "11. OWNER runs the AI digest" @"
Needs ANTHROPIC_API_KEY on the backend (env only, never committed).
Owner inbox -> AI digest ($Ui/owner/digest) -> Generate today's digest.
The page shows the latest Claude summary of academy, settlements, claims, vaults and treasury.
Without the key the page says AI is off - that is expected, not a failure of the money loop.
"@

Pause-Step "12. VENUE lists on-chain, OWNER approves" @"
Phantom: wallet C. Open $Ui/stage-map/venues/<venueId> -> On-chain listing and escrow -> Register on-chain
(register_venue: VenueListing PDA ["venue", wallet C, venueId], status pending). Disconnect.
Phantom: owner $Owner. Inbox -> Venues and bookings -> the venue -> Approve venue (approve_venue).
A wallet granted STAGE (0x04) under Inbox -> Agent powers may approve instead of the owner.
"@

Pause-Step "13. Booking escrow -> contract hash -> VAULT settle" @"
Phantom: wallet C. Book a show at the approved venue for the band, then open the booking under
On-chain listing and escrow -> Escrow with wallet (propose_booking: gross USDC -> escrow of the
VenueAccessToken PDA ["access", wallet C, band, date]). Cancel and refund works only before confirm.
Phantom: owner. Inbox -> Venues and bookings -> the booking -> Confirm and pin contract
(confirm_booking stores the SHA-256 of the contract text).
Inbox -> Escrows to settle -> the booking -> Settle from escrow (settle_booking):
expenses -> venue, fee -> treasury ATA $TreasuryAta, pool -> vault, member pending += pool x weight.
Members claim again at $Ui/stage-map/claim.
"@

Pause-Step "14. Subscriptions with an on-chain expiry" @"
Phantom: wallet B. Academy -> a plan -> Months (1-12) -> Pay with wallet (subscribe_academy(plan, months)).
The page shows "Active until <date>. Paying again adds months."
Catalog -> Region access -> region + reach tier + Months -> Pay with wallet (subscribe_geographic;
GeographicSubscription PDA ["geo_sub", wallet B, geo code]) for the wallet's first band.
Pay a zone that contains the approved venue: ATLAS (step 16) routes only inside paid, unexpired zones.
"@

Pause-Step "15. Per-song royalty pool + sync license 80/20" @"
Phantom: owner (or a WAVE 0x10 agent). Catalog -> a track -> Activate song pool -> Activate with wallet
(create_royalty_pool: RoyaltyPool PDA ["royalty", trackId], splits sum 10000 bps).
Deposit royalties -> amount -> Pay with wallet (deposit_royalties credits each member by the song split).
Any wallet as licensee: track -> Sync licenses -> New sync deal -> Propose deal -> Pay with wallet
(pay_sync_license: 20% -> treasury ATA, 80% -> vault credited by the splits; each deal pays once).
Members: $Ui/stage-map/claim -> Song credits lists their share of each deposit and license.
"@

Pause-Step "16. NEXUS / HARMONY / ATLAS agents (46)" @"
NEXUS needs ANTHROPIC_API_KEY on the backend (env only); without it the page says AI offline (503), by design.
Academy -> Score Enigma (NEXUS) -> Request Score Enigma -> Ask NEXUS. On the result, Apply level on-chain
opens the Enigma level signer (update_musician_level, owner or a NEXUS 0x01 agent wallet).
Band page -> Find musicians -> Run HARMONY (explained 0-100 ranking).
Band page -> Tour routes -> Plan a route -> Generate route (stops inside the zone paid in step 14).
"@

Pause-Step "17. Slack approvals" @"
Owner inbox -> Agent decisions -> Slack approvals shows Buttons are live / Buttons are off.
Live needs SLACK_SIGNING_SECRET plus the Slack app's Interactivity Request URL
https://<public tunnel>/api/slack/interactions (and a webhook or bot token for delivery).
A yellow/red agent decision then arrives in Slack with Approve / Reject. Click Approve:
the Slack message names who answered, and Agent decisions -> Answered decisions shows
"Approved in Slack by <user>". Answering it again on the web is refused (first answer wins).
"@

Pause-Step "18. OWNER publishes a video course, STUDENT watches" @"
Phantom: owner $Owner. Inbox -> Courses -> New course (title, minimum Enigma level) -> Create course.
Add section -> + Add lesson -> paste an unlisted YouTube / Vimeo / Bunny Stream / Cloudflare Stream link
(the form says "Recognized: ...") -> tick Free preview for the first lesson -> Add lesson. Add a second, paid lesson.
Publish -> Preview as student: the lesson plays in the embedded player. Courses -> Course access -> Grant access
gives one wallet free access (one course or all, optional end date). Disconnect.
Phantom: wallet B. Academy -> Video courses -> the course -> the free preview plays; the paid lesson opens only with
an active plan (step 5/14) and the course's minimum level, or an owner grant. Locked lessons never send the video link.
Mark complete and continue -> the course percentage rises.
"@

Pause-Step "19. Level-5 INSTRUCTOR drafts, OWNER reviews, practice to NEXUS" @"
Teaching is earned: Enigma level 5 (set on-chain by NEXUS or the owner, step 16) AND an approved instructor role
(apply at /apply, owner approves at Roles). Academy -> Teach shows both requirements and the one missing step.
Phantom: an eligible instructor wallet. Teach -> My courses -> New course, sections, lessons with a
"Practice exercise for NEXUS" -> Submit for review. The course locks: "Waiting for the studio owner's review".
Phantom: owner. Inbox -> Courses -> Review queue (1) -> Review -> Watch a lesson -> Send back with note
(the instructor sees "Changes requested: ...") or Approve and publish (the course reaches Video courses).
Phantom: wallet B. Open an unlocked lesson with an exercise -> Send practice to NEXUS: the Score Enigma form shows
the exercise and the stored evaluation links back to the lesson.
"@

Pause-Step "20. OPTIONAL, LAST: owner multisig" @"
WARNING: once governance exists on this shared DevNet program, the direct treasury withdraw (step 10) and
direct agent grants are refused for everyone ("GovernanceActive"). Skip this step to keep the judge path.
Phantom: owner. Inbox -> Governance -> Set up signers -> Sign setup (init_governance, 1-5 signers + threshold;
Governance PDA $GovernancePda).
New proposal -> Withdraw treasury -> Sign proposal. Each other signer: open the proposal -> Approve.
At threshold -> Execute (execute_withdraw_proposal; destination must be a signer's USDC ATA).
"@

Write-Host ""
Write-Host "Smoke finished. Money loop: academy + settle fee -> treasury PDA -> owner withdraw;" -ForegroundColor Green
Write-Host "settle pool -> vault -> members by weight -> claim. AI digest summarizes the day." -ForegroundColor Green
Write-Host "venue escrow settle, subscriptions with expiry, per-song pools + sync 80/20," -ForegroundColor Green
Write-Host "NEXUS / HARMONY / ATLAS, Slack approvals, optional multisig." -ForegroundColor Green
Write-Host "owner video courses, students watch with progress, level-5 instructors teach after owner review." -ForegroundColor Green
Write-Host "Show Explorer for academy, band vault, settle, each claim, escrow settle, song deposit, sync license and the treasury withdraw."
