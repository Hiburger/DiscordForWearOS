// Tiny on-watch web server that lets the user solve Discord's hCaptcha on a
// another device's browser (same Wi-Fi, where the widget is usable. Similar to the
// token login web portal, but for the captcha check

package com.zaffox.discordwear

import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class CaptchaWebServer(
    private val port: Int = 8081,
    private val sitekey: String,
    private val rqdata: String? = null,
    private val onSolution: suspend (String) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running = false

    fun getLocalAddresses(): List<String> {
        val addresses = mutableListOf<String>()
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback || !iface.name.startsWith("wlan")) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is java.net.Inet4Address) {
                        addresses.add(addr.hostAddress ?: continue)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CaptchaWebServer", "Error getting addresses", e)
        }
        return addresses
    }

    // I hate captchas too. Really.

    fun start() {
        if (running) return
        running = true
        scope.launch {
            try {
                serverSocket = ServerSocket(port, 5, InetAddress.getByName("0.0.0.0"))
                Log.i("CaptchaWebServer", "Listening on port $port")
                while (running) {
                    val client = serverSocket?.accept() ?: break
                    launch { handleClient(client) }
                }
            } catch (e: Exception) {
                if (running) Log.e("CaptchaWebServer", "Server error", e)
            }
        }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        scope.cancel()
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)

            val requestLine = reader.readLine() ?: return@withContext
            val headers = mutableListOf<String>()
            var line = reader.readLine()
            while (!line.isNullOrBlank()) {
                headers.add(line)
                line = reader.readLine()
            }

            val isPost = requestLine.startsWith("POST")
            val contentLength = headers.firstOrNull { it.startsWith("Content-Length:") }
                ?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0

            var solution: String? = null
            if (isPost && contentLength > 0) {
                // read() may return fewer chars than asked; loop until full
                val body = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = reader.read(body, read, contentLength - read)
                    if (n <= 0) break
                    read += n
                }
                val bodyStr = String(body, 0, read)
                solution = bodyStr.split("&")
                    .firstOrNull { it.startsWith("h-captcha-response=") }
                    ?.removePrefix("h-captcha-response=")
                    ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                    ?.trim()
                    ?.takeIf { it.isNotBlank() && it != "null" && it.length > 10 }
            }

            if (!solution.isNullOrBlank()) {
                val ok = buildSuccessPage()
                val bytes = ok.toByteArray(Charsets.UTF_8)
                writer.print("HTTP/1.1 200 OK\r\n")
                writer.print("Content-Type: text/html; charset=utf-8\r\n")
                writer.print("Content-Length: ${bytes.size}\r\n")
                writer.print("Connection: close\r\n\r\n")
                writer.print(ok)
                writer.flush()
                socket.close()
                onSolution(solution)
            } else {
                val page = buildCaptchaPage()
                val bytes = page.toByteArray(Charsets.UTF_8)
                writer.print("HTTP/1.1 200 OK\r\n")
                writer.print("Content-Type: text/html; charset=utf-8\r\n")
                writer.print("Content-Length: ${bytes.size}\r\n")
                writer.print("Connection: close\r\n\r\n")
                writer.print(page)
                writer.flush()
                socket.close()
            }
        } catch (e: Exception) {
            Log.e("CaptchaWebServer", "Client error", e)
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun jsQuote(s: String): String =
        "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n") + "'"

    private fun buildCaptchaPage(): String {
        val sitekeyJs = jsQuote(sitekey)
        val rqdataJs = if (rqdata.isNullOrBlank()) "null" else jsQuote(rqdata)
        return """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>DiscordWear Check</title>
<script src="https://js.hcaptcha.com/1/api.js?onload=onHcaptchaLoad&render=explicit" async defer></script>
<style>
  body { font-family: system-ui, sans-serif; background: #1e1f22; color: #dbdee1;
         display: flex; flex-direction: column; align-items: center;
         justify-content: center; min-height: 100vh; margin: 0; padding: 16px; }
  .card { background: #2b2d31; border-radius: 12px; padding: 24px;
          width: 100%; max-width: 420px; box-sizing: border-box; text-align: center; }
  h1 { margin: 0 0 8px; font-size: 1.2rem; color: #fff; }
  p { font-size: .85rem; color: #949ba4; line-height: 1.5; }
  #captcha { display: flex; justify-content: center; margin: 16px 0; min-height: 78px; }
  button { width: 100%; padding: 10px; border: none; border-radius: 4px;
           background: #5865f2; color: #fff; font-size: 1rem; cursor: pointer; font-weight: 600; }
  button:disabled { opacity: .5; cursor: default; }
  .err { color: #f23f42; font-size: .8rem; margin-top: 8px; }
</style>
</head>
<body>
<div class="card">
  <h1>We need you to prove you're human...</h1>
  <p>Discord blocked the watch QR login with a check. Solve it below, then the watch retries automatically !</p>
  <div id="captcha"></div>
  <div class="err" id="err"></div>
  <form id="f" method="POST" action="/">
    <input type="hidden" name="h-captcha-response" id="token">
    <button id="send" type="submit" disabled>Send to watch</button>
  </form>
  <p style="font-size:.75rem">If this widget errors (sitekey is tied to discord.com), use token login on the watch instead.</p>
</div>
<script>
  var SITEKEY = $sitekeyJs;
  var RQDATA = $rqdataJs;
  var widgetId = null;
  // Called by hCaptcha with the solution token (wired via render callback).
  function onSolved(token) {
    document.getElementById('token').value = token;
    document.getElementById('send').disabled = false;
  }
  function onExpired() {
    document.getElementById('token').value = '';
    document.getElementById('send').disabled = true;
  }
  function onHcaptchaLoad() {
    try {
      var opts = { sitekey: SITEKEY, callback: onSolved, 'expired-callback': onExpired };
      // hCaptcha Enterprise: rqdata must be supplied or the token is rejected.
      if (RQDATA) { opts.rqdata = RQDATA; }
      widgetId = hcaptcha.render('captcha', opts);
      // Invisible challenges need an explicit execute; harmless for checkbox.
      try {
        var args = RQDATA ? { rqdata: RQDATA } : undefined;
        hcaptcha.execute(widgetId, args);
      } catch (e) {}
    } catch (e) {
      document.getElementById('err').textContent = 'Could not load check widget: ' + e;
    }
  }
  // Fallback poll in case the callback is missed: pick up a solved token.
  var tries = 0;
  var t = setInterval(function() {
    tries++;
    try {
      if (window.hcaptcha && widgetId !== null) {
        var resp = hcaptcha.getResponse(widgetId);
        if (resp && resp.length > 10) { onSolved(resp); clearInterval(t); }
      }
    } catch (e) {}
    if (tries > 600) clearInterval(t);
  }, 500);
</script>
</body>
</html>"""
    }

    private fun buildSuccessPage() = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>DiscordWear</title>
<style>
  body { font-family: system-ui, sans-serif; background: #1e1f22; color: #dbdee1;
         display: flex; align-items: center; justify-content: center;
         min-height: 100vh; margin: 0; }
  .card { background: #2b2d31; border-radius: 12px; padding: 32px;
          text-align: center; max-width: 320px; }
  h1 { color: #23a55a; margin: 8px 0; }
  p { color: #949ba4; font-size: .9rem; }
</style>
</head>
<body>
<div class="card">
  <h1>Sent!</h1>
  <p>Check your watch; it is retrying to login now. You may close this page :)</p>
</div>
</body>
</html>"""
}
