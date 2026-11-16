package com.example.ui.player

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Message
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
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

@Stable
class WebEmbedController {
    private var webView: WebView? = null
    internal var isPlayingNow: Boolean = false
    internal var isBufferingNow: Boolean = false
    internal var isMutedNow: Boolean = false
    internal var userPausedNow: Boolean = false
    internal var autoTapAttempts: Int = 0
    internal var initialHost: String? = null
    internal var pageFinished: Boolean = false

    internal fun attach(view: WebView) {
        webView = view
    }

    internal fun isAttachedTo(view: WebView): Boolean = webView === view

    internal fun detach(view: WebView) {
        if (webView === view) {
            webView = null
        }
    }

    internal fun resetForLoad(embedUrl: String) {
        isPlayingNow = false
        isBufferingNow = false
        isMutedNow = false
        userPausedNow = false
        autoTapAttempts = 0
        pageFinished = false
        initialHost = runCatching { Uri.parse(embedUrl).host?.lowercase() }.getOrNull()
    }

    fun play() {
        userPausedNow = false
        val view = webView ?: return
        view.post {
            if (!isAttachedTo(view)) return@post
            view.evaluateJavascript(
                "window.__ppvUserActionPlay && window.__ppvUserActionPlay();",
                null,
            )
        }
    }

    fun pause() {
        userPausedNow = true
        isPlayingNow = false
        isBufferingNow = false
        val view = webView ?: return
        view.post {
            if (!isAttachedTo(view)) return@post
            view.evaluateJavascript(
                "window.__ppvUserActionPause && window.__ppvUserActionPause();",
                null,
            )
        }
    }

    fun togglePlayPause() {
        if (!userPausedNow) {
            pause()
        } else {
            play()
        }
    }

    fun toggleMute() {
        val view = webView ?: return
        view.post {
            if (!isAttachedTo(view)) return@post
            view.evaluateJavascript(
                "window.__ppvUserActionToggleMute && window.__ppvUserActionToggleMute();",
                null,
            )
        }
    }

    internal fun dispatchTrustedTap(normX: Float, normY: Float) {
        val view = webView ?: return
        view.post {
            if (!isAttachedTo(view)) return@post
            val w = view.width
            val h = view.height
            if (w <= 0 || h <= 0) return@post
            val tapX = w * normX.coerceIn(0.05f, 0.95f)
            val tapY = h * normY.coerceIn(0.05f, 0.95f)
            val downTime = SystemClock.uptimeMillis()
            val downEvent = MotionEvent.obtain(
                downTime,
                downTime,
                MotionEvent.ACTION_DOWN,
                tapX,
                tapY,
                0,
            )
            view.dispatchTouchEvent(downEvent)
            downEvent.recycle()

            view.postDelayed(
                {
                    if (!isAttachedTo(view)) return@postDelayed
                    val upTime = SystemClock.uptimeMillis()
                    val upEvent = MotionEvent.obtain(
                        downTime,
                        upTime,
                        MotionEvent.ACTION_UP,
                        tapX,
                        tapY,
                        0,
                    )
                    view.dispatchTouchEvent(upEvent)
                    upEvent.recycle()
                },
                60L,
            )
        }
    }
}

@Keep
private class PpvEmbedBridge(
    private val webView: WebView,
    private val controller: WebEmbedController,
    private val onBuffering: () -> Unit,
    private val onPlaying: () -> Unit,
    private val onPaused: () -> Unit,
    private val onMutedChanged: (Boolean) -> Unit,
) {
    @JavascriptInterface
    fun onPlayerState(state: String, muted: Boolean) {
        webView.post {
            if (!controller.isAttachedTo(webView)) return@post
            controller.isMutedNow = muted
            onMutedChanged(muted)
            when (state) {
                "playing" -> {
                    if (controller.userPausedNow) return@post
                    controller.isPlayingNow = true
                    controller.isBufferingNow = false
                    controller.userPausedNow = false
                    onPlaying()
                }
                "buffering" -> {
                    if (!controller.userPausedNow) {
                        controller.isPlayingNow = false
                        controller.isBufferingNow = true
                        onBuffering()
                    }
                }
                "paused", "idle" -> {
                    controller.isPlayingNow = false
                    controller.isBufferingNow = false
                    controller.userPausedNow = true
                    onPaused()
                }
            }
        }
    }

    @JavascriptInterface
    fun requestTrustedTap(normX: Float, normY: Float, forceFromUser: Boolean) {
        webView.post {
            if (!controller.isAttachedTo(webView)) return@post
            if (!forceFromUser) {
                if (controller.isPlayingNow || controller.isBufferingNow ||
                    controller.userPausedNow || controller.autoTapAttempts >= 3
                ) {
                    return@post
                }
                controller.autoTapAttempts += 1
            }
            controller.dispatchTrustedTap(normX, normY)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun EmbeddedWebPlayer(
    embedUrl: String,
    reloadNonce: Int,
    controller: WebEmbedController,
    onBuffering: () -> Unit,
    onPlaying: () -> Unit,
    onPaused: () -> Unit,
    onMutedChanged: (Boolean) -> Unit,
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

    DisposableEffect(webView, containerLayout, controller) {
        var customFullscreenView: View? = null
        var customCallback: WebChromeClient.CustomViewCallback? = null
        var documentStartScriptHandler: ScriptHandler? = null
        val defaultPosterBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
            eraseColor(AndroidColor.BLACK)
        }

        if (webView.parent == null) {
            containerLayout.addView(webView)
        }
        controller.attach(webView)

        val bridge = PpvEmbedBridge(
            webView = webView,
            controller = controller,
            onBuffering = onBuffering,
            onPlaying = onPlaying,
            onPaused = onPaused,
            onMutedChanged = onMutedChanged,
        )
        runCatching {
            webView.addJavascriptInterface(bridge, "PpvEmbedBridge")
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
            override fun getDefaultVideoPoster(): Bitmap = defaultPosterBitmap

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
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase() ?: return false
                if (scheme != "http" && scheme != "https") {
                    return true
                }
                // After initial load settles, block non-redirect cross-host main-frame ad hijacks
                if (controller.pageFinished && request.isForMainFrame && !request.isRedirect) {
                    val targetHost = uri.host?.lowercase()
                    val expectedHost = controller.initialHost
                    if (!expectedHost.isNullOrBlank() && !targetHost.isNullOrBlank() && targetHost != expectedHost) {
                        return true
                    }
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                controller.pageFinished = false
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
                    controller.pageFinished = true
                    view?.evaluateJavascript(EMBED_COMPAT_SCRIPT, null)
                    view?.evaluateJavascript(
                        "window.__ppvStartAutoplayWatchdog && window.__ppvStartAutoplayWatchdog();",
                        null,
                    )
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
            controller.detach(webView)
            runCatching { webView.removeJavascriptInterface("PpvEmbedBridge") }
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
            if (!defaultPosterBitmap.isRecycled) {
                defaultPosterBitmap.recycle()
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { containerLayout },
        update = {
            val key = "$embedUrl#$reloadNonce"
            if (lastLoadedKey != key) {
                lastLoadedKey = key
                controller.resetForLoad(embedUrl)
                val headers = mapOf("Referer" to "https://ppv.st/")
                webView.loadUrl(embedUrl, headers)
            }
        },
    )
}

/**
 * Compatibility & TV autoplay script for embedded video hosts in Android WebView:
 * 1) Neutralizes `sandDetect` PDF-plugin probes & strips `iframe[sandbox]` so WebView never
 *    shows "Remove sandbox attributes on the iframe tag".
 * 2) Suppresses popunder ad windows (`window.open`) that steal focus or intercept clicks.
 * 3) Hooks `window.jwplayer` and `<video>` elements to force `autostart: true` with sound on
 *    (`mute: false`), monitors real playback state, and avoids interfering while buffering or
 *    once playback has started.
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

  if (window.__ppvEmbedGuardInstalled) return;
  window.__ppvEmbedGuardInstalled = true;
  cleanDom(document);
  window.__ppvUserPaused = false;
  window.__ppvUserToggledMute = false;
  window.__ppvHasStarted = false;

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
    window.open = function() { return null; };
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

  function getAnyVideo() {
    var vids = document.querySelectorAll('video');
    for (var i = 0; i < vids.length; i++) {
      return vids[i];
    }
    return null;
  }

  function isVideoPlaying() {
    var vids = document.querySelectorAll('video');
    for (var i = 0; i < vids.length; i++) {
      var v = vids[i];
      if (!v.paused && !v.ended && v.readyState >= 3) {
        return true;
      }
    }
    var jw = getJwInstance();
    if (jw && typeof jw.getState === 'function') {
      try {
        if (jw.getState() === 'playing') return true;
      } catch (e) {}
    }
    return false;
  }

  function isPlayingOrBuffering() {
    var vids = document.querySelectorAll('video');
    for (var i = 0; i < vids.length; i++) {
      var v = vids[i];
      if (!v.paused && !v.ended) {
        return true;
      }
    }
    var jw = getJwInstance();
    if (jw && typeof jw.getState === 'function') {
      try {
        var st = jw.getState();
        if (st === 'playing' || st === 'buffering') return true;
      } catch (e) {}
    }
    return false;
  }

  function getCurrentMuted() {
    var jw = getJwInstance();
    if (jw && typeof jw.getMute === 'function') {
      try { return !!jw.getMute(); } catch (e) {}
    }
    var v = getAnyVideo();
    if (v) return !!v.muted;
    return false;
  }

  function ensureUnmutedByDefault(inst) {
    if (window.__ppvUserToggledMute) return;
    if (inst && typeof inst.setMute === 'function') {
      try {
        if (typeof inst.getMute !== 'function' || inst.getMute()) {
          inst.setMute(false);
        }
        if (typeof inst.setVolume === 'function' && typeof inst.getVolume === 'function' && inst.getVolume() === 0) {
          inst.setVolume(100);
        }
      } catch (e) {}
    }
    var vids = document.querySelectorAll('video');
    for (var i = 0; i < vids.length; i++) {
      try { vids[i].removeAttribute('muted'); } catch (e) {}
      if (vids[i].muted) {
        try { vids[i].muted = false; } catch (e) {}
      }
      if (vids[i].volume === 0) {
        try { vids[i].volume = 1; } catch (e) {}
      }
    }
  }

  var lastReportedState = '';
  var lastReportedMute = null;
  function reportState(state, muted) {
    var m = (typeof muted === 'boolean') ? muted : getCurrentMuted();
    var s = state || (isVideoPlaying() ? 'playing' : 'paused');
    if (s === lastReportedState && m === lastReportedMute) return;
    lastReportedState = s;
    lastReportedMute = m;
    if (window.PpvEmbedBridge && typeof window.PpvEmbedBridge.onPlayerState === 'function') {
      try { window.PpvEmbedBridge.onPlayerState(s, !!m); } catch (e) {}
    }
  }

  function findPlayButtonCoords() {
    var selectors = [
      '.jw-display-icon-container',
      '.jw-icon-display',
      '.jw-display-icon-display',
      '.player-poster',
      '.plyr__control--overlaid',
      'button[aria-label*="Play" i]',
      '.vjs-big-play-button'
    ];
    for (var i = 0; i < selectors.length; i++) {
      var el = document.querySelector(selectors[i]);
      if (el && typeof el.getBoundingClientRect === 'function') {
        var r = el.getBoundingClientRect();
        if (r.width > 4 && r.height > 4 && window.innerWidth > 0 && window.innerHeight > 0) {
          return {
            x: (r.left + r.width * 0.5) / window.innerWidth,
            y: (r.top + r.height * 0.5) / window.innerHeight,
            el: el
          };
        }
      }
    }
    return { x: 0.5, y: 0.5, el: null };
  }

  function getJwInstance() {
    if (window.__ppvLastJw) return window.__ppvLastJw;
    if (typeof window.__ppvRawJw === 'function') {
      try {
        var inst = window.__ppvRawJw('player') || window.__ppvRawJw();
        if (inst && typeof inst.getState === 'function') {
          window.__ppvLastJw = inst;
          return inst;
        }
      } catch (e) {}
    }
    if (typeof window.jwplayer === 'function') {
      try {
        var inst2 = window.jwplayer('player') || window.jwplayer();
        if (inst2 && typeof inst2.getState === 'function') {
          window.__ppvLastJw = inst2;
          return inst2;
        }
      } catch (e) {}
    }
    return null;
  }

  function watchVideoElement(v) {
    if (!v || v.__ppvWatched) return;
    v.__ppvWatched = true;
    try {
      v.playsInline = true;
      v.autoplay = true;
      if (!window.__ppvUserToggledMute) {
        v.removeAttribute('muted');
        v.muted = false;
        if (v.volume === 0) v.volume = 1;
      }
      v.setAttribute('playsinline', '');
      v.setAttribute('autoplay', '');
    } catch (e) {}

    v.addEventListener('playing', function() {
      window.__ppvHasStarted = true;
      ensureUnmutedByDefault(getJwInstance());
      reportState('playing', !!v.muted);
    });
    v.addEventListener('pause', function() {
      if (window.__ppvUserPaused) {
        reportState('paused', !!v.muted);
      }
    });
    v.addEventListener('waiting', function() {
      if (!window.__ppvUserPaused) {
        reportState('buffering', !!v.muted);
      }
    });
    v.addEventListener('volumechange', function() {
      var st = isVideoPlaying() ? 'playing' : (window.__ppvUserPaused ? 'paused' : (lastReportedState || 'buffering'));
      reportState(st, !!v.muted);
    });
    v.addEventListener('canplay', function() {
      if (!window.__ppvUserPaused && !window.__ppvHasStarted && v.paused) {
        ensureUnmutedByDefault(getJwInstance());
        try {
          var p = v.play();
          if (p && typeof p.catch === 'function') p.catch(function() {});
        } catch (e) {}
      }
    });
  }

  function scanVideos() {
    var vids = document.querySelectorAll('video');
    for (var i = 0; i < vids.length; i++) {
      watchVideoElement(vids[i]);
    }
  }

  function attachJwEvents(inst) {
    if (!inst || inst.__ppvEventsAttached || typeof inst.on !== 'function') return;
    inst.__ppvEventsAttached = true;
    window.__ppvLastJw = inst;

    inst.on('ready', function() {
      scanVideos();
      ensureUnmutedByDefault(inst);
      if (!window.__ppvUserPaused && !window.__ppvHasStarted && !isPlayingOrBuffering()) {
        try { inst.play(); } catch (e) {}
        setTimeout(function() { ensurePlaying(false); }, 450);
      }
    });
    inst.on('autostartNotAllowed', function() {
      if (!window.__ppvUserPaused && !window.__ppvHasStarted && !isPlayingOrBuffering()) {
        ensurePlaying(true);
      }
    });
    inst.on('play', function() {
      window.__ppvHasStarted = true;
      ensureUnmutedByDefault(inst);
      reportState('playing', getCurrentMuted());
    });
    inst.on('firstFrame', function() {
      window.__ppvHasStarted = true;
      ensureUnmutedByDefault(inst);
      reportState('playing', getCurrentMuted());
    });
    inst.on('buffer', function() {
      if (!window.__ppvUserPaused) {
        reportState('buffering', getCurrentMuted());
      }
    });
    inst.on('pause', function() {
      if (window.__ppvUserPaused) {
        reportState('paused', getCurrentMuted());
      }
    });
    inst.on('idle', function() {
      if (window.__ppvUserPaused) {
        reportState('paused', getCurrentMuted());
      } else if (window.__ppvHasStarted) {
        reportState('buffering', getCurrentMuted());
      }
    });
    inst.on('mute', function(ev) {
      var st = isVideoPlaying() ? 'playing' : (window.__ppvUserPaused ? 'paused' : (lastReportedState || 'buffering'));
      reportState(st, !!(ev && ev.mute));
    });
  }

  function wrapJwInstance(inst) {
    if (!inst || typeof inst !== 'object') return inst;
    window.__ppvLastJw = inst;
    if (inst.__ppvWrapped) return inst;
    inst.__ppvWrapped = true;

    if (typeof inst.setup === 'function') {
      var origSetup = inst.setup;
      inst.setup = function(cfg) {
        if (cfg && typeof cfg === 'object') {
          if (!window.__ppvUserPaused) {
            cfg.autostart = true;
          }
          if (!window.__ppvUserToggledMute) {
            cfg.mute = false;
            if (!cfg.volume) cfg.volume = 100;
          }
          if (cfg.autoPause && typeof cfg.autoPause === 'object') {
            cfg.autoPause.viewability = false;
          }
        }
        var res = origSetup.apply(this, arguments);
        attachJwEvents(res || this);
        window.__ppvStartAutoplayWatchdog && window.__ppvStartAutoplayWatchdog();
        return res;
      };
    }
    attachJwEvents(inst);
    return inst;
  }

  function wrapJwFactory(fn) {
    if (typeof fn !== 'function') return fn;
    window.__ppvRawJw = fn;
    if (fn.__ppvFactoryWrapped) return fn;
    try {
      var proxied = new Proxy(fn, {
        apply: function(target, thisArg, args) {
          var inst = Reflect.apply(target, thisArg, args);
          return wrapJwInstance(inst);
        }
      });
      proxied.__ppvFactoryWrapped = true;
      return proxied;
    } catch (e) {
      var fallback = function() {
        var inst = fn.apply(this, arguments);
        return wrapJwInstance(inst);
      };
      fallback.__ppvFactoryWrapped = true;
      return fallback;
    }
  }

  if (!window.__ppvJwHooked) {
    window.__ppvJwHooked = true;
    var currentJw = typeof window.jwplayer === 'function' ? wrapJwFactory(window.jwplayer) : window.jwplayer;
    try {
      Object.defineProperty(window, 'jwplayer', {
        configurable: true,
        enumerable: true,
        get: function() { return currentJw; },
        set: function(val) {
          currentJw = wrapJwFactory(val);
        }
      });
    } catch (e) {}
  }

  function ensurePlaying(allowTap) {
    if (window.__ppvUserPaused || window.__ppvHasStarted) return;
    scanVideos();
    if (isVideoPlaying()) {
      window.__ppvHasStarted = true;
      ensureUnmutedByDefault(getJwInstance());
      reportState('playing', getCurrentMuted());
      return;
    }
    // Never interrupt or re-trigger a stream that is actively buffering
    if (isPlayingOrBuffering()) {
      return;
    }
    var jw = getJwInstance();
    if (jw) {
      attachJwEvents(jw);
      ensureUnmutedByDefault(jw);
      var st = '';
      try { st = jw.getState(); } catch (e) {}
      if (st === 'playing') {
        window.__ppvHasStarted = true;
        reportState('playing', getCurrentMuted());
        return;
      }
      if (st === 'buffering') {
        return;
      }
      if (typeof jw.play === 'function') {
        try { jw.play(); } catch (e) {}
      }
    }

    var vids = document.querySelectorAll('video');
    for (var i = 0; i < vids.length; i++) {
      var v = vids[i];
      if (v.paused && (v.currentSrc || v.src || v.srcObject || v.readyState >= 1)) {
        if (!window.__ppvUserToggledMute) {
          v.muted = false;
        }
        try {
          var pr = v.play();
          if (pr && typeof pr.catch === 'function') {
            pr.catch(function() {});
          }
        } catch (e) {}
      }
    }

    if (allowTap && !isPlayingOrBuffering() && !window.__ppvHasStarted) {
      var target = findPlayButtonCoords();
      if (target.el && typeof target.el.click === 'function') {
        try { target.el.click(); } catch (e) {}
      }
      if (window.PpvEmbedBridge && typeof window.PpvEmbedBridge.requestTrustedTap === 'function') {
        try { window.PpvEmbedBridge.requestTrustedTap(target.x, target.y, false); } catch (e) {}
      }
    }
  }

  var watchdogTimer = null;
  window.__ppvStartAutoplayWatchdog = function() {
    if (watchdogTimer || window.__ppvHasStarted || window.__ppvUserPaused) {
      return;
    }
    var ticks = 0;
    watchdogTimer = setInterval(function() {
      ticks += 1;
      scanVideos();
      if (window.__ppvUserPaused || window.__ppvHasStarted) {
        clearInterval(watchdogTimer);
        watchdogTimer = null;
        return;
      }
      if (isVideoPlaying()) {
        window.__ppvHasStarted = true;
        ensureUnmutedByDefault(getJwInstance());
        reportState('playing', getCurrentMuted());
        clearInterval(watchdogTimer);
        watchdogTimer = null;
        return;
      }
      if (isPlayingOrBuffering()) {
        if (ticks >= 24) {
          clearInterval(watchdogTimer);
          watchdogTimer = null;
        }
        return;
      }
      if (ticks === 1 || ticks === 2) {
        ensurePlaying(false);
      } else if (ticks === 3 || ticks === 5) {
        ensurePlaying(true);
      } else if (ticks >= 12) {
        if (!isPlayingOrBuffering()) {
          reportState('paused', getCurrentMuted());
        }
        clearInterval(watchdogTimer);
        watchdogTimer = null;
      }
    }, 600);
  };

  window.__ppvUserActionPlay = function() {
    window.__ppvUserPaused = false;
    if (isPlayingOrBuffering()) {
      reportState(isVideoPlaying() ? 'playing' : 'buffering', getCurrentMuted());
      return;
    }
    scanVideos();
    var jw = getJwInstance();
    ensureUnmutedByDefault(jw);
    if (jw && typeof jw.play === 'function') {
      try { jw.play(); } catch (e) {}
    }
    var vids = document.querySelectorAll('video');
    for (var i = 0; i < vids.length; i++) {
      try {
        var pr = vids[i].play();
        if (pr && typeof pr.catch === 'function') pr.catch(function() {});
      } catch (e) {}
    }
    setTimeout(function() {
      if (!isPlayingOrBuffering() && !window.__ppvUserPaused) {
        var target = findPlayButtonCoords();
        if (target.el && typeof target.el.click === 'function') {
          try { target.el.click(); } catch (e) {}
        }
        if (window.PpvEmbedBridge && typeof window.PpvEmbedBridge.requestTrustedTap === 'function') {
          try { window.PpvEmbedBridge.requestTrustedTap(target.x, target.y, true); } catch (e) {}
        }
      }
    }, 120);
  };

  window.__ppvUserActionPause = function() {
    window.__ppvUserPaused = true;
    var jw = getJwInstance();
    if (jw && typeof jw.pause === 'function') {
      try { jw.pause(); } catch (e) {}
    }
    var vids = document.querySelectorAll('video');
    for (var i = 0; i < vids.length; i++) {
      try { vids[i].pause(); } catch (e) {}
    }
    reportState('paused', getCurrentMuted());
  };

  window.__ppvUserActionToggleMute = function() {
    window.__ppvUserToggledMute = true;
    var nextMuted = !getCurrentMuted();
    var jw = getJwInstance();
    if (jw && typeof jw.setMute === 'function') {
      try { jw.setMute(nextMuted); } catch (e) {}
    }
    var vids = document.querySelectorAll('video');
    for (var i = 0; i < vids.length; i++) {
      try { vids[i].muted = nextMuted; } catch (e) {}
    }
    reportState(isVideoPlaying() ? 'playing' : (lastReportedState || 'paused'), nextMuted);
  };

  function attachObserver() {
    if (window.__ppvSandboxObserver || !document.documentElement || typeof MutationObserver === 'undefined') {
      return;
    }
    cleanDom(document);
    scanVideos();
    window.__ppvSandboxObserver = new MutationObserver(function(mutations) {
      var foundVideo = false;
      for (var m = 0; m < mutations.length; m++) {
        var nodes = mutations[m].addedNodes;
        for (var n = 0; n < nodes.length; n++) {
          var node = nodes[n];
          if (node && node.nodeType === 1) {
            if (node.tagName === 'IFRAME' && node.hasAttribute('sandbox')) {
              node.removeAttribute('sandbox');
            }
            if (node.tagName === 'VIDEO') {
              foundVideo = true;
              watchVideoElement(node);
            }
            if (node.tagName === 'OBJECT' && (node.id === 'sandDetect' || (node.getAttribute('data') || '').indexOf('application/pdf') !== -1)) {
              node.removeAttribute('onerror');
              if (node.parentNode) { node.parentNode.removeChild(node); }
            } else {
              cleanDom(node);
              if (node.querySelectorAll) {
                var innerVids = node.querySelectorAll('video');
                if (innerVids.length > 0) {
                  foundVideo = true;
                  for (var k = 0; k < innerVids.length; k++) {
                    watchVideoElement(innerVids[k]);
                  }
                }
              }
            }
          }
        }
      }
      if (foundVideo && !window.__ppvUserPaused && !window.__ppvHasStarted) {
        ensurePlaying(false);
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
