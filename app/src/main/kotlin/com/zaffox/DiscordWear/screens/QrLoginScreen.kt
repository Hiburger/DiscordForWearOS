package com.zaffox.discordwear.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.zaffox.discordwear.CaptchaWebServer
import com.zaffox.discordwear.RemoteAuthClient
import com.zaffox.discordwear.RemoteAuthState
import com.zaffox.discordwear.RemoteAuthStatus
import com.zaffox.discordwear.SetupPreferences
import com.zaffox.discordwear.discordApp
import kotlinx.coroutines.launch

@Composable
fun QrLoginScreen(onSetupComplete: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val listState = rememberScalingLazyListState()
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<RemoteAuthState>(RemoteAuthState.Connecting) }
    var status by remember { mutableStateOf(RemoteAuthStatus()) }
    var client by remember { mutableStateOf<RemoteAuthClient?>(null) }
    var restartNonce by remember { mutableStateOf(0) }

    // Captcha-solve web server (phone browser, same Wi-Fi)
    var captchaServer by remember { mutableStateOf<CaptchaWebServer?>(null) }
    var captchaAddresses by remember { mutableStateOf<List<String>>(emptyList()) }
    var captchaStatus by remember { mutableStateOf("") }

    fun stopCaptchaServer() {
        captchaServer?.stop()
        captchaServer = null
        captchaAddresses = emptyList()
    }

    fun startQrClient() {
        client?.disconnect()
        stopCaptchaServer()
        captchaStatus = ""
        state = RemoteAuthState.Connecting
        status = RemoteAuthStatus()
        val c = RemoteAuthClient(
            onStateChange = { newState ->
                state = newState
                // New challenge -> reset solve UI; terminal states -> stop server.
                if (newState is RemoteAuthState.CaptchaRequired) {
                    stopCaptchaServer()
                    captchaStatus = ""
                }
                if (newState is RemoteAuthState.Error || newState is RemoteAuthState.Canceled) {
                    stopCaptchaServer()
                }
            },
            onStatusUpdate = { newStatus -> status = newStatus },
            onTokenReceived = { token ->
                stopCaptchaServer()
                SetupPreferences.saveToken(context, token)
                context.discordApp.initRepository(token)
                onSetupComplete()
            }
        )
        client = c
        c.connect()
    }

    LaunchedEffect(restartNonce) {
        startQrClient()
    }

    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    DisposableEffect(Unit) {
        onDispose {
            client?.disconnect()
            captchaServer?.stop()
        }
    }

    ScreenScaffold(scrollState = listState) {
        ScalingLazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            when (val s = state) {

                is RemoteAuthState.Connecting -> {
                    item {
                        Text(
                            "Connecting…",
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center
                        )
                    }
                    item { CircularProgressIndicator(modifier = Modifier.size(28.dp)) }
                    // Verbose log lines
                    items(status.lines.size) { i ->
                        Text(
                            status.lines[i],
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Start,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 1.dp)
                        )
                    }
                    item {
                        Button(
                            onClick = { client?.disconnect(); onBack() },
                            modifier = Modifier.fillMaxWidth(0.7f).height(32.dp),
                            colors = ButtonDefaults.filledTonalButtonColors()
                        ) { Text("Cancel") }
                    }
                }

                is RemoteAuthState.WaitingForScan -> {
                    item {
                        Text(
                            "Scan with Discord",
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center
                        )
                    }
                    item {
                        QrCodeImage(
                            content = "https://discord.com/ra/${s.fingerprint}",
                            modifier = Modifier
                                .size(130.dp)
                                .background(Color.White)
                                .padding(4.dp)
                        )
                    }
                    item {
                        Text(
                            "Profile → Scan QR Code in the Discord app",
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        )
                    }
                    item {
                        Text(
                            "Scan quickly; this code expires!",
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        )
                    }
                    item {
                        Button(
                            onClick = { client?.disconnect(); onBack() },
                            modifier = Modifier.fillMaxWidth(0.7f).height(32.dp),
                            colors = ButtonDefaults.filledTonalButtonColors()
                        ) { Text("Cancel") }
                    }
                }

                is RemoteAuthState.UserScanned -> {
                    item {
                        Text(
                            "Confirm on your phone",
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center
                        )
                    }
                    item {
                        val avatarUrl = if (s.avatarHash != "0")
                            "https://cdn.discordapp.com/avatars/${s.userId}/${s.avatarHash}.png?size=80"
                        else null
                        if (avatarUrl != null) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp).clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    s.username.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                    item {
                        Text(
                            s.username,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    item {
                        Text(
                            "Tap 'Log In' in the Discord app",
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        )
                    }
                    item { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
                }

                is RemoteAuthState.Canceled -> {
                    item {
                        Text("Canceled", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                    }
                    item {
                        Text(
                            "Login was canceled on your phone.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                    item {
                        Button(
                            onClick = { onBack() },
                            modifier = Modifier.fillMaxWidth(0.7f).height(32.dp)
                        ) { Text("Go Back") }
                    }
                }

                is RemoteAuthState.CaptchaRequired -> {
                    item {
                        Text(
                            "Check needed",
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center
                        )
                    }
                    item {
                        Text(
                            "Discord asked for a human check (hCaptcha). QR can't finish on the watch alone.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                    if (s.captcha.service != "hcaptcha") {
                        item {
                            Text(
                                "Discord asked for a '${s.captcha.service}' check, which this app can't solve. Please use token login instead.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    } else if (captchaServer == null && captchaStatus.isBlank()) {
                        item {
                            Button(
                                onClick = {
                                    val srv = CaptchaWebServer(
                                        port = 8081,
                                        sitekey = s.captcha.sitekey,
                                        rqdata = s.captcha.rqdata,
                                        onSolution = { solution ->
                                            scope.launch {
                                                captchaStatus = "Check solved! Retrying…"
                                                stopCaptchaServer()
                                                state = RemoteAuthState.Connecting
                                            }
                                            client?.retryWithCaptcha(solution)
                                        }
                                    )
                                    srv.start()
                                    captchaServer = srv
                                    captchaAddresses = srv.getLocalAddresses()
                                    captchaStatus = if (srv.getLocalAddresses().isEmpty()) {
                                        "Server started. Connect to Wi-Fi first."
                                    } else {
                                        ""
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(0.85f).height(36.dp)
                            ) { Text("Solve on phone") }
                        }
                    } else {
                        if (captchaAddresses.isNotEmpty()) {
                            item {
                                Text(
                                    "On same Wi-Fi open:",
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            captchaAddresses.forEach { addr ->
                                item {
                                    Text(
                                        "http://$addr:8081",
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            item {
                                Text(
                                    "Solve the check there. The watch retries by itself.",
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                                )
                            }
                        }
                        if (captchaStatus.isNotBlank()) {
                            item {
                                Text(
                                    captchaStatus,
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                                )
                            }
                        }
                        item { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
                        item {
                            Button(
                                onClick = { stopCaptchaServer(); captchaStatus = "" },
                                modifier = Modifier.fillMaxWidth(0.7f).height(32.dp),
                                colors = ButtonDefaults.filledTonalButtonColors()
                            ) { Text("Stop server") }
                        }
                    }
                    item {
                        Text(
                            "If the phone page errors (sitekey is tied to discord.com), use token login instead.",
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        )
                    }
                    item {
                        Button(
                            onClick = { restartNonce++ },
                            modifier = Modifier.fillMaxWidth(0.7f).height(32.dp),
                            colors = ButtonDefaults.filledTonalButtonColors()
                        ) { Text("New QR code") }
                    }
                    item {
                        Button(
                            onClick = { client?.disconnect(); stopCaptchaServer(); onBack() },
                            modifier = Modifier.fillMaxWidth(0.7f).height(32.dp),
                            colors = ButtonDefaults.filledTonalButtonColors()
                        ) { Text("Use token instead") }
                    }
                }

                is RemoteAuthState.Error -> {
                    item {
                        Text(
                            "Error",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                    item {
                        Text(
                            s.message.take(300),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                    item {
                        Text(
                            "Tip: QR is often blocked by Discord. Token login almost always works.",
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        )
                    }
                    // Last log lines only — full JSON doesn't fit on a watch.
                    items(status.lines.takeLast(6).size) { i ->
                        val line = status.lines.takeLast(6)[i]
                        Text(
                            line.take(120),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp)
                        )
                    }
                    item {
                        Button(
                            onClick = { restartNonce++ },
                            modifier = Modifier.fillMaxWidth(0.7f).height(32.dp),
                            colors = ButtonDefaults.filledTonalButtonColors()
                        ) { Text("Try new QR") }
                    }
                    item {
                        Button(
                            onClick = { onBack() },
                            modifier = Modifier.fillMaxWidth(0.7f).height(32.dp)
                        ) { Text("Use token instead") }
                    }
                }
            }
        }
    }
}

@Composable
private fun QrCodeImage(content: String, modifier: Modifier = Modifier) {
    val bitmap = remember(content) { generateQrBitmap(content, 256) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "QR Code",
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    }
}

private fun generateQrBitmap(content: String, size: Int): Bitmap? = runCatching {
    val hints = mapOf(EncodeHintType.MARGIN to 0)
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) for (y in 0 until size)
        bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    bmp
}.getOrNull()
