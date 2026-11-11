<#
.SYNOPSIS
  Signs files (an installer, an .exe) with a code-signing certificate and checks the signature (TODO 2A.6).

.DESCRIPTION
  Two ways to name the certificate:
    -PfxPath and -PfxPassword   a certificate FILE (this is what .github/workflows/release.yml uses when its secrets are set)
    -Thumbprint                 a certificate that is in the Windows certificate store, for example one whose key lives on a
                                hardware token: since 2023 the certificate authorities issue new public code-signing certificates
                                with the key on hardware, so such a certificate cannot be exported to a file; sign on the PC the
                                token is plugged into instead (docs/SIGNING.md).
  The signature gets a trusted time stamp, so it stays valid after the certificate expires.

.EXAMPLE
  .\scripts\Sign-Windows.ps1 -Files target\installer\Lunavera-1.0.0.msi -Thumbprint 0123456789ABCDEF0123456789ABCDEF01234567
#>
param(
    [Parameter(Mandatory = $true)][string[]]$Files,
    [string]$PfxPath,
    [string]$PfxPassword,
    [string]$Thumbprint,
    [string]$TimestampUrl = "http://timestamp.digicert.com"
)

$ErrorActionPreference = "Stop"

if (-not $PfxPath -and -not $Thumbprint) {
    throw "Name the certificate: -PfxPath and -PfxPassword, or -Thumbprint."
}
if ($PfxPath -and -not (Test-Path $PfxPath)) {
    throw "The certificate file '$PfxPath' does not exist."
}

# signtool.exe comes with the Windows SDK (it is on the GitHub Windows runners and on a PC with Visual Studio or the SDK).
$signtool = Get-ChildItem "${env:ProgramFiles(x86)}\Windows Kits\10\bin\*\x64\signtool.exe" -ErrorAction SilentlyContinue |
    Sort-Object FullName | Select-Object -Last 1
if (-not $signtool) {
    throw "signtool.exe was not found: install the Windows SDK (the 'Windows SDK Signing Tools for Desktop Apps' part)."
}

foreach ($file in $Files) {
    if (-not (Test-Path $file)) { throw "The file '$file' does not exist." }
    $arguments = @("sign", "/fd", "SHA256", "/tr", $TimestampUrl, "/td", "SHA256")
    if ($PfxPath) {
        $arguments += @("/f", $PfxPath)
        if ($PfxPassword) { $arguments += @("/p", $PfxPassword) }
    } else {
        $arguments += @("/sha1", $Thumbprint)
    }
    $arguments += $file
    & $signtool.FullName @arguments
    if ($LASTEXITCODE -ne 0) { throw "signtool could not sign '$file' (exit code $LASTEXITCODE)." }

    # The check a person would do by hand: right-click, Properties, Digital Signatures. /pa = the default Windows trust policy.
    & $signtool.FullName verify /pa /v $file
    if ($LASTEXITCODE -ne 0) { throw "'$file' was signed but the signature does not verify (exit code $LASTEXITCODE)." }
    Write-Host "Signed and verified: $file"
}
