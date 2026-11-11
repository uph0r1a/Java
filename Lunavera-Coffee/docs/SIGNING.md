# Signing the Windows installer, and checking a download

TODO 2A.6. **Written without a Windows PC, a certificate or GitHub to run it on: nothing here has been run.** The first real run
of `.github/workflows/release.yml` is the test; fix this page with what happens.

## 1. Do you need it at all?

Windows shows the "Windows protected your PC" (SmartScreen) warning for a program that came **from the internet** (the file carries
a "Mark of the Web") and that it does not know. A file copied from a USB stick or from a shared folder on the shop's network has no
such mark, so a shop that installs the program itself on its own PCs does not see the warning and can skip signing completely.
Signing matters when other people download the installer from a web page.

A signature proves two things: who made the file, and that it was not changed afterwards. It does **not** by itself remove the
warning: Windows also looks at how many people have run the file before, so even a signed installer from a new certificate can
warn for a while (as far as I know; Microsoft changes this).

## 2. What the project does today

- `.github/workflows/release.yml` ("Release": run it by hand under Actions, or push a tag such as `v1.0.0`) runs the tests, builds
  the program folder and zip (profile `modular-image`: moditect and jlink) and a per-user Windows installer (`jpackage`, an `.msi`), signs the installer **if**
  the two secrets below exist, writes `SHA256SUMS.txt` with a fingerprint of every file, and attaches everything to a **draft**
  release. Nothing is published until you press "Publish".
- `scripts/Sign-Windows.ps1` does the signing and checks the result (`signtool verify /pa`). The workflow uses it, and you can run it
  yourself.
- `.github/workflows/dependency-scan.yml` looks every library up in a vulnerability database (OSV) on every pom change and every
  Monday; `dependabot.yml` proposes new versions; the Maven wrapper refuses a Maven download that is not the exact pinned file.

The installer is **not** the data folder: the shop's `data/` is created next to where the program is started from, as described in
`docs/PACKAGING.md`. Check on a real PC, after an install and after an upgrade, where `data/` ends up before you hand an installer
to anybody (TODO 1.3, 6.15).

## 3. Getting a certificate

| Way | What to know |
| --- | --- |
| A code-signing certificate from a certificate authority | Costs money every year. Since June 2023 a new public certificate must keep its private key on hardware (a USB token, or the authority's cloud signing service), so it **cannot be exported to a file**. |
| The authority's cloud signing (DigiCert KeyLocker, SSL.com eSigner and others) or Azure Trusted Signing | The key stays with the service and a tool signs through it. Needs a different step than the file in this workflow; Azure Trusted Signing is only offered to organisations in some countries: check that yours is one. |
| A certificate you make yourself | Free, but only the PCs that you set up to trust it will accept it, and the SmartScreen warning stays for everybody else. Useful for a shop with a few tills, see section 5. |
| No certificate | Fine for a shop that installs from a USB stick or the network (section 1). |

## 4. Signing in the release workflow (a certificate FILE)

Works only with a certificate whose key can be exported to a `.pfx` (an older certificate, or one made by a private authority).

1. Turn the file into one line of text: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("codesign.pfx")) | Set-Clipboard`
2. In the repository: Settings, Secrets and variables, Actions, "New repository secret":
   `WINDOWS_CODESIGN_PFX_BASE64` (the line from step 1) and `WINDOWS_CODESIGN_PASSWORD` (the file's password).
3. Run the Release workflow. The log says "Signed and verified" or, with no secret, "the installer is NOT signed".

The certificate file is written to the runner's temporary folder for the signing step only and deleted afterwards, in a `finally`
block, whatever happens. Secrets are not available to pull requests from forks.

## 5. Signing by hand (a certificate on a hardware token, or one of your own)

On the PC the token is plugged into (or that has your own certificate in its store), with the Windows SDK installed:

```powershell
.\scripts\Sign-Windows.ps1 -Files .\Lunavera-1.0.0.msi -Thumbprint <the certificate's thumbprint>
```

The thumbprint is in `certmgr.msc`, certificate, Details. To make a certificate of your own for the shop's tills (once, as
administrator): `New-SelfSignedCertificate -Type CodeSigningCert -Subject "CN=Lunavera Coffee" -CertStoreLocation Cert:\CurrentUser\My`;
export its public part (no private key) and import it into "Trusted Publishers" and "Trusted Root Certification Authorities" on each
PC that should accept the installer.

## 6. Checking a download

- Fingerprint: `Get-FileHash .\Lunavera-1.0.0.msi -Algorithm SHA256` must print the same value as the line for that file in
  `SHA256SUMS.txt`, which you got from the release page, not from the same place as the file if you can avoid it.
- The same for `lunavera.zip`: `Get-FileHash .\lunavera.zip -Algorithm SHA256` (Windows) or `sha256sum lunavera.zip` (Linux, Mac). The list of
  libraries inside is `lunavera-bom.json` (see `docs/STATIC-ANALYSIS.md`).
- Signature (signed installers only): right-click the file, Properties, "Digital Signatures", select the line, Details: it must say
  that the signature is OK. Or `signtool verify /pa /v <file>`.
