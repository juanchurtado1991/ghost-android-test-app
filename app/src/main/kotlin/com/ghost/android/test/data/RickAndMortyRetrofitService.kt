package com.ghost.android.test.data

import com.ghost.android.test.domain.RickAndMortyResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface RickAndMortyRetrofitService {
    @GET("character")
    suspend fun getCharacters(@Query("page") page: Int): RickAndMortyResponse
}

