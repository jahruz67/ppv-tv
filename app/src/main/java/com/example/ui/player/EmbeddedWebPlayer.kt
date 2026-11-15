package com.example.ui.player

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EmbeddedWebPlayer(
    embedUrl: String,
    reloadNonce: Int,
    onBuffering: () -> Unit,
    onPlaying: () -> Unit,
    onError: (title: String, detail: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var lastLoadedKey by remember { mutableStateOf("") }

    val containerLayout = remember {
        FrameLayout(context).apply {
            setBackgroundColor(AndroidColor.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
    }

    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(AndroidColor.BLACK)
            isFocusable = false
            isFocusableInTouchMode = false
            keepScreenOn = true
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            val targetWebView = this
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(targetWebView, true)
            }
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowContentAccess = true
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(true)
                mediaPlaybackRequiresUserGesture = false
                useWideViewPort = true
                loadWithOverviewMode = true
                builtInZoomControls = false
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                userAgentString =
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
            }
        }
    }

    DisposableEffect(webView, containerLayout) {
        var customFullscreenView: View? = null
        var customCallback: WebChromeClient.CustomViewCallback? = null
        var documentStartScriptHandler: ScriptHandler? = null

        if (webView.parent == null) {
            containerLayout.addView(webView)
        }

        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                documentStartScriptHandler = WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    EMBED_COMPAT_SCRIPT,
                    setOf("*"),
                )
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?,
            ): Boolean {
                // Ignore popup/ad windows so they cannot hijack the playback WebView
                return false
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                val req = request ?: return
                val protectedMedia = req.resources?.filter {
                    it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
                }.orEmpty()
                if (protectedMedia.isNotEmpty()) {
                    req.grant(protectedMedia.toTypedArray())
                } else {
                    req.deny()
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customFullscreenView?.let { containerLayout.removeView(it) }
                customFullscreenView = view
                customCallback = callback
                webView.visibility = View.GONE
                containerLayout.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }

            override fun onHideCustomView() {
                customFullscreenView?.let { containerLayout.removeView(it) }
                customFullscreenView = null
                customCallback?.onCustomViewHidden()
                customCallback = null
                webView.visibility = View.VISIBLE
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val scheme = request?.url?.scheme?.lowercase() ?: return false
                // Block intent://, market://, or custom app-launch redirects; keep http/https inside WebView
                return scheme != "http" && scheme != "https"
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                onBuffering()
                if (url != null && url != "about:blank") {
                    view?.evaluateJavascript(EMBED_COMPAT_SCRIPT, null)
                }
            }

            override fun onLoadResource(view: WebView?, url: String?) {
                if (url != null && url.contains("/js/bundle", ignoreCase = true)) {
                    view?.evaluateJavascript(EMBED_COMPAT_SCRIPT, null)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (url != null && url != "about:blank") {
                    view?.evaluateJavascript(EMBED_COMPAT_SCRIPT, null)
                    CookieManager.getInstance().flush()
                    onPlaying()
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                if (request?.isForMainFrame == true) {
                    onError(
                        "Embed Load Error",
                        error?.description?.toString() ?: "Unable to load broadcast embed.",
                    )
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?,
            ) {
                if (request?.isForMainFrame == true) {
                    val code = errorResponse?.statusCode ?: 500
                    onError(
                        "Embed Unavailable (HTTP $code)",
                        "The broadcast embed host returned HTTP $code. Try another source or retry later.",
                    )
                }
            }
        }

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webView.onPause()
                Lifecycle.Event.ON_RESUME -> webView.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            runCatching { documentStartScriptHandler?.remove() }
            documentStartScriptHandler = null
            customFullscreenView?.let { containerLayout.removeView(it) }
            customCallback?.onCustomViewHidden()
            containerLayout.removeAllViews()
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.removeAllViews()
            webView.destroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { containerLayout },
        update = {
            val key = "$embedUrl#$reloadNonce"
            if (lastLoadedKey != key) {
                lastLoadedKey = key
                onBuffering()
                val headers = mapOf("Referer" to "https://ppv.st/")
                webView.loadUrl(embedUrl, headers)
            }
        },
    )
}

/**
 * Compatibility guard for embedded video hosts running in Android WebView:
 * 1) Embeds such as taifood-blog.asia inject `<object id="sandDetect" data="data:application/pdf;..."
 *    onerror="window.sandDetect()">` to detect sandboxed iframes by probing Chrome's desktop PDF plugin.
 *    Android WebView has no built-in PDF viewer plugin, so `<object data="data:application/pdf">` always
 *    fires `onerror` and triggers `window.sandDetect()`, replacing the page with
 *    "Remove sandbox attributes on the iframe tag". Defining `window.sandDetect` as a non-overwritable
 *    no-op and neutralizing the `<object>` probe before page scripts run prevents this false positive.
 * 2) Strips `sandbox` attributes from any nested `<iframe>` tags created dynamically.
 */
private const val EMBED_COMPAT_SCRIPT = """
(function() {
  function cleanDom(root) {
    if (!root || !root.querySelectorAll) return;
    var frames = root.querySelectorAll('iframe[sandbox]');
    for (var i = 0; i < frames.length; i++) {
      var f = frames[i];
      f.removeAttribute('sandbox');
      if (f.src) { f.src = f.src; }
    }
    var probes = root.querySelectorAll('object#sandDetect, object[data^="data:application/pdf"]');
    for (var j = 0; j < probes.length; j++) {
      var p = probes[j];
      p.removeAttribute('onerror');
      if (p.parentNode) { p.parentNode.removeChild(p); }
    }
  }

  cleanDom(document);
  if (window.__ppvEmbedGuardInstalled) return;
  window.__ppvEmbedGuardInstalled = true;

  var noop = function() {};
  try {
    Object.defineProperty(window, 'sandDetect', {
      configurable: true,
      enumerable: true,
      get: function() { return noop; },
      set: function() {}
    });
  } catch (e) {}

  try {
    if (typeof Element !== 'undefined' && Element.prototype) {
      var origInsertAdjacentHTML = Element.prototype.insertAdjacentHTML;
      if (origInsertAdjacentHTML) {
        Element.prototype.insertAdjacentHTML = function(position, text) {
          var str = String(text || '');
          if (str.indexOf('sandDetect') !== -1 || str.indexOf('application/pdf') !== -1) {
            str = str.replace(/\sonerror\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)/gi, '');
            if (str.indexOf('<object') !== -1 && str.indexOf('sandDetect') !== -1) {
              return;
            }
          }
          return origInsertAdjacentHTML.call(this, position, str);
        };
      }
      var origSetAttribute = Element.prototype.setAttribute;
      if (origSetAttribute) {
        Element.prototype.setAttribute = function(name, value) {
          if (this.tagName === 'IFRAME' && String(name).toLowerCase() === 'sandbox') {
            return;
          }
          if (this.tagName === 'OBJECT' && String(name).toLowerCase() === 'onerror') {
            return;
          }
          return origSetAttribute.call(this, name, value);
        };
      }
    }
  } catch (e) {}

  function attachObserver() {
    if (window.__ppvSandboxObserver || !document.documentElement || typeof MutationObserver === 'undefined') {
      return;
    }
    cleanDom(document);
    window.__ppvSandboxObserver = new MutationObserver(function(mutations) {
      for (var m = 0; m < mutations.length; m++) {
        var nodes = mutations[m].addedNodes;
        for (var n = 0; n < nodes.length; n++) {
          var node = nodes[n];
          if (node && node.nodeType === 1) {
            if (node.tagName === 'IFRAME' && node.hasAttribute('sandbox')) {
              node.removeAttribute('sandbox');
            }
            if (node.tagName === 'OBJECT' && (node.id === 'sandDetect' || (node.getAttribute('data') || '').indexOf('application/pdf') !== -1)) {
              node.removeAttribute('onerror');
              if (node.parentNode) { node.parentNode.removeChild(node); }
            } else {
              cleanDom(node);
            }
          }
        }
      }
    });
    window.__ppvSandboxObserver.observe(document.documentElement, { childList: true, subtree: true });
  }

  if (document.documentElement) {
    attachObserver();
  } else if (document.addEventListener) {
    document.addEventListener('DOMContentLoaded', attachObserver, false);
  }
})();
"""
