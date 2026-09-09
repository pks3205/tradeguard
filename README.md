# XAUUSD Setup Alerts

A standalone Android app that scans **XAUUSD (gold) on the 15-minute timeframe** and
sends a **notification only when a final, high-quality setup has fully formed**.

It is **notification-only** by design:

- ❌ No trade execution
- ❌ No broker connection
- ❌ No "BUY NOW / SELL NOW" instructions

Every alert carries the wording **"Final setup detected — manually verify your broker chart."**

| | |
|---|---|
| Package | `com.tradeguard.xauusd` |
| Language | Kotlin + Jetpack Compose (Material 3) |
| minSdk / targetSdk / compileSdk | 26 / 35 / 35 |
| JVM | Java 17 / Kotlin JVM toolchain 17 |
| Data feed (prototype) | Yahoo Finance chart endpoint, no API key required |
| Poll interval | 60 seconds (completed 15-minute candles only) |

---

## How the scanner works

1. Every 60 seconds the foreground service fetches 5 days of 15-minute candles:
   `https://query1.finance.yahoo.com/v8/finance/chart/XAUUSD%3DX?interval=15m&range=5d&includePrePost=false`
2. Only **completed** 15-minute candles are evaluated (the in-progress candle is ignored).
3. The pure `SetupDetector` (no Android deps) checks two setups.
4. A notification is sent only when **every** condition — including a minimum
   **1:2 risk/reward** — has been confirmed on a closed candle.
5. Each alert ID is persisted locally (`SharedPreferences`), so the **same setup is
   never notified twice**.

### Setup 1 — Liquidity Sweep → MSS → FVG entry

**BUY** only after all of these:

1. A prior swing low exists (2-candle fractal swing by default).
2. A candle **wicks below** that swing low and **closes back above** it (the sweep).
3. A **bullish displacement** candle appears: body ≥ 1.5× the average body of the
   previous 14 candles.
4. A **bullish Market Structure Shift**: a candle closes above the latest relevant
   lower high.
5. A **bullish 3-candle FVG** exists: candle 1 high < candle 3 low.
6. A later completed candle **retraces into the FVG** and closes back above the lower
   FVG boundary.
7. Suggested **SL** = below the sweep low with an ATR/range buffer; suggested **TP** =
   the relevant previous high / BSL.
8. Notify only if **R:R ≥ 1:2**.

**SELL** is the exact inverse (sweep of a prior swing high, bearish displacement,
bearish MSS, bearish FVG `candle 1 low > candle 3 high`, SL above sweep high, TP at the
relevant previous low / SSL).

### Setup 2 — New York Opening Range Breakout

The opening range is **fixed in India time**: **5:30 PM–6:00 PM IST**
(`Asia/Kolkata`) and does **not** shift with New York DST. Internally it is mapped to
**12:00–12:15 UTC**, and the range high/low is built from those two 15-minute candles.

**BUY**:

1. A completed candle closes **above** the range high.
2. Price later **retests** the broken range high.
3. A later completed **bullish continuation** candle closes above the retest candle high.
4. Trend filter: **EMA 20 > EMA 50** and current price **above EMA 20**.
5. SL below the retest/range level, minimum **1:2 R:R**.

**SELL** is the inverse (breakdown below range low, retest, bearish continuation close
below the retest candle low, EMA 20 < EMA 50, price below EMA 20).

### What does NOT trigger a notification

Raw sweeps, raw breakouts, and sequences still waiting on an MSS, FVG retrace, retest,
or continuation candle. Notifications fire only after the **final entry-ready candle
closes**.

### Notification format

- Title examples: **"XAUUSD 15M bullish FVG entry"**, **"XAUUSD 15M NY range BUY"**
- Body includes: setup, entry, SL, TP, and calculated R:R.

---

## App features

- **Dashboard** with Scanner ON/OFF, last successful scan time, last signal title, and
  the current feed error (if any).
- **"Start M15 Scanner"** / **"Stop Scanner"** buttons.
- In-app explanation of both setup models and the free-feed/limitations warnings.
- **Foreground service** (`FOREGROUND_SERVICE_DATA_SYNC`) with a visible persistent
  notification, so scanning continues while the app UI is closed.
- **Home-screen widget** showing scanner status and the last alert; tapping it opens the
  app (it contains no scanner logic).
- Android 13+ **notification permission** is requested when you start the scanner.

Permissions declared: `INTERNET`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_DATA_SYNC`.

---

## Free-feed limitations (please read)

- The Yahoo Finance endpoint is a **free/public prototype feed**. It can be **delayed**,
  and it is **rate-limited** — repeated rapid requests can be temporarily refused
  (HTTP 429).
- Prices/levels **must be verified against your broker chart** before acting.
- The default `range=5d` gives enough history (≈480 candles) for both setups; the
  detector requires at least 60 completed candles for the ORB setup and 40 for the
  sweep setup.

## Android background limitations (please read)

- **Force Stop** kills the service and pauses scanning until you reopen the app and tap
  Start again.
- **Battery optimization** can delay or stop the 60-second polling. Exclude the app from
  battery optimization for reliable scanning.
- Android 13+ requires the notification permission to show alert notifications; the
  persistent scanner notification is always shown while the service runs.

---

## Build

```bash
./gradlew --no-daemon testDebugUnitTest   # run unit tests
./gradlew --no-daemon assembleDebug       # build the debug APK
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Requirements: JDK 17 and the Android SDK (compileSdk 35). CI is provided by
`.github/workflows/build-apk.yml` (JDK 17 → unit tests → `assembleDebug` → upload the
APK as artifact `xauusd-setup-alerts-debug`).

## Project layout

```
app/src/main/java/com/tradeguard/xauusd/
├── data/        Candle model + YahooFinanceClient (HttpURLConnection + org.json)
├── detect/      Indicators + SetupDetector (pure, unit-testable logic)
├── scan/        ScannerEngine (completed-candle gate) + ScannerService (foreground)
├── store/       AppPrefs (SharedPreferences) + SentSignalRegistry (dedup)
├── notify/      Notification channels + alert/status notifications
├── widget/      ScannerWidgetProvider (status/last-alert home-screen widget)
└── MainActivity.kt   Compose dashboard
app/src/test/java/com/tradeguard/xauusd/
├── SetupDetectorTest.kt        (bullish ORB, incomplete ORB, insufficient history, sweeps, SELLs)
└── SentSignalRegistryTest.kt   (duplicate alert IDs are not sent twice)
```

## Disclaimer

This is an educational scanner prototype. It is **not financial advice** and does not
guarantee the timeliness or accuracy of the free feed. Always confirm any setup on your
own broker chart.
