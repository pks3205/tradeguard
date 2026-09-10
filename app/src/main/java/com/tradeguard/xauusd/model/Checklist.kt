package com.tradeguard.xauusd.model

/** A single checklist item (rule). */
data class Rule(
    val id: String,
    val label: String
)

/** One trade setup with its BUY and SELL rule checklists. */
data class Setup(
    val id: String,
    val shortTitle: String,
    val title: String,
    val subtitle: String,
    val buy: List<Rule>,
    val sell: List<Rule>
)

/**
 * The complete manual XAUUSD 15-minute rule checklist. Pure Kotlin data so it
 * is trivially unit-testable. Nothing here trades, connects to a broker, or
 * fetches a feed — the trader checks every rule against their own chart.
 */
object ChecklistCatalog {

    val SweepMssFvg = Setup(
        id = "sweep_mss_fvg",
        shortTitle = "Setup 1 · Sweep→MSS→FVG",
        title = "Setup 1 · Liquidity Sweep → MSS → FVG entry",
        subtitle = "Sweep of prior liquidity, displacement candle, market-structure shift and a fair value gap retrace.",
        buy = listOf(
            Rule("s1_buy_1", "Prior swing low (SSL) exists — 2-candle fractal swing"),
            Rule("s1_buy_2", "Price wicked below that swing low"),
            Rule("s1_buy_3", "Sweep candle closed back above the swept low"),
            Rule("s1_buy_4", "Bullish displacement candle (body ≥ 1.5× average of previous 14 candles)"),
            Rule("s1_buy_5", "Bullish MSS — a candle closed above the latest lower high"),
            Rule("s1_buy_6", "Bullish 3-candle FVG (candle 1 high < candle 3 low)"),
            Rule("s1_buy_7", "A later M15 candle retraced into the FVG and closed above its lower boundary"),
            Rule("s1_buy_8", "SL set below the sweep low with an ATR/range buffer"),
            Rule("s1_buy_9", "TP set at the relevant previous high (BSL)"),
            Rule("s1_buy_10", "Risk:reward ≥ 1:2")
        ),
        sell = listOf(
            Rule("s1_sell_1", "Prior swing high (BSL) exists — 2-candle fractal swing"),
            Rule("s1_sell_2", "Price wicked above that swing high"),
            Rule("s1_sell_3", "Sweep candle closed back below the swept high"),
            Rule("s1_sell_4", "Bearish displacement candle (body ≥ 1.5× average of previous 14 candles)"),
            Rule("s1_sell_5", "Bearish MSS — a candle closed below the latest higher low"),
            Rule("s1_sell_6", "Bearish 3-candle FVG (candle 1 low > candle 3 high)"),
            Rule("s1_sell_7", "A later M15 candle retraced into the FVG and closed below its upper boundary"),
            Rule("s1_sell_8", "SL set above the sweep high with an ATR/range buffer"),
            Rule("s1_sell_9", "TP set at the relevant previous low (SSL)"),
            Rule("s1_sell_10", "Risk:reward ≥ 1:2")
        )
    )

    val NyOpeningRange = Setup(
        id = "ny_orb",
        shortTitle = "Setup 2 · NY Range Breakout",
        title = "Setup 2 · New York Opening Range Breakout",
        subtitle = "Fixed 5:30–6:00 PM IST range (no DST shift). Breakout → retest → continuation.",
        buy = listOf(
            Rule("s2_buy_1", "Range high/low built from the two M15 candles between 5:30–6:00 PM IST"),
            Rule("s2_buy_2", "A completed M15 candle closed above the range high"),
            Rule("s2_buy_3", "Price later retested the broken range high"),
            Rule("s2_buy_4", "A bullish continuation candle closed above the retest candle high"),
            Rule("s2_buy_5", "EMA 20 > EMA 50"),
            Rule("s2_buy_6", "Current price is above EMA 20"),
            Rule("s2_buy_7", "SL set below the retest/range level"),
            Rule("s2_buy_8", "Risk:reward ≥ 1:2")
        ),
        sell = listOf(
            Rule("s2_sell_1", "Range high/low built from the two M15 candles between 5:30–6:00 PM IST"),
            Rule("s2_sell_2", "A completed M15 candle closed below the range low"),
            Rule("s2_sell_3", "Price later retested the broken range low"),
            Rule("s2_sell_4", "A bearish continuation candle closed below the retest candle low"),
            Rule("s2_sell_5", "EMA 20 < EMA 50"),
            Rule("s2_sell_6", "Current price is below EMA 20"),
            Rule("s2_sell_7", "SL set above the retest/range level"),
            Rule("s2_sell_8", "Risk:reward ≥ 1:2")
        )
    )

    val setups: List<Setup> = listOf(SweepMssFvg, NyOpeningRange)

    fun allRules(): List<Rule> = setups.flatMap { it.buy + it.sell }
}
