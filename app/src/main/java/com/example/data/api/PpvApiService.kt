package com.example.data.api

import com.example.data.models.PpvStreamsResponse
import retrofit2.http.GET

interface PpvApiService {

    @GET("api/streams")
    suspend fun getStreams(): PpvStreamsResponse
}
