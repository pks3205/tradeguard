# XAUUSD Setup Checklist

A standalone Android app with a **manual rule-list checklist** for XAUUSD (gold) on the
15-minute timeframe. It is **not a scanner**: there is no live feed, no background service,
and no notifications. You tick each rule against **your own broker chart** before taking a
trade.

- ❌ No trade execution
- ❌ No broker connection
- ❌ No "BUY NOW / SELL NOW" instructions
- ❌ No background scanning / polling / notifications

| | |
|---|---|
| Package | `com.tradeguard.xauusd` |
| Language | Kotlin + Jetpack Compose (Material 3) |
| minSdk / targetSdk / compileSdk | 26 / 35 / 35 |
| JVM | Java 17 / Kotlin JVM toolchain 17 |
| Persistence | SharedPreferences (one boolean per rule) |

---

## What the app does

The home screen lets you pick a **setup** and a **direction (BUY / SELL)**, then shows the
ordered rule checklist for that combination:

- Setup 1 · Liquidity Sweep → MSS → FVG entry
- Setup 2 · New York Opening Range Breakout

Each rule is a checkbox. Progress is shown as "X / N rules", and when **all** rules are
checked the app shows an "All rules checked" banner reminding you to verify every level on
your broker chart — it never tells you to buy or sell.

### Setup 1 — Liquidity Sweep → MSS → FVG entry

BUY checklist (SELL is the exact inverse):

1. Prior swing low (SSL) exists — 2-candle fractal swing.
2. Price wicked below that swing low.
3. Sweep candle closed back above the swept low.
4. Bullish displacement candle (body ≥ 1.5× average of the previous 14 candles).
5. Bullish MSS — a candle closed above the latest lower high.
6. Bullish 3-candle FVG (candle 1 high < candle 3 low).
7. A later M15 candle retraced into the FVG and closed above its lower boundary.
8. SL below the sweep low with an ATR/range buffer.
9. TP at the relevant previous high (BSL).
10. Risk:reward ≥ 1:2.

### Setup 2 — New York Opening Range Breakout

Opening range is **fixed in India time**: **5:30 PM–6:00 PM IST** (`Asia/Kolkata`), no DST
shift. The range high/low is built from the two M15 candles in that window.

BUY checklist (SELL is the inverse):

1. Range high/low built from the two M15 candles between 5:30–6:00 PM IST.
2. A completed M15 candle closed above the range high.
3. Price later retested the broken range high.
4. A bullish continuation candle closed above the retest candle high.
5. EMA 20 > EMA 50.
6. Current price is above EMA 20.
7. SL below the retest/range level.
8. Risk:reward ≥ 1:2.

---

## Build

```bash
./gradlew --no-daemon testDebugUnitTest   # run unit tests
./gradlew --no-daemon assembleDebug       # build the debug APK
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Requirements: JDK 17 and the Android SDK (compileSdk 35). CI is provided by
`.github/workflows/build-apk.yml` (JDK 17 → unit tests → `assembleDebug` → upload the APK as
artifact `xauusd-setup-alerts-debug`).

## Project layout

```
app/src/main/java/com/tradeguard/xauusd/
├── model/    ChecklistCatalog + Rule/Setup data (pure, unit-testable)
├── store/    ChecklistStore (SharedPreferences persistence)
└── MainActivity.kt   Compose checklist screen
app/src/test/java/com/tradeguard/xauusd/ChecklistCatalogTest.kt
```

## Disclaimer

This is a manual checklist for education and record-keeping, **not financial advice** and
**not a trading signal**. Always confirm every rule on your own broker chart.
