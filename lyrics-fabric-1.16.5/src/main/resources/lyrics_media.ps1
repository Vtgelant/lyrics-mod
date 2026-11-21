param([string]$CmdFile)
$ErrorActionPreference = "SilentlyContinue"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Add-Type -AssemblyName System.Runtime.WindowsRuntime

$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
})[0]

function Await($op, $type) {
    $t = $asTaskGeneric.MakeGenericMethod($type).Invoke($null, @($op))
    $t.Wait(-1) | Out-Null
    $t.Result
}

[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime] | Out-Null
[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime] | Out-Null

$mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])

function Snapshot {
    $s = $mgr.GetCurrentSession()
    if ($null -eq $s) { return '{"none":true}' }
    $p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
    if ($null -eq $p -or [string]::IsNullOrEmpty($p.Title)) { return '{"none":true}' }
    $tl = $s.GetTimelineProperties()
    $pb = $s.GetPlaybackInfo()
    $playing = ($pb.PlaybackStatus.ToString() -eq 'Playing')
    $pos = $tl.Position.TotalMilliseconds
    if ($playing) {
        $d = ([DateTimeOffset]::UtcNow - $tl.LastUpdatedTime).TotalMilliseconds
        if ($d -gt 0 -and $d -lt 3600000) { $pos += $d }
    }
    $dur = $tl.EndTime.TotalMilliseconds - $tl.StartTime.TotalMilliseconds
    [pscustomobject]@{
        title   = [string]$p.Title
        artist  = [string]$p.Artist
        album   = [string]$p.AlbumTitle
        pos     = [long]$pos
        dur     = [long]$dur
        playing = $playing
    } | ConvertTo-Json -Compress
}

$i = 0
while ($true) {
    if ($CmdFile -and (Test-Path -LiteralPath $CmdFile)) {
        $c = (Get-Content -LiteralPath $CmdFile -Raw).Trim()
        Remove-Item -LiteralPath $CmdFile -Force
        $s = $mgr.GetCurrentSession()
        if ($s) {
            switch ($c) {
                'next'   { [void](Await ($s.TrySkipNextAsync()) ([bool])) }
                'prev'   { [void](Await ($s.TrySkipPreviousAsync()) ([bool])) }
                'toggle' { [void](Await ($s.TryTogglePlayPauseAsync()) ([bool])) }
            }
        }
        $i = 0
    }
    if ($i % 4 -eq 0) { Write-Output (Snapshot) }
    $i++
    Start-Sleep -Milliseconds 250
}
