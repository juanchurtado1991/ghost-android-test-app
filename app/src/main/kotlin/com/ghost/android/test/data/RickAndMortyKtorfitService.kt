package com.ghost.android.test.data

import com.ghost.android.test.domain.RickAndMortyResponse
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Query

interface RickAndMortyKtorfitService {
    @GET("character")
    suspend fun getCharacters(@Query("page") page: Int): RickAndMortyResponse
}

