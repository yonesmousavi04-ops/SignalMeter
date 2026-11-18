package com.example.signalmeter

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var callPct: TextView
    private lateinit var callBar: ProgressBar
    private lateinit var callInfo: TextView
    private lateinit var netPct: TextView
    private lateinit var netBar: ProgressBar
    private lateinit var netInfo: TextView

    private val ticker = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        val perms = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (perms.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            requestPermissions(perms, 1)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    // ---------- UI ----------

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            setBackgroundColor(Color.parseColor("#F5F6FA"))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val call = card("آنتن‌دهی تماس (سیم‌کارت)")
        callPct = call.second; callBar = call.third; callInfo = call.fourth
        val net = card("آنتن‌دهی اینترنت")
        netPct = net.second; netBar = net.third; netInfo = net.fourth

        root.addView(call.first)
        root.addView(net.first)
        setContentView(root)
    }

    private data class Card4(
        val first: LinearLayout,
        val second: TextView,
        val third: ProgressBar,
        val fourth: TextView
    )

    private fun card(title: String): Card4 {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            setBackgroundColor(Color.WHITE)
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.bottomMargin = 40
            layoutParams = lp
        }
        val t = TextView(this).apply { text = title; textSize = 16f; setTextColor(Color.DKGRAY) }
        val pct = TextView(this).apply {
            text = "—"; textSize = 56f; typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
        }
        val info = TextView(this).apply { textSize = 13f; setTextColor(Color.GRAY) }
        box.addView(t); box.addView(pct); box.addView(bar); box.addView(info)
        return Card4(box, pct, bar, info)
    }

    private fun show(pct: TextView, bar: ProgressBar, info: TextView, value: Int?, note: String) {
        if (value == null) {
            pct.text = "—"
            pct.setTextColor(Color.GRAY)
            bar.progress = 0
        } else {
            pct.text = "$value٪".toPersianDigits()
            pct.setTextColor(
                when {
                    value >= 66 -> Color.parseColor("#2E9E5B")
                    value >= 33 -> Color.parseColor("#E0A100")
                    else -> Color.parseColor("#D93A3A")
                }
            )
            bar.progress = value
        }
        info.text = note
    }

    private fun String.toPersianDigits(): String =
        map { if (it in '0'..'9') ('۰' + (it - '0')) else it }.joinToString("")

    // ---------- Logic ----------

    private fun update() {
        val cell = cellularSignal()
        if (cell == null) {
            show(callPct, callBar, callInfo, null, "سیم‌کارت یا دسترسی در دسترس نیست")
        } else {
            show(callPct, callBar, callInfo, cell.first, "${cell.second} dBm".toPersianDigits())
        }

        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        when {
            caps == null -> show(netPct, netBar, netInfo, null, "بدون اتصال")
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                val w = wifiSignal()
                show(netPct, netBar, netInfo, w?.first, "Wi-Fi  ${w?.second ?: ""} dBm".toPersianDigits())
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                show(netPct, netBar, netInfo, cell?.first, "داده‌ی همراه")
            else -> show(netPct, netBar, netInfo, null, "نوع اتصال نامشخص")
        }
    }

    /** returns Pair(percent 0..100, dBm) */
    private fun cellularSignal(): Pair<Int, Int>? {
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
            return null
        return try {
            val tm = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            val cs = tm.signalStrength?.cellSignalStrengths?.firstOrNull() ?: return null
            val dbm = cs.dbm
            if (dbm == Int.MAX_VALUE || dbm == 0) return null
            // -120 dBm = 0%  ،  -50 dBm = 100%
            Pair(((dbm + 120) * 100 / 70).coerceIn(0, 100), dbm)
        } catch (e: SecurityException) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun wifiSignal(): Pair<Int, Int>? {
        return try {
            val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val rssi = wm.connectionInfo.rssi
            if (rssi <= -127) return null
            // -100 dBm = 0%  ،  -45 dBm = 100%
            Pair(((rssi + 100) * 100 / 55).coerceIn(0, 100), rssi)
        } catch (e: SecurityException) {
            null
        }
    }
}
