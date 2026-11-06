package com.example.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class SearchSource(
    val title: String,
    val snippet: String,
    val url: String
)

data class SearchResult(
    val query: String,
    val directAnswer: String?,
    val summary: String,
    val sources: List<SearchSource>
)

object GoogleSearchEngine {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    suspend fun search(query: String): SearchResult = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return@withContext SearchResult(cleanQuery, null, "No query specified.", emptyList())
        }

        val encodedQuery = try {
            URLEncoder.encode(cleanQuery, "UTF-8")
        } catch (_: Exception) {
            cleanQuery
        }

        var instantAnswer: String? = null
        try {
            val instantUrl = "https://api.duckduckgo.com/?q=$encodedQuery&format=json&no_html=1&skip_disambig=1"
            val instantReq = Request.Builder()
                .url(instantUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            val instantResp = httpClient.newCall(instantReq).execute()
            if (instantResp.isSuccessful) {
                val bodyStr = instantResp.body?.string() ?: ""
                if (bodyStr.isNotBlank()) {
                    val json = JSONObject(bodyStr)
                    val abstractText = json.optString("AbstractText", "")
                    val answerText = json.optString("Answer", "")
                    if (answerText.isNotBlank()) {
                        instantAnswer = cleanHtml(answerText)
                    } else if (abstractText.isNotBlank()) {
                        instantAnswer = cleanHtml(abstractText)
                    }
                }
            }
        } catch (_: Exception) {}

        val sources = mutableListOf<SearchSource>()
        try {
            val htmlUrl = "https://html.duckduckgo.com/html/?q=$encodedQuery"
            val htmlReq = Request.Builder()
                .url(htmlUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val htmlResp = httpClient.newCall(htmlReq).execute()
            if (htmlResp.isSuccessful) {
                val html = htmlResp.body?.string() ?: ""
                parseHtmlResults(html, sources)
            }
        } catch (_: Exception) {}

        if (instantAnswer == null && sources.isEmpty()) {
            try {
                val wikiUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/$encodedQuery"
                val wikiReq = Request.Builder()
                    .url(wikiUrl)
                    .header("User-Agent", "JarvisApp/1.0 (contact: info@jarvis.ai)")
                    .build()

                val wikiResp = httpClient.newCall(wikiReq).execute()
                if (wikiResp.isSuccessful) {
                    val wikiBody = wikiResp.body?.string() ?: ""
                    val json = JSONObject(wikiBody)
                    val extract = json.optString("extract", "")
                    val title = json.optString("title", cleanQuery)
                    if (extract.isNotBlank()) {
                        instantAnswer = extract
                        sources.add(SearchSource(title, extract, json.optJSONObject("content_urls")?.optJSONObject("desktop")?.optString("page", "") ?: ""))
                    }
                }
            } catch (_: Exception) {}
        }

        val summaryBuilder = StringBuilder()
        if (!instantAnswer.isNullOrBlank()) {
            summaryBuilder.append(instantAnswer)
        } else if (sources.isNotEmpty()) {
            val topSnippets = sources.take(2).joinToString(" ") { it.snippet }
            summaryBuilder.append(topSnippets)
        } else {
            summaryBuilder.append("Live telemetry returned no verified search consensus for \"$cleanQuery\".")
        }

        SearchResult(
            query = cleanQuery,
            directAnswer = instantAnswer,
            summary = summaryBuilder.toString().trim(),
            sources = sources
        )
    }

    private fun parseHtmlResults(html: String, outSources: MutableList<SearchSource>) {
        try {
            val snippetPattern = Pattern.compile("<a[^>]*class=\"result__snippet[^>]*>(.*?)</a>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
            val snippetMatcher = snippetPattern.matcher(html)
            val snippets = mutableListOf<String>()
            while (snippetMatcher.find() && snippets.size < 5) {
                val snippetText = cleanHtml(snippetMatcher.group(1) ?: "")
                if (snippetText.length > 20) {
                    snippets.add(snippetText)
                }
            }

            if (snippets.isEmpty()) {
                val fallbackPattern = Pattern.compile("<a class=\"result__snippet\"[^>]*>(.*?)</a>", Pattern.DOTALL)
                val m = fallbackPattern.matcher(html)
                while (m.find() && snippets.size < 5) {
                    val s = cleanHtml(m.group(1) ?: "")
                    if (s.isNotBlank()) snippets.add(s)
                }
            }

            for (i in snippets.indices) {
                outSources.add(
                    SearchSource(
                        title = "Search Result #${i + 1}",
                        snippet = snippets[i],
                        url = "https://www.google.com/search"
                    )
                )
            }
        } catch (_: Exception) {}
    }

    private fun cleanHtml(raw: String): String {
        return raw
            .replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
