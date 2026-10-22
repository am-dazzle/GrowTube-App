package com.example.network

import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class ThumbnailInfo(val url: String)

@JsonClass(generateAdapter = true)
data class ThumbnailsMap(
    val default: ThumbnailInfo?,
    val medium: ThumbnailInfo?,
    val high: ThumbnailInfo?
)

@JsonClass(generateAdapter = true)
data class ChannelSnippet(
    val title: String,
    val customUrl: String?,
    val description: String?,
    val thumbnails: ThumbnailsMap?
)

@JsonClass(generateAdapter = true)
data class ChannelStatistics(
    val subscriberCount: String?,
    val viewCount: String?,
    val videoCount: String?
)

@JsonClass(generateAdapter = true)
data class RelatedPlaylists(val uploads: String?)

@JsonClass(generateAdapter = true)
data class ChannelContentDetails(val relatedPlaylists: RelatedPlaylists?)

@JsonClass(generateAdapter = true)
data class YouTubeChannelItem(
    val id: String,
    val snippet: ChannelSnippet?,
    val statistics: ChannelStatistics?,
    val contentDetails: ChannelContentDetails?
)

@JsonClass(generateAdapter = true)
data class YouTubeChannelsResponse(val items: List<YouTubeChannelItem>?)

@JsonClass(generateAdapter = true)
data class ResourceId(val videoId: String)

@JsonClass(generateAdapter = true)
data class PlaylistItemSnippet(
    val title: String,
    val publishedAt: String,
    val resourceId: ResourceId?
)

@JsonClass(generateAdapter = true)
data class PlaylistItemContentDetails(val videoId: String)

@JsonClass(generateAdapter = true)
data class PlaylistItem(
    val snippet: PlaylistItemSnippet?,
    val contentDetails: PlaylistItemContentDetails?
)

@JsonClass(generateAdapter = true)
data class YouTubePlaylistItemsResponse(val items: List<PlaylistItem>?)

@JsonClass(generateAdapter = true)
data class VideoSnippet(val title: String, val publishedAt: String)

@JsonClass(generateAdapter = true)
data class VideoStatistics(
    val viewCount: String?,
    val likeCount: String?,
    val commentCount: String?
)

@JsonClass(generateAdapter = true)
data class YouTubeVideoItem(
    val id: String,
    val snippet: VideoSnippet?,
    val statistics: VideoStatistics?
)

@JsonClass(generateAdapter = true)
data class YouTubeVideosResponse(val items: List<YouTubeVideoItem>?)

@JsonClass(generateAdapter = true)
data class SearchResultId(val kind: String, val channelId: String? = null, val videoId: String? = null)

@JsonClass(generateAdapter = true)
data class SearchResultSnippet(
    val title: String,
    val description: String? = null,
    val channelTitle: String? = null,
    val thumbnails: ThumbnailsMap? = null
)

@JsonClass(generateAdapter = true)
data class SearchResultItem(
    val id: SearchResultId,
    val snippet: SearchResultSnippet? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeLocalSearchResponse(val items: List<SearchResultItem>?)

interface YouTubeApiService {
    @GET("youtube/v3/channels")
    suspend fun getChannels(
        @Query("part") part: String,
        @Query("id") id: String? = null,
        @Query("forHandle") forHandle: String? = null,
        @Query("key") apiKey: String
    ): YouTubeChannelsResponse

    @GET("youtube/v3/playlistItems")
    suspend fun getPlaylistItems(
        @Query("part") part: String,
        @Query("playlistId") playlistId: String,
        @Query("maxResults") maxResults: Int,
        @Query("key") apiKey: String
    ): YouTubePlaylistItemsResponse

    @GET("youtube/v3/videos")
    suspend fun getVideos(
        @Query("part") part: String,
        @Query("id") ids: String,
        @Query("key") apiKey: String
    ): YouTubeVideosResponse

    @GET("youtube/v3/search")
    suspend fun searchChannels(
        @Query("part") part: String = "snippet",
        @Query("q") query: String,
        @Query("type") type: String = "channel",
        @Query("maxResults") maxResults: Int = 10,
        @Query("key") apiKey: String
    ): YouTubeLocalSearchResponse
}

object YouTubeRetrofitClient {
    private const val BASE_URL = "https://youtube.googleapis.com/"

    private val moshi = com.squareup.moshi.Moshi.Builder()
        .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val service: YouTubeApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(YouTubeApiService::class.java)
    }
}
