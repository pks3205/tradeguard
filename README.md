# Internet Access Control (Windows 64-bit)

Ek chhota Windows app jo **saare installed/running applications** ki list dikhata hai —
har app ke saamne checkbox ke saath. App ka simple rule:

> **CHECKED = Internet BLOCKED**
> **UNCHECKED = Internet ALLOWED**

Matlab: **jisme check NAHI rahega, sirf usi ka internet chalega.** Pehle "Check All"
dabao (sab block), phir jin apps ko internet chahiye unhe uncheck karo, aur "Apply Rules"
dabao. Bas — checked apps ka internet Windows Firewall ke outbound rules se band ho jata hai.

- ✅ "Check All" / "Uncheck All" buttons
- ✅ "Add program..." se koi bhi `.exe` manually add karo
- ✅ Selection auto-save hota hai (`%LOCALAPPDATA%\InternetAccessControl\state.json`)
- ✅ Sirf internet block karta hai — koi file delete nahi karta
- ⚠️ Administrator rights chahiye (UAC prompt khud aata hai)

---

## Instant run (bina build ke — abhi)

1. Is repo ki `windows/` folder ki do files download karo:
   - `InternetAccessControl.ps1`
   - `InternetAccessControl.bat`
2. Dono ko ek hi folder mein rakho.
3. **`InternetAccessControl.bat` par double-click** karo → UAC prompt par "Yes" → checklist window khul jayegi.

(Alternate: PowerShell mein `Set-ExecutionPolicy Bypass -Scope Process` ke baad
`.\InternetAccessControl.ps1` chalao.)

## Asli .exe banana

Asli 64-bit `.exe` (`InternetAccessControl.exe`, single file) C# WinForms source se banta hai:
`windows/exe/InternetAccessControl.csproj`.

**Local build (Windows + .NET 8 SDK chahiye):**

```bat
cd windows\exe
dotnet publish InternetAccessControl.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -o out
rem exe: out\InternetAccessControl.exe
```

**GitHub Actions build (recommended):**

1. `windows/build-windows.yml.example` ko copy karke `.github/workflows/build-windows.yml`
   naam se repo mein add karo (Actions tab se bhi bana sakte ho).
2. Push hote hi build chalega aur `InternetAccessControl.exe` artifact milega
   (Actions run page → Artifacts → `InternetAccessControl-win64`).

> Note: ye `.exe` `requireAdministrator` manifest ke saath banta hai, isliye double-click
> karte hi UAC prompt aayega.

---

## How it works (technically)

- App **installed programs** (Start Menu shortcuts) + **abhi chal rahe programs** ki list
  banata hai. Windows system files (`C:\Windows`) ko list se bahar rakha jata hai taaki
  Windows khud break na ho.
- **Checked app** → `netsh advfirewall firewall add rule ... dir=out action=block`
  (ek outbound block rule, sirf usi app ke liye).
- **Unchecked app** → usi app ka block rule delete ho jata hai (internet wapas).
- Har rule ka naam unique hota hai (app name + path ka hash), isliye ek hi app baar-baar
  block nahi hota.

## Safety & notes

- Sirf **outbound** internet block hota hai. LAN/localhost nahi chhoota, aur koi file
  delete nahi hoti.
- System processes (Windows folder ke) deliberately list mein nahi dikhte.
- Agar galat app block ho jaye: app kholo, use **uncheck** karo, **Apply Rules** dabao —
  internet wapas aa jayega.
- Ye ek utility hai; iska galat istemal (kisi aur ke system par bina permission ke) na karein.

## Repo layout

```
windows/
├── InternetAccessControl.ps1      # instant GUI app (PowerShell + WinForms)
├── InternetAccessControl.bat      # double-click launcher
├── build-windows.yml.example      # GitHub Actions workflow (copy to .github/workflows/)
└── exe/                           # C# WinForms source for the real .exe
    ├── InternetAccessControl.csproj
    ├── app.manifest
    └── Program.cs
```
