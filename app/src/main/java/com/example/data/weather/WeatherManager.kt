package com.example.data.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class LiveWeatherData(
    val temperature: Int = 25,
    val condition: String = "Clear Sky",
    val city: String = "Global Core",
    val feelsLike: Int = 26,
    val high: Int = 28,
    val low: Int = 20,
    val humidity: Int = 55,
    val weatherCode: Int = 0,
    val lastUpdatedMillis: Long = System.currentTimeMillis(),
    val isLive: Boolean = true,
    val isCached: Boolean = false,
    val errorMessage: String? = null
) {
    fun getFormattedLastUpdated(): String {
        return try {
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            sdf.format(Date(lastUpdatedMillis))
        } catch (_: Exception) {
            "Just now"
        }
    }
}

class WeatherManager {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _weatherState = MutableStateFlow(LiveWeatherData())
    val weatherState: StateFlow<LiveWeatherData> = _weatherState.asStateFlow()

    suspend fun fetchWeatherForCity(cityName: String): LiveWeatherData = withContext(Dispatchers.IO) {
        val cleanName = cityName.trim()
        if (cleanName.isBlank()) return@withContext refreshWeather()

        try {
            // Query Open-Meteo Geocoding API for city coordinates worldwide
            val encodedCity = URLEncoder.encode(cleanName, "UTF-8")
            val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=$encodedCity&count=1&language=en&format=json"
            val geoReq = Request.Builder().url(geoUrl).build()
            val geoResp = httpClient.newCall(geoReq).execute()
            val geoBody = geoResp.body?.string() ?: ""

            if (geoResp.isSuccessful && geoBody.isNotBlank()) {
                val geoJson = JSONObject(geoBody)
                val results = geoJson.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val first = results.getJSONObject(0)
                    val lat = first.optDouble("latitude", 28.6139)
                    val lon = first.optDouble("longitude", 77.2090)
                    val resolvedName = first.optString("name", cleanName)
                    val country = first.optString("country", "")
                    val label = if (country.isNotBlank()) "$resolvedName, $country" else resolvedName
                    return@withContext refreshWeather(lat, lon, label)
                }
            }
        } catch (e: Exception) {
            val cached = _weatherState.value.copy(
                isLive = false,
                isCached = true,
                errorMessage = "City resolution failed: ${e.message}"
            )
            _weatherState.value = cached
            return@withContext cached
        }

        refreshWeather(cityName = cleanName)
    }

    suspend fun refreshWeather(
        lat: Double = 28.6139,
        lon: Double = 77.2090,
        cityName: String = "Central Core"
    ): LiveWeatherData = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=auto"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful && body.isNotBlank()) {
                val json = JSONObject(body)
                val current = json.optJSONObject("current")
                val daily = json.optJSONObject("daily")

                val temp = current?.optDouble("temperature_2m", 25.0)?.toInt() ?: 25
                val feels = current?.optDouble("apparent_temperature", 26.0)?.toInt() ?: 26
                val humidity = current?.optInt("relative_humidity_2m", 55) ?: 55
                val code = current?.optInt("weather_code", 0) ?: 0

                val maxArr = daily?.optJSONArray("temperature_2m_max")
                val minArr = daily?.optJSONArray("temperature_2m_min")
                val high = if (maxArr != null && maxArr.length() > 0) maxArr.optDouble(0, 28.0).toInt() else 28
                val low = if (minArr != null && minArr.length() > 0) minArr.optDouble(0, 20.0).toInt() else 20

                val conditionText = when (code) {
                    0 -> "Clear Sky"
                    1, 2, 3 -> "Partly Cloudy"
                    45, 48 -> "Foggy"
                    51, 53, 55 -> "Light Drizzle"
                    61, 63, 65 -> "Rain Showers"
                    71, 73, 75 -> "Snow Precipitations"
                    80, 81, 82 -> "Heavy Rain"
                    95, 96, 99 -> "Thunderstorm Alert"
                    else -> "Clear Sky"
                }

                val data = LiveWeatherData(
                    temperature = temp,
                    condition = conditionText,
                    city = cityName,
                    feelsLike = feels,
                    high = high,
                    low = low,
                    humidity = humidity,
                    weatherCode = code,
                    lastUpdatedMillis = System.currentTimeMillis(),
                    isLive = true,
                    isCached = false,
                    errorMessage = null
                )
                _weatherState.value = data
                return@withContext data
            }
        } catch (e: Exception) {
            val current = _weatherState.value
            val cachedData = current.copy(
                isLive = false,
                isCached = true,
                errorMessage = "Network error: Unable to reach weather service."
            )
            _weatherState.value = cachedData
            return@withContext cachedData
        }

        val fallback = LiveWeatherData(
            temperature = 25,
            condition = "Weather Unavailable",
            city = cityName,
            feelsLike = 25,
            high = 28,
            low = 20,
            humidity = 55,
            weatherCode = 0,
            lastUpdatedMillis = System.currentTimeMillis(),
            isLive = false,
            isCached = true,
            errorMessage = "Server returned empty response."
        )
        _weatherState.value = fallback
        fallback
    }
}
