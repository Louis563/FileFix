package com.example.filefix.network

import com.example.filefix.model.FileItem
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @GET("files")
    suspend fun getFiles(): Response<List<FileItem>>

    @POST("files/fix/{fileId}")
    suspend fun fixFile(@Path("fileId") fileId: String): Response<FileItem>

    @DELETE("files/{fileId}")
    suspend fun deleteFile(@Path("fileId") fileId: String): Response<Unit>
}