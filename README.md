# MyMoneyKeep 雲端記帳本 (Android)

<div align="center">

![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-purple?style=flat-square&logo=kotlin)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=flat-square&logo=android)
![Room](https://img.shields.io/badge/Room-Database-orange?style=flat-square)
![Platform](https://img.shields.io/badge/Platform-Android%2024%2B-green?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)

**一款以「本地優先、隱私安全、無伺服器」為核心的智慧型語音雲端記帳 Android 應用程式。**

</div>

---

## 🌟 核心特色

- 🎙️ **語音智慧記帳**：支援語音輸入，結合自然語言處理與智慧分析，迅速拆解消費項目、金額與分類。
- ☁️ **Google Drive 個人雲端試算表同步**：
  - 採用 Android 原生 OAuth 安全授權，直接連線使用者個人的 Google 雲端硬碟。
  - 自動於雲端建立專屬記帳資料夾並生成原生的 **Google 試算表 (Google Sheets)**，點開直接以試算表格子檢視，並支援一鍵下載還原至手機。
- 🤖 **Gemini AI 財務分析**：支援使用者自訂 Gemini API Key，深入洞察個人收支結構、提供消費預警與個人化財務優化建議。
- 🔒 **本地優先與隱私架構 (Local-First)**：
  - 所有收支明細預設儲存於手機本機 Room 資料庫，離線亦可完整使用。
  - 無第三方後端伺服器中轉，使用者資料完全由個人掌控。
- 🎨 **現代化 Material 3 視覺體驗**：
  - 支援動態深淺色主題與多種風格切換。
  - 豐富的圖表統計、明細篩選與即時收支儀表板。

---

## 🛠️ 技術棧 (Tech Stack)

- **UI 框架**：Jetpack Compose (Material 3), Navigation Compose
- **架構設計**：MVVM 架構、Unidirectional Data Flow (UDF)
- **非同步與狀態**：Kotlin Coroutines, StateFlow, SharedFlow
- **本地資料持久化**：Room Database (KSP)
- **雲端與網路**：
  - Google Play Services Auth (原生 Android OAuth 登入)
  - Google Drive REST API v3 / Google Sheets API v4
  - Retrofit 2, Moshi, OkHttp 3
- **單元與截圖測試**：Robolectric, Roborazzi

---

## 🚀 快速開始 (Getting Started)

本專案採用**「零環境變數依賴 (Zero Env Dependency)」**設計，Clone 原始碼後**無須手動建立任何 `.env` 或 `local.properties`**，即可直接在 Android Studio 中編譯運行。

### 📋 開發環境需求
- **Android Studio**：Ladybug (2024.2.1) 或更新版本
- **JDK**：OpenJDK 17
- **Android SDK**：API Level 36 (最低支援 Android 7.0 / API Level 24)

### 📥 下載與編譯
1. **Clone 專案庫**：
   ```bash
   git clone https://github.com/FooSam/MyMoneyKeep.git
   ```
2. **以 Android Studio 開啟專案**：
   - 點擊 **Open** 並選擇 `MyMoneyKeep` 目錄。
   - 等待 Gradle 自動同步（Sync）完成。
3. **執行專案**：
   - 連接 Android 實機或啟動模擬器，點擊 **Run 'app'** 即可立即體驗訪客模式與本機記帳功能！

> 💡 **關於 `local.properties` 與 Android SDK 設定備註**：
> - **使用 Android Studio**：開啟專案時 IDE 會自動偵測本機 SDK 並自動在專案目錄產生 `local.properties`，無需手動建立。
> - **使用命令列 (CLI) 或 CI/CD 編譯**：請確保系統已設定環境變數 `ANDROID_HOME`（或 `ANDROID_SDK_ROOT`），或手動在 `MyMoneyKeep` 根目錄建立 `local.properties` 並指定 SDK 路徑：
>   ```properties
>   # Windows 範例
>   sdk.dir=C\:\\Users\\<您的使用者名稱>\\AppData\\Local\\Android\\Sdk
>   # macOS 範例
>   sdk.dir=/Users/<您的使用者名稱>/Library/Android/sdk
>   ```

---

## 🔐 Google 登入與雲端備份設定 (開發者指引)

專案使用 Android 原生 OAuth 進行安全核對（依賴「App 包名 + 簽名憑證 SHA-1」）。若您希望在本地開發時測試 **Google 登入** 與 **Google Drive 雲端同步** 功能，請依以下步驟設定您個人的 Google Cloud Console：

### 步驟 1：取得本地 Debug 簽名 SHA-1
在專案根目錄終端機執行以下指令：
```bash
# Windows
.\gradlew signingReport

# macOS / Linux
./gradlew signingReport
```
在輸出中找到 `Variant: debug` 區塊，複製其對應的 **`SHA1`** 指紋（格式如 `AA:BB:CC:...`）。

### 步驟 2：在 Google Cloud Console 建立 OAuth 用戶端
1. 前往 [Google Cloud Console](https://console.cloud.google.com/) 並建立新專案（或選擇既有專案）。
2. 進入 **【API 和服務】** > **【已啟用的 API 和服務】**，點擊 **啟用 API 和服務**，搜尋並啟用 **`Google Drive API`**。
3. 進入 **【API 和服務】** > **【OAuth 同意畫面】**：
   - 依指示設定應用程式名稱與支援信箱。
   - 於【範圍 (Scopes)】中新增 `https://www.googleapis.com/auth/drive.file`（僅需存取 App 所建立檔案的最小權限）。
   - 若發布狀態為「測試中」，請在【測試使用者】中加入您自己的 Google 帳號。
4. 進入 **【API 和服務】** > **【憑證】**：
   - 點擊 **建立憑證** > **OAuth 用戶端 ID**。
   - 應用程式類型選擇：**Android**。
   - **套件名稱 (Package Name)**：`com.aistudio.mymoneykeep.app`（直接填寫專案預設包名即可）。
   - **SHA-1 憑證指紋**：貼上步驟 1 取得的 SHA-1。
5. 點擊 **建立** 儲存。

> 💡 **備註**：完成設定後，您在本地編譯運行的 App 即可直接點擊 Google 登入，並與您的 Google Drive 進行同步備份！

---

## 🤖 Gemini AI 智慧分析設定

為保障金鑰安全與隱私，AI 智慧分析功能採用**「使用者自帶金鑰 (BYOK)」**設計：

1. 前往 [Google AI Studio](https://aistudio.google.com/) 免費申請個人專屬的 Gemini API Key。
2. 開啟 App，切換至 **【帳號設定】** 頁籤。
3. 在 **「Gemini API 金鑰設定」** 欄位貼上您的金鑰並儲存。
4. 儲存後即可在【消費報表】與【語音記帳】中享受智慧財務分析建議！

---

## 📦 發布與 Release 打包 (Release Build)

正式發布時，本專案由 `version.json` 統一管理版本號，並在 Release 打包時強制啟用 R8 程式碼混淆與資源壓縮：

```powershell
# PowerShell 打包指令範例
$env:JAVA_HOME = "D:\Work\Sam\Project\APPBuild\jdk17.0.19_10"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :app:assembleRelease
```
產出的 Release APK 將自動命名為 `${APP_ProductName}-v${APP_Version}-release.apk` 並匯出至 `Release/` 資料夾。

---

## 📄 開源授權 (License)

本專案採用 [MIT License](LICENSE) 授權開放。歡迎自由 Fork、提交 PR 與參與貢獻！
