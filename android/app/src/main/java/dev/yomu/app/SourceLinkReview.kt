package dev.yomu.app

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** A source review stays above the library without changing import or playback state. */
@Composable
internal fun SourceLinkReviewButton(url: String?, label: String = "Open link") {
    val source = url?.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) } ?: return
    var showing by rememberSaveable(source) { mutableStateOf(false) }
    TextButton(onClick = { showing = true }) { Text(label) }
    if (showing) SourceLinkReview(source) { showing = false }
}

@Composable
private fun SourceLinkReview(url: String, onClose: () -> Unit) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(url) }
    var browserError by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler {
            if (webView?.canGoBack() == true) webView?.goBack() else onClose()
        }
        Surface(Modifier.fillMaxWidth(.96f).fillMaxHeight(.86f), shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
            Column {
                Text("Review source", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp))
                Text(currentUrl, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                    modifier = Modifier.padding(horizontal = 16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = {
                        browserError = runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)))
                        }.isFailure
                    }) { Text("Open in browser") }
                    TextButton(onClick = onClose) { Text("Close") }
                }
                if (browserError) Text("No browser is available.", color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp))
                if (progress < 100) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                AndroidView(modifier = Modifier.fillMaxWidth().weight(1f), onRelease = {
                    it.stopLoading()
                    it.destroy()
                    webView = null
                }, factory = { viewContext ->
                    WebView(viewContext).apply {
                        webView = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val scheme = request.url.scheme
                                return scheme != "https" && scheme != "http"
                            }
                            override fun onPageStarted(view: WebView, target: String, favicon: android.graphics.Bitmap?) {
                                if (target.startsWith("https://") || target.startsWith("http://")) currentUrl = target
                            }
                        }
                        loadUrl(url)
                    }
                })
            }
        }
    }
}
