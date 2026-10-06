package cn.qztc.timetable.feature.timetable

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.net.URLEncoder
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class CampusRouteStep(
    val instruction: String,
    val roadName: String = "",
    val distanceMeters: Int = 0,
    val durationSeconds: Int = 0,
)

@Serializable
private data class RouteRequest(
    val origin: CampusCoordinate,
    val destination: CampusCoordinate,
)

@Serializable
private data class RouteResponse(
    val points: List<CampusCoordinate> = emptyList(),
    val distanceMeters: Int = 0,
    val durationSeconds: Int = 0,
    val steps: List<CampusRouteStep> = emptyList(),
)

@Serializable
private data class ErrorResponse(val error: String = "")

@Serializable
data class CampusSearchResult(
    val id: String = "",
    val name: String,
    val address: String = "",
    val type: String = "",
    val latitude: Double,
    val longitude: Double,
) {
    fun point(): CampusPoint = CampusPoint(
        id = "amap-$id",
        name = name,
        coordinate = CampusCoordinate(latitude, longitude),
    )
}

@Serializable
private data class SearchResponse(val results: List<CampusSearchResult> = emptyList())

class CampusMapApi(
    private val baseUrl: String = BuildConfig.CAMPUS_MAP_API_BASE_URL,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun staticMap(
        center: CampusCoordinate,
        zoom: Int,
        width: Int,
        height: Int,
    ): Bitmap = withContext(Dispatchers.IO) {
        val query = "latitude=${center.latitude}&longitude=${center.longitude}&zoom=$zoom&width=$width&height=$height"
        val connection = (URL("${baseUrl.trimEnd('/')}/map/static?$query").openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            val status = connection.responseCode
            if (status !in 200..299) throw apiError(connection, status)
            connection.inputStream.use { input ->
                BitmapFactory.decodeStream(input) ?: throw CampusMapApiException("服务器返回的地图图片无效")
            }
        } catch (error: CampusMapApiException) {
            throw error
        } catch (error: IOException) {
            throw CampusMapApiException("地图服务暂时不可用，请检查网络连接", error)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun searchPlaces(keywords: String): List<CampusSearchResult> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(keywords.trim(), Charsets.UTF_8.name())
        val connection = (URL("${baseUrl.trimEnd('/')}/search?keywords=$encoded").openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw decodeApiError(status, body)
            json.decodeFromString<SearchResponse>(body).results
        } catch (error: CampusMapApiException) {
            throw error
        } catch (error: IOException) {
            throw CampusMapApiException("搜索服务暂时不可用，请检查网络连接", error)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun walkingRoute(
        origin: CampusCoordinate,
        destination: CampusCoordinate,
    ): CampusMapRoute = withContext(Dispatchers.IO) {
        val connection = (URL("${baseUrl.trimEnd('/')}/route").openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val body = json.encodeToString(RouteRequest(origin, destination))
            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw decodeApiError(status, responseBody)
            }
            val response = runCatching { json.decodeFromString<RouteResponse>(responseBody) }
                .getOrElse { throw CampusMapApiException("服务器返回的路线格式无效", it) }
            if (response.points.size < 2) throw CampusMapApiException("服务器未返回可用路线")
            CampusMapRoute(
                points = response.points,
                distanceMeters = response.distanceMeters,
                durationSeconds = response.durationSeconds,
                steps = response.steps,
            )
        } catch (error: CampusMapApiException) {
            throw error
        } catch (error: IOException) {
            throw CampusMapApiException("路线服务暂时不可用，请检查网络连接", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun apiError(connection: HttpURLConnection, status: Int): CampusMapApiException {
        val body = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        return decodeApiError(status, body)
    }

    private fun decodeApiError(status: Int, body: String): CampusMapApiException {
        if (status == HttpURLConnection.HTTP_NOT_FOUND) {
            return CampusMapApiException("地图服务暂未部署，请稍后重试")
        }
        val message = runCatching { json.decodeFromString<ErrorResponse>(body).error }.getOrNull()
            ?.takeIf { it.isNotBlank() }
        return CampusMapApiException(message ?: "地图服务暂时不可用")
    }
}

class CampusMapApiException(message: String, cause: Throwable? = null) : Exception(message, cause)
