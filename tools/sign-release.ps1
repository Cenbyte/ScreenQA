param(
    [Parameter(Mandatory=$true)][string]$UnsignedApk,
    [Parameter(Mandatory=$true)][string]$Keystore,
    [Parameter(Mandatory=$true)][string]$Alias,
    [Parameter(Mandatory=$true)][string]$BuildToolsDirectory,
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [Parameter(Mandatory=$true)][string]$OutputApk
)
$ErrorActionPreference = 'Stop'
$unsignedPath = (Resolve-Path -LiteralPath $UnsignedApk).Path
$keystorePath = (Resolve-Path -LiteralPath $Keystore).Path
$outputPath = [IO.Path]::GetFullPath($OutputApk)
if ($outputPath -eq $unsignedPath -or $outputPath -eq $keystorePath -or (Test-Path -LiteralPath $outputPath)) {
    throw 'Output must be a new APK path, distinct from the input and keystore.'
}
$alignedPath = $outputPath + '.aligned.tmp.apk'
if (Test-Path -LiteralPath $alignedPath) { throw 'Temporary output already exists.' }
$zipalign = Join-Path $BuildToolsDirectory 'zipalign.exe'
$apksigner = Join-Path $BuildToolsDirectory 'apksigner.bat'
if (!(Test-Path -LiteralPath $zipalign) -or !(Test-Path -LiteralPath $apksigner)) {
    throw 'Android Build Tools directory is invalid.'
}
$previousJava = $env:JAVA_HOME
$previousStore = $env:UNIVERSITY_STORE_PASSWORD
$previousKey = $env:UNIVERSITY_KEY_PASSWORD
try {
    $env:JAVA_HOME = $JavaHome
    $storeSecret = Read-Host 'Keystore password (not saved)' -AsSecureString
    $keySecret = Read-Host 'Key password (not saved)' -AsSecureString
    $env:UNIVERSITY_STORE_PASSWORD = [Net.NetworkCredential]::new('', $storeSecret).Password
    $env:UNIVERSITY_KEY_PASSWORD = [Net.NetworkCredential]::new('', $keySecret).Password
    & $zipalign -p -f 4 $unsignedPath $alignedPath
    if ($LASTEXITCODE -ne 0) { throw 'zipalign failed.' }
    & $apksigner sign --ks $keystorePath --ks-key-alias $Alias --ks-pass env:UNIVERSITY_STORE_PASSWORD --key-pass env:UNIVERSITY_KEY_PASSWORD --out $outputPath $alignedPath
    if ($LASTEXITCODE -ne 0) { throw 'Signing failed.' }
    & $apksigner verify --verbose --print-certs $outputPath
    if ($LASTEXITCODE -ne 0) { throw 'Signature verification failed.' }
    & $zipalign -c 4 $outputPath
    if ($LASTEXITCODE -ne 0) { throw 'Alignment verification failed.' }
    Get-FileHash -LiteralPath $outputPath -Algorithm SHA256
} finally {
    $env:JAVA_HOME = $previousJava
    $env:UNIVERSITY_STORE_PASSWORD = $previousStore
    $env:UNIVERSITY_KEY_PASSWORD = $previousKey
    if ($null -ne $storeSecret) { $storeSecret.Dispose() }
    if ($null -ne $keySecret) { $keySecret.Dispose() }
    if (Test-Path -LiteralPath $alignedPath) { Remove-Item -LiteralPath $alignedPath }
}
