package com.zaffox.discordwear

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.RSAPrivateKey
import java.security.spec.MGF1ParameterSpec
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

import com.zaffox.discordwear.api.DiscordHttp

sealed class RemoteAuthState {
    object Connecting : RemoteAuthState()
    data class WaitingForScan(val fingerprint: String) : RemoteAuthState()
    data class UserScanned(val userId: String, val username: String, val avatarHash: String) : RemoteAuthState()
    object Canceled : RemoteAuthState()
    data class CaptchaRequired(val ticket: String, val captcha: RemoteAuthCaptcha) : RemoteAuthState()
    data class Error(val message: String) : RemoteAuthState()
}

data class RemoteAuthCaptcha(
    val sitekey: String,
    val service: String = "hcaptcha",
    val sessionId: String? = null,
    val rqdata: String? = null,
    val rqtoken: String? = null
)

data class RemoteAuthStatus(val lines: List<String> = emptyList()) {
    fun plus(line: String) = RemoteAuthStatus(lines + line)
}

class RemoteAuthClient(
    private val onStateChange: (RemoteAuthState) -> Unit,
    private val onStatusUpdate: (RemoteAuthStatus) -> Unit,
    private val onTokenReceived: suspend (String) -> Unit
) {
    private val TAG = "RemoteAuthClient"
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ws: WebSocket? = null
    private var heartbeatJob: Job? = null

    private var privateKey: RSAPrivateKey? = null
    private var publicKeySpki: ByteArray? = null  // Java encoded() = SPKI DER

    // solved hCaptchas can be retried without re-scanning the QR
    private var pendingTicket: String? = null
    private var pendingCaptcha: RemoteAuthCaptcha? = null

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val oaepSpec = OAEPParameterSpec(
        "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT
    )

    private var status = RemoteAuthStatus()

    private fun setState(s: RemoteAuthState) {
        mainScope.launch { onStateChange(s) }
    }

    private fun log(line: String) {
        Log.d(TAG, line)
        status = status.plus(line)
        mainScope.launch { onStatusUpdate(status) }
    }

    fun connect() {
        pendingTicket = null
        pendingCaptcha = null
        setState(RemoteAuthState.Connecting)
        log("Opening WebSocket…")
        val request = Request.Builder()
            .url("wss://remote-auth-gateway.discord.gg/?v=2")
            .header("Origin", "https://discord.com")
            .header("User-Agent", DiscordHttp.USER_AGENT)
            .build()

        ws = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                log("Connected (HTTP ${response.code})")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "<<< $text")
                ioScope.launch { handleMessage(text) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                log("Failed (HTTP ${response?.code}): ${t.message}")
                setState(RemoteAuthState.Error("Connection failed: ${t.message}"))
                cleanup()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                log("Closed: $code ${reason.ifBlank { "(no reason)" }}")
                if (code != 1000) setState(RemoteAuthState.Error("Disconnected ($code)"))
                cleanup()
            }
        })
    }

    private suspend fun handleMessage(text: String) {
        val json = JSONObject(text)
        when (val op = json.getString("op")) {
            "hello" -> {
                val intervalMs = json.getLong("heartbeat_interval")
                log("← hello (heartbeat ${intervalMs}ms)")
                log("Generating RSA-2048 keypair…")
                generateKeyPair()
                val b64 = Base64.encodeToString(publicKeySpki, Base64.NO_WRAP)
                log("Pubkey: ${b64.take(20)}… (${publicKeySpki!!.size}B SPKI)")
                send(JSONObject().put("op", "init").put("encoded_public_key", b64))
                startHeartbeat(intervalMs)
                log("→ init sent, waiting for nonce_proof…")
            }

            "nonce_proof" -> {
                log("← nonce_proof, decrypting…")
                val encryptedNonce = json.getString("encrypted_nonce")
                val decryptedNonce = decryptBytes(Base64.decode(encryptedNonce, Base64.DEFAULT))
                val proof =
                    Base64.encodeToString(decryptedNonce, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                log("Nonce decrypted (${decryptedNonce.size}B), proof=${proof.take(16)}…")
                send(JSONObject().put("op", "nonce_proof").put("nonce", proof))
                log("→ nonce_proof sent, waiting for pending_remote_init…")
            }

            "pending_remote_init" -> {
                val fingerprint = json.getString("fingerprint")
                log("← pending_remote_init!")
                val spki = publicKeySpki!!
                val digest = MessageDigest.getInstance("SHA-256").digest(spki)
                val computed = Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                log("Fingerprint match: ${fingerprint == computed}")
                if (fingerprint != computed) {
                    log("Expected: $fingerprint / Got: $computed")
                }
                setState(RemoteAuthState.WaitingForScan(fingerprint))
            }

            "pending_ticket" -> {
                log("← pending_ticket, decrypting user…")
                val payload = decryptBytes(Base64.decode(json.getString("encrypted_user_payload"), Base64.DEFAULT))
                    .toString(Charsets.UTF_8)
                val parts = payload.split(":")
                if (parts.size >= 4) {
                    log("User: ${parts[3]}")
                    setState(RemoteAuthState.UserScanned(userId = parts[0], avatarHash = parts[2], username = parts[3]))
                }
            }

            "pending_login" -> {
                log("← pending_login, exchanging ticket…")
                exchangeTicketForToken(json.getString("ticket"))
            }

            "cancel" -> {
                log("← cancel"); setState(RemoteAuthState.Canceled)
            }

            "heartbeat_ack" -> log("← heartbeat_ack")
            else -> log("← unknown op: $op")
        }
    }

    private fun generateKeyPair() {
        val kp = KeyPairGenerator.getInstance("RSA").also { it.initialize(2048) }.generateKeyPair()
        privateKey = kp.private as RSAPrivateKey
        publicKeySpki = kp.public.encoded   // Java always encodes RSA public keys as SPKI DER
    }

    private fun decryptBytes(cipherBytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepSpec)
        return cipher.doFinal(cipherBytes)
    }

    private suspend fun exchangeTicketForToken(ticket: String) {
        exchangeTicketForToken(ticket, captchaKey = null, captcha = null)
    }

    // Retry the ticket exchange after the user solves the hCaptcha on their other device. Kept on the watch so no re-scan is needed

    fun retryWithCaptcha(captchaKey: String) {
        val ticket = pendingTicket
        val captcha = pendingCaptcha
        if (ticket.isNullOrBlank() || captcha == null) {
            log("No pending captcha challenge to retry!")
            setState(RemoteAuthState.Error("Captcha session expired: pls get a new QR code"))
            return
        }
        log("Retrying with solved captcha…")
        ioScope.launch { exchangeTicketForToken(ticket, captchaKey, captcha) }
    }

    private suspend fun exchangeTicketForToken(
        ticket: String,
        captchaKey: String?,
        captcha: RemoteAuthCaptcha?
    ) {
        try {
            val body = JSONObject().put("ticket", ticket).toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val builder = Request.Builder()
                .url("https://discord.com/api/v9/users/@me/remote-auth/login")
                .post(body)
                .header("Content-Type", "application/json")
                .header("User-Agent", DiscordHttp.USER_AGENT)
                .header("X-Super-Properties", DiscordHttp.superProperties)
                .header("Origin", "https://discord.com")
                .header("Referer", "https://discord.com/login")
            if (captchaKey != null) {
                builder.header("X-Captcha-Key", captchaKey)
                captcha?.sessionId?.takeIf { it.isNotBlank() }?.let {
                    builder.header("X-Captcha-Session-Id", it)
                }
                captcha?.rqtoken?.takeIf { it.isNotBlank() }?.let {
                    builder.header("X-Captcha-Rqtoken", it)
                }
            }
            val request = builder.build()
            val response = withContext(Dispatchers.IO) { http.newCall(request).execute() }
            val responseBody = response.body?.string() ?: throw Exception("Empty response")
            if (!response.isSuccessful) {
                if (isCaptchaChallenge(response.code, responseBody)) {
                    val parsed = parseCaptcha(responseBody)
                    if (parsed.sitekey.isBlank()) {
                        log("Captcha required but no sitekey in response")
                        throw Exception("Discord asked for human verification, but gave no challenge. Use token login.")
                    }
                    pendingTicket = ticket
                    pendingCaptcha = parsed
                    val c = pendingCaptcha!!
                    log("Captcha required (sitekey=${c.sitekey.take(8)}…, rqdata=${c.rqdata?.length ?: 0} chars)")
                    setState(RemoteAuthState.CaptchaRequired(ticket, c))
                    return
                }
                // Keep errors short: the watch screen can't fit raw JSON (i can tell by experience)
                log("Ticket exchange HTTP ${response.code}: ${responseBody.take(200)}")
                throw Exception("HTTP ${response.code}: ${shortError(responseBody)}")
            }
            log("Ticket exchange HTTP ${response.code}")
            val encryptedToken = JSONObject(responseBody).getString("encrypted_token")
            val token = decryptBytes(Base64.decode(encryptedToken, Base64.DEFAULT)).toString(Charsets.UTF_8)
            log("Token decrypted! Logging in…")
            withContext(Dispatchers.Main) { onTokenReceived(token) }
        } catch (e: Exception) {
            log("Token exchange error: ${e.message}")
            setState(RemoteAuthState.Error("Token exchange failed: ${e.message}"))
        }
    }

    private fun isCaptchaChallenge(httpCode: Int, body: String): Boolean {
        if (httpCode != 400) return false
        return try {
            val json = JSONObject(body)
            json.has("captcha_key") && json.has("captcha_service")
        } catch (_: Exception) {
            body.contains("captcha-required") && body.contains("captcha_sitekey")
        }
    }

    private fun parseCaptcha(body: String): RemoteAuthCaptcha {
        val json = JSONObject(body)
        return RemoteAuthCaptcha(
            sitekey = json.optString("captcha_sitekey", ""),
            service = json.optString("captcha_service", "hcaptcha"),
            sessionId = json.optString("captcha_session_id").takeIf { it.isNotBlank() },
            rqdata = json.optString("captcha_rqdata").takeIf { it.isNotBlank() },
            rqtoken = json.optString("captcha_rqtoken").takeIf { it.isNotBlank() }
        )
    }

    private fun shortError(body: String): String {
        // same small-screen problem
        if (body.contains("captcha-required")) return "Discord asked for human verification (hCaptcha)."
        return body.take(160)
    }

    private fun startHeartbeat(intervalMs: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = ioScope.launch {
            while (isActive) {
                delay(intervalMs)
                send(JSONObject().put("op", "heartbeat"))
                log("→ heartbeat")
            }
        }
    }

    private fun send(json: JSONObject) {
        Log.d(TAG, ">>> $json")
        ws?.send(json.toString())
    }

    fun disconnect() {
        runCatching { ws?.close(1000, "user cancelled") }
        ws = null
        pendingTicket = null
        pendingCaptcha = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        mainScope.cancel()
        ioScope.cancel()
    }


    private fun cleanup() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        ws = null
    }
}
