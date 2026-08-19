# MyMoneyKeep - 智慧雲端記帳本 (Android)

<div align="center">

![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-purple?style=flat-square&logo=kotlin)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=flat-square&logo=android)
![Room](https://img.shields.io/badge/Room-Database-orange?style=flat-square)
![Platform](https://img.shields.io/badge/Platform-Android%2024%2B-green?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)
![Version](https://img.shields.io/badge/Version-v1.00.00.41-brightgreen?style=flat-square)

**一款以「本地優先、隱私安全、無伺服器、智能語音」為核心的現代化全方位記帳 Android 應用程式。**

</div>

---

## 🌟 核心特色 (Core Features)

### 1. 🎙️ 智慧語音記帳與三軌智能調度
- **自然語言秒級辨識**：支援語音或文字自然語言輸入（例如：「午餐 120」、「昨天加 95 無鉛汽油 800 元」），自動精準拆解品項、金額、交易類別與日期。
- **三軌智慧調度引擎**：
  - **第一軌：雲端 Gemini 2.5 Flash**（支援 BYOK 自自帶金鑰，提供深度財務解析與對話顧問）。
  - **第二軌：地端 Edge AI / Gemini Nano**（適配 Android AICore 離線神經網路加速）。
  - **第三軌：本地高階規則引擎**（支援「一百八」、「兩千五」、「1.5萬」等中文數字轉換、相對日期換算與離線分類，100% 離線可用）。

### 2. 🧮 二合一「外幣匯率計算機」與無縫記帳連動
- **160+ 國貨幣即時匯率**：整合 ExchangeRate-API 與 Frankfurter API，提供全球多國貨幣即時換算與國旗 Emoji 選擇清單。
- **平滑歷史走勢圖表**：提供 1週 (1W)、1個月 (1M)、3個月 (3M)、1年 (1Y) 之平滑貝茲曲線匯率趨勢圖。
- **內建高精度計算機**：支援四則運算（`+`、`-`、`×`、`÷`、`=`、`C`、`⌫`），並提供專屬 **「📥 帶入記帳」** 鍵，一鍵自動將折算本幣金額與外幣匯率備註帶入記帳明細！
- **離線快取支援**：無網路環境下自動備退使用本機快取匯率，並提供常用幣別匯率快速對照清單。

### 3. 📊 多維度消費報表與圓餅圖分析
- **靈活時間維度切換**：支援「全部」、「週報表」、「月度報表」、「季度報表」、「年度報表」切換，並可透過日期選單或左右切換鈕快速瀏覽前後期數據。
- **互動式支出佔比圓餅圖**：直觀視覺化呈現各類別支出比例，並即時統計總收入、總支出與淨結餘。

### 4. 📱 4*2 桌面質感 Widget 小工具全新升級
- **桌面即時收支看板**：於手機桌面 4*2 尺寸即時呈現「今日支出」、「今日收入」與「當月結餘」，排版勻稱緊湊。
- **大按鈕獨立快捷操作**：
  - **【🧮 匯率換算】**：大尺寸圓角按鈕，點擊直接精準開啟外幣計算機頁面。
  - **【🎙️ 語音記帳】**：點擊直達 App 首頁開始記帳。
  - **【🔄 重新整理】**：右上角放大點擊區，隨時一鍵更新最新桌面數據。

### 5. ☁️ Google Drive 個人雲端試算表自動同步
- **零中轉私有雲端架構**：採用 Android 原生 OAuth 安全授權，直連使用者個人的 Google 雲端硬碟，零第三方伺服器中轉。
- **年度自動分表與美化排版**：
  - 自動於雲端建立『MyMoneyKeep_雲端記帳本』專屬資料夾，並依年份建立獨立 Google 試算表（如『2026_MyMoneyKeep_記帳本』）。
  - 同步時自動套用整齊排版與自訂類別顏色標記，點開試算表一目了然。
  - 支援隨時 **「從雲端還原」**，換機或重裝無損下載歷史數據。

### 6. 🌐 全介面 5 大多國語系即時切換
- 支援 **繁體中文 (zh-TW)**、**簡體中文 (zh-CN)**、**英文 (English)**、**日文 (日本語)**、**韓文 (한국어)** 5 種語言即時切換，所有畫面、按鈕、彈窗與 Widget 全面本地化。

### 7. 💾 試算表全表 CSV 備份與匯入/匯出
- 支援將全表記帳資料一鍵複製為標準 CSV 格式至剪貼簿，亦可直接貼上外部 CSV 數據快速匯入。

### 8. 🎨 自訂收支類別與代表色彩
- 支援自訂多達 20 種類別名稱、收支屬性（收入/支出）與 16 款預設標籤代表色，類別色彩同步連動 App 報表與 Google 試算表。

### 9. 🛡️ 企業級安全性與除錯診斷
- **Firebase Crashlytics 遙測**：全域未處理例外捕捉，Release 自動上傳 R8 mapping 檔。
- **開發者真機 SHA-1 診斷**：點擊版本資訊 6 次可開啟登入診斷工具，一鍵複製真機運行 SHA-1 憑證與 GCP OAuth 排查報告。

---

## 🛠️ 技術棧 (Tech Stack)

- **UI 與設計系統**：Jetpack Compose (Material 3), Navigation Compose, Android AppWidget (RemoteViews)
- **架構設計**：MVVM 架構、單向資料流 (UDF)、本地優先架構 (Local-First Architecture)
- **非同步與響應式**：Kotlin Coroutines, StateFlow, SharedFlow
- **本地資料庫**：Room Database (KSP)
- **網路與雲端串接**：
  - Google Play Services Auth (原生 Android OAuth 登入)
  - Google Drive REST API v3 / Google Sheets API v4
  - Google Gemini 2.5 Flash REST API (BYOK)
  - ExchangeRate-API / Frankfurter API (外幣即時匯率)
  - Retrofit 2, Moshi, OkHttp 3
- **語系與本地化**：LocaleHelper (Context Wrapper 多國語系動態切換)
- **雲端監控與遙測**：Firebase Crashlytics, Firebase Analytics
- **自動化測試**：Robolectric, JUnit 4, Roborazzi

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

---

## 🔐 Google 登入與雲端備份設定 (開發者指引)

專案使用 Android 原生 OAuth 進行安全核對（依賴「App 包名 + 簽名憑證 SHA-1」）。若您希望在本地開發時測試 **Google 登入** 與 **Google Drive 雲端同步** 功能，請依以下步驟設定您個人的 Google Cloud Console：

### 步驟 1：取得 SHA-1 簽名憑證指紋
- **方法 A（推薦：App 實時自檢一鍵複製）**：
  1. 編譯並在手機或模擬器啟動 App。
  2. 點擊「使用 Google 帳號登入」，若尚未設定 GCP 憑證，App 會自動彈出 **【Google 登入失敗診斷】** 視窗。
  3. 視窗內即時顯示當前運行的 **真實 SHA-1**，點擊 **「複製診斷報告」** 即可直接取得！
- **方法 B（命令列 signingReport）**：
  在專案根目錄終端機執行：
  ```bash
  .\gradlew signingReport
  ```
  在輸出中找到 `Variant: debug` 區塊，複製其對應的 **`SHA1`** 指紋。

### 步驟 2：在 Google Cloud Console 建立 OAuth 用戶端
1. 前往 [Google Cloud Console](https://console.cloud.google.com/) 並建立新專案（或選擇既有專案）。
2. 進入 **【API 和服務】** > **【已啟用的 API 和服務】**，搜尋並啟用以下兩項 API：
   - **`Google Drive API`**
   - **`Google Sheets API`**
3. 進入 **【API 和服務】** > **【OAuth 同意畫面】**：
   - 於【範圍 (Scopes)】中新增 `https://www.googleapis.com/auth/drive` 與 `https://www.googleapis.com/auth/spreadsheets`。
   - 若發布狀態為「測試中」，請在【測試使用者】中加入您自己的 Google 帳號。
4. 進入 **【API 和服務】** > **【憑證】**：
   - 點擊 **建立憑證** > **OAuth 用戶端 ID**。
   - 應用程式類型選擇：**Android**。
   - **套件名稱 (Package Name)**：`com.aistudio.mymoneykeep.app`
   - **SHA-1 憑證指紋**：貼上步驟 1 取得的 SHA-1。
5. 點擊 **建立** 儲存即可！

---

## 🤖 Gemini 2.5 Flash AI 智慧顧問設定

為保障金鑰安全與個人隱私，AI 智慧分析與對話問答功能採用**「使用者自帶金鑰 (BYOK)」**設計：

1. 前往 [Google AI Studio](https://aistudio.google.com/) 免費申請個人專屬的 Gemini API Key。
2. 開啟 App，切換至 **【帳號設定】** 頁籤。
3. 在 **「Gemini AI API Key 設定」** 欄位貼上您的金鑰並點擊儲存。
4. 儲存後即可在【語音記帳】直接以語音或文字進行自然語言記帳與對話問答（例如：「8月午餐總共多少」、「幫我分析這個月花費」），享受極速秒回的專業財務顧問體驗！未填寫 Key 時亦可使用本機離線規則與 Gemini Nano 基礎辨識。

---

## 📦 發布與 Release 打包 (Release Build)

正式發布時，本專案由 `version.json` 統一管理版本號，並在 Release 打包時強制啟用 R8 程式碼混淆、無用資源壓縮與 Crashlytics 混淆 Mapping 自動上傳：

```powershell
# 產出正式發布 APK (實機安裝測試)
.\gradlew :app:assembleRelease

# 產出 Google Play 上架專用 AAB (Android App Bundle)
.\gradlew :app:bundleRelease
```
產出的 Release APK / AAB 將自動命名為 `${APP_ProductName}-v${APP_Version}-release.apk` (或 `.aab`) 並匯出至 `Release/` 資料夾。

---

## 📄 開源授權 (License)

本專案採用 [MIT License](LICENSE) 授權開放。歡迎自由 Fork、提交 PR 與參與貢獻！
