package ua.tvbrowser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar

class MainActivity : Activity() {

    companion object {
        private const val HOME = "https://www.google.com"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }

    private lateinit var webView: WebView
    private lateinit var webContainer: FrameLayout
    private lateinit var fullscreenContainer: FrameLayout
    private lateinit var urlInput: EditText
    private lateinit var btnCursor: Button
    private lateinit var btnSpeed: Button
    private lateinit var progress: ProgressBar
    private lateinit var cursor: CursorView

    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null

    private var cursorMode = true
    private var cursorX = 0f
    private var cursorY = 0f

    private val speedSteps = floatArrayOf(0.5f, 1f, 1.5f, 2.5f)
    private val speedLabels = arrayOf("×0.5", "×1", "×1.5", "×2.5")
    private var speedIndex = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        webContainer = findViewById(R.id.webContainer)
        fullscreenContainer = findViewById(R.id.fullscreenContainer)
        urlInput = findViewById(R.id.urlInput)
        btnCursor = findViewById(R.id.btnCursor)
        btnSpeed = findViewById(R.id.btnSpeed)
        speedIndex = getSharedPreferences("tvbrowser", Context.MODE_PRIVATE)
            .getInt("speed", 1).coerceIn(0, speedSteps.size - 1)
        btnSpeed.text = "Швидкість: " + speedLabels[speedIndex]
        progress = findViewById(R.id.progress)

        cursor = CursorView(this)
        webContainer.addView(
            cursor,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        webContainer.post {
            cursorX = webContainer.width / 2f
            cursorY = webContainer.height / 2f
            cursor.moveTo(cursorX, cursorY)
        }

        setupWebView()
        setupToolbar()

        webView.setOnFocusChangeListener { _, hasFocus -> refreshCursor(hasFocus) }
        webView.requestFocus()
        webView.loadUrl(HOME)
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = DESKTOP_UA
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme
                if (scheme == "http" || scheme == "https") return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (e: Exception) {
                    // немає застосунку для цього посилання
                }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (url != null && !urlInput.hasFocus()) urlInput.setText(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (url != null && !urlInput.hasFocus()) urlInput.setText(url)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }

            override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
                if (view == null) return
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customCallback = callback
                fullscreenContainer.addView(view)
                fullscreenContainer.visibility = View.VISIBLE
                view.requestFocus()
            }

            override fun onHideCustomView() {
                hideCustomView()
            }
        }
    }

    private fun setupToolbar() {
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
        }
        findViewById<Button>(R.id.btnForward).setOnClickListener {
            if (webView.canGoForward()) webView.goForward()
        }
        findViewById<Button>(R.id.btnReload).setOnClickListener { webView.reload() }
        findViewById<Button>(R.id.btnHome).setOnClickListener { load(HOME) }
        findViewById<Button>(R.id.btnGo).setOnClickListener { load(urlInput.text.toString()) }
        btnCursor.setOnClickListener {
            cursorMode = !cursorMode
            refreshCursor(webView.isFocused)
        }

        btnSpeed.setOnClickListener {
            speedIndex = (speedIndex + 1) % speedSteps.size
            btnSpeed.text = "Швидкість: " + speedLabels[speedIndex]
            getSharedPreferences("tvbrowser", Context.MODE_PRIVATE)
                .edit().putInt("speed", speedIndex).apply()
        }

        urlInput.setOnEditorActionListener { v, actionId, event ->
            val enter = event != null &&
                event.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                load(v.text.toString())
                true
            } else {
                false
            }
        }
    }

    private fun refreshCursor(webHasFocus: Boolean) {
        btnCursor.text = if (cursorMode) "Курсор: ВКЛ" else "Курсор: ВИКЛ"
        cursor.visibility = if (cursorMode && webHasFocus) View.VISIBLE else View.INVISIBLE
    }

    private fun load(text: String) {
        webView.loadUrl(normalize(text))
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(urlInput.windowToken, 0)
        webView.requestFocus()
    }

    private fun normalize(input: String): String {
        val s = input.trim()
        if (s.isEmpty()) return HOME
        if (s.startsWith("http://") || s.startsWith("https://")) return s
        val looksLikeHost = !s.contains(" ") && (s.contains(".") || s.startsWith("localhost"))
        return if (looksLikeHost) "https://$s" else "https://www.google.com/search?q=" + Uri.encode(s)
    }

    private fun hideCustomView() {
        val v = customView ?: return
        fullscreenContainer.removeView(v)
        fullscreenContainer.visibility = View.GONE
        customCallback?.onCustomViewHidden()
        customView = null
        customCallback = null
        webView.requestFocus()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN

        if (event.keyCode == KeyEvent.KEYCODE_BACK && down) {
            if (customView != null) {
                hideCustomView()
                return true
            }
            if (webView.canGoBack()) {
                webView.goBack()
                return true
            }
        }

        if (customView == null && cursorMode && webView.hasFocus()) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (down) moveCursor(event.keyCode, event.repeatCount)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {
                    if (down && event.repeatCount == 0) clickAtCursor()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun moveCursor(keyCode: Int, repeat: Int) {
        val step = (dp(12) + minOf(repeat, 20) * dp(2)) * speedSteps[speedIndex]
        val maxX = (webContainer.width - 1).toFloat()
        val maxY = (webContainer.height - 1).toFloat()
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (cursorX <= 0f) webView.scrollBy(-step.toInt(), 0)
                cursorX = maxOf(0f, cursorX - step)
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (cursorX >= maxX) webView.scrollBy(step.toInt(), 0)
                cursorX = minOf(maxX, cursorX + step)
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (cursorY <= 0f) {
                    if (webView.scrollY > 0) {
                        webView.scrollBy(0, -step.toInt())
                    } else if (repeat == 0) {
                        urlInput.requestFocus()
                    }
                }
                cursorY = maxOf(0f, cursorY - step)
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (cursorY >= maxY) webView.scrollBy(0, step.toInt())
                cursorY = minOf(maxY, cursorY + step)
            }
        }
        cursor.moveTo(cursorX, cursorY)
    }

    private fun clickAtCursor() {
        val t = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, cursorX, cursorY, 0)
        val up = MotionEvent.obtain(t, t + 80, MotionEvent.ACTION_UP, cursorX, cursorY, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        up.source = InputDevice.SOURCE_TOUCHSCREEN
        webView.dispatchTouchEvent(down)
        webView.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        webContainer.removeAllViews()
        webView.destroy()
        super.onDestroy()
    }
}

class CursorView(context: Context) : View(context) {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val arrow = Path()
    private var px = 0f
    private var py = 0f

    init {
        isFocusable = false
    }

    fun moveTo(x: Float, y: Float) {
        px = x
        py = y
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        arrow.reset()
        arrow.moveTo(px, py)
        arrow.lineTo(px, py + 22 * d)
        arrow.lineTo(px + 6 * d, py + 17 * d)
        arrow.lineTo(px + 10 * d, py + 25 * d)
        arrow.lineTo(px + 13 * d, py + 23.5f * d)
        arrow.lineTo(px + 9 * d, py + 16 * d)
        arrow.lineTo(px + 16 * d, py + 16 * d)
        arrow.close()
        canvas.drawPath(arrow, fillPaint)
        canvas.drawPath(arrow, strokePaint)
    }
}
