package com.example.ai

import org.json.JSONObject
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object AiNetworkUtils {

    /**
     * Determine if an HTTP status code represents a transient, retryable server condition.
     * Retries are bounded to transient failures only (429, 500, 502, 503, 504).
     * Permanent failures (400, 401, 403, 404, 413, 505) must never be retried.
     */
    fun isRetryable(code: Int): Boolean {
        return code == 429 || code == 500 || code == 502 || code == 503 || code == 504
    }

    /**
     * Cleans sensitive tokens and private data from raw provider response strings.
     */
    private fun sanitizeDetail(raw: String): String {
        return raw.replace(Regex("(?i)(key|token|auth)=[^&\\s]+"), "$1=[REDACTED]")
            .replace(Regex("AIza[0-9A-Za-z-_]{15,}"), "AIza[REDACTED]")
            .replace(Regex("sk-[0-9A-Za-z-_]{10,}"), "sk-[REDACTED]")
            .take(300)
    }

    fun parseErrorMessage(providerName: String, code: Int, body: String): String {
        var detail = try {
            val json = JSONObject(body)
            val errObj = if (json.has("error")) json.optJSONObject("error") else null
            val msgFromErr = errObj?.optString("message")?.takeIf { it.isNotBlank() }
            val directMsg = json.optString("message").takeIf { it.isNotBlank() }
            msgFromErr ?: directMsg ?: ""
        } catch (_: Exception) {
            ""
        }

        if (detail.isBlank()) {
            val regex = """"message"\s*:\s*"([^"]+)"""".toRegex()
            detail = regex.find(body)?.groupValues?.getOrNull(1) ?: body.trim()
        }

        val cleanDetail = sanitizeDetail(detail).ifBlank { "HTTP $code" }

        return when (code) {
            400 -> "[$providerName Bad Request // 400] Malformed request or invalid parameters: $cleanDetail"
            401, 403 -> "[$providerName Authentication Failed // $code] Your API key is invalid or permission was denied. Please check your configuration."
            404 -> "[$providerName Model Not Found // 404] The selected model or endpoint was not found ($cleanDetail). Please choose a supported model."
            408 -> "[$providerName Request Timeout // 408] The request timed out. You can retry."
            409 -> "[$providerName Conflict // 409] Request conflict encountered: $cleanDetail"
            413 -> "[$providerName Payload Too Large // 413] Prompt or conversation context exceeded size limits. Try clearing history."
            429 -> "[$providerName Rate Limit Exceeded // 429] Your provider's request limit has been reached: $cleanDetail"
            500 -> "[$providerName Internal Server Error // 500] The AI service is temporarily unavailable. Please try again."
            502 -> "[$providerName Bad Gateway // 502] Upstream AI service gateway error. Please retry."
            503 -> "[$providerName Service Unavailable // 503] The AI service is temporarily overloaded or unavailable. Please retry."
            504 -> "[$providerName Gateway Timeout // 504] Remote AI server response timed out. You can retry."
            505 -> "[$providerName HTTP Version Error // 505] HTTP protocol version not supported by the remote host."
            in 501..599 -> "[$providerName Server Outage // $code] Remote AI server error ($cleanDetail). Please try again later."
            else -> "[$providerName Error // $code] $cleanDetail"
        }
    }

    fun parseException(providerName: String, e: Exception): String {
        return when (e) {
            is SocketTimeoutException -> "[$providerName Timeout] Network connection timed out. You can retry."
            is UnknownHostException -> "[$providerName Offline] Unable to reach network servers. Ensure device is online."
            else -> "[$providerName Network Error] ${e.localizedMessage ?: "Unknown communication failure"}"
        }
    }
}
