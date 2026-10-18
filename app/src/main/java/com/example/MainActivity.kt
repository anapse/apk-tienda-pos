package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                PosTiendaApp()
            }
        }
    }
}

fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PosTiendaApp() {
    val context = LocalContext.current
    val targetUrl = "https://anapse.github.io/Anapse-PosTienda/"

    var webView: WebView? by remember { mutableStateOf(null) }
    var currentUrl by remember { mutableStateOf(targetUrl) }
    var pageTitle by remember { mutableStateOf("Anapse PosTienda") }
    var progress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isOffline by remember { mutableStateOf(!isNetworkAvailable(context)) }
    var showMenu by remember { mutableStateOf(false) }
    var isDesktopMode by remember { mutableStateOf(false) }

    // File upload callback
    var filePathCallback: ValueCallback<Array<Uri>>? by remember { mutableStateOf(null) }
    var cameraImageUri: Uri? by remember { mutableStateOf(null) }

    // Camera launcher for file chooser
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraImageUri != null) {
            filePathCallback?.onReceiveValue(arrayOf(cameraImageUri!!))
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    // File chooser launcher
    val fileChooserLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val results = if (data?.dataString != null) {
                arrayOf(Uri.parse(data.dataString))
            } else if (data?.clipData != null) {
                val clip = data.clipData!!
                Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
            } else {
                cameraImageUri?.let { arrayOf(it) }
            }
            filePathCallback?.onReceiveValue(results)
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    // Handle back press inside WebView history
    BackHandler(enabled = true) {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            (context as? Activity)?.finish()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = pageTitle.ifEmpty { "Anapse PosTienda" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = currentUrl,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (webView?.canGoBack() == true) {
                                webView?.goBack()
                            }
                        },
                        enabled = webView?.canGoBack() == true,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (webView?.canGoForward() == true) {
                                webView?.goForward()
                            }
                        },
                        enabled = webView?.canGoForward() == true,
                        modifier = Modifier.testTag("forward_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                    }

                    IconButton(
                        onClick = {
                            hasError = false
                            webView?.reload()
                        },
                        modifier = Modifier.testTag("reload_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                    }

                    IconButton(
                        onClick = {
                            hasError = false
                            webView?.loadUrl(targetUrl)
                        },
                        modifier = Modifier.testTag("home_button")
                    ) {
                        Icon(Icons.Default.Home, contentDescription = "Home")
                    }

                    IconButton(
                        onClick = { showMenu = !showMenu },
                        modifier = Modifier.testTag("menu_button")
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (isDesktopMode) "Switch to Mobile View" else "Switch to Desktop View") },
                            onClick = {
                                showMenu = false
                                isDesktopMode = !isDesktopMode
                                webView?.settings?.userAgentString = if (isDesktopMode) {
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                                } else {
                                    null
                                }
                                webView?.reload()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear Cache & Cookies") },
                            onClick = {
                                showMenu = false
                                webView?.clearCache(true)
                                CookieManager.getInstance().removeAllCookies(null)
                                Toast.makeText(context, "Cache and cookies cleared", Toast.LENGTH_SHORT).show()
                                webView?.reload()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share Link") },
                            onClick = {
                                showMenu = false
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, currentUrl)
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, null)
                                context.startActivity(shareIntent)
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (isOffline) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = "Offline", tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "No internet connection. Some features may not load.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                if (isLoading && progress < 100) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (hasError) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Error",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Could not load PosTienda",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage.ifEmpty { "Please check your connection and try again." },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    hasError = false
                                    isOffline = !isNetworkAvailable(context)
                                    webView?.reload()
                                },
                                modifier = Modifier.testTag("retry_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Retry")
                            }
                        }
                    } else {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        databaseEnabled = true
                                        setSupportZoom(true)
                                        builtInZoomControls = true
                                        displayZoomControls = false
                                        loadWithOverviewMode = true
                                        useWideViewPort = true
                                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                        cacheMode = WebSettings.LOAD_DEFAULT
                                    }

                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                            super.onPageStarted(view, url, favicon)
                                            isLoading = true
                                            url?.let { currentUrl = it }
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            isLoading = false
                                            url?.let { currentUrl = it }
                                            view?.title?.let { if (it.isNotBlank()) pageTitle = it }
                                        }

                                        override fun onReceivedError(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                            error: WebResourceError?
                                        ) {
                                            super.onReceivedError(view, request, error)
                                            if (request?.isForMainFrame == true) {
                                                hasError = true
                                                errorMessage = error?.description?.toString() ?: "Unknown error"
                                            }
                                        }
                                    }

                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            super.onProgressChanged(view, newProgress)
                                            progress = newProgress
                                        }

                                        override fun onReceivedTitle(view: WebView?, title: String?) {
                                            super.onReceivedTitle(view, title)
                                            title?.let { if (it.isNotBlank()) pageTitle = it }
                                        }

                                        override fun onShowFileChooser(
                                            webView: WebView?,
                                            callback: ValueCallback<Array<Uri>>?,
                                            fileChooserParams: FileChooserParams?
                                        ): Boolean {
                                            filePathCallback?.onReceiveValue(null)
                                            filePathCallback = callback

                                            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                                            var captureIntent: Intent? = null
                                            try {
                                                val photoFile = createTempImageFile(context)
                                                cameraImageUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                                                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
                                                captureIntent = takePictureIntent
                                            } catch (e: IOException) {
                                                cameraImageUri = null
                                            }

                                            val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                                addCategory(Intent.CATEGORY_OPENABLE)
                                                type = "*/*"
                                            }

                                            val intentArray = if (captureIntent != null) arrayOf(captureIntent) else emptyArray()

                                            val chooserIntent = Intent(Intent.ACTION_CHOOSER).apply {
                                                putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
                                                putExtra(Intent.EXTRA_TITLE, "Select File or Take Photo")
                                                putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray)
                                            }

                                            try {
                                                fileChooserLauncher.launch(chooserIntent)
                                            } catch (e: Exception) {
                                                filePathCallback = null
                                                return false
                                            }
                                            return true
                                        }
                                    }

                                    loadUrl(targetUrl)
                                    webView = this
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("web_view")
                        ) { view ->
                            webView = view
                        }
                    }
                }
            }
        }
    }
}

@Throws(IOException::class)
fun createTempImageFile(context: Context): File {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
}
