package com.androidlord.seekerkeyboard.ime

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout

class SeekerKeyboardView(
    context: Context,
) : LinearLayout(context) {
    private val rowSpecs = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("shift", "z", "x", "c", "v", "b", "n", "m", "⌫"),
    )

    private var uppercase = false

    init {
        orientation = VERTICAL
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }

    fun render(
        settings: KeyboardSettings,
        onKeyPress: (String) -> Unit,
    ) {
        removeAllViews()
        setBackgroundColor(backgroundColor(settings.theme))

        if (settings.showNumberRow) {
            addView(buildRow(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"), settings, onKeyPress))
        }

        rowSpecs.forEach { row ->
            addView(buildRow(row, settings, onKeyPress))
        }

        val bottomRow = mutableListOf("123", ",")
        if (settings.showWalletKey) {
            bottomRow += "wallet"
        }
        bottomRow += listOf("space", ".", "enter")
        addView(buildRow(bottomRow, settings, onKeyPress))
    }

    private fun buildRow(
        labels: List<String>,
        settings: KeyboardSettings,
        onKeyPress: (String) -> Unit,
    ): View {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
        }

        labels.forEach { label ->
            row.addView(
                Button(context).apply {
                    text = displayLabel(label)
                    isAllCaps = false
                    setTextColor(foregroundColor(settings.theme))
                    setBackgroundColor(keyColor(settings.theme, label))
                    textSize = 16f
                    minHeight = 0
                    minimumHeight = 0
                    layoutParams = LayoutParams(0, dp(settings.keyHeightDp), keyWeight(label)).apply {
                        marginStart = dp(3)
                        marginEnd = dp(3)
                    }
                    setOnClickListener {
                        if (label == "shift") {
                            uppercase = !uppercase
                            render(settings, onKeyPress)
                        } else {
                            onKeyPress(resolveKeyValue(label))
                        }
                    }
                }
            )
        }
        return row
    }

    private fun displayLabel(label: String): String {
        return when (label) {
            "space" -> "space"
            "wallet" -> "wallet"
            "enter" -> "enter"
            "shift" -> if (uppercase) "SHIFT" else "shift"
            else -> resolveKeyValue(label)
        }
    }

    private fun resolveKeyValue(label: String): String {
        return when (label) {
            "⌫", "shift", "space", "wallet", "enter", "123" -> label
            else -> if (uppercase) label.uppercase() else label
        }
    }

    private fun keyWeight(label: String): Float {
        return when (label) {
            "space" -> 4.2f
            "wallet" -> 1.6f
            "enter", "shift", "123", "⌫" -> 1.4f
            else -> 1f
        }
    }

    private fun backgroundColor(theme: KeyboardTheme): Int {
        return when (theme) {
            KeyboardTheme.SAND -> Color.parseColor("#F1E3D3")
            KeyboardTheme.TEAL -> Color.parseColor("#D8F4EE")
            KeyboardTheme.GRAPHITE -> Color.parseColor("#20272B")
        }
    }

    private fun keyColor(theme: KeyboardTheme, label: String): Int {
        val accent = when (theme) {
            KeyboardTheme.SAND -> "#C16A39"
            KeyboardTheme.TEAL -> "#12786B"
            KeyboardTheme.GRAPHITE -> "#5C6F52"
        }
        val neutral = when (theme) {
            KeyboardTheme.SAND -> "#FFF8F2"
            KeyboardTheme.TEAL -> "#F3FFFC"
            KeyboardTheme.GRAPHITE -> "#344047"
        }
        return Color.parseColor(if (label == "wallet") accent else neutral)
    }

    private fun foregroundColor(theme: KeyboardTheme): Int {
        return if (theme == KeyboardTheme.GRAPHITE) Color.WHITE else Color.BLACK
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics,
        ).toInt()
    }
}
