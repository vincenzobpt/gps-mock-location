package com.mocklocation.app.network

import com.mocklocation.app.model.LatLng
import com.mocklocation.app.model.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

/** Retrofit interface for the OSRM public routing API. */
interface OsrmApiService {
    @GET("route/v1/driving/{coordinates}?overview=full&geometries=geojson&steps=false&alternatives=false")
    suspend fun getRoute(@Path("coordinates", encoded = true) coordinates: String): OsrmResponse
}

/** Repository that wraps OSRM calls and maps responses to [Route]. */
class OsrmRepository {

    private val api: OsrmApiService

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://router.project-osrm.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(OsrmApiService::class.java)
    }

    /**
     * Fetch a driving route between [start] and [end].
     * @return [Route] on success, null on error / no route.
     */
    suspend fun getRoute(start: LatLng, end: LatLng): Result<Route> = withContext(Dispatchers.IO) {
        try {
            val coords = "${start.longitude},${start.latitude};${end.longitude},${end.latitude}"
            val response = api.getRoute(coords)
            if (response.code == "Ok" && !response.routes.isNullOrEmpty()) {
                val route = response.routes.first()
                val points = route.geometry.coordinates.map { (lng, lat) ->
                    LatLng(latitude = lat, longitude = lng)
                }
                // Check if coordinates include elevation (3 elements per point)
                val elevations = route.geometry.coordinates.mapNotNull { coord ->
                    if (coord.size >= 3) coord[2] else null
                }
                Result.success(
                    Route(
                        points = points,
                        distanceMeters = route.distance,
                        durationSeconds = route.duration,
                        elevations = if (elevations.size == points.size) elevations else emptyList()
                    )
                )
            } else {
                Result.failure(Exception("OSRM error: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
