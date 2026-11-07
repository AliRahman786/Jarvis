package com.example.data.weather

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Retrofit DTOs for Open-Meteo Forecast & Geocoding APIs.
 */
data class OpenMeteoCurrentDto(
    @Json(name = "temperature_2m") val temperature2m: Double? = null,
    @Json(name = "relative_humidity_2m") val relativeHumidity2m: Int? = null,
    @Json(name = "apparent_temperature") val apparentTemperature: Double? = null,
    @Json(name = "weather_code") val weatherCode: Int? = null,
    @Json(name = "wind_speed_10m") val windSpeed10m: Double? = null
)

data class OpenMeteoDailyDto(
    @Json(name = "temperature_2m_max") val temperature2mMax: List<Double>? = null,
    @Json(name = "temperature_2m_min") val temperature2mMin: List<Double>? = null
)

data class OpenMeteoForecastResponseDto(
    @Json(name = "latitude") val latitude: Double? = null,
    @Json(name = "longitude") val longitude: Double? = null,
    @Json(name = "current") val current: OpenMeteoCurrentDto? = null,
    @Json(name = "daily") val daily: OpenMeteoDailyDto? = null
)

data class OpenMeteoGeoResultDto(
    @Json(name = "name") val name: String? = null,
    @Json(name = "latitude") val latitude: Double? = null,
    @Json(name = "longitude") val longitude: Double? = null,
    @Json(name = "country") val country: String? = null
)

data class OpenMeteoGeocodingResponseDto(
    @Json(name = "results") val results: List<OpenMeteoGeoResultDto>? = null
)

/**
 * Retrofit Network Service Interface for fetching real-time weather data by GPS coordinates.
 */
interface WeatherRetrofitApiService {
    @GET("v1/forecast")
    suspend fun getForecastByCoordinates(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m",
        @Query("daily") daily: String = "temperature_2m_max,temperature_2m_min",
        @Query("timezone") timezone: String = "auto"
    ): OpenMeteoForecastResponseDto
}

/**
 * Retrofit Network Service Interface for worldwide city geocoding.
 */
interface WeatherGeocodingRetrofitService {
    @GET("v1/search")
    suspend fun searchCity(
        @Query("name") name: String,
        @Query("count") count: Int = 1,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json"
    ): OpenMeteoGeocodingResponseDto
}

data class LiveWeatherData(
    val temperature: Int = 25,
    val condition: String = "Clear Sky",
    val city: String = "Global Core",
    val feelsLike: Int = 26,
    val high: Int = 28,
    val low: Int = 20,
    val humidity: Int = 55,
    val windSpeedKmh: Int = 10,
    val weatherCode: Int = 0,
    val latitude: Double = 28.6139,
    val longitude: Double = 77.2090,
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

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val forecastRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://api.open-meteo.com/")
        .client(httpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val geocodingRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://geocoding-api.open-meteo.com/")
        .client(httpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val weatherApiService: WeatherRetrofitApiService =
        forecastRetrofit.create(WeatherRetrofitApiService::class.java)

    val geocodingApiService: WeatherGeocodingRetrofitService =
        geocodingRetrofit.create(WeatherGeocodingRetrofitService::class.java)

    private val _weatherState = MutableStateFlow(LiveWeatherData())
    val weatherState: StateFlow<LiveWeatherData> = _weatherState.asStateFlow()

    suspend fun fetchWeatherForCity(cityName: String): LiveWeatherData = withContext(Dispatchers.IO) {
        val cleanName = cityName.trim()
        if (cleanName.isBlank()) return@withContext refreshWeather()

        try {
            val geoResp = geocodingApiService.searchCity(name = cleanName)
            val first = geoResp.results?.firstOrNull()
            if (first != null && first.latitude != null && first.longitude != null) {
                val resolvedName = first.name?.takeIf { it.isNotBlank() } ?: cleanName
                val country = first.country.orEmpty()
                val label = if (country.isNotBlank()) "$resolvedName, $country" else resolvedName
                return@withContext refreshWeather(
                    lat = first.latitude,
                    lon = first.longitude,
                    cityName = label
                )
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

    /**
     * Fetches real-time meteorological telemetry for the user's current GPS coordinates via Retrofit.
     */
    suspend fun refreshWeather(
        lat: Double = 28.6139,
        lon: Double = 77.2090,
        cityName: String = "Central Core"
    ): LiveWeatherData = withContext(Dispatchers.IO) {
        try {
            val response = weatherApiService.getForecastByCoordinates(
                latitude = lat,
                longitude = lon
            )
            val current = response.current
            val daily = response.daily

            if (current != null) {
                val temp = current.temperature2m?.roundToInt() ?: 25
                val feels = current.apparentTemperature?.roundToInt() ?: temp
                val humidity = current.relativeHumidity2m ?: 55
                val windSpeed = current.windSpeed10m?.roundToInt() ?: 10
                val code = current.weatherCode ?: 0

                val high = daily?.temperature2mMax?.firstOrNull()?.roundToInt() ?: (temp + 3)
                val low = daily?.temperature2mMin?.firstOrNull()?.roundToInt() ?: (temp - 4)

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
                    windSpeedKmh = windSpeed,
                    weatherCode = code,
                    latitude = lat,
                    longitude = lon,
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
                latitude = lat,
                longitude = lon,
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
            windSpeedKmh = 8,
            weatherCode = 0,
            latitude = lat,
            longitude = lon,
            lastUpdatedMillis = System.currentTimeMillis(),
            isLive = false,
            isCached = true,
            errorMessage = "Server returned empty response."
        )
        _weatherState.value = fallback
        fallback
    }
}
