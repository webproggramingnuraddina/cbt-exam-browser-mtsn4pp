
package id.sch.smkwhapo.cbtexambrowser

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorOverlay: View

    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val data =
                if (result.resultCode == Activity.RESULT_OK) result.data else null

            val uris = WebChromeClient.FileChooserParams.parseResult(
                result.resultCode,
                data
            )

            filePathCallback?.onReceiveValue(uris)
            filePathCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        hideSystemBars()

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        errorOverlay = findViewById(R.id.errorOverlay)

        setupWebView()
        setupErrorOverlay()
        setupExitButton()

        // Aktifkan Screen Pinning satu kali
        enterExamPinning()

        // Buka halaman CBT
        webView.loadUrl(getString(R.string.exam_url))
    }

    // ---------- FULLSCREEN ----------

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun hideSystemBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
    }

    // ---------- SCREEN PINNING ----------

    private fun enterExamPinning() {
        try {
            if (android.os.Build.VERSION.SDK_INT >=
                android.os.Build.VERSION_CODES.LOLLIPOP
            ) {
                startLockTask()
            }
        } catch (e: Exception) {
            Log.e("CBT", "Gagal mengaktifkan Screen Pinning", e)

            Toast.makeText(
                this,
                "Gagal mengaktifkan penguncian aplikasi",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun releaseExamPinning() {
        try {
            stopLockTask()
            Log.d("CBT", "Screen Pinning dilepaskan")
        } catch (e: Exception) {
            Log.e("CBT", "Gagal melepaskan Screen Pinning", e)
        }
    }

    // ---------- WEBVIEW SETUP ----------

    private val allowedHost by lazy {
        getString(R.string.allowed_host)
    }

    private fun setupWebView() {
        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode =
            WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false

        if (android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.O
        ) {
            settings.safeBrowsingEnabled = true
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance()
            .setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {

                val host = request.url.host ?: return true
                val scheme = request.url.scheme
                val isHttps = scheme == "https"

                return if (
                    isHttps &&
                    (host == allowedHost ||
                        host.endsWith(".$allowedHost"))
                ) {
                    false
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Tautan di luar domain ujian diblokir",
                        Toast.LENGTH_SHORT
                    ).show()

                    true
                }
            }

            override fun onPageStarted(
                view: WebView,
                url: String,
                favicon: android.graphics.Bitmap?
            ) {
                errorOverlay.visibility = View.GONE
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(
                view: WebView,
                url: String
            ) {
                progressBar.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    progressBar.visibility = View.GONE
                    errorOverlay.visibility = View.VISIBLE
                }
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: android.net.http.SslError
            ) {
                handler.cancel()

                Toast.makeText(
                    this@MainActivity,
                    "Koneksi tidak aman diblokir",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {

            override fun onProgressChanged(
                view: WebView,
                newProgress: Int
            ) {
                progressBar.progress = newProgress
            }

            override fun onShowFileChooser(
                webView: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {

                filePathCallback = callback

                val intent = params.createIntent()

                try {
                    fileChooserLauncher.launch(intent)
                } catch (e: ActivityNotFoundException) {
                    filePathCallback = null
                    return false
                }

                return true
            }
        }
    }

    // ---------- ERROR OVERLAY ----------

    private fun setupErrorOverlay() {
        findViewById<View>(R.id.retryButton).setOnClickListener {
            webView.loadUrl(getString(R.string.exam_url))
        }
    }

    // ---------- TOMBOL BACK ----------

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            Toast.makeText(
                this,
                "Gunakan tombol X untuk keluar dari aplikasi",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ---------- TOMBOL X ----------

    private fun setupExitButton() {
        findViewById<ImageButton>(R.id.exitButton).setOnClickListener {
            confirmExit()
        }
    }

    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setTitle(R.string.exit_dialog_title)
            .setMessage(R.string.exit_dialog_message)
            .setPositiveButton(R.string.exit_confirm) { _, _ ->

                // Lepaskan penguncian setelah siswa menyetujui keluar
                releaseExamPinning()

                // Tutup aplikasi
                finishAndRemoveTask()
            }
            .setNegativeButton(R.string.exit_cancel, null)
            .show()
    }

    // ---------- DESTROY ----------

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.destroy()
        }

        super.onDestroy()
    }
}
