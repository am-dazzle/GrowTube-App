package com.example.network

import android.content.Context
import com.example.data.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class GrowTubeAnalyzer(private val context: Context) {
    private val prefs = GrowTubePreferences(context)
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val reportAdapter = moshi.adapter(FullGrowTubeReport::class.java)
    private val analysisAdapter = moshi.adapter(ChannelAnalysis::class.java)
    private val launchReportAdapter = moshi.adapter(LaunchReport::class.java)
    private val thumbnailAnalysisAdapter = moshi.adapter(ThumbnailAnalysisResult::class.java)
    private val call1Adapter = moshi.adapter(Call1Response::class.java)
    private val call2Adapter = moshi.adapter(Call2Response::class.java)
    private val call3Adapter = moshi.adapter(Call3Response::class.java)

    interface ProgressListener {
        fun onProgressUpdate(step: Int, text: String)
    }

    suspend fun analyzeChannel(
        inputUrlOrHandle: String,
        listener: ProgressListener
    ): FullGrowTubeReport = withContext(Dispatchers.IO) {
        val ytKey = prefs.getYoutubeApiKey().trim()
        var geminiKey = prefs.getGeminiApiKey().trim()
        if (geminiKey.isEmpty()) {
            try {
                geminiKey = com.example.BuildConfig.GEMINI_API_KEY.trim()
            } catch (e: Throwable) {
                // Keep empty
            }
        }
        if (geminiKey == "MY_GEMINI_API_KEY") {
            geminiKey = ""
        }

        val parsedInput = extractHandleOrChannelId(inputUrlOrHandle)
        val isDemoMode = ytKey.isEmpty() || geminiKey.isEmpty() || inputUrlOrHandle.lowercase().contains("demo")

        if (isDemoMode) {
            return@withContext runDemoAnalysis(parsedInput.first, listener, geminiKey)
        }

        try {
            // STEP 1: Fetching channel data
            listener.onProgressUpdate(1, "Fetching channel data from YouTube...")
            delay(800) // Aesthetic pacing
            
            val ytService = YouTubeRetrofitClient.service
            
            val channelResponse = if (parsedInput.second) {
                ytService.getChannels(part = "snippet,statistics,contentDetails", id = parsedInput.first, apiKey = ytKey)
            } else {
                ytService.getChannels(part = "snippet,statistics,contentDetails", forHandle = parsedInput.first, apiKey = ytKey)
            }

            val channelItem = channelResponse.items?.firstOrNull()
                ?: throw Exception("YouTube channel not found. Please verify the URL or Handle.")

            val channelName = channelItem.snippet?.title ?: "Unknown Channel"
            val handleClean = channelItem.snippet?.customUrl ?: parsedInput.first
            val subs = channelItem.statistics?.subscriberCount?.toLongOrNull() ?: 0L
            val views = channelItem.statistics?.viewCount?.toLongOrNull() ?: 0L
            val videos = channelItem.statistics?.videoCount?.toLongOrNull() ?: 0L
            val avatarUrl = channelItem.snippet?.thumbnails?.high?.url 
                ?: channelItem.snippet?.thumbnails?.medium?.url 
                ?: channelItem.snippet?.thumbnails?.default?.url ?: ""
            val uploadsPlaylistId = channelItem.contentDetails?.relatedPlaylists?.uploads

            // STEP 2: Reading last 20 videos
            listener.onProgressUpdate(2, "Reading last 20 videos & compilation...")
            delay(1000)

            val recentVideosList = mutableListOf<VideoItem>()
            var calculatedAvgViews = 0L
            var uploadFrequencyVal = 1.0
            var uploadFrequencyTxt = "1.0 videos / week"

            if (!uploadsPlaylistId.isNullOrEmpty()) {
                val playlistResponse = ytService.getPlaylistItems(
                    part = "snippet,contentDetails",
                    playlistId = uploadsPlaylistId,
                    maxResults = 20,
                    apiKey = ytKey
                )

                val playlistItems = playlistResponse.items ?: emptyList()
                if (playlistItems.isNotEmpty()) {
                    val videoIds = playlistItems.mapNotNull { it.snippet?.resourceId?.videoId }.joinToString(",")
                    val videosDetailsResponse = ytService.getVideos(
                        part = "snippet,statistics",
                        ids = videoIds,
                        apiKey = ytKey
                    )

                    val videoDetailItems = videosDetailsResponse.items ?: emptyList()
                    var sumViews = 0L
                    var totalFetched = 0

                    videoDetailItems.forEach { v ->
                        val title = v.snippet?.title ?: "Untitled Video"
                        val vViews = v.statistics?.viewCount?.toLongOrNull() ?: 0L
                        val likes = v.statistics?.likeCount?.toLongOrNull() ?: 0L
                        val comments = v.statistics?.commentCount?.toLongOrNull() ?: 0L
                        val pubDate = v.snippet?.publishedAt?.take(10) ?: "" // YYYY-MM-DD
                        
                        recentVideosList.add(VideoItem(title, vViews, likes, comments, pubDate))
                        sumViews += vViews
                        totalFetched++
                    }

                    if (totalFetched > 0) {
                        calculatedAvgViews = sumViews / totalFetched
                    }

                    // Calculate Upload Frequency based on first and last video dates from the last 20
                    if (recentVideosList.size >= 2) {
                        try {
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            val newestDate = sdf.parse(recentVideosList.first().date)
                            val oldestDate = sdf.parse(recentVideosList.last().date)
                            if (newestDate != null && oldestDate != null) {
                                val diffInMillis = newestDate.time - oldestDate.time
                                var diffInWeeks = diffInMillis / (1000 * 60 * 60 * 24 * 7.0)
                                if (diffInWeeks < 1.0) diffInWeeks = 1.0
                                uploadFrequencyVal = (((recentVideosList.size - 1) / diffInWeeks) * 10).roundToInt() / 10.0
                                uploadFrequencyTxt = if (uploadFrequencyVal >= 1.0) {
                                    "$uploadFrequencyVal videos per week"
                                } else {
                                    "${(uploadFrequencyVal * 4.3).roundToInt()} videos per month"
                                }
                            }
                        } catch (e: Exception) {
                            uploadFrequencyVal = 1.5
                            uploadFrequencyTxt = "1.5 videos per week"
                        }
                    } else {
                        uploadFrequencyVal = 1.0
                        uploadFrequencyTxt = "1.0 videos per week"
                    }
                }
            }

            val stats = ChannelStats(
                id = channelItem.id,
                name = channelName,
                handle = handleClean,
                subscribers = subs,
                totalViews = views,
                totalVideos = videos,
                avatarUrl = avatarUrl,
                uploadFrequencyValue = uploadFrequencyVal,
                uploadFrequencyText = uploadFrequencyTxt,
                averageViews = calculatedAvgViews,
                recentVideos = recentVideosList
            )

            // STEP 3: Finding competitors
            listener.onProgressUpdate(3, "Mapping competitors & gap elements...")
            delay(1000)

            // STEP 4: AI generating insights
            listener.onProgressUpdate(4, "Deeply analyzing channel via Gemini AI...")
            
            val (analysis, latestVideoAnalysis) = callGeminiForAnalysis(stats, geminiKey, listener)
            
            // Initial competitors are empty per user requirements
            val finalAnalysis = analysis.copy(
                competitors = emptyList(),
                gapAnalysis = "Add competitors to see analysis. Use manual search or let AI suggest channels."
            )
            val report = FullGrowTubeReport(stats, finalAnalysis, latestVideoAnalysis)

            // Save in Recents
            prefs.addRecentChannel(
                RecentChannelItem(
                    handle = stats.handle,
                    channelName = stats.name,
                    avatarUrl = stats.avatarUrl,
                    subscribers = stats.subscribers,
                    reportJson = reportAdapter.toJson(report)
                )
            )

            return@withContext report

        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
            // Fall back to high-fidelity simulated/cached mode if something goes wrong,
            // or bubble up the exception to show error with retry.
            // Let's throw the error so user is notified, but let's make it a clean message.
            throw Exception(e.message ?: "Analysis failed. Please confirm network connection, API limits, and spelling.")
        }
    }

    private fun cleanHandle(handle: String): String {
        val trimmed = handle.trim()
        if (trimmed.startsWith("@")) return trimmed
        return "@$trimmed"
    }

    private fun calculateDaysSinceUpload(dateStr: String): Double {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        return try {
            val parsedDate = sdf.parse(dateStr)
            if (parsedDate != null) {
                val diffMs = java.lang.System.currentTimeMillis() - parsedDate.time
                val days = diffMs.toDouble() / (1000.0 * 60.0 * 60.0 * 24.0)
                days.coerceAtLeast(0.1)
            } else {
                1.0
            }
        } catch (e: Exception) {
            1.0
        }
    }

    suspend fun verifyAndFetchCompetitorStats(
        suggestedHandle: String,
        suggestedName: String,
        userSubs: Long,
        apiKey: String,
        ytService: YouTubeApiService
    ): CompetitorAnalysis? {
        try {
            val handleCleaned = cleanHandle(suggestedHandle)
            android.util.Log.d("GrowTubeVerify", "Calling YouTube API for handle: $handleCleaned")
            
            val channelResponse = ytService.getChannels(
                part = "snippet,statistics,contentDetails",
                forHandle = handleCleaned,
                apiKey = apiKey
            )
            
            val channelItem = channelResponse.items?.firstOrNull() ?: return null
            
            val chName = channelItem.snippet?.title ?: suggestedName
            val chHandle = channelItem.snippet?.customUrl ?: handleCleaned
            val subs = channelItem.statistics?.subscriberCount?.toLongOrNull() ?: 0L
            val videoCount = channelItem.statistics?.videoCount?.toLongOrNull() ?: 0L
            
            // Validate subscriber count - must not be below 50% of user's channel subscribers
            if (subs < userSubs * 0.5) {
                android.util.Log.d("GrowTubeVerify", "Rejected $chHandle: subscriber count $subs is lower than 50% of user's subscriber count (${userSubs * 0.5})")
                return null
            }
            
            // Validate minimum video count - must not be less than 5 videos
            if (videoCount < 5) {
                android.util.Log.d("GrowTubeVerify", "Rejected $chHandle: video count $videoCount is less than 5")
                return null
            }
            
            val uploadsPlaylistId = channelItem.contentDetails?.relatedPlaylists?.uploads ?: return null
            
            // Fetch playlist items for up to 10 latest videos
            val playlistResponse = ytService.getPlaylistItems(
                part = "snippet,contentDetails",
                playlistId = uploadsPlaylistId,
                maxResults = 10,
                apiKey = apiKey
            )
            
            val playlistItems = playlistResponse.items ?: emptyList()
            if (playlistItems.size < 5) {
                android.util.Log.d("GrowTubeVerify", "Rejected $chHandle: fetched playlist items count ${playlistItems.size} is less than 5")
                return null
            }
            
            val videoIds = playlistItems.mapNotNull { it.snippet?.resourceId?.videoId }.joinToString(",")
            val videosDetailsResponse = ytService.getVideos(
                part = "snippet,statistics",
                ids = videoIds,
                apiKey = apiKey
            )
            
            val videoDetailItems = videosDetailsResponse.items ?: emptyList()
            if (videoDetailItems.isEmpty()) {
                return null
            }
            
            var sumViews = 0L
            var totalFetched = 0
            val dates = mutableListOf<String>()
            
            videoDetailItems.forEach { v ->
                val vViews = v.statistics?.viewCount?.toLongOrNull() ?: 0L
                val pubDate = v.snippet?.publishedAt?.take(10) ?: ""
                if (pubDate.isNotEmpty()) {
                    dates.add(pubDate)
                }
                sumViews += vViews
                totalFetched++
            }
            
            if (totalFetched == 0) return null
            val calculatedAvgViews = sumViews / totalFetched
            
            var calculatedFreqStr = "1.0 vids/week"
            if (dates.size >= 2) {
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val newest = sdf.parse(dates.first())
                    val oldest = sdf.parse(dates.last())
                    if (newest != null && oldest != null) {
                        val diffMs = newest.time - oldest.time
                        val diffInWeeks = diffMs / (1000.0 * 60 * 60 * 24 * 7)
                        if (diffInWeeks > 0.1) {
                            val freqVal = (((dates.size - 1) / diffInWeeks) * 10).toInt() / 10.0
                            calculatedFreqStr = if (freqVal >= 1.0) {
                                "$freqVal vids/week"
                            } else {
                                "${(freqVal * 4.3).toInt()} vids/month"
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            return CompetitorAnalysis(
                name = chName,
                handle = chHandle,
                subscribers = subs,
                avgViews = calculatedAvgViews,
                uploadFrequency = calculatedFreqStr,
                contentFormat = "Verified Niche Video",
                biggestStrength = "Strong competitor footprint.",
                isManual = false
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun calculateHealthScore(stats: ChannelStats): Int {
        val subs = stats.subscribers.coerceAtLeast(1L)
        
        // 1. Views to subscriber ratio (Avg views of recent 20 videos vs subscriber count)
        // Max score: 40 points
        val viewRatio = stats.averageViews.toDouble() / subs
        val viewsScore = when {
            viewRatio >= 0.30 -> 40.0
            viewRatio >= 0.15 -> 30.0 + ((viewRatio - 0.15) / 0.15) * 10.0
            viewRatio >= 0.05 -> 15.0 + ((viewRatio - 0.05) / 0.10) * 15.0
            else -> 5.0 + (viewRatio / 0.05) * 10.0
        }

        // 2. Engagement rate on recent 20 videos: (likes + comments) / views
        // Max score: 30 points
        val recentVids = stats.recentVideos
        val totalRecentViews = recentVids.sumOf { it.views }.coerceAtLeast(1L)
        val totalRecentEngagements = recentVids.sumOf { it.likes + it.comments }
        val engagementRate = totalRecentEngagements.toDouble() / totalRecentViews
        val engagementScore = when {
            engagementRate >= 0.10 -> 30.0
            engagementRate >= 0.04 -> 20.0 + ((engagementRate - 0.04) / 0.06) * 10.0
            engagementRate >= 0.01 -> 10.0 + ((engagementRate - 0.01) / 0.03) * 10.0
            else -> (engagementRate / 0.01) * 10.0
        }

        // 3. Upload consistency (uploads per week) -> uploadFrequencyValue
        // Max score: 30 points
        val freq = stats.uploadFrequencyValue
        val uploadScore = when {
            freq >= 1.0 -> 30.0
            freq >= 0.5 -> 20.0 + ((freq - 0.5) / 0.5) * 10.0
            freq >= 0.2 -> 10.0 + ((freq - 0.2) / 0.3) * 10.0
            else -> (freq / 0.2) * 10.0
        }

        val totalScore = (viewsScore + engagementScore + uploadScore).roundToInt()
        return totalScore.coerceIn(10, 100)
    }

    private suspend fun callGeminiForAnalysis(
        stats: ChannelStats,
        apiKey: String,
        listener: ProgressListener
    ): Pair<ChannelAnalysis, LatestVideoAnalysis?> {
        val videosWithStats = stats.recentVideos.map { video ->
            val days = calculateDaysSinceUpload(video.date)
            val viewsPerDay = video.views.toDouble() / days
            Triple(video, days, viewsPerDay)
        }
        val olderVideos = videosWithStats.filter { it.second >= 7.0 }
        val eligibleVideos = if (olderVideos.isEmpty()) videosWithStats else olderVideos
        val rankedOlderVideos = eligibleVideos.sortedByDescending { it.third }
        
        val realBest3 = rankedOlderVideos.take(3).map { it.first }
        val realWorst3 = if (rankedOlderVideos.size >= 6) {
            rankedOlderVideos.takeLast(3).map { it.first }
        } else {
            val remaining = (rankedOlderVideos.size - 3).coerceAtLeast(0)
            rankedOlderVideos.takeLast(remaining).map { it.first }
        }

        val bestVideosText = realBest3.mapIndexed { index, video ->
            val days = calculateDaysSinceUpload(video.date)
            val viewsPerDay = Math.round(video.views.toDouble() / days)
            "Best Video #${index + 1}: \"${video.title}\" | Views: ${video.views} | Views Per Day: $viewsPerDay | Likes: ${video.likes} | Comments: ${video.comments} | Date: ${video.date}"
        }.joinToString("\n")

        val worstVideosText = realWorst3.mapIndexed { index, video ->
            val days = calculateDaysSinceUpload(video.date)
            val viewsPerDay = Math.round(video.views.toDouble() / days)
            "Worst Video #${index + 1}: \"${video.title}\" | Views: ${video.views} | Views Per Day: $viewsPerDay | Likes: ${video.likes} | Comments: ${video.comments} | Date: ${video.date}"
        }.joinToString("\n")

        val recentVideosText = stats.recentVideos.mapIndexed { index, video ->
            val days = calculateDaysSinceUpload(video.date)
            val viewsPerDay = Math.round(video.views.toDouble() / days)
            "Video #${index + 1}: \"${video.title}\" | Views: ${video.views} | Views Per Day: $viewsPerDay | Likes: ${video.likes} | Comments: ${video.comments} | Date: ${video.date}"
        }.joinToString("\n")

        val latestVideo = stats.recentVideos.firstOrNull()
        val latestVideoText = if (latestVideo != null) {
            val days = calculateDaysSinceUpload(latestVideo.date)
            val viewsPerDay = Math.round(latestVideo.views.toDouble() / days)
            "Latest Video \"${latestVideo.title}\" | Views: ${latestVideo.views} | Views Per Day: $viewsPerDay | Likes: ${latestVideo.likes} | Comments: ${latestVideo.comments} | Date: ${latestVideo.date}"
        } else {
            "No recent videos found."
        }

        val systemInstruction = "You are GrowTube AI, the ultimate world-class YouTube content strategist and diagnostic engine. " +
                "You analyze channel metrics and historical video performance, map competition footprints, and extract high-performing actionable strategy models."

        // ================= CALL 1 =================
        listener.onProgressUpdate(4, "AI analyzing channel DNA & latest upload...")
        
        val promptText1 = """
            Perform a strategic channel analysis (Part 1 - DNA, Niche, Best/Worst Performance, Audience & Main Roadblock, + LATEST UPLOAD PERFORMANCE ANALYSIS) based on these metrics:
            Channel: ${stats.name} (${stats.handle})
            Subscribers: ${stats.subscribers}
            Total Videos: ${stats.totalVideos}
            Total Views: ${stats.totalViews}
            Average Views of Recent Videos: ${stats.averageViews}
            Upload Frequency: ${stats.uploadFrequencyText}

            COMPLETE ACTUAL METRICS AND HISTORICAL UPLOAD LIST (Last 20 videos from YouTube API):
            $recentVideosText

            Real Best 3 Videos (Top 3 by views):
            $bestVideosText
            
            Real Worst 3 Videos (Bottom 3 by views):
            $worstVideosText

            LATEST UPLOAD DETAILS FOR ANALYSIS:
            $latestVideoText

            Instructions for Analysis:
            1. READ AND ANALYZE CHANNEL DNA:
               First, deeply analyze the provided channel name, handle, and ALL of the actual video titles/performance listed under COMPLETE ACTUAL METRICS and BEST/WORST to formulate a perfect Niche Fingerprint containing:
               - primaryCategory: The category detected from real files/titles (E.g., Music, Cooking, Fitness, Gaming, Tech, Education, or others).
               - subCategory: The exact subcategory detected from real data.
               - microNiche: The micro-niche detected from real data.
               - contentIdentity: The detailed community identity of this channel (who they make videos for and why).
               - contentFormat: Exactly how the creator makes and structures content based on their actual video titles.

            2. EXPLAIN BEST AND WORST PERFORMING VIDEOS (CRITICAL REAL DATA RULE):
               - For "bestVideos", analyze EXACTLY the 3 Best Performing Videos listed above. You MUST use their exact original titles in the "title" field of the JSON structure. 
               - For "worstVideos", analyze EXACTLY the 3 Worst Performing Videos listed above. You MUST use their exact original titles in the "title" field of the JSON structure.
               - In the "reason" field for each, provide a 1-sentence strategic analysis explaining WHY this video succeeded or underperformed based on its title, CTR appeal, or topic.
               - Do NOT change, replace, or invent any video titles. Do NOT suggest other videos. Use the actual data provided.

            3. AUDIENCE PROFILE ANALYSIS (audienceProfile):
               Generate a detailed 2-sentence description of demographics and optimal viewing tendencies.
               - MUST be generated based on the REAL data provided only:
                 - Analyze the actual video titles to determine what specific demographic groups are drawn to them.
                 - Analyze the actual view counts, likes, and comment counts to judge viewer retention and community passion.
                 - Analyze the detected niche and upload patterns to determine when and how they consume your content.
                 - Must NOT use generic assumptions.

            4. CORE STRATEGIC ROADBLOCK (mainWeakness):
               Formulate a highly customizable, specific description of the single most critical roadblock, title formulation error, or posting inconsistency holding this channel back.
               - MUST be identified based on:
                 - A direct comparison of the real best performing vs worst performing videos from the API data.
                 - Relationship between actual upload frequency and viewer fatigue.
               - Must be specific to this channel only.
               - Must NOT be generic YouTube advice.

            5. HEALTH SCORE:
               - Calculate and assign a "healthScore" from 10 to 100 representing general growth trajectory.
               - Provide a 1-sentence "healthReason" explaining why.

            6. LATEST UPLOAD PERFORMANCE ANALYSIS (latestVideoAnalysis):
               - Assess the latest upload provided in details relative to your historical performance and average view metrics.
               - verdict: "STRONG" or "WEAK" representing if it started above or below average.
               - reasons: A 1-2 sentence detailed reason for this verdict.
               - actions: A list of exactly 3 combat actions to immediately optimize and boost its distribution momentum.

            Perform an analysis and output a raw JSON object string. Do not wrap in backticks or markdown, do not write prefaces. Just return raw parsable JSON matching this EXACT structure:
            {
              "healthScore": 85,
              "healthReason": "A 1-sentence analytical reason for why the channel got its health score.",
              "bestVideos": [
                {"title": "The exact title of Best Video #1", "reason": "1-sentence why it succeeded"},
                {"title": "The exact title of Best Video #2", "reason": "1-sentence why it worked"},
                {"title": "The exact title of Best Video #3", "reason": "1-sentence why it worked"}
              ],
              "worstVideos": [
                {"title": "The exact title of Worst Video #1", "reason": "1-sentence why it underperformed"},
                {"title": "The exact title of Worst Video #2", "reason": "1-sentence why it failed"},
                {"title": "The exact title of Worst Video #3", "reason": "1-sentence why it failed"}
              ],
              "audienceProfile": "A detailed 2-sentence description of demographics and optimal viewing tendencies.",
              "mainWeakness": "The single most critical roadblock, title formulation error, or posting inconsistency holding this channel back.",
              "primaryCategory": "Calculated Primary Category",
              "subCategory": "Calculated Sub-Category",
              "microNiche": "Calculated Micro-Niche",
              "contentIdentity": "Calculated Content Identity",
              "contentFormat": "Calculated Content Format",
              "latestVideoAnalysis": {
                "verdict": "STRONG",
                "reasons": "Detailed analytic explanation...",
                "actions": [
                  "Action suggestion 1",
                  "Action suggestion 2",
                  "Action suggestion 3"
                ]
              }
            }
         """.trimIndent()

        var retries = 0
        val maxRetries = 3
        var responseText1: String? = null

        while (true) {
            try {
                val service = GeminiRetrofitClient.service
                val request = GeminiRequest(
                    contents = listOf(GeminiContent(listOf(GeminiPart(promptText1)))),
                    systemInstruction = GeminiContent(listOf(GeminiPart(systemInstruction))),
                    generationConfig = GeminiGenerationConfig(responseMimeType = "application/json", temperature = 0.2f)
                )

                val response = service.generateContent(apiKey = apiKey, request = request)
                responseText1 = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw Exception("Empty response returned from Gemini on Call 1.")
                break
            } catch (e: Exception) {
                val is429 = (e is retrofit2.HttpException && e.code() == 429) ||
                            e.message?.contains("429") == true ||
                            e.toString().contains("429")
                if (is429 && retries < maxRetries) {
                    retries++
                    listener.onProgressUpdate(4, "AI is busy (Call 1/2), retrying in 10 seconds...")
                    delay(10000)
                    listener.onProgressUpdate(4, "Retrying DNA analysis via Gemini AI...")
                } else {
                    throw e
                }
            }
        }

        // Parse JSON for Call 1
        val cleanedJson1 = responseText1!!.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val parsedCall1 = call1Adapter.fromJson(cleanedJson1) 
            ?: throw Exception("JSON Parsing Failed on Gemini DNA analysis (Call 1).")


        val calculatedHealthScore = calculateHealthScore(stats)

        val finalBestList = realBest3.mapIndexed { index, realVideo ->
            val parsedInsight = parsedCall1.bestVideos.find { 
                it.title.lowercase().trim() == realVideo.title.lowercase().trim() 
            } ?: parsedCall1.bestVideos.find {
                it.title.lowercase().contains(realVideo.title.lowercase().take(8)) ||
                realVideo.title.lowercase().contains(it.title.lowercase().take(8))
            } ?: parsedCall1.bestVideos.getOrNull(index)
            
            VideoInsight(title = realVideo.title, reason = parsedInsight?.reason ?: "Strong view velocity and CTR.")
        }

        val finalWorstList = realWorst3.mapIndexed { index, realVideo ->
            val parsedInsight = parsedCall1.worstVideos.find { 
                it.title.lowercase().trim() == realVideo.title.lowercase().trim() 
            } ?: parsedCall1.worstVideos.find {
                it.title.lowercase().contains(realVideo.title.lowercase().take(8)) ||
                realVideo.title.lowercase().contains(it.title.lowercase().take(8))
            } ?: parsedCall1.worstVideos.getOrNull(index)
            
            VideoInsight(title = realVideo.title, reason = parsedInsight?.reason ?: "Lower CTR or upload timing issue.")
        }


        val channelAnalysis = ChannelAnalysis(
            healthScore = calculatedHealthScore,
            healthReason = parsedCall1.healthReason,
            bestVideos = finalBestList,
            worstVideos = finalWorstList,
            audienceProfile = parsedCall1.audienceProfile,
            mainWeakness = parsedCall1.mainWeakness,
            competitors = emptyList(),
            gapAnalysis = "",
            strategiesToAdopt = emptyList(),
            yourAdvantage = "",
            videoIdeas = emptyList(),
            contentCalendar = emptyList(),
            bestUploadDays = emptyList(),
            recommendedLength = "",
            shortsVsLongRatio = "",
            primaryCategory = parsedCall1.primaryCategory,
            subCategory = parsedCall1.subCategory,
            microNiche = parsedCall1.microNiche,
            contentIdentity = parsedCall1.contentIdentity,
            contentFormat = parsedCall1.contentFormat
        )

        return Pair(channelAnalysis, parsedCall1.latestVideoAnalysis)
    }

    private suspend fun runDemoAnalysis(
        handle: String,
        listener: ProgressListener,
        geminiKey: String
    ): FullGrowTubeReport {
        // Run simulated analysis
        listener.onProgressUpdate(1, "Fetching channel data from YouTube [DEMO MODE]...")
        delay(1000)
        
        listener.onProgressUpdate(2, "Reading last 20 videos [DEMO MODE]...")
        delay(1200)

        // Niche categorization
        val lowercaseHandle = handle.lowercase()
        val theme = when {
            lowercaseHandle.contains("cook") || lowercaseHandle.contains("food") || lowercaseHandle.contains("recipe") || lowercaseHandle.contains("chef") -> "Cooking"
            lowercaseHandle.contains("fit") || lowercaseHandle.contains("gym") || lowercaseHandle.contains("muscle") || lowercaseHandle.contains("health") -> "Fitness"
            lowercaseHandle.contains("tech") || lowercaseHandle.contains("gadget") || lowercaseHandle.contains("coding") || lowercaseHandle.contains("code") || lowercaseHandle.contains("mkbhd") -> "Tech"
            else -> "Entertainment"
        }

        listener.onProgressUpdate(3, "Mapping competitor spaces [DEMO MODE]...")
        delay(1000)

        listener.onProgressUpdate(4, "Deeply analyzing channel via Gemini AI [DEMO MODE]...")
        delay(1500)

        val stats = generateMockStats(handle, theme)
        
        var parsedAnalysis: ChannelAnalysis? = null
        var latestVideoAnalysis: LatestVideoAnalysis? = null
        if (geminiKey.isNotEmpty()) {
            try {
                val pair = callGeminiForAnalysis(stats, geminiKey, listener)
                parsedAnalysis = pair.first
                latestVideoAnalysis = pair.second
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        val rawAnalysis = parsedAnalysis ?: generateMockAnalysis(handle, theme, stats)
        val computedHealthScore = calculateHealthScore(stats)
        
        val videosWithStats = stats.recentVideos.map { video ->
            val days = calculateDaysSinceUpload(video.date)
            val viewsPerDay = video.views.toDouble() / days
            Triple(video, days, viewsPerDay)
        }
        val olderVideos = videosWithStats.filter { it.second >= 7.0 }
        val eligibleVideos = if (olderVideos.isEmpty()) videosWithStats else olderVideos
        val rankedOlderVideos = eligibleVideos.sortedByDescending { it.third }
        
        val realBest3 = rankedOlderVideos.take(3).map { it.first }
        val realWorst3 = if (rankedOlderVideos.size >= 6) {
            rankedOlderVideos.takeLast(3).map { it.first }
        } else {
            val remaining = (rankedOlderVideos.size - 3).coerceAtLeast(0)
            rankedOlderVideos.takeLast(remaining).map { it.first }
        }

        val finalBestList = realBest3.mapIndexed { index, realVideo ->
            val oldInsight = rawAnalysis.bestVideos.find { 
                it.title.lowercase().trim() == realVideo.title.lowercase().trim() 
            } ?: rawAnalysis.bestVideos.find {
                it.title.lowercase().contains(realVideo.title.lowercase().take(8)) ||
                realVideo.title.lowercase().contains(it.title.lowercase().take(8))
            } ?: rawAnalysis.bestVideos.getOrNull(index)
            
            VideoInsight(title = realVideo.title, reason = oldInsight?.reason ?: "Strong view velocity and CTR.")
        }

        val finalWorstList = realWorst3.mapIndexed { index, realVideo ->
            val oldInsight = rawAnalysis.worstVideos.find { 
                it.title.lowercase().trim() == realVideo.title.lowercase().trim() 
            } ?: rawAnalysis.worstVideos.find {
                it.title.lowercase().contains(realVideo.title.lowercase().take(8)) ||
                realVideo.title.lowercase().contains(it.title.lowercase().take(8))
            } ?: rawAnalysis.worstVideos.getOrNull(index)
            
            VideoInsight(title = realVideo.title, reason = oldInsight?.reason ?: "Lower initial engagement relative to niche.")
        }

        val finalLatestVideoAnalysis = latestVideoAnalysis ?: generateMockLatestVideoAnalysis(theme, stats)

        val analysis = rawAnalysis.copy(
            healthScore = computedHealthScore,
            bestVideos = finalBestList,
            worstVideos = finalWorstList,
            competitors = emptyList(),
            gapAnalysis = "Add competitors to see analysis. Use manual search or let AI suggest channels."
        )
        val report = FullGrowTubeReport(stats, analysis, finalLatestVideoAnalysis)

        // Save in Recents
        prefs.addRecentChannel(
            RecentChannelItem(
                handle = stats.handle,
                channelName = stats.name,
                avatarUrl = stats.avatarUrl,
                subscribers = stats.subscribers,
                reportJson = reportAdapter.toJson(report)
            )
        )

        return report
    }

    private fun extractHandleOrChannelId(input: String): Pair<String, Boolean> {
        val trimmed = input.trim()
        if (trimmed.startsWith("UC") && trimmed.length == 24) {
            return Pair(trimmed, true)
        }
        
        if (trimmed.contains("youtube.com/channel/")) {
            val parts = trimmed.split("youtube.com/channel/")
            if (parts.size > 1) {
                val cid = parts[1].split("/")[0].split("?")[0].trim()
                if (cid.startsWith("UC") && cid.length == 24) {
                    return Pair(cid, true)
                }
            }
        }
        if (trimmed.contains("youtube.com/@")) {
            val parts = trimmed.split("youtube.com/@")
            if (parts.size > 1) {
                val h = parts[1].split("/")[0].split("?")[0].trim()
                return Pair("@$h", false)
            }
        }
        
        if (trimmed.startsWith("@")) {
            return Pair(trimmed, false)
        }
        return Pair("@$trimmed", false)
    }

    private fun generateMockStats(handle: String, theme: String): ChannelStats {
        val name = when (theme) {
            "Cooking" -> "Flavors & Pan Cooking"
            "Fitness" -> "IronFit & Aesthetics"
            "Tech" -> "TechnoSpace Gadgets"
            else -> "Nexus Entertainment"
        }
        val subscribers = when (theme) {
            "Cooking" -> 45000L
            "Fitness" -> 85000L
            "Tech" -> 195000L
            else -> 320000L
        }
        val views = subscribers * 45
        val videos = when (theme) {
            "Cooking" -> 122L
            "Fitness" -> 214L
            "Tech" -> 382L
            else -> 412L
        }
        
        // Let's generate 20 highly stylized video names
        val videoTitles = when (theme) {
            "Cooking" -> listOf(
                "I Cooking 15-Minute Creamy Garlic Pasta!",
                "The Ultimate Steak-Searing Masterclass",
                "Why Your Sourdough Bread Keeps Collapsing",
                "My 3 Secrets To Making Street-Style Ramen",
                "Recreating Subway Cookie Recipe (Taste Test)",
                "The Only Knife Skills Tutorial You Will Ever Need",
                "I Ate Nothing But Instant Noodles For 7 Days",
                "Delicious Leftover Rice Fried Rice Trick",
                "Easy Homemade Pizza Sauce From Scratch",
                "Perfect Fluffy Pancakes Recipe inside 5 Mins",
                "Preparing Gourmet Dinner for Under $10",
                "My Meal Prep Strategy - Healthy Fast Lunches",
                "How to Cook Salmon in a Skillet Perfectly",
                "This French Omelette Method is Magic",
                "Refreshing Fresh Lemonade for Hot Summer",
                "Vegetarian Chili my Family Obsesses Over",
                "Classic Creamy Tomato Soup & Grilled Cheese",
                "Crispy Oven Baked Fries Without Oil",
                "I Tried Gordon Ramsay's Famous Beef Wellington",
                "What I Eat in a Day as a Slower Culinary Student"
            )
            "Fitness" -> listOf(
                "Complete Dumbbell Workout for Huge Arms",
                "How to Fix Your Squat Form (Save Your Knees)",
                "Full Body Stretching Routine for Stiff Men",
                "My Full Day of Eating for Fast Fat Loss",
                "The Ultimate Push Day Routine inside Gym",
                "Is Creatine Safe? What I Wish I Knew",
                "I Ran 10K Every Single Day For 30 Days",
                "How I Built a Six Pack Without Sit-ups",
                "The Science Behind Muscle Recovery Explained",
                "Calisthenics vs Weights: Which is Best?",
                "Complete Pull Workout for a Wider Lighter Back",
                "My 3 Favourite Cardio Methods to Lean Out",
                "The Truth Behind Protein Powders Revealed",
                "Home Exercises to Fix Slouched Posture",
                "I Tried Arnold Schwarzenegger's Golden Era Workout",
                "Bulking vs Cutting: Beginner Mistakes to Avoid",
                "How to Stay Disciplined and Never Skip a Workout",
                "3 Mistakes You Are Making with Dumbbell Curls",
                "Deadlift Form Guide - Absolute Best Technique",
                "What I Eat in a Week to Maintain low Bodyfat"
            )
            "Tech" -> listOf(
                "The Budget Smartphone of the Year is Here!",
                "Why I Switched Back to the iPhone Air",
                "Building a Minimal Desk Setup in 2026",
                "Is This Portable Console Really the Next Switch?",
                "We Need to Talk About Apple Intelligence Features",
                "This mechanical keyboard sounds like Heaven",
                "My New Daily Cyber Backpack Review",
                "7 Apps That Make Me 2X Productive",
                "Unboxing the Crazy Futuristic Desktop Display",
                "The Ultimate Coding Laptop Showdown!",
                "I Rebuilt my Workspace for Under $200",
                "Why Modern Wireless Audios Keep Failing",
                "Inside the World's Smartest House!",
                "Best Noise Cancelling Earbuds of 2026",
                "Testing the Android Glass Augmented Reality Pro",
                "Is This AI-Pin Actually Useful For Coding?",
                "How I Organized my Files to Reach Inbox Zero",
                "The Rise and Fall of USB-C Smart Wearables",
                "Why Linux is the Perfect OS for Programming",
                "The Tech Gadget Purchases I Instantly Regretted"
            )
            else -> listOf(
                "Sidhu AI Tribute Track - Legend Legacy (Official Trap Mix)",
                "We Asked AI to Sing Sidhu Moose Wala's Next Album - Dhol Trap",
                "Why Punjabi AI Vocals Sound Artificial (Dhol Frequency Fix)",
                "Amrinder Gill AI Vocal Mix - Channi Channi (Slowed & Reverb)",
                "Diljit Dosanjh & Karan Aujla AI - Collaboration Beat [4K]",
                "Sartaj AI Classical Tribute - Sufi Soul Trap Beat",
                "Unreleased Sidhu Track Recreated with Advanced Speech AI Mod",
                "Babbu Maan AI Vocal Cover - Mitran Di Chatri Trap Remix",
                "Karan Aujla AI Cover - Softly (Slowed Acoustic Guitar Vibe)",
                "Sidhu Moose Wala x Tupac Shakur - Outlawz AI Mashup",
                "Chamkila AI Classical Vocal Restoration (Lofi Dhol)",
                "Why Most Punjabi AI Producers Fail (Vocal Alignment Guide)",
                "The Ultimate Punjabi AI Voice Setup (Tutorial & Free Models)",
                "Late Night Punjabi Lofi Blend - Chill Remix [AI Cover]",
                "Garry Sandhu AI vocal - Illegal Weapon Trap Edit",
                "Sidhu AI Tribute - Last Ride Remodelled Beat",
                "Deep Sidhu Tribute Trap Beat + Voice Restored Vocals",
                "Kharoud Bass Boosted Dhol Trap Beat - Instrumental Only",
                "How I Clean Punjabi Vocals to sound like Real Studio AI",
                "Asking Famous Punjabi Artists of UK to review our AI Cover"
            )
        }

        val parsedVideos = mutableListOf<VideoItem>()
        val baseViews = (subscribers * 0.4).roundToInt().toLong()
        var currentDaysAgo = 2
        
        videoTitles.forEachIndexed { index, title ->
            val factor = if (index % 5 == 0) 2.5 else if (index % 4 == 0) 0.5 else 1.0
            val viewsVal = (baseViews * factor * (1.1 - index * 0.03)).roundToInt().coerceAtLeast(1500).toLong()
            val likesVal = (viewsVal * 0.05).toLong()
            val commsVal = (likesVal * 0.08).toLong()
            
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -currentDaysAgo)
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val dateStr = sdf.format(calendar.time)
            
            // Advance dates
            currentDaysAgo += if (index % 2 == 0) 3 else 4
            
            parsedVideos.add(VideoItem(title, viewsVal, likesVal, commsVal, dateStr))
        }

        val totalVWeeks = currentDaysAgo / 7.0
        val freqVal = ((20 / totalVWeeks) * 10).roundToInt() / 10.0
        val freqTxt = "$freqVal videos per week"

        return ChannelStats(
            id = "UCmock" + handle.hashCode().absoluteValue,
            name = name,
            handle = handle,
            subscribers = subscribers,
            totalViews = views,
            totalVideos = videos,
            avatarUrl = "https://images.unsplash.com/photo-1611162617213-7d7a39e9b1d7?auto=format&fit=crop&w=150&q=80",
            uploadFrequencyValue = freqVal,
            uploadFrequencyText = freqTxt,
            averageViews = parsedVideos.map { it.views }.average().roundToLong(),
            recentVideos = parsedVideos
        )
    }

    private fun getSafeVideoTitle(stats: ChannelStats, index: Int, defaultTitle: String): String {
        val list = stats.recentVideos
        if (list.isEmpty()) return defaultTitle
        return list[index % list.size].title
    }

    private fun generateMockLatestVideoAnalysis(theme: String, stats: ChannelStats): LatestVideoAnalysis {
        val latestTitle = stats.recentVideos.firstOrNull()?.title ?: "Untitled Latest Upload"
        return when (theme) {
            "Cooking" -> LatestVideoAnalysis(
                verdict = "STRONG",
                reasons = "Your latest recipe walk-through \"$latestTitle\" is currently pacing 15% higher in views per day compared to your historical averages. High click-through rate tells us viewers are loving the thumbnail hook.",
                actions = listOf(
                    "Pin a comment asking viewers what recipe they want next to drive the engagement algorithm.",
                    "Add an end screen bubble pointing directly to your other top-pacing garlic pasta recipe.",
                    "Post a Community Tab teaser containing a poll of your next ingredient to maintain week-long retention."
                )
            )
            "Fitness" -> LatestVideoAnalysis(
                verdict = "WEAK",
                reasons = "The latest video \"$latestTitle\" is pacing slightly lower (9%) in typical view velocity. This is due to a highly saturated workout title pattern during a weekend upload.",
                actions = listOf(
                    "Update the thumbnail to include bold, neon-contrasted text stating 'DO THIS NOT THAT'.",
                    "Add detailed chapters with timecodes so mobile viewers can jump directly to the technique guides.",
                    "Create a 45-second high-energy Short highlighting the exercise technique to drive funnel traffic."
                )
            )
            "Tech" -> LatestVideoAnalysis(
                verdict = "STRONG",
                reasons = "Your latest Gadgets unboxing video \"$latestTitle\" has hit 250% higher CTR velocity in the first 24 hours. The visual grading in your cinematic b-roll is pulling in a massive tech audience.",
                actions = listOf(
                    "Pin an affiliate links section in the description and top comment immediately to monetize the traffic spike.",
                    "Build a YouTube Shorts version highlighting the mechanical keyboard sound test to capitalize on viral audio feeds.",
                    "Post a behind-the-scenes build teaser on your Community Tab to keep gadget discussion hot."
                )
            )
            else -> LatestVideoAnalysis(
                verdict = "STRONG",
                reasons = "The latest upload \"$latestTitle\" is maintaining steady view velocity right on your channel average. Steady retention patterns show a highly loyal audience base.",
                actions = listOf(
                    "Reply to the top 10 comments in the first hour to boost community connection metrics.",
                    "Optimize video description tags with 3 relevant trending search terms.",
                    "Add card overlays at the 50% audience retention mark pointing to your highest-converting masterpiece."
                )
            )
        }
    }

    private fun generateMockAnalysis(handle: String, theme: String, stats: ChannelStats): ChannelAnalysis {
        return when (theme) {
            "Cooking" -> ChannelAnalysis(
                healthScore = 78,
                healthReason = "Strong view-to-sub subscriber ratio on recipe walk-throughs, but bread and sourdough niche uploads are capping engagement.",
                bestVideos = listOf(
                    VideoInsight(getSafeVideoTitle(stats, 0, "15-Minute Creamy Garlic Pasta!"), "Highly optimized recipe keywords combined with vibrant color-balanced thumbnails which maximized CTR."),
                    VideoInsight(getSafeVideoTitle(stats, 1, "The Ultimate Steak-Searing Masterclass"), "Perfect retention pacing. Jumping directly into action inside the first 3 seconds, skipping long intros."),
                    VideoInsight(getSafeVideoTitle(stats, 5, "Easy Homemade Noodle Dough Recipe"), "High evergreen keyword interest. Knife skills search search volume is high year-round.")
                ),
                worstVideos = listOf(
                    VideoInsight(getSafeVideoTitle(stats, 2, "Why Your Sourdough Bread Keeps Collapsing"), "Overly specific problem statement that alienated broader casual home-cooking enthusiasts."),
                    VideoInsight(getSafeVideoTitle(stats, 6, "Recreating the Viral Ramen Trend Perfectly"), "7-Day noodles challenge is a highly saturated format; thumbnail visual clutter didn't stand out in general feeds."),
                    VideoInsight(getSafeVideoTitle(stats, 13, "How I Cooks 3-Ingredient Dessert in 5 Minutes (Without Oven!)"), "Title was too formal. Speaking of technical kitchen terminology instead of accessible tasty benefits.")
                ),
                audienceProfile = "Predominantly food lovers and busy young professionals aged 18-34 wanting easy culinary shortcuts, cooking hacks, and comfort foods around dinner time.",
                mainWeakness = "Thumbnail graphics lacking text contrast and excessive silent culinary work-shot pauses that drop viewer retention under 45%.",
                primaryCategory = "Cooking & Culinary",
                subCategory = "Home Cooking Hacks",
                microNiche = "Under-10-Minute Comfort Meals",
                contentIdentity = "Busy professionals seeking ultra-fast kitchen shortcuts",
                contentFormat = "Cinematic recipe b-roll + rapid tutorial voiceover",
                competitors = listOf(
                    CompetitorAnalysis("Tasty Bites Cooking", "@tastybites", 180000, 65000, "2 vids/week", "Cinematic b-roll + VO", "Highly aesthetic food closeups"),
                    CompetitorAnalysis("Epicurean Fast", "@epicureanfast", 95000, 22000, "1 vid/week", "Cinematic b-roll + VO", "Extremely fast-paced editing flow"),
                    CompetitorAnalysis("Chef Josh Cooks", "@joshcooks", 210000, 180000, "3 vids/month", "Cinematic b-roll + VO", "Elite culinary knife guides"),
                    CompetitorAnalysis("Easy Kitchen", "@easykitchen", 32000, 9500, "1 vid/week", "Cinematic b-roll + VO", "Super clean step-by-step overlays"),
                    CompetitorAnalysis("Pan & Fire", "@panfire", 140000, 41000, "2 vids/week", "Cinematic b-roll + VO", "Excellent sound branding sizzles"),
                    CompetitorAnalysis("Rapid Recipes", "@rapidrecipes", 28000, 8000, "1 vid/week", "Cinematic b-roll + VO", "Brilliant minimal cost focus"),
                    CompetitorAnalysis("Bistro Shorts", "@bistroshorts", 45000, 12000, "3 vids/week", "Cinematic b-roll + VO", "Outstanding vertical frame composition"),
                    CompetitorAnalysis("Green Spatula", "@greenspatula", 110000, 35000, "1 vid/week", "Cinematic b-roll + VO", "Eco-friendly kitchen setups"),
                    CompetitorAnalysis("Simply Seared", "@simplyseared", 65000, 18000, "2 vids/week", "Cinematic b-roll + VO", "Ultra-clear graphics for portion sizes"),
                    CompetitorAnalysis("Sizzle & Spice", "@sizzlespice", 150000, 44000, "1 vid/week", "Cinematic b-roll + VO", "Engaging storytelling backstory hooks")
                ),
                gapAnalysis = "Most competitors focus solely on complex, multi-equipment gourmet foods. There is a clean unfilled gap for high-quality 'one-pot, under-10-minute' dinner recipes.",
                strategiesToAdopt = listOf(
                    "Insert zoom-in sizzle sound close-ups inside of intro sequence to establish sensory hooks.",
                    "Frame key titles with emotion keywords ('The SECRET recipe', 'Game Changer pancake').",
                    "Add detailed text labels/pointers directly on food in thumbnails."
                ),
                yourAdvantage = "Exceptional step-by-step clarity. Your instructions are much easier to follow than Tasty Bites' rushed editing.",
                videoIdeas = listOf(
                    VideoIdea("How I Cooks 3-Ingredient Dessert in 5 Minutes (Without Oven!)", "High emotional payoff, requires minimal skill, and uses universal household items.", "Long", "Easy"),
                    VideoIdea("The Secret fried rice trick restaurants don't tell you", "Curiosity gaps drive outstanding search discovery and browse clicks.", "Long", "Easy"),
                    VideoIdea("Why your garlic garlic bread ALWAYS burns", "Addresses a common chef frustration quickly with clear visual demonstration.", "Short", "Medium"),
                    VideoIdea("Recreating the viral ramen trend perfectly", "Riding the waves of popular social media movements.", "Long", "Medium"),
                    VideoIdea("1 Pot Pasta Hack everyone is talking about", "Simple, low dishwashing effort is a viral hook.", "Short", "Easy"),
                    VideoIdea("Knife skills speed challenge!", "Active, competitive engagement showing skill.", "Long", "Hard"),
                    VideoIdea("Is a $100 pan ACTUALLY better than $10 pan?", "A product comparison hook that triggers high tech-style curiosity.", "Long", "Medium"),
                    VideoIdea("Easy homemade noodle dough recipe", "Perfect evergreen Search optimization choice.", "Long", "Hard"),
                    VideoIdea("5 Lunchbox hacks every busy person needs", "Extremely practical time-saving ideas.", "Short", "Easy"),
                    VideoIdea("How long does leftover pizza ACTUALLY keep?", "Funny kitchen question that provokes huge comment section debates.", "Long", "Medium")
                ),
                contentCalendar = listOf(
                    CalendarEntry(1, "June 1", "How I Cooks 3-Ingredient Dessert in 5 Minutes"),
                    CalendarEntry(5, "June 5", "The Secret fried rice trick restaurants don't tell you"),
                    CalendarEntry(10, "June 10", "Why your garlic garlic bread ALWAYS burns"),
                    CalendarEntry(15, "June 15", "Recreating the viral ramen trend perfectly"),
                    CalendarEntry(20, "June 20", "1 Pot Pasta Hack everyone is talking about"),
                    CalendarEntry(25, "June 25", "Knife skills speed challenge!"),
                    CalendarEntry(30, "June 30", "Is a $100 pan ACTUALLY better than $10 pan?")
                ),
                bestUploadDays = listOf("Wednesday", "Friday", "Sunday"),
                recommendedLength = "8-10 mins for recipe details, 30-45 seconds for Shorts",
                shortsVsLongRatio = "50% Long, 50% Shorts to build funnel"
            )
            "Fitness" -> ChannelAnalysis(
                healthScore = 84,
                healthReason = "Very strong engagement on instructional workout videos, but diet meal preps show audience drop-offs due to dull camera setups.",
                bestVideos = listOf(
                    VideoInsight(getSafeVideoTitle(stats, 0, "Direct Muscle Exercise Guide"), "Direct answer to user body goal request. Huge arm workout search optimization with precise rep list."),
                    VideoInsight(getSafeVideoTitle(stats, 2, "Stretching Routine Guide"), "Universal appeal. Addressing physical stiffness is helpful to gamers, desk workers, and lifts alike."),
                    VideoInsight(getSafeVideoTitle(stats, 4, "Form Correction Guide"), "Excellent structural division. Distinct chapters allow clean navigation.")
                ),
                worstVideos = listOf(
                    VideoInsight(getSafeVideoTitle(stats, 3, "Calorie Dense Recipe Day"), "Diet eating days underperformed due to lack of recipe visual variety and generic speaking pauses."),
                    VideoInsight(getSafeVideoTitle(stats, 6, "Full Running Challenge"), "10K running was heavily saturated in late 2025; did not compete with more extreme running channels."),
                    VideoInsight(getSafeVideoTitle(stats, 11, "Cardio Exercise Review"), "Cardio review title had passive wording which didn't command visual attention.")
                ),
                audienceProfile = "Intermediate gymgoers, desk-bound workers, and fitness enthusiasts aged 16-40 demanding efficient workout techniques and active nutritional tips.",
                mainWeakness = "Speaking too quiet or with low-energy monotones during technical lifting tutorials. Lacks motivating urgency.",
                primaryCategory = "Fitness & Physical Health",
                subCategory = "Posture Correction & Form Repairs",
                microNiche = "Desk Worker Joint Longevity",
                contentIdentity = "Sedentary experts recovering physical agility",
                contentFormat = "ASMR physical stretching walk-throughs",
                competitors = listOf(
                    CompetitorAnalysis("GymScience", "@gymscience", 380000, 92000, "1 vid/week", "ASMR stretching walkthroughs", "Elite anatomical correct charts"),
                    CompetitorAnalysis("Lean & Lift", "@leanlift", 150000, 31000, "2 vids/week", "ASMR stretching walkthroughs", "Deep voice explanation clarity"),
                    CompetitorAnalysis("Aesthetic Theory", "@aesthetic_theory", 95000, 19000, "1 vid/week", "ASMR stretching walkthroughs", "Minimalist gym setting presentation"),
                    CompetitorAnalysis("Coach Dave Fitness", "@davefit", 320000, 85000, "3 vids/month", "ASMR stretching walkthroughs", "Extremely powerful motivational speeches"),
                    CompetitorAnalysis("Pure Dumbbell Workouts", "@pure_dumbbell", 68000, 14000, "2 vids/week", "ASMR stretching walkthroughs", "Ultra-clear camera frame positioning"),
                    CompetitorAnalysis("Posture Rep", "@posturerep", 55000, 12000, "1 vid/week", "ASMR stretching walkthroughs", "Highly specific desktop worker solutions"),
                    CompetitorAnalysis("Physiology Gym", "@physiologygym", 120000, 31000, "2 vids/week", "ASMR stretching walkthroughs", "Excellent muscle fiber animation overlays"),
                    CompetitorAnalysis("Iron Therapy", "@irontherapy", 220000, 62000, "1 vid/week", "ASMR stretching walkthroughs", "Captivating user story transformations"),
                    CompetitorAnalysis("Joint Power", "@jointpower", 85000, 24000, "2 vids/month", "ASMR stretching walkthroughs", "Simple home-friendly exercises"),
                    CompetitorAnalysis("Form Catalyst", "@formcatalyst", 190000, 48000, "1 vid/week", "ASMR stretching walkthroughs", "No fluff, bulletproof biomechanics guides")
                ),
                gapAnalysis = "Most competitors focus on aesthetic vanity flexes. There is a deep unsatisfied gap for 'anti-injury physical correction' and bulletproof knee joint workouts.",
                strategiesToAdopt = listOf(
                    "Insert animated skeletal or muscle highlight overlays to visually show muscle execution lines.",
                    "Use high-contrast bold yellow font in thumbnails to emphasize anatomical parts.",
                    "Include a progress countdown bar in top-right of exercise splits."
                ),
                yourAdvantage = "No fluff, high scientific accuracy. You explain bio-mechanics far clearer than davefit.",
                videoIdeas = listOf(
                    VideoIdea("The only 3 Exercises you need for a wider back", "Clear checklist numbers drive excellent search intent click-through rates.", "Long", "Easy"),
                    VideoIdea("How to squat without knee clicking ever again", "Solves a painful universal lift issue safely and directly.", "Long", "Medium"),
                    VideoIdea("3 dumbbell mistakes everyone is making", "Using correction triggers instant user interest to check their form.", "Short", "Easy"),
                    VideoIdea("Full day of calorie-dense meals on $5 a day", "Cheap fitness eating is a highly searched high-ctr concept.", "Long", "Medium"),
                    VideoIdea("How many pushups can you ACTUALLY do?", "Highly interactive challenge format.", "Short", "Easy"),
                    VideoIdea("Perfect posture stretching routine inside 4 minutes", "Ultra-low time barrier highly shares on group feeds.", "Long", "Easy"),
                    VideoIdea("Why protein powder might be destroying your gut", "A counter-cultural warning that sparks massive curiosity and clicks.", "Long", "Medium"),
                    VideoIdea("The lazy athlete's recovery strategy", "Appealing to efficiency and laziness always scales.", "Long", "Easy"),
                    VideoIdea("Calisthenics pullups form repair guide", "Clean execution step instructions.", "Short", "Medium"),
                    VideoIdea("I tried the golden age workout routine", "Famous historical challenge format.", "Long", "Hard")
                ),
                contentCalendar = listOf(
                    CalendarEntry(1, "June 1", "The only 3 Exercises you need for a wider back"),
                    CalendarEntry(5, "June 5", "How to squat without knee clicking ever again"),
                    CalendarEntry(10, "June 10", "3 dumbbell mistakes everyone is making"),
                    CalendarEntry(15, "June 15", "Full day of calorie-dense meals on ${'$'}5 a day"),
                    CalendarEntry(20, "June 20", "How many pushups can you ACTUALLY do?"),
                    CalendarEntry(25, "June 25", "Perfect posture stretching routine inside 4 minutes"),
                    CalendarEntry(30, "June 30", "Why protein powder might be destroying your gut")
                ),
                bestUploadDays = listOf("Monday", "Wednesday", "Saturday"),
                recommendedLength = "10-12 mins for routines, 40-50 secs for Shorts",
                shortsVsLongRatio = "70% Long, 30% Shorts to build training brand"
            )
            "Tech" -> ChannelAnalysis(
                healthScore = 88,
                healthReason = "Outstanding detail in desktop keyboard sound test recordings and desk setups, but smartphone spec comparisons underperform.",
                bestVideos = listOf(
                    VideoInsight(getSafeVideoTitle(stats, 0, "Desk Setup Upgrade Guide"), "Excellent timing. Capitalized on the annual high phone search rush immediately with a brutal value proposition title."),
                    VideoInsight(getSafeVideoTitle(stats, 2, "My Creative Workspace Setup"), "Beautiful cinematic b-roll. Desk setups provoke high lifestyle aesthetic sharing across Pintrest & Reddit."),
                    VideoInsight(getSafeVideoTitle(stats, 5, "Keyboard ASMR Sound Test"), "Phenomenal ASMR sound recording quality. Keyboard sound tests have massive retention rates.")
                ),
                worstVideos = listOf(
                    VideoInsight(getSafeVideoTitle(stats, 4, "Smart Gadget Performance List"), "Too generic. Apple intelligence had 500 equivalent videos on the search timeline, diluting impressions."),
                    VideoInsight(getSafeVideoTitle(stats, 7, "Digital Asset Workflow Design"), "Productivity app list lacked visual screenshots in the intro, leading to user swipe-aways."),
                    VideoInsight(getSafeVideoTitle(stats, 15, "Vintage Tech Restoration"), "AI-Pin is a declining search topic. Low interest led to low CTR.")
                ),
                audienceProfile = "Tech enthusiasts, software engineers, desktop minimalist hobbyists, and gadget collectors aged 18-35 craving high-aesthetic cinematic reviews.",
                mainWeakness = "Spending too much time reading abstract technical spec-sheets in the first 2 minutes instead of showing immediate active b-roll handling.",
                primaryCategory = "Technology & Gadgets",
                subCategory = "Desk Setups & Mechanical Keyboards",
                microNiche = "Minimalist Silent Setup Crafts",
                contentIdentity = "Software engineers perfecting their daily desk habitat",
                contentFormat = "Keyboard ASMR typing sound tests",
                competitors = listOf(
                    CompetitorAnalysis("TechMinimalist", "@techminimalist", 820000, 310000, "1 vid/week", "Keyboard ASMR sound tests", "Premium studio mic acoustics"),
                    CompetitorAnalysis("Gadget Lab", "@gadgetlab", 145000, 48000, "1 vid/week", "Keyboard ASMR sound tests", "Very high thumbnail contrast ratio"),
                    CompetitorAnalysis("ByteSized Tech", "@bytesized", 190000, 24000, "2 vids/week", "Keyboard ASMR sound tests", "Super short, high-value visual quickcuts"),
                    CompetitorAnalysis("Workspace Setup Pro", "@workspace_setup", 320000, 95000, "1 vid/week", "Keyboard ASMR sound tests", "Stunning neon ambient background lighting"),
                    CompetitorAnalysis("Mano Keyboard ASMR", "@manokeyboard", 210000, 72000, "3 vids/month", "Keyboard ASMR sound tests", "Incredible plate acoustic mod focus"),
                    CompetitorAnalysis("Desk Blueprint", "@deskblueprint", 160000, 41000, "1 vid/week", "Keyboard ASMR sound tests", "Unique structural hardware arrangement details"),
                    CompetitorAnalysis("Keystroke Heaven", "@keystrokeheaven", 115000, 32000, "2 vids/month", "Keyboard ASMR sound tests", "Beautiful retro classic keycaps selection"),
                    CompetitorAnalysis("Spec Nerd", "@specnerd", 410000, 115000, "1 vid/week", "Keyboard ASMR sound tests", "Accurate soundboard capture consistency"),
                    CompetitorAnalysis("Future Workspace", "@futureworkspace", 680000, 185000, "1 vid/week", "Keyboard ASMR sound tests", "High-budget cinematic camera lens work"),
                    CompetitorAnalysis("Daily Tech Minimal", "@dailytechminimal", 250000, 58000, "2 vids/week", "Keyboard ASMR sound tests", "Practical daily hardware utility emphasis")
                ),
                gapAnalysis = "Most desk setup influencers list items costing $10,000+. There is an enormous untapped audience for 'Ultra-Budget Desk Upgrades under $25' that look high-end.",
                strategiesToAdopt = listOf(
                    "Employ high-fidelity ASMR mechanical keyboard clicks or ambient sound in background tracks.",
                    "Use crisp, colored neon light backgrounds behind presenter to create deep studio atmosphere.",
                    "Frame thumbnails using macro extreme close-ups of gadget knobs or camera lenses."
                ),
                yourAdvantage = "Mastery of audio and acoustics. Keyboard sounds on your channel are captured perfectly compared to TechMinimalist.",
                videoIdeas = listOf(
                    VideoIdea("9 Desk Upgrades under $15 (That feel like $150)", "High contrast value and immediate accessibility drive massive browse clicks.", "Long", "Easy"),
                    VideoIdea("How to build the ultimate typing keyboard", "Appeals directly to keystroke hobbyists wanting customized build layouts.", "Long", "Medium"),
                    VideoIdea("Why you should NOT buy a mechanical keyboard in 2026", "A strong contrary opinion that provokes massive comment section debates.", "Long", "Easy"),
                    VideoIdea("My minimal setup workspace setup - final edition", "Highly sought after aesthetic layout walkthrough.", "Long", "Medium"),
                    VideoIdea("Is this cheap tech purchase actually worth it?", "Direct answers to typical smartphone budget buyers.", "Short", "Easy"),
                    VideoIdea("The perfect programer desk layout guide", "Combines minimal aesthetic with daily utility workflow.", "Long", "Easy"),
                    VideoIdea("How I organized 10000 digital files inside an hour", "Practical data decluttering tips.", "Long", "Medium"),
                    VideoIdea("This gadget completely changed my typing posture", "Solves physical pain using a modern ergonomic purchase.", "Short", "Easy"),
                    VideoIdea("Coding on a $99 refurbished laptop challenge!", "Highly educational, low-barrier, funny developer content.", "Long", "Hard"),
                    VideoIdea("My tech purchases I instantly regretted", "Deep authentic vulnerability that builds powerful viewer trust.", "Long", "Medium")
                ),
                contentCalendar = listOf(
                    CalendarEntry(1, "June 1", "9 Desk Upgrades under ${'$'}15 (That feel like ${'$'}150)"),
                    CalendarEntry(5, "June 5", "How to build the ultimate typing keyboard"),
                    CalendarEntry(10, "June 10", "Why you should NOT buy a mechanical keyboard"),
                    CalendarEntry(15, "June 15", "My minimal setup workspace setup - final edition"),
                    CalendarEntry(20, "June 20", "Is this cheap tech purchase actually worth it?"),
                    CalendarEntry(25, "June 25", "The perfect programer desk layout guide"),
                    CalendarEntry(30, "June 30", "How I organized 10000 digital files inside an hour")
                ),
                bestUploadDays = listOf("Thursday", "Saturday", "Sunday"),
                recommendedLength = "12-14 mins for builds, 30-45 secs for ASMR Shorts",
                shortsVsLongRatio = "80% Long, 20% Shorts to build setup aesthetic"
            )
            else -> ChannelAnalysis(
                healthScore = 80,
                healthReason = "Strong views on Sidhu AI mashups, but lack of vocal frequency tuning results in metallic artificial hums and rapid listener drop-offs.",
                bestVideos = listOf(
                    VideoInsight(getSafeVideoTitle(stats, 0, "AI Song Remix and Vocal Cover"), "Excellent trending piggyback. Captured high search demand for Legend Legacy AI tribute with custom trap backing."),
                    VideoInsight(getSafeVideoTitle(stats, 3, "Vaporwave Slowed & Reverb Mix"), "Vaporwave slowed and reverb remix created high replay loop retention of 72% on secondary devices."),
                    VideoInsight(getSafeVideoTitle(stats, 4, "Artist Collaboration Hypothetical Duet"), "Diljit and Karan hypothetical duet created tremendous curiosity and high viral sharing on Reddit.")
                ),
                worstVideos = listOf(
                    VideoInsight(getSafeVideoTitle(stats, 2, "Vocal Frequency Fine Tuning Tutorial"), "Too technical. Tutorial style did not appeal to casual music lovers seeking pure entertainment tracks."),
                    VideoInsight(getSafeVideoTitle(stats, 11, "Vocal Pitch Restoration Crash Course"), "Saturated general tutorial topic. Bypassed by community in favor of direct AI songs."),
                    VideoInsight(getSafeVideoTitle(stats, 17, "Instrumental Beat and Backing Drop"), "Instrumental track was missing primary singer hook lines, driving high initial swipe-aways.")
                ),
                audienceProfile = "Predominantly urban Punjabi music lovers and diaspora youth aged 15-34 in India, Canada, UK, and Australia seeking unreleased sound blends or nostalgia tributes.",
                mainWeakness = "Vocal blending alignment with Punjabi dhol frequencies, resulting in slightly robotic artificial sound peaks that drop audience retention past 55s.",
                primaryCategory = "Entertainment & Music",
                subCategory = "AI Music & Cover Remixes",
                microNiche = "Punjabi AI Vocal Tribute Songs",
                contentIdentity = "Punjabi diaspora youth seeking nostalgia and modern trap-remixed classical vocals",
                contentFormat = "AI vocals overlay + original trap beats arrangement",
                competitors = listOf(
                    CompetitorAnalysis("Sidhu AI Tribute", "@sidhu_ai_tribute", 1200000, 390000, "1 vid/week", "AI vocals + original trap beats", "Exceptional modern Punjabi hook lines"),
                    CompetitorAnalysis("Vibe AI Remix", "@vibe_ai_remix", 450000, 89000, "3 vids/week", "AI vocals + original trap beats", "Outstanding vocal clarity & tone consistency"),
                    CompetitorAnalysis("Majha AI Music", "@majha_ai_music", 310000, 75000, "2 vids/week", "AI vocals + dhol trap beats", "Elite percussion and modern dhol drops"),
                    CompetitorAnalysis("Urban Folk AI", "@urbanfolk_ai", 620000, 140000, "1 vid/week", "AI vocals + classical fusion", "Staggering retro-to-modern transition structures"),
                    CompetitorAnalysis("Doaba AI Beats", "@doaba_beats", 850000, 195000, "1 vid/week", "AI vocals + dhol trap beats", "Highly active social media viral trends leverage"),
                    CompetitorAnalysis("Viral AI Singer", "@viral_singer_ai", 180000, 35000, "1 vid/week", "AI vocals + lofi remixes", "Clever lyrical concept hook videos"),
                    CompetitorAnalysis("Classic AI Fusion", "@classic_ai_fusion", 250000, 56000, "2 vids/week", "AI vocals + lofi Punjabi", "Amazing old-school audio restoration"),
                    CompetitorAnalysis("Sufi Trap AI", "@sufitrap_ai", 420000, 95000, "1 vid/week", "AI vocals + sufi fusion", "Incredibly deep emotional background pads"),
                    CompetitorAnalysis("Beatmaker AI", "@beatmaker_ai", 520000, 115000, "1 vid/week", "AI vocals only", "Exceptional instrumental-only alternate remixes"),
                    CompetitorAnalysis("Punjab AI Legacy", "@punjab_legacy", 720000, 160000, "2 vids/week", "AI vocals + standard trap", "Rich historical references in lyrics")
                ),
                gapAnalysis = "Most competitors focus solely on basic stock looping sounds. There is a deep unsatisfied gap for authentic regional instruments blended with state-of-the-art AI vocal arrangements.",
                strategiesToAdopt = listOf(
                    "Calibrate vocal midrange frequencies (1.5kHz to 3kHz) to prevent metallic robot peaks in headphones.",
                    "Synchronize dhol snare rolls with vocal cadence transitions to create dramatic physical impact.",
                    "Leverage diaspora nostalgia factor by pairing vertical visuals of Punjab landscapes with dhol drops."
                ),
                yourAdvantage = "Superior master compression. Your dhol sounds punch through cleaner on studio speakers than Sidhu AI Tribute's stock loops.",
                videoIdeas = listOf(
                    VideoIdea("We Asked AI to sing Sidhu Moose Wala in Punjabi (Unreleased Trap Remix)", "Combines unreleased intrigue with a high-CTR title structure that addresses dhol-vocal clashing directly.", "Long", "Medium"),
                    VideoIdea("Diljit Dosanjh x Karan Aujla AI Collaboration (Slowed & Reverb Trap Vibe)", "Capitalizes on two active superstars in a slowed-reverb style that requires less complex vocal matching.", "Long", "Easy"),
                    VideoIdea("How we resolved the metallic robot screech in Punjabi AI covers", "Addresses Core Strategic Roadblock directly with an educational tutorial, establishing peerless authority.", "Short", "Easy"),
                    VideoIdea("Chamkila Classical Hook x Urban Trap Beat [Advanced AI Vocal Restoration]", "Taps into retro Chamkila folk nostalgia which has a 95% retention rate for older listener brackets.", "Long", "Hard"),
                    VideoIdea("Amrinder Gill AI Smooth Lofi Remix (Study/Late Night Blend)", "Slowed lofi transitions are low-prep production styles but generate massive replay/loop traffic overnight.", "Long", "Easy"),
                    VideoIdea("Sidhu Moose Wala x Tupac: The Outlawz AI Mashup (Dhol Mix)", "Taps into the global hip-hop connection, driving huge diaspora curiosity clicks.", "Long", "Medium"),
                    VideoIdea("We recreated [Unreleased Artist Track] using only state-of-the-art Voice Models", "Rides high on unreleased song hype, bypassing saturated video topics completely.", "Long", "Hard"),
                    VideoIdea("AI VS REAL: Can you guess which Sidhu vocal is actual human?", "Highly interactive visual/audio challenge that triggers double comment section engagement.", "Short", "Easy"),
                    VideoIdea("The 3 biggest mistakes Punjabi AI beatmakers make inside FL Studio", "Combats dhol frequency clashing directly, targeting aspiring creators to build specialized community.", "Short", "Easy"),
                    VideoIdea("Babbu Maan - Mitran Di Chatri (Modern Trap dhol remodelled)", "Converts highly popular classic folk track into modern dhol-trap, capturing old and young markets.", "Long", "Medium")
                ),
                contentCalendar = listOf(
                    CalendarEntry(1, "June 1", "We Asked AI to sing Sidhu Moose Wala in Punjabi (Unreleased Trap Remix)"),
                    CalendarEntry(5, "June 5", "Diljit Dosanjh x Karan Aujla AI Collaboration (Slowed & Reverb)"),
                    CalendarEntry(10, "June 10", "How we resolved the metallic robot screech in Punjabi AI covers"),
                    CalendarEntry(15, "June 15", "Chamkila Classical Hook x Urban Trap Beat [Vocal Restoration]"),
                    CalendarEntry(20, "June 20", "Amrinder Gill AI Smooth Lofi Remix (Study Blend)"),
                    CalendarEntry(25, "June 25", "Sidhu Moose Wala x Tupac: The Outlawz AI Mashup (Dhol Mix)"),
                    CalendarEntry(30, "June 30", "AI VS REAL: Can you guess which Sidhu vocal is actual human?")
                ),
                bestUploadDays = listOf("Friday", "Sunday"),
                recommendedLength = "5-7 mins for vocal tracks, 30-45 seconds for teaser Shorts (Optimized for India IST 6:00 PM / Canada EST 7:30 AM global release peak)",
                shortsVsLongRatio = "40% Long, 60% Shorts (Using high-impact 30s Shorts teasers to funnel diaspora listeners into full dhol tracks)"
            )
        }
    }

    suspend fun generateVideoAutopsy(
        video: VideoInsight,
        report: FullGrowTubeReport,
        apiKey: String
    ): AutopsyReport = withContext(Dispatchers.IO) {
        val stats = report.stats
        val analysis = report.analysis
        
        val recentVideosText = stats.recentVideos.take(10).joinToString("\n") { 
            "- TITLE: ${it.title} | VIEWS: ${it.views} | DATE: ${it.date}"
        }
        val bestVideosText = analysis.bestVideos.joinToString("\n") { "- TITLE: ${it.title}" }
        val competitorNames = analysis.competitors.joinToString(", ") { "${it.name} (${it.handle})" }

        val systemInstruction = "You are GrowTube AI, the ultimate world-class YouTube content strategist and diagnostic engine. " +
                "You perform brutal, hyper-targeted, niche-specific Video Autopsies on underperforming YouTube videos."

        val promptText = """
            Perform a 3-Layer Video Autopsy on this underperforming video:
            FAILED VIDEO TITLE: "${video.title}"
            PRIMARY KILL REASON DETECTED: "${video.reason}"
            
            CHANNEL METRICS & CONTEXT:
            Channel Name: "${stats.name}"
            Channel Handle: "${stats.handle}"
            
            CHANNEL'S BEST PERFORMING VIDEOS (Viral Anchor):
            ${bestVideosText}
            
            CHANNEL'S RECENT VIDEOS (last 10):
            ${recentVideosText}
            
            COMPETITOR CHANNELS IN SAME SPACE:
            ${competitorNames}
            
            You must analyze the channel's profile and build a precise 3-Layer diagnostic autopsy.
            Follow these EXACT rules for your analysis:
            1. NEVER suggest anything outside of the channel's specific micro-niche.
            2. All examples, suggested titles, keywords, thumbnail fixes, and description templates must strictly match the channel's specific niche and language context.
               - E.g., If the channel covers Punjabi songs / Punjabi music / Sidhu Moosewala AI vocals, all suggested titles must be Punjabi-music-relevant, incorporating Punjabi cultural context and terms.
               - E.g., If the channel is cooking, suggestions must be culinary focus.
               - E.g., If coding, tech, gaming, etc., adapt perfectly to those domains.
            
            You must output a raw JSON object string. Do not wrap in backticks or markdown, do not write prefaces. Just return raw parsable JSON matching this EXACT JSON structure:
            {
              "primaryCategory": "Primary category (e.g., Music, Cooking, Gaming, Tech, Fitness, Comedy, Education)",
              "subCategory": "Sub-Category (e.g., Punjabi Music, Indian Vegetarian Recipes, PC Game Emulation)",
              "microNiche": "Micro-Niche (e.g., AI Tribute Vocals, Old School Remixes, One-Pot 10-Min Dinner Recipes)",
              "contentIdentity": "Content Identity (e.g., Sidhu Moosewala AI Vocal Covers, Healthy Budget Bodybuilding Meals)",
              "topicsCovered": "What specific topics does this channel cover? (1 concise sentence)",
              "bestVideoFormat": "What format do their best videos follow? (1 concise sentence)",
              "languageCulture": "Language and cultural context (e.g., Punjabi-English hybrid urban tracks or English aesthetic minimalist tech setups)",
              "coreDemographic": "Demographic context (e.g., Punjabi youth worldwide or productivity-focused programmers)",
              
              "viralTitleStructures": "Title structures that work in this exact micro-niche (e.g., 'NAME' first, then massive emotional hook)",
              "viralEmotionalTriggers": "Emotional triggers (curiosity, nostalgia, high energy, craving, etc.) that move this niche's audience",
              "viralThumbnailStyles": "Thumbnail visual styles that perform best here (colors, face expressions, typography)",
              "viralDescriptionPatterns": "Description patterns (socials, links, timestamps, tags) that rank highest",
              "viralTagsDiscovery": "Tags and keyword strategies that drive discovery in this niche",
              
              "titleCritique": "Brutal, highly specific, niche-sensitive critique of the failed title: '${video.title}'. Explain exactly why it failed compared to the viral benchmark of this niche.",
              "alternateTitles": [
                {
                  "suggestedTitle": "Title Option 1 (Must be highly clickable, strictly in the channel's style and niche)",
                  "inspiredBy": "Inspired by: [Channel's own Best Video Title OR Competitor Channel Name/Video]"
                },
                {
                  "suggestedTitle": "Title Option 2",
                  "inspiredBy": "Inspired by: [Inspired by item]"
                },
                {
                  "suggestedTitle": "Title Option 3",
                  "inspiredBy": "Inspired by: [Inspired by item]"
                },
                {
                  "suggestedTitle": "Title Option 4",
                  "inspiredBy": "Inspired by: [Inspired by item]"
                },
                {
                  "suggestedTitle": "Title Option 5",
                  "inspiredBy": "Inspired by: [Inspired by item]"
                }
              ],
              "thumbnailCritique": "Detailed visual critique of why the thumbnail for '${video.title}' is underperforming compared to niche-specific visual styles.",
              "thumbnailFixes": "3 clear, action-oriented thumbnail styling fixes specific to this niche (e.g., '1. Dynamic framing... 2. Contrast colors...'). Use newlines inside this string.",
              "descriptionCritique": "Critique of why the failed video's description does not convert or rank well in search indices of this niche.",
              "recommendedDescription": "An improved description template specifically written in the niche language, featuring custom timestamps, cultural keywords, links, and formatted for high-conversion.",
              "tagsCritique": "Critique of search discoverability and broad versus narrow tag bottlenecks in this niche.",
              "suggestedTags": [
                "#tag1",
                "#tag2",
                "#tag3",
                "#tag4",
                "#tag5",
                "#tag6",
                "#tag7",
                "#tag8",
                "#tag9",
                "#tag10",
                "#tag11",
                "#tag12",
                "#tag13",
                "#tag14",
                "#tag15"
              ]
            }
        """.trimIndent()

        val service = GeminiRetrofitClient.service
        val request = GeminiRequest(
            contents = listOf(GeminiContent(listOf(GeminiPart(promptText)))),
            systemInstruction = GeminiContent(listOf(GeminiPart(systemInstruction))),
            generationConfig = GeminiGenerationConfig(responseMimeType = "application/json", temperature = 0.25f)
        )

        val response = service.generateContent(apiKey = apiKey, request = request)
        val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("Empty response returned from Gemini.")

        val cleanedJson = responseText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val autopsyAdapter = moshi.adapter(AutopsyReport::class.java)
        
        autopsyAdapter.fromJson(cleanedJson) ?: throw Exception("JSON Parsing Failed on Gemini autopsy analysis.")
    }

    fun generateLocalAutopsyFallback(
        video: VideoInsight,
        report: FullGrowTubeReport
    ): AutopsyReport {
        val nameLower = report.stats.name.lowercase()
        val allTitles = (report.stats.recentVideos.map { it.title } + video.title).joinToString(" ").lowercase()
        
        val isMusic = nameLower.contains("sidhu") || nameLower.contains("moosewala") || nameLower.contains("tribute") ||
                nameLower.contains("music") || nameLower.contains("song") || nameLower.contains("singer") ||
                allTitles.contains("punjabi") || allTitles.contains("song") || allTitles.contains("remix") ||
                allTitles.contains("vocal") || allTitles.contains("moosewala") || allTitles.contains("tribute") ||
                allTitles.contains("music") || allTitles.contains("ai cover") || allTitles.contains("beat")
                
        val isCooking = nameLower.contains("cook") || nameLower.contains("recipe") || nameLower.contains("kitchen") ||
                nameLower.contains("chef") || nameLower.contains("food") || 
                allTitles.contains("cook") || allTitles.contains("recipe") || allTitles.contains("biryani") ||
                allTitles.contains("paneer") || allTitles.contains("kitchen") || allTitles.contains("chef") ||
                allTitles.contains("dish") || allTitles.contains("food") || allTitles.contains("dinner") ||
                allTitles.contains("breakfast") || allTitles.contains("lunch")
                
        val isFitness = nameLower.contains("fit") || nameLower.contains("gym") || nameLower.contains("muscle") ||
                nameLower.contains("workout") || nameLower.contains("health") ||
                allTitles.contains("gym") || allTitles.contains("workout") || allTitles.contains("muscle") ||
                allTitles.contains("cardio") || allTitles.contains("exercise") || allTitles.contains("fitness") ||
                allTitles.contains("abs") || allTitles.contains("bench press") || allTitles.contains("deadlift")
                
        val isTech = nameLower.contains("tech") || nameLower.contains("minimal") || nameLower.contains("code") ||
                nameLower.contains("dev") || nameLower.contains("setup") || nameLower.contains("workspace") ||
                allTitles.contains("tech") || allTitles.contains("setup") || allTitles.contains("mechanical keyboard") ||
                allTitles.contains("developer") || allTitles.contains("coding") || allTitles.contains("kotlin") ||
                allTitles.contains("jetpack") || allTitles.contains("programmer")

        val isGaming = nameLower.contains("gaming") || nameLower.contains("gameplay") || nameLower.contains("gamer") ||
                allTitles.contains("gaming") || allTitles.contains("gameplay") || allTitles.contains("game") ||
                allTitles.contains("speedrun") || allTitles.contains("boss") || allTitles.contains("exploit")

        return when {
            isMusic -> {
                AutopsyReport(
                    primaryCategory = "Music / Audio Artistry",
                    subCategory = "Punjabi Regional Music",
                    microNiche = "AI Vocal Tributes & Covers",
                    contentIdentity = "Classic Sidhu Moosewala & Punjabi AI Vocal Re-creations",
                    topicsCovered = "Generating synthetic high-fidelity music tracks imitating legendary Punjabi voices with new compositions.",
                    bestVideoFormat = "Lyric video with dramatic animated glowing vintage vehicle or rural backdrop visualizer.",
                    languageCulture = "Punjabi language mixed with dramatic urban English subtitles/hooks, targeting Punjabi diaspora globally.",
                    coreDemographic = "Punjabi music fans worldwide, primarily youth aged 16-34 residing in Punjab, Canada, UK, and Australia.",
                    
                    viralTitleStructures = "Singer's Name + Song Accent in Punjabi (Bold Caps) -> Visual Hook -> Emotional Subtitle",
                    viralEmotionalTriggers = "Nostalgia, cultural pride, deep cinematic melancholy, and curiosity regarding AI quality.",
                    viralThumbnailStyles = "Ultra-saturated, high-contrast portrait of the artist in dark overlays, framed by glowing neon borders (orange/red) with Punjabi typography.",
                    viralDescriptionPatterns = "Standard music licensing disclaimer, sound credit attribution links, translation lyrics, and active Spotify/Apple Music playlist directs.",
                    viralTagsDiscovery = "Regional artist handle hashtags, niche synthetic vocal tags, and high-frequency search keywords like #PunjabiNewSong.",
                    
                    titleCritique = "The failed title '${video.title}' lacks immediate cultural triggers. Niche audiences seek a Punjabi name or song title upfront to recognize the vibe. Standard English phrasing hides the emotional hook.",
                    alternateTitles = listOf(
                        AlternateTitleItem(suggestedTitle = "Sidhu Moosewala AI Tribute - LAST RIDE REBORN (Punjabi Vocals)", inspiredBy = "Inspired by: ${report.analysis.bestVideos.getOrNull(0)?.title ?: "Own Channel's Hits"}"),
                        AlternateTitleItem(suggestedTitle = "LEGENDS NEVER DIE: New Sidhu AI Song [Full Track]", inspiredBy = "Inspired by: ${report.analysis.competitors.getOrNull(0)?.name ?: "Niche Competitors"}"),
                        AlternateTitleItem(suggestedTitle = "How We Remixed the Sidhu Moosewala AI Vocal in 4K (Behind the Beats)", inspiredBy = "Inspired by: ${report.analysis.bestVideos.getOrNull(1)?.title ?: "Own Top Release"}"),
                        AlternateTitleItem(suggestedTitle = "This Punjabi AI Tribute Song Is Breaking the Internet right now!", inspiredBy = "Inspired by: ${report.analysis.competitors.getOrNull(1)?.name ?: "Viral Niche Rivals"}"),
                        AlternateTitleItem(suggestedTitle = "Sidhu Moosewala AI - URBAN FOLK REMIX | Official Lyric Video", inspiredBy = "Inspired by: ${report.analysis.competitors.getOrNull(2)?.name ?: "Trending Creators"}")
                    ),
                    thumbnailCritique = "The thumbnail feels sterile and misses the dramatic, cinematic styling that Punjabi music listeners expect. It lacks portrait focal tracking and standard high-contrast borders.",
                    thumbnailFixes = "1. Apply a dark vignette overlay with an high-contrast central face cutout of the tribute artist.\n2. Add high-visibility bold yellow or red Punjabi/Gurmukhi text labels on the sidebar.\n3. Include a vibrant neon glow behind the primary text to increase DeX & mobile screen pop.",
                    descriptionCritique = "Lacks standard lyrics transcription, missing proper music licensing tribute declarations, and contains zero regional keyword density for algorithmic indexing.",
                    recommendedDescription = """
🎵 Stream/Download the Recreated Tribute:
🔗 https://instagram.com/growtube-music-tributes

"Legends never fade. This is an AI-assisted artistic tribute honoring the late, great Sidhu Moosewala, showcasing the future of regional music synthesis."

Timestamps:
0:00 - Cinematic Intro Beats (Urban Punjabi Dhol)
1:12 - First Verse - High-Intensity AI Vocals
2:50 - Chorus & Melancholy Hooks
3:45 - Instrumental Outro & Visual Fade

Credits & Disclaimers:
- Strictly an artistic fan-made tribute, non-monetized tribute.
- Original vocal inspiration: Sidhu Moosewala.

If this touched your soul, subscribe and hit the bell for more Punjabi AI tribute songs! 🚀
                    """.trimIndent(),
                    tagsCritique = "Missing major regional discovery tags. Generic tags like 'song' compete with billions of global media posts, failing to rank.",
                    suggestedTags = listOf(
                        "#SidhuMoosewala", "#PunjabiAISong", "#SidhuAITribute", "#PunjabiMusic2026",
                        "#LegendsNeverDie", "#MoosewalaForever", "#AILyricVideo", "#PunjabiRemix",
                        "#UrbanPunjabi", "#MoosewalaCover", "#RegionalAIVocals", "#TributeTrack"
                    )
                )
            }
            isCooking -> {
                AutopsyReport(
                    primaryCategory = "Culinary Arts & Recipes",
                    subCategory = "Home Cooking Guides",
                    microNiche = "Accessible Quick Dinner Recipes",
                    contentIdentity = "Delicious Comfort Food Recipes Made in Under 10 Minutes",
                    topicsCovered = "Step-by-step easy meal ideas, cooking hacks, and appetizing comfort food preparations.",
                    bestVideoFormat = "A fast-paced view, jumping immediately into bubbling pans with high-quality sizzle sounds within 3 seconds.",
                    languageCulture = "Friendly, enthusiastic recipe commentary in localized cultural language with bold ingredient overlays.",
                    coreDemographic = "Food lovers, busy students, and home cooks aged 18-35 wanting easy kitchen shortcuts.",
                    
                    viralTitleStructures = "Dish Name + 'SECRET' or 'Better Than Takeout' + Time Limit Hook",
                    viralEmotionalTriggers = "Craving, culinary curiosity, low-effort high-satisfaction kitchen payoffs, and confidence.",
                    viralThumbnailStyles = "A close-up macro view of the finished dish with rich color saturation, steam rise trails, and a cheese pull or gravy drip in mid-air.",
                    viralDescriptionPatterns = "Exact numeric list of ingredients, measurements, substitutions, and affiliate kitchen tool links.",
                    viralTagsDiscovery = "Dish name variations, dynamic recipe tags, and specific seasonal kitchen keywords.",
                    
                    titleCritique = "The failed title '${video.title}' reads too much like a rigid chemical recipe journal rather than an enticing culinary experience. It needs active craving-driven vocabulary and simple time assertions.",
                    alternateTitles = listOf(
                        AlternateTitleItem(suggestedTitle = "The secret recipe trick to make your dish BETTER than restaurants!", inspiredBy = "Inspired by: ${report.analysis.bestVideos.getOrNull(0)?.title ?: "Own Channel's Best"}"),
                        AlternateTitleItem(suggestedTitle = "This 10-Minute Comfort Food Recipe Is A Pure Game Changer", inspiredBy = "Inspired by: ${report.analysis.competitors.getOrNull(0)?.name ?: "Niche Competitors"}"),
                        AlternateTitleItem(suggestedTitle = "Why your homemade meals taste BLAND (Avoid this 1 critical mistake)", inspiredBy = "Inspired by: ${report.analysis.bestVideos.getOrNull(1)?.title ?: "Own Top Recipe"}"),
                        AlternateTitleItem(suggestedTitle = "I recreated the most viral street food recipe in 5 minutes!", inspiredBy = "Inspired by: ${report.analysis.competitors.getOrNull(1)?.name ?: "Epicure Dinner"}"),
                        AlternateTitleItem(suggestedTitle = "Easy 3-Ingredient Dinner recipe that anyone can cook under $5", inspiredBy = "Inspired by: ${report.analysis.competitors.getOrNull(2)?.name ?: "Budget Chef"}")
                    ),
                    thumbnailCritique = "The food looks slightly dull and lacks professional lighting contrast. It is missing extreme close-up details that trigger olfactory and appetizing impulses.",
                    thumbnailFixes = "1. Boost saturation on primary colors (reds, yellows, greens) to make ingredients look vibrant.\n2. Shoot a macro detail (e.g., cheese pull, sauce drizzle) in the middle third.\n3. Add minimalist bold text labels framing the dish with high-contrast text outlines.",
                    descriptionCritique = "Underserving SEO metrics. Missing the complete index list of exact kitchen ingredients and quick-jump chapter markers.",
                    recommendedDescription = """
🔥 The absolute ultimate comfort food recipe is here! Make this in under 10 minutes with normal pantry ingredients.

📝 FULL INGREDIENT LIST & MEASUREMENTS:
- ingredient 1 (2 tsp)
- ingredient 2 (1 cup)
- secret spice mix (1 tbsp)

Timestamps:
0:00 - The ultimate mouthwatering finished look
0:50 - Prep work & pan secrets
3:15 - Incorporating the secret spice
5:30 - Plating design and sensory test

If you are hungry for more daily cooking hacks, subscribe and join the foodie family! 🍕
                    """.trimIndent(),
                    tagsCritique = "Missing relevant food search terms. Generic tags like 'cooking' or 'recipe' do not capture specific ingredient intent waves.",
                    suggestedTags = listOf(
                        "#HomeCooking", "#QuickRecipe", "#ComfortFood", "#EasyDinner",
                        "#10MinMeals", "#SecretRecipe", "#YummyFood", "#BudgetCooking",
                        "#FoodieHacks", "#GourmetEasy", "#VegFood", "#Appetizer"
                    )
                )
            }
            isFitness -> {
                AutopsyReport(
                    primaryCategory = "Health & Sports Science",
                    subCategory = "Aesthetic Physical Fitness",
                    microNiche = "Efficient Science-Based Workouts",
                    contentIdentity = "Maximum Muscle Building Guides and Posture Reps",
                    topicsCovered = "No-bull bio-mechanics, routine form repair guides, and clean muscle targets.",
                    bestVideoFormat = "A direct, motivating speaker displaying muscle execution lines on screen with active progress markers.",
                    languageCulture = "High urgency, energetic instruction paired with precise scientific facts and rep numbers.",
                    coreDemographic = "Athletes, intermediate gymgoers, and desk workers wanting to look fit and stay injury-free.",
                    
                    viralTitleStructures = "Anatomical Target + 'Only X Exercises' + Structural Warning Hook",
                    viralEmotionalTriggers = "Body insecurity, scientific confidence, routine efficiency, and motivating challenge vibes.",
                    viralThumbnailStyles = "Anatomical split-screen comparison showing 'Wrong' versus 'Right', featuring bright yellow accent arrows and a highly pump-saturated muscle cutout.",
                    viralDescriptionPatterns = "Complete workbook workout log, recommended set/rep counts, recovery routine timelines, and supplements checklist.",
                    viralTagsDiscovery = "Target muscle descriptors, scientific hypertrophy tags, and localized gym terms.",
                    
                    titleCritique = "The failed title '${video.title}' lacks immediate physical stakes. It reads like a passive anatomical textbook instead of a targeted physical repair blueprint.",
                    alternateTitles = listOf(
                        AlternateTitleItem(suggestedTitle = "The ONLY 3 Exercises you need for a wider target muscle", inspiredBy = "Inspired by: ${report.analysis.bestVideos.getOrNull(0)?.title ?: "Own Channel's Hits"}"),
                        AlternateTitleItem(suggestedTitle = "Avoid this 1 critical workout mistake that ruins your posture!", inspiredBy = "Inspired by: ${report.analysis.competitors.getOrNull(0)?.name ?: "Niche Competitors"}"),
                        AlternateTitleItem(suggestedTitle = "How I scale physical strength in 30 days (Science-Based)", inspiredBy = "Inspired by: ${report.analysis.bestVideos.getOrNull(1)?.title ?: "Own Best Series"}"),
                        AlternateTitleItem(suggestedTitle = "Stop doing this lift until you watch this form repair guide!", inspiredBy = "Inspired by: ${report.analysis.competitors.getOrNull(1)?.name ?: "Lean Gym"}"),
                        AlternateTitleItem(suggestedTitle = "The ultimate 5-minute daily routine to fix joint stiffness", inspiredBy = "Inspired by: ${report.analysis.competitors.getOrNull(2)?.name ?: "BioMechanics Coach"}")
                    ),
                    thumbnailCritique = "Missing clear visual anatomical contrasts. It lacks yellow outline pointers which are essential to index form correction concepts on YouTube explore feeds.",
                    thumbnailFixes = "1. Add a high-contrast side-by-side split screen showing 'WRONG vs RIGHT' form highlights.\n2. Boost red/yellow color bars to emphasize muscle heat-map active zones.\n3. Drop background details using lens-blur to frame your muscle cutout perfectly.",
                    descriptionCritique = "Lacks detailed workout rep outlines and key scientific study citations to establish strong search authority.",
                    recommendedDescription = """
🔥 Stop guessing and start building smart! This is the science-based anatomical guide you've been waiting for.

📋 THE FULL WORKOUT ROUTINE LOG:
💪 Exercise 1: 3 Sets x 10 Reps (45s rest)
💪 Exercise 2: 4 Sets x 12 Reps (Slow eccentric)
💪 Exercise 3: 3 Rest-Pause Sets to failure

Timestamps:
0:00 - The science of target muscle growth
1:20 - The critical mistake ruins your posture
4:30 - Step-by-step form execution walkthrough
7:15 - Nutritional recovery & reps summary

If you want to unlock scientific muscle repairs, subscribe and level up your body! 🏋️
                    """.trimIndent(),
                    tagsCritique = "Using general fitness tags which dilutes recommendations. Need targeted muscle and execution intent keywords.",
                    suggestedTags = listOf(
                        "#GymWorkout", "#MuscleHypertrophy", "#ScienceBasedTraining", "#WorkoutRoutine",
                        "#BodyTransformation", "#FixYourForm", "#StrengthRoutine", "#PostureCorrection",
                        "#FitnessTips", "#LiftingSecrets", "#AthleticHypertrophy", "#FitGuides"
                    )
                )
            }
            isGaming -> {
                AutopsyReport(
                    primaryCategory = "Gaming & Esports",
                    subCategory = "Competitive Gaming Guides",
                    microNiche = "Game Mechanic Exploits",
                    contentIdentity = "How to Master Game Exploits and Level Up Fast",
                    topicsCovered = "Speedruns, game mechanics, meta exploits, and setup guides.",
                    bestVideoFormat = "A rapid-paced desktop review showcasing live, highly entertaining gaming stunts of high difficulty.",
                    languageCulture = "Young, high-energy gaming slang combined with dramatic overlay text screens.",
                    coreDemographic = "Active competitive gamers and software enthusiasts aged 13-28.",
                    
                    viralTitleStructures = "Game Title + 'EXPLOIT' or 'Speedrun Record' + Shock Factor Hook",
                    viralEmotionalTriggers = "Overcoming challenges, extreme skill envy, hidden game knowledge, and laughter.",
                    viralThumbnailStyles = "Ultra-bright saturated in-game screenshots with large expressive faces, red circles, and neon borders framing the exploit visual.",
                    viralDescriptionPatterns = "Sponsor overlays, PC hardware configs, discord server directs, and patch notes analysis.",
                    viralTagsDiscovery = "Specific game title tags, platform tags, and exploit meta tags.",
                    
                    titleCritique = "The failed title '${video.title}' is too passive and fails to highlight the unique game mechanic or exploit in play. It needs standard clickbait gaming curiosity triggers.",
                    alternateTitles = listOf(
                        AlternateTitleItem(suggestedTitle = "I found the ultimate exploit in this game... (Banned immediately)", inspiredBy = "Inspired by: ${report.analysis.bestVideos.getOrNull(0)?.title ?: "Own Channel's Hits"}"),
                        AlternateTitleItem(suggestedTitle = "How I speedran this level in record time (Perfect Mechanics)", inspiredBy = "Inspired by: ${report.analysis.competitors.getOrNull(0)?.name ?: "Gaming Rivals"}"),
                        AlternateTitleItem(suggestedTitle = "Stop upgrading this item until you watch this review!", inspiredBy = "Inspired by: ${report.analysis.bestVideos.getOrNull(1)?.title ?: "Own Top Release"}"),
                        AlternateTitleItem(suggestedTitle = "This cheat completely breaks the game's final boss", inspiredBy = "Inspired by: ${report.analysis.competitors.getOrNull(1)?.name ?: "Meta Runners"}"),
                        AlternateTitleItem(suggestedTitle = "The lazy gamestate hack that everyone is using right now", inspiredBy = "Inspired by: ${report.analysis.competitors.getOrNull(2)?.name ?: "Pro Gamers"}")
                    ),
                    thumbnailCritique = "The gameplay screenshot is way too dark and cluttered, blending into YouTube's feed. Viewer eyes cannot trace the game action or facial reaction instantly.",
                    thumbnailFixes = "1. Overlay high-contrast neon green arrows pointing directly at the game exploit element.\n2. Scale character cutout size by 150% and apply a high-contrast white outline.\n3. Settle on a dark background behind characters to create maximum depth of field.",
                    descriptionCritique = "Underutilizing standard PC specs and Discord social community links that algorithmic crawlers use to group interest niches.",
                    recommendedDescription = """
  🎮 Recreating the absolute hardest game challenge in patch 1.2! This is how to master the mechanic.

  🔗 JOIN THE DISCORD CHANNEL FOR COMMUNITY SESSIONS:
  🔗 https://discord.com/invite/growtube-gamers

  Timestamps:
  0:00 - The epic exploit run showcase
  1:20 - Character hardware & settings check
  3:15 - Step-by-step mechanical setup breakdown
  7:40 - Running the gauntlet test

  If you want to control the gaming meta, subscribe and smash that like button! 🚀
                      """.trimIndent(),
                    tagsCritique = "Missing the core game title strings and specific platform specifications to trigger recommender crawler matches.",
                    suggestedTags = listOf(
                        "#GamingExploits", "#GameSpeedrun", "#ProGameplay", "#MetaReview",
                        "#GamingSlang", "#LiveSteamHighlights", "#PlatformGamer", "#EsportsMeta",
                        "#HardestChallenge", "#GuideWalkthrough", "#LevelUpFast", "#RetroGaming"
                    )
                )
            }
            else -> {
                AutopsyReport(
                    primaryCategory = "Software Engineering & Tech",
                    subCategory = "Modern Android App Development",
                    microNiche = "Jetpack Compose & Kotlin Architecture",
                    contentIdentity = "High-Quality Modular Android & Developer Blueprints",
                    topicsCovered = "Writing clean, performant Kotlin/Compose architecture, system designs, and studio toolkits.",
                    bestVideoFormat = "A beautifully recorded desktop workflow showcasing beautiful UI designs with zero fluff.",
                    languageCulture = "Clear, articulate software jargon paired with elegant Space Grotesk/JetBrains Mono visual alignments.",
                    coreDemographic = "Full-stack mobile developers, software architectural students, and freelance app designers aged 18-35.",
                    
                    viralTitleStructures = "Subject Matter + 'Avoid This Mistake' or 'In 24 Hours' + Professional Scale Hook",
                    viralEmotionalTriggers = "Unlocking hidden professional patterns, engineering pride, clean layout aesthetics, and avoiding bugs.",
                    viralThumbnailStyles = "Minimalist slate-colored backdrop with vibrant high-contrast code segments, technology vector logos (Kotlin, Android, Android Studio), and yellow typography lines.",
                    viralDescriptionPatterns = "Direct GitHub repository link, detailed timestamp step markers, system specifications, and documentation links.",
                    viralTagsDiscovery = "Framework tag indicators, development languages, and toolchain keywords.",
                    
                    titleCritique = "The failed title '${video.title}' reads like a flat academic tutorial index entry instead of a professional, curiosity-inducing developer experience. It needs clear structural hooks.",
                    alternateTitles = listOf(
                        AlternateTitleItem(suggestedTitle = "Avoid this 1 Kotlin architecture mistake that ruins performance!", inspiredBy = "Inspired by: ${report.analysis.bestVideos.getOrNull(0)?.title ?: "Own Best Practice Release"}"),
                        AlternateTitleItem(suggestedTitle = "I coded a responsive application in Jetpack Compose... in 24 hours", inspiredBy = "Inspired by: ${report.analysis.competitors.getOrNull(0)?.name ?: "Niche Competitors"}"),
                        AlternateTitleItem(suggestedTitle = "The clean architecture guide they don't want you to know", inspiredBy = "Inspired by: ${report.analysis.bestVideos.getOrNull(1)?.title ?: "Own Top Engineering Walkthrough"}"),
                        AlternateTitleItem(suggestedTitle = "Why senior developers NEVER use global state managers anymore", inspiredBy = "Inspired by: ${report.analysis.competitors.getOrNull(1)?.name ?: "Dev Architect"}"),
                        AlternateTitleItem(suggestedTitle = "9 Jetpack Compose UI hacks that elevate application aesthetics", inspiredBy = "Inspired by: ${report.analysis.competitors.getOrNull(2)?.name ?: "Design Hub"}")
                    ),
                    thumbnailCritique = "The code segments or graphic vectors are too small to read on standard mobile devices, and the layout looks cramped with excessive system screenshot clutter.",
                    thumbnailFixes = "1. Increase code font size/scale and frame it on a clean high-contrast dark IDE card.\n2. Embed high-contrast vector technology logos (Kotlin K or Android head) on the outer third.\n3. Apply a bright neon border (cyan or purple) around key visual blocks to pop from YouTube feeds.",
                    descriptionCritique = "Missing necessary developer resources, complete modular directory trees, and the project source GitHub repository directories.",
                    recommendedDescription = """
🔥 The absolute ultimate guide to mastering architectural layouts is here! Stop writing sloppy boilerplate code.

🔗 GET THE COMPLETE SOURCE CODE ON GITHUB:
🔗 https://github.com/growtube-developer/compose-responsive-starter

Timestamps:
0:00 - The shocking truth about generic tutorials
1:40 - Setting up the foundational modular architecture 
4:15 - Resolving the critical performance lag bugs
8:10 - Ultimate UI response scaling & measurement optimization
12:30 - Final compilation overview & my golden rule

If you're ready to transform your development skills, subscribe and join the code network! 🚀
                    """.trimIndent(),
                    tagsCritique = "Using outdated platform terminology. Missing specific contemporary development framework tags that group developer feeds.",
                    suggestedTags = listOf(
                        "#AndroidStudio", "#KotlinDevelopment", "#JetpackCompose", "#ComposeTutorial",
                        "#SoftwareEngineering", "#CleanArchitecture", "#MobileAppDev", "#CodingGuidelines",
                        "#LearnKotlin", "#DeveloperTips", "#SystemDesign", "#ResponsiveFramework"
                    )
                )
            }
        }
    }

    suspend fun generateLaunchReport(
        topic: String,
        report: FullGrowTubeReport,
        apiKey: String
    ): LaunchReport = withContext(Dispatchers.IO) {
        val stats = report.stats
        val analysis = report.analysis
        
        if (apiKey.isEmpty() || topic.lowercase().contains("demo")) {
            return@withContext generateLocalLaunchFallback(topic, report)
        }

        try {
            val systemInstruction = "You are GrowTube AI, the ultimate world-class YouTube content optimizer and diagnostic engine. " +
                    "You analyze a channel's niche fingerprint and generate hyper-optimized, high-CTR metadata before launching a video."

            val promptText = """
                Generate a complete Pre-Upload Launch optimization blueprint for a video about this topic: "$topic"
                
                CHANNEL NICHE CONTEXT:
                Channel Name: "${stats.name}"
                Channel Handle: "${stats.handle}"
                Niche Fingerprint Category: "${analysis.primaryCategory ?: "Entertainment"}"
                Sub Category: "${analysis.subCategory ?: "General Hub"}"
                Micro Niche: "${analysis.microNiche ?: "Niche"}"
                Audience Profile: "${analysis.audienceProfile}"
                
                CHANNEL'S BEST PERFORMING VIDEOS:
                ${(analysis.bestVideos ?: emptyList()).joinToString("\n") { "- TITLE: ${it.title}" }}
                
                COMPETITOR CHANNELS IN SAME SPACE:
                ${(analysis.competitors ?: emptyList()).joinToString("\n") { "${it.name} (${it.handle})" }}
                
                MANDATORY DEEP AUDIENCE-FIRST DATA ANALYSIS:
                Before generating suggestions, you MUST perform this analysis:
                Step 1 (Channel Audience Profile): What content does this channel's audience watch and engage with? Use the channel's existing video titles and stats to extract their tastes/interests.
                Step 2 (Viral Video Pattern Research): Search your knowledge base for videos/songs in this exact micro-niche that went viral (got disproportionately high views). Analyze their title patterns, thumbnail CTR styles, click triggers, and emotional drivers.
                Step 3 (Cross-Reference): Compare this channel's own top-performing videos with competitor viral hits to identify the exact successful patterns this audience clicks on.
                Step 4 (Audience-First Generation): Based 100% on the audience profile and viral patterns discovered above, generate the following metadata. Do NOT write generic YouTube advice.
                
                You must output a single valid JSON object following this exact schema:
                {
                  "recommendedTitleStyleReason": "Why this specific style is recommended based on the channel's audience demographic in 1-2 sentences",
                  "titleOptions": [
                    {
                      "suggestedTitle": "Title Option (strictly matches viral patterns of this micro-niche)",
                      "ctrScore": 9,
                      "whyItWillWork": "Why this specific audience will click it based on their curiosity triggers",
                      "inspiredBy": "Which specific viral video or competitor channel inspired this title pattern",
                      "whyAudienceClick": "Specific trigger that matches this channel's audience interests"
                    }
                  ],
                  "thumbnailBlueprint": {
                    "visualDescription": "Detailed visual description of how the thumbnail should look based on viral micro-niche thumbnail styles",
                    "colors": "Comma separated color scheme",
                    "emotion": "Dominant emotional mood",
                    "textPlacement": "Where and how to overlay titles",
                    "backgroundStyle": "Background specifications",
                    "faceRecommendation": "Recommendation for face/no face",
                    "referenceNicheStandard": "Reference to what works for top competitors in this specific niche"
                  },
                  "description": {
                    "primaryHook": "First 2 lines: high-impact, deeply engaging hook that this specific audience connects with (must sound human and natural)",
                    "bodyText": "Main body text written in the exact same friendly, conversational tone and style of the channel's existing descriptions (avoid robotic AI slang)",
                    "timestamps": "Timestamps section in placeholder layout (e.g. 0:00 - Intro\n1:30 - Setup)",
                    "socialLinksPlaceholder": "Social profile redirect placeholder template list",
                    "cta": "Call to action asking users to subscribe/comment in the channel's personal voice",
                    "fullText": "The complete compiled ready-to-copy description formatted sequentially with hooks, body, timeline, links, CTAs, AND an appended list of 10 to 15 relevant niche hashtags separated by spaces at the absolute bottom. Must sound natural and human."
                  },
                  "tags": [
                    {
                      "name": "detailed keyword tag (mix of broad, medium, and search phrases that this audience actually searches for)",
                      "searchVolume": "High",
                      "relevanceReason": "Why this specific keyword is relevant to this audience's search habits",
                      "inspirationVideo": "Viral video or competitor channel that leverages this tag"
                    }
                  ],
                  "hashtags": [
                    {
                      "name": "#nichehashtag",
                      "isHighlighted": true
                    }
                  ],
                  "score": {
                    "titleStrength": 18,
                    "thumbnailPotential": 17,
                    "descriptionQuality": 19,
                    "tagsCoverage": 18,
                    "nicheAlignment": 20,
                    "totalScore": 92,
                    "status": "Ready to Launch"
                  }
                }
                
                Additional Rules:
                - Generate exactly 10 titles in the 'titleOptions' list.
                - Generate exactly 20 keyword tags in 'tags'.
                - Generate exactly 15 hashtags in 'hashtags'.
                - Ensure the compiled description is natural, direct, highly human-like, and strictly contains the hashtags block inside the bottom.
                - Output ONLY raw JSON. No markdown blocks like ```json.
                - Do not use backslashes inside normal text.
            """.trimIndent()

            val service = GeminiRetrofitClient.service
            val request = GeminiRequest(
                contents = listOf(GeminiContent(listOf(GeminiPart(promptText)))),
                systemInstruction = GeminiContent(listOf(GeminiPart(systemInstruction))),
                generationConfig = GeminiGenerationConfig(responseMimeType = "application/json", temperature = 0.25f)
            )

            val response = service.generateContent(apiKey = apiKey, request = request)
            val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw Exception("Empty response returned from Gemini.")

            val cleanedJson = responseText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val parsedResult = launchReportAdapter.fromJson(cleanedJson) ?: throw Exception("Failed to parse JSON Launch Report.")

            // Ensure channel details are bound correctly
            parsedResult.copy(channelHandle = stats.handle, topicName = topic, timestamp = System.currentTimeMillis())
        } catch (e: Exception) {
            e.printStackTrace()
            generateLocalLaunchFallback(topic, report)
        }
    }

    suspend fun analyzeUploadedThumbnail(
        imageUriStr: String,
        topic: String,
        report: FullGrowTubeReport,
        apiKey: String
    ): ThumbnailAnalysisResult = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty() || imageUriStr.startsWith("demo_") || topic.lowercase().contains("demo")) {
            return@withContext generateLocalThumbnailAnalysis(topic, report)
        }

        try {
            val promptText = """
                Perform a brutal visual critique of a proposed thumbnail for this YouTube video:
                Topic: "$topic"
                Niche: "${report.analysis.primaryCategory ?: "Entertainment"}"
                Micro Niche: "${report.analysis.microNiche ?: "Niche"}"
                Audience Profile: "${report.analysis.audienceProfile}"
                
                You MUST analyze this thumbnail layout strictly against viral thumbnails in this exact micro-niche (not generic general YouTube rules).
                Compare against actual historical click patterns of this channel's specific audience. Generate a highly critical, hyper-targeted critique evaluating:
                1. What works: Does this visual draft align with what this specific audience actually clicks on and what works in this micro-niche?
                2. What needs to be fixed: Identify bottleneck flaws, layout overlap, or styling issues strictly relative to viral patterns of similar successful videos.
                3. Actionable Fix Suggestions: 3 highly focused design guidelines tailored ONLY to this micro-niche (e.g. font scale, neon outlines, specific elements).
                
                Return a single JSON matching this schema:
                {
                  "predictedCtr": "Predicted CTR as a string, e.g. '8.5%'",
                  "audienceScore": 8,
                  "whatWorks": "Clear sentences of what visual layers operate successfully",
                  "whatNeedsToFix": "Highlighting specific design flaws, color dullness or placement overlap based on niche comparative patterns",
                  "specificFixSuggestions": "3 numbered practical visual instructions (e.g. '1. Fix alignment... \n2. Boost contrast... \n3. Add drop shadow...')"
                }
                
                Output ONLY raw JSON. No markdown tags.
            """.trimIndent()

            val service = GeminiRetrofitClient.service
            val request = GeminiRequest(
                contents = listOf(GeminiContent(listOf(GeminiPart(promptText)))),
                generationConfig = GeminiGenerationConfig(responseMimeType = "application/json", temperature = 0.3f)
            )

            val response = service.generateContent(apiKey = apiKey, request = request)
            val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw Exception("Empty response.")

            val cleanedJson = responseText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            thumbnailAnalysisAdapter.fromJson(cleanedJson) ?: generateLocalThumbnailAnalysis(topic, report)
        } catch (e: Exception) {
            e.printStackTrace()
            generateLocalThumbnailAnalysis(topic, report)
        }
    }

    fun generateLocalLaunchFallback(
        topic: String,
        report: FullGrowTubeReport
    ): LaunchReport {
        val nameLower = report.stats.name.lowercase()
        val allTitles = ((report.stats.recentVideos ?: emptyList()).map { it.title } + topic).joinToString(" ").lowercase()
        
        val isMusic = nameLower.contains("sidhu") || nameLower.contains("moosewala") || nameLower.contains("tribute") ||
                nameLower.contains("music") || nameLower.contains("song") || nameLower.contains("singer") ||
                allTitles.contains("punjabi") || allTitles.contains("song") || allTitles.contains("remix") ||
                allTitles.contains("vocal") || allTitles.contains("moosewala") || allTitles.contains("tribute") ||
                allTitles.contains("music") || allTitles.contains("ai cover") || allTitles.contains("beat")
                
        val isCooking = nameLower.contains("cook") || nameLower.contains("recipe") || nameLower.contains("kitchen") ||
                nameLower.contains("chef") || nameLower.contains("food") || 
                allTitles.contains("cook") || allTitles.contains("recipe") || allTitles.contains("biryani") ||
                allTitles.contains("paneer") || allTitles.contains("kitchen") || allTitles.contains("chef") ||
                allTitles.contains("dish") || allTitles.contains("food") || allTitles.contains("dinner") ||
                allTitles.contains("breakfast") || allTitles.contains("lunch")
                
        val isFitness = nameLower.contains("fit") || nameLower.contains("gym") || nameLower.contains("muscle") ||
                nameLower.contains("workout") || nameLower.contains("health") ||
                allTitles.contains("gym") || allTitles.contains("workout") || allTitles.contains("muscle") ||
                allTitles.contains("cardio") || allTitles.contains("exercise") || allTitles.contains("fitness") ||
                allTitles.contains("abs") || allTitles.contains("bench press") || allTitles.contains("deadlift")
                
        val isTech = nameLower.contains("tech") || nameLower.contains("minimal") || nameLower.contains("code") ||
                nameLower.contains("dev") || nameLower.contains("setup") || nameLower.contains("workspace") ||
                allTitles.contains("tech") || allTitles.contains("setup") || allTitles.contains("mechanical keyboard") ||
                allTitles.contains("developer") || allTitles.contains("coding") || allTitles.contains("kotlin") ||
                allTitles.contains("jetpack") || allTitles.contains("programmer")

        val styleReason: String
        val titles: List<TitleOption>
        val blueprint: ThumbnailBlueprint
        val desc: DescriptionOutput
        val keywords: List<LaunchTag>
        val hashtagsList: List<LaunchHashtag>
        val scoreBreakdown: ScoreBreakdown

        if (isMusic) {
            styleReason = "Based on your regional Punjabi music audience, high-impact cultural emotional keywords mixed with bold english prefixes draw 45% more direct click interactions."
            titles = listOf(
                TitleOption("LEGENDARY VIBE: $topic (Retro Trap Beat Remix)", 9, "High search frequency for 'Trap Beat Remix' combined with trending folk interest.", "Inspired by: Speed Records Remixes", "Captures traditional ears longing for high-energy modern club loops."),
                TitleOption("We Asked AI to Sing $topic in Punjabi (Unreleased Cover)", 10, "Extremely high curiosity hook utilizing unreleased AI simulation branding.", "Inspired by: @AIPunjabiVibe", "Appeals to fans intrigued by speculative AI vocal covers of evergreen hits."),
                TitleOption("$topic - Sidhu Moosewala Tribute | Punjabi Lo-Fi Cover", 9, "Captures everlasting Sidhu Moosewala nostalgia which holds excellent replay value.", "Inspired by: @SidhuFanatics Tribute", "Tears into deep regional respect for the late artist, holding longest average view duration."),
                TitleOption("The Ultimate Punjabi Car Playlist 2026 feat. $topic", 8, "Great compilations alignment targeting long commutes and high-volume background streams.", "Inspired by: Saga Music compilation styles", "Perfect fit for audience's secondary device loop playback in cars and gyms."),
                TitleOption("$topic (Slowed and Reverb Duet) [Extreme Bass Boost]", 8, "Slowed and reverb versions receive 3x higher retention on secondary screen devices.", "Inspired by: @SlowedAndReverbHub", "Captures late-night mood listeners seeking lo-fi emotional background soundscapes."),
                TitleOption("Is this the best Punjabi AI cover of the year? ($topic)", 9, "Challenging question structure that triggers intensive comment section discussions.", "Inspired by: @BeatCraftReviews", "Forces comments from listeners criticizing or praising the high-tech vocal conversion."),
                TitleOption("POV: Cruising Punjab with $topic on full volume", 7, "Aesthetic POV style aligns with trending visual lifestyle Reels and TikTok formats.", "Inspired by: @PunjabAesthetics", "Rides lifestyle-centric culture hooks that trigger high community sharing rates."),
                TitleOption("$topic (Live Acoustic Guitar Version) AI Remix", 8, "Acoustic keyword captures users looking for organic melodic strings.", "Inspired by: @OrganicAcoustics", "Contrast-focused cover designed to draw listeners looking for raw melodic keys."),
                TitleOption("We Remixed $topic under 10 Minutes! (Behind the Beat)", 7, "Behind-the-scenes content builds deep educational trust with aspiring beatmakers.", "Inspired by: @BeatStarsAcademy", "Appeals to home music producers seeking to see technical stem arrangements."),
                TitleOption("$topic (Unreleased Vocal Stems Demo Clip)", 10, "Using 'Unreleased Vocal Stems' creates extreme urgency and high immediate CTR.", "Inspired by: @StudioLeaksOfficial", "Triggers immediate click impulse due to high exclusivity association with unreleased stems.")
            )
            blueprint = ThumbnailBlueprint(
                "Dramatic ultra-saturated dark portrait of the lead singer framed by glowing warm red/orange neon outlines, set against a classic rural vintage tractor background.",
                "Crimson Red, Jet Black, Soft Neon Orange, High-Contrast White.",
                "Cinematic melancholy, regional pride, deep artistic nostalgia.",
                "Bold, large Punjabi text overlay in the top-third: '$topic' with neon stroke. Minimal English text details.",
                "Heavy ambient shadow drop overlays, shallow depth-of-field background blur of crop fields.",
                "High recommendation for a close-up expressive face containing high motion grain.",
                "Niche standard creators like regional music studios utilize glowing warm filters which increase average click-through rate by 3.5% globally."
            )
            desc = DescriptionOutput(
                "🔥 The unreleased ultimate beat cover for '$topic' is finally here! Experience the absolute depth of Punjabi regional fusion.",
                "This project brings a modern high-fidelity tribute cover, fine-tuned over several hours to ensure pristine sound, heavy bass lines, and unmatched vocal frequency alignments.\n\nSupport original creators by buying streams, following licenses, and liking this video.",
                "0:00 - Master Sound Preview\n1:00 - High Bass Intro Drop\n3:20 - Melodic Verse Bridge\n5:45 - High-Energy Dynamic Outro",
                "🎵 Spotify Playlist: https://spotify.com/regional-punjabi-lofi\n📸 Instagram: @GrowTubeTributes\n📦 Sound Stems & Kits: https://kit.co/punjab-beats",
                "If this lo-fi beat hit your soul, smash that subscribe button, hit share, and leave your favourite lyrics in the comments below! 🚀",
                "🔥 The unreleased ultimate beat cover for '$topic' is finally here! Experience the absolute depth of Punjabi regional fusion.\n\nThis project brings a digital high-fidelity tribute cover, fine-tuned over several hours to ensure pristine sound, heavy bass lines, and unmatched vocal frequency alignments.\n\nTimestamps:\n0:00 - Master Sound Preview\n1:00 - High Bass Intro Drop\n3:20 - Melodic Verse Bridge\n5:45 - High-Energy Dynamic Outro\n\n🌐 Socials & Gear:\n🎵 Spotify Playlist: https://spotify.com/regional-punjabi-lofi\n📸 Instagram: @GrowTubeTributes\n📦 Sound Stems & Kits: https://kit.co/punjab-beats\n\nIf this lo-fi beat hit your soul, smash that subscribe button, hit share, and leave your favourite lyrics in the comments! 🚀\n\n#punjabisong #aicover #lofibexts #sidhufan #remixlife #unreleased #caraudio #beatmaker #regionalvibes #punjabimusic #trapbeats"
            )
            keywords = listOf(
                LaunchTag("punjabi songs 2026", "High", "Aligned with active regional searches.", "Inspired by competitor: Speed Records"),
                LaunchTag("punjabi mix", "High", "Matches car commute search queries.", "Inspired by competitor: Saga Music"),
                LaunchTag("sidhu tribute", "High", "Captures high nostalgia-driven queries.", "Inspired by competitor: TributeHub"),
                LaunchTag("ai vocal cover", "High", "Captures trending AI song searches.", "Inspired by competitor: AIStudioOfficial"),
                LaunchTag("trap beat remix", "High", "Appeals to modern electronic remix loops.", "Inspired by competitor: TrapBeatPunjab"),
                LaunchTag("unreleased track remix", "Medium", "Curiosity keywords with low supply.", "Inspired by competitor: LeakStudio"),
                LaunchTag("punjabi lofi", "Medium", "Hits late-night chill playlists.", "Inspired by competitor: LofiPunjab"),
                LaunchTag("bass boosted punjabi", "Medium", "High target for car audio tests.", "Inspired by competitor: BassBoostChannel"),
                LaunchTag("car playlist punjabi", "Medium", "Hits road trip search results.", "Inspired by competitor: CarCommuteTracks"),
                LaunchTag("sidhu AI sound", "Medium", "Niche trend targeting sound design.", "Inspired by competitor: @VoiceSynthGuy"),
                LaunchTag("satisfying beat", "Medium", "Broad electronic music indexing.", "Inspired by competitor: SatisfyingBeats"),
                LaunchTag("punjabi lyrics status", "Low", "Social media sharing utility keys.", "Inspired by competitor: StatusKings"),
                LaunchTag("regional music blending", "Low", "Conceptual tags grouping genre index.", "Inspired by competitor: GenreBenders"),
                LaunchTag("sad punjabi songs lofi", "Low", "Emotion categorization tag search.", "Inspired by competitor: SadVibesOnly"),
                LaunchTag("punjabi mashup track", "Low", "Traditional compilations term.", "Inspired by competitor: MashupFactory"),
                LaunchTag("instrumental stems music", "Low", "Targeting beatmakers and DJs directly.", "Inspired by competitor: StemVibe"),
                LaunchTag("new punjabi single", "Low", "Generic release index identifier.", "Inspired by competitor: SingleReleases"),
                LaunchTag("ai vocal fine tuning", "Low", "Attracts technical AI enthusiasts.", "Inspired by competitor: AIResonanceClub"),
                LaunchTag("traditional sound fusion", "Low", "Index tier tag for World genre listings.", "Inspired by competitor: FusionStudio"),
                LaunchTag("underground punjabi remix", "Low", "Appeals to indie subculture hunters.", "Inspired by competitor: UndergroundVibes")
            )
            hashtagsList = listOf(
                LaunchHashtag("#punjabisong", true), LaunchHashtag("#aicover", true), LaunchHashtag("#lofibexts", true),
                LaunchHashtag("#sidhufan", false), LaunchHashtag("#remixlife", false), LaunchHashtag("#unreleased", false),
                LaunchHashtag("#caraudio", false), LaunchHashtag("#beatmaker", false), LaunchHashtag("#regionalvibes", false),
                LaunchHashtag("#punjabimusic", false), LaunchHashtag("#trapbeats", false), LaunchHashtag("#soundstems", false),
                LaunchHashtag("#acousticremix", false), LaunchHashtag("#nostalgiabeat", false), LaunchHashtag("#growtubemusic", false)
            )
            scoreBreakdown = ScoreBreakdown(18, 17, 19, 18, 20, 92, "High Audience Fit")
        } else if (isCooking) {
            styleReason = "Based on your culinary audience, highly appetizing macro shots combined with quick time indicators or portion hacks draw the fastest casual dinner-seeking crowds."
            titles = listOf(
                TitleOption("The absolute easiest key to cooking $topic! (15-Min Quick Recipe)", 10, "Leverages severe speed convenience with a strong visual culinary hook.", "Inspired by: @Tasty 15-Min Meals", "Hits busy weeknight home-cooks looking for instant delicious solutions."),
                TitleOption("I made the viral $topic at home... and it's actually 10x better!", 9, "Rides on trending recipe copycats that users are actively searching to make.", "Inspired by: Joshua Weissman copycat styles", "Provokes viewers looking to verify if copycats can beat famous restaurants."),
                TitleOption("3 Simple Mistakes ruining your homemade $topic recipe!", 10, "Negative-interest fear hooks trigger curiosity and immediate tap response.", "Inspired by: Babish mistake series", "Triggers caution in foodies wanting to preserve their culinary ingredients."),
                TitleOption("Restaurant-Style $topic made with only 4 ingredients", 9, "Simplifies the complex cooking process which appeals heavily to busy beginners.", "Inspired by: @TastySimplified", "Lower standard entry barriers for college students or single parents."),
                TitleOption("How to cook perfect, crispy $topic in 5 smart steps", 8, "Structured, numbered approach ensures excellent, step-by-step trust.", "Inspired by: Giallozafferano tutorials", "Attracts logical viewers eager for a reliable masterclass process."),
                TitleOption("What happens when you add this 1 secret ingredient to $topic?", 9, "Classic curiosity gap title which guarantees a high search CTR.", "Inspired by: Chef Wang secret hacks", "Forces immediate interest from culinary experimenters hoping to elevate flavor."),
                TitleOption("The ultimate comfort food: ultimate $topic style guide", 8, "Comfort food keywords perform and rank exceptionally well around dinner times.", "Inspired by: NYT Cooking comfort playlists", "Targets high-resonance evening dinner decisions and family gatherings."),
                TitleOption("Better than takeout? Learn how to recreate $topic", 8, "Takeout replacement comparisons drive high action-oriented search interest.", "Inspired by: Sam The Cooking Guy", "Appeals directly to budget-conscious foodies wanting to save delivery fees."),
                TitleOption("Stop buying expensive $topic! Cook it under $5 instead", 9, "Smart budget-saving angle drives viral social shares across Pinterest and Reddit.", "Inspired by: @StruggleMeals", "Leverages cost-of-living stresses to drive heavy viral click velocity."),
                TitleOption("POV: Baking the fluffiest, most satisfying $topic from scratch", 7, "ASMR, tactile baking POV formats hold ultra-high visual retention rates.", "Inspired by: @ASMRKitchen", "Hits tactile, atmospheric lovers looking for satisfying culinary relaxation.")
            )
            blueprint = ThumbnailBlueprint(
                "Bright, color-balanced extreme macro close-up of $topic boasting visible steam currents, vibrant fresh green herb garnishes, and soft warm lighting.",
                "Golden Amber, Herb Green, Crisp White background, Soft Brown.",
                "Mouthwatering premium taste, extreme freshness, easy accessibility.",
                "Bold contrast text on the right: 'EASY 15-MIN' in bright yellow with thick drop shadow. No visual clutter.",
                "Soft country kitchen backdrop with clean white countertops, heavily blurred.",
                "No face needed—focus purely on a fork lifting a perfect, melting portion.",
                "Top food channels like Binging with Babish utilize tight, warm-grade overhead angles to maximize organic click-through rates by 4.2%."
            )
            desc = DescriptionOutput(
                "🍳 Learn how to make the ultimate restaurant-quality $topic right in your own kitchen! Crisp details, no fluff.",
                "This step-by-step tutorial reveals the secret techniques to achieving perfect textures, rich sauces, and incredible flavor balances without spent hours standing in a hot kitchen. Perfect for quick weeknight family dinners or elegant weekend cooking displays.",
                "0:00 - The Secret Preview\n1:10 - Ingredient Preparation & Hacks\n3:15 - Perfect Cooking Temperature Control\n5:40 - Garnishes and Table Dressing\n7:10 - Absolute Taste Test & Summary",
                "🌐 Download my full Recipe book: https://recipes.growtube-cooking.demo\n📸 Instagram: @GrowTubeKitchen\n📦 Organic Spices & Cookware gear: https://kit.co/kitchen-gear",
                "If this delicious plate made your mouth water, subscribe, hit the bell, and tell me: what recipe should we make next? 🍽️",
                "🍳 Learn how to make the ultimate restaurant-quality $topic right in your own kitchen! Crisp details, no fluff.\n\nThis step-by-step tutorial reveals the secret techniques to achieving perfect textures, rich sauces, and incredible flavor balances. Perfect for quick weeknight family dinners.\n\nTimestamps:\n0:00 - The Secret Preview\n1:10 - Ingredient Prep Hacks\n3:15 - Perfect Temp Control\n5:40 - Garnishes & Plate Dressing\n7:10 - Taste Test & Outro\n\n🌐 Socials & Gear:\n🌐 Download Recipe: https://recipes.growtube-cooking.demo\n📸 Instagram: @GrowTubeKitchen\n📦 Cookware gear: https://kit.co/kitchen-gear\n\nIf this delicious plate made your mouth water, subscribe, hit the bell, and tell me: what recipe should we make next? 🍽️\n\n#easycooking #tastyrecipe #comfortfood #copycatrecipe #viralcooking #dinnerhacks #budgetmeal #quickrecipes #foodlovers"
            )
            keywords = listOf(
                LaunchTag("how to cook $topic", "High", "Core search query for recipe seekers.", "Inspired by: Tasty Recipes"),
                LaunchTag("easy $topic recipe", "High", "Captures fast cooking intent.", "Inspired by: Food Network"),
                LaunchTag("cooking hacks", "High", "Highly shareable kitchen hacks.", "Inspired by: Chef Hacks"),
                LaunchTag("quick dinner ideas", "High", "Hits daily dinner decision grids.", "Inspired by: Quick Dinner Hub"),
                LaunchTag("homemade recipe steps", "Medium", "Great informational targeting.", "Inspired by: Simple Home Cooking"),
                LaunchTag("15 minute meals", "Medium", "Captures urgent convenience seekers.", "Inspired by: Jamie Oliver 15Min"),
                LaunchTag("vegan friendly options", "Medium", "Appeals to diet niches.", "Inspired by: VeganCooks"),
                LaunchTag("restaurant copycat recipe", "Medium", "High target for copycat searchers.", "Inspired by: CopycatChef"),
                LaunchTag("healthy meal prep", "Medium", "Addresses lifestyle prep plans.", "Inspired by: FitFoods"),
                LaunchTag("budget dinner options", "Medium", "Leverages cost savings search.", "Inspired by: CheapEats"),
                LaunchTag("comfort food prep", "Medium", "High resonance cold season search.", "Inspired by: CozyKitchen"),
                LaunchTag("easy cooking techniques", "Low", "Underpinning skill search tag.", "Inspired by: CulinaryAcademy"),
                LaunchTag("culinary knife skills", "Low", "Niche technique targeting.", "Inspired by: ProKnifeSkills"),
                LaunchTag("oven free desserts", "Low", "No bake searches index.", "Inspired by: NoBakeClub"),
                LaunchTag("low calorie dinner", "Low", "Weight-loss adjacent search.", "Inspired by: CalorieCounter"),
                LaunchTag("viral food hacks", "Low", "Rides social media trends.", "Inspired by: FoodTikTokTrends"),
                LaunchTag("gourmet standard hacks", "Low", "Premium culinary keyword.", "Inspired by: GourmetHome"),
                LaunchTag("spicy recipes tasty", "Low", "Taste preference targeting.", "Inspired by: SpicyBites"),
                LaunchTag("traditional comfort meal", "Low", "Nostalgic family recipe tags.", "Inspired by: GrandmaStyle"),
                LaunchTag("quick homemade snack", "Low", "Micro meal convenience.", "Inspired by: EasySnacks")
            )
            hashtagsList = listOf(
                LaunchHashtag("#easycooking", true), LaunchHashtag("#tastyrecipe", true), LaunchHashtag("#comfortfood", true),
                LaunchHashtag("#copycatrecipe", false), LaunchHashtag("#viralcooking", false), LaunchHashtag("#dinnerhacks", false),
                LaunchHashtag("#budgetmeal", false), LaunchHashtag("#quickrecipes", false), LaunchHashtag("#foodlovers", false),
                LaunchHashtag("#chefstandards", false), LaunchHashtag("#veganoptions", false), LaunchHashtag("#mealprepideas", false),
                LaunchHashtag("#kitchenhacks", false), LaunchHashtag("#deliciousdish", false), LaunchHashtag("#growtubecook", false)
            )
            scoreBreakdown = ScoreBreakdown(19, 18, 17, 19, 18, 91, "High Audience Fit")
        } else if (isFitness) {
            styleReason = "Based on your fitness audience, motivational body transformation targets combined with child-safe mistake-fixes or posture corrections drive the highest search engagement."
            titles = listOf(
                TitleOption("Stop doing $topic like this! (Critical Injury Mistake)", 10, "Negative-hook fear of injury causes instant clicks from safety-focused lifters.", "Inspired by: Athlean-X posture guides", "Touches security motivation triggers, urging instant clicks to avoid joint wear."),
                TitleOption("How to optimize $topic for maximum muscle growth!", 10, "Focuses purely on high-value results which targets hard-working hyper-growers.", "Inspired by: Jeff Nippard science style", "Appeals to analytical lifters eager to optimize kinetic tension of the muscle."),
                TitleOption("3 simple tweaks to double your strength in $topic", 9, "Promises immediate double strength gains which lifters can apply today.", "Inspired by: Squat University form corrections", "Promises minor effort for massive compounding returns on lift numbers."),
                TitleOption("I tried the ultimate $topic routine for 30 consecutive days", 9, "Transformation journey format holds excellent long-term retention rates.", "Inspired by: Buzzfeed transformation logs", "Taps human desire for visual comparison of long-term physique change."),
                TitleOption("The smartest anatomical way to perform $topic", 8, "Scientific, anatomical wording appeals to modern expert research lifters.", "Inspired by: Dr. Mike Israetel guides", "Targets muscle biomechanical nerds searching for flawless muscle activation."),
                TitleOption("A beginner's mistake-free roadmap to perfect $topic", 8, "Friendly, beginner-focused guide structure lowers the barrier to entry.", "Inspired by: @CalisthenicsMovement", "Reduces intimidation factors for people taking their initial gym steps."),
                TitleOption("Why you aren't growing from $topic (Postural fixes!)", 9, "Addresses underlying stagnation pain-points directly in the title tag.", "Inspired by: Renaissance Periodization", "Focuses on frustrating plateau limits that users actively search to fix."),
                TitleOption("Minimalist gym setup to master professional $topic form", 8, "Appeals to home gym or garage lifters looking for simple setup equipment.", "Inspired by: HomeGymReviews", "Captures local garage-gym designers optimizing their structural space."),
                TitleOption("The 5-minute gym warm-up routine before doing $topic", 7, "Practical, short daily additions rank highly in morning search blocks.", "Inspired by: MoveU mobility tutorials", "High routine repetition value as users play this in the dressing room daily."),
                TitleOption("POV: Train with a pro coach to fix your $topic errors", 8, "POV style places the learner inside the session which increases average view duration.", "Inspired by: @ChrisHeria workout feeds", "Provides immersive feeling of direct coaching help from elite trainers.")
            )
            blueprint = ThumbnailBlueprint(
                "Aggressive, high-shadow athletic gym background. Side-by-side comparison with a red 'X' on a bent posture vs. a green '✓' on a perfectly straight anatomical line.",
                "High-Contrast Lime Green, Danger Red, Charcoal Black, Bright White.",
                "Scientific form fixes, athletic posture corrections, high-energy gym motivation.",
                "Bold, highlighted text overlay: 'DO THIS' (green) and 'NOT THIS' (red) in clean sans-serif typography.",
                "Raw gym environment with iron weights under stark, dramatic overhead spotlights.",
                "Recommend showing a full athletic form in motion from a clear 45-degree angle.",
                "Top bodybuilding channels like Athlean-X use direct marker indicators to guide eyes and score 20% higher organic click count."
            )
            desc = DescriptionOutput(
                "🏋️‍♂️ Stop wasting your time in the gym. Master the absolute correct postural form for '$topic'!",
                "In this comprehensive training guide, we break down the joint kinematics, muscular activation patterns, and standard setup mistakes of '$topic'. Stop making rookie form mistakes that lead to joint pain, and discover how to pack on real dense muscle safely.",
                "0:00 - The Joint Pain Mistake\n1:15 - Proper Joint Angles & Setup\n3:30 - Optimal Muscular Tension Hacks\n5:45 - High-Performance Set Breakdown\n7:55 - Recommended Rep Schemas",
                "💪 Join my Full Gym Training Program: https://fit.growtube-gym.demo\n📸 Instagram: @GrowTubeFitness\n📦 Workout Gear & Supplements: https://kit.co/body-supps",
                "If you want to fix your form and build real explosive power, subscribe and tell me your current personal record in the comments! ⚡",
                "🏋️‍♂️ Stop wasting your time in the gym. Master the absolute correct postural form for '$topic'!\n\nIn this comprehensive training guide, we break down the joint kinematics, muscular activation patterns, and standard setup mistakes of '$topic'. Stop making rookie form mistakes.\n\nTimestamps:\n0:00 - Joint Pain Mistakes\n1:15 - Proper Angle Setup\n3:30 - Optimal Muscular Tension\n5:45 - High-Performance Set Breakdown\n7:55 - Recommended Reps\n\n🌐 Socials & Gear:\n💪 Train Program: https://fit.growtube-gym.demo\n📸 Instagram: @GrowTubeFitness\n📦 Workout Gear: https://kit.co/body-supps\n\nIf you want to fix your form and build real explosive power, subscribe and tell me your current personal record in the comments! ⚡\n\n#fixyourform #gymscience #liftweights #bodybuilding #correctform #injuryfree #gymbeginners #musclegrowth #workoutfit #strengthgains"
            )
            keywords = listOf(
                LaunchTag("correct form for $topic", "High", "Critical safety query for rookie weightlifters.", "Inspired by: Squat University"),
                LaunchTag("workout routines $topic", "High", "General routine indexer.", "Inspired by: BodybuildingCom"),
                LaunchTag("injury prevention gym", "High", "Safety motivation focus.", "Inspired by: Athlean-X"),
                LaunchTag("muscle growth tips", "High", "Hypertrophy goal searches.", "Inspired by: Jeff Nippard"),
                LaunchTag("hypertrophy exercises", "Medium", "Scientific terminology alignment.", "Inspired by: Renaissance Periodization"),
                LaunchTag("proper workout posture", "Medium", "Kinematic correction indexing.", "Inspired by: @CalisthenicsMovement"),
                LaunchTag("gym beginner mistakes", "Medium", "Lower intimidation keyword.", "Inspired by: GymHelp101"),
                LaunchTag("strength gain hacks", "Medium", "Performance-oriented search term.", "Inspired by: PowerliftingInc"),
                LaunchTag("home gym workout", "Medium", "Garage fitness developers search.", "Inspired by: HomeGymReviews"),
                LaunchTag("posture correction guide", "Medium", "Postural joint strain fixes.", "Inspired by: YogaPhysio"),
                LaunchTag("form tweaks gym", "Medium", "Actionable adjustment lookup.", "Inspired by: WorkoutHacks"),
                LaunchTag("lifting mechanics science", "Low", "Biomechanical research crowd keys.", "Inspired by: KinematicsLab"),
                LaunchTag("abdominal core exercises", "Low", "Core stabilization secondary tag.", "Inspired by: SixPackClub"),
                LaunchTag("full body stretching", "Low", "Flexibility query routing.", "Inspired by: StretchingGuru"),
                LaunchTag("deadlift bench exercises", "Low", "Major lifts classification index.", "Inspired by: PowerLiftingStars"),
                LaunchTag("personal records lifting", "Low", "Ego-driven engagement keyword.", "Inspired by: PRTracker"),
                LaunchTag("aesthetic building guide", "Low", "Physique styling categorization.", "Inspired by: AestheticLifter"),
                LaunchTag("active rehabilitation routine", "Low", "Medical-adjacent recovery keys.", "Inspired by: PhysicalTherapyChannels"),
                LaunchTag("joint strain fixes", "Low", "Pain management lookup.", "Inspired by: BackFixDoctors"),
                LaunchTag("reps and sets routine", "Low", "Structural daily workout keys.", "Inspired by: RoutineArchitect")
            )
            hashtagsList = listOf(
                LaunchHashtag("#fixyourform", true), LaunchHashtag("#gymscience", true), LaunchHashtag("#liftweights", true),
                LaunchHashtag("#bodybuilding", false), LaunchHashtag("#correctform", false), LaunchHashtag("#injuryfree", false),
                LaunchHashtag("#gymbeginners", false), LaunchHashtag("#musclegrowth", false), LaunchHashtag("#workoutfit", false),
                LaunchHashtag("#strengthgains", false), LaunchHashtag("#posturefix", false), LaunchHashtag("#gymmotives", false),
                LaunchHashtag("#repsandsets", false), LaunchHashtag("#trainingsession", false), LaunchHashtag("#growtubefit", false)
            )
            scoreBreakdown = ScoreBreakdown(18, 19, 18, 17, 19, 91, "High Audience Fit")
        } else {
            val isReallyTech = isTech
            styleReason = if (isReallyTech) {
                "Based on your gadget-hungry tech audience, cinematic product handling combined with clean setups or honest spec reviews perform the highest on visual feeds."
            } else {
                "Based on your entertainment-seeking general audience, high-curiosity narratives, interactive storytelling elements, or challenge structures drive the most organic shares."
            }
            titles = if (isReallyTech) {
                listOf(
                    TitleOption("Why senior software engineers NEVER buy $topic anymore!", 10, "Targeting developers with a strong, contrarian developer opinion hook.", "Inspired by: @TechLead videos", "Taps professional curiosity and imposter concerns of self-taught programmers."),
                    TitleOption("I spent 30 days with $topic... and I was totally wrong!", 10, "Honest product reviews utilize curiosity gaps that users trust and search for.", "Inspired by: Marquees Brownlee reviews", "Leverages extreme retrospective interest to see if custom product was solid."),
                    TitleOption("Avoid this 1 critical mistake in your desk setup ($topic)", 9, "Addresses workspace bottlenecks directly, driving high-aesthetic setup clicks.", "Inspired by: Matthew Encina setups", "Triggers immediate concern of visual workspace designers looking for perfection."),
                    TitleOption("The smartest minimal tech setup guide: $topic edition", 9, "Aesthetic minimalist packaging appeals strongly to lifestyle desk enthusiasts.", "Inspired by: Ali Abdaal guides", "Appeals to clean, sleek productivity desk owners who prioritize neat aesthetics."),
                    TitleOption("Before you buy a cheap $topic: Watch this first!", 9, "Saves consumers money, driving high action-oriented buying clicks.", "Inspired by: Linus Tech Tips buyers advice", "Saves money-wasting regrets for modern device buyers searching for budget benchmarks."),
                    TitleOption("$topic review: An absolute game changer or pure AI slop?", 10, "Uses contemporary trending tech slang terms which drives high-relevance CTR.", "Inspired by: @Dave2D style", "Capitalizes on tech skepticism concerning oversaturated AI marketing features."),
                    TitleOption("How to build the ultimate custom $topic setup on a budget", 8, "Smart, budget-saving setups perform exceptionally well across Tech Reddit.", "Inspired by: RandomFrankP reviews", "Appeals to students or young developers doing custom DIY workspace builds."),
                    TitleOption("9 hidden tech features of the brand new $topic", 8, "Detailing easter-tree software parameters builds immense product authority.", "Inspired by: @Mrwhosetheboss lists", "Highlights secret features that power users love to try immediately."),
                    TitleOption("POV: ASMR setup session building the silent $topic", 8, "Acoustic keyboard/tech sound recordings capture massive organic search volume.", "Inspired by: @TaehaTypes ASMR", "Hits tactile, quiet audio-sensory lovers wishing to relax to mechanical sounds."),
                    TitleOption("We unboxed the craziest custom $topic ever designed!", 8, "Extreme custom builds trigger immediate visual and spec-hungry clicks.", "Inspired by: Unbox Therapy channels", "Appeals to tech visual stimulus craving crowds who enjoy watching extreme premium setups.")
                )
            } else {
                listOf(
                    TitleOption("I survived the ultimate $topic challenge! (Brutal!)", 10, "Extreme challenge video hook with high pacing and visual indicators.", "Inspired by: MrBeast challenges", "Satisfies dramatic entertainment cravings with intense real-time progression stakes."),
                    TitleOption("Avoid this 1 devastating mistake when learning $topic!", 10, "Addresses a critical pain point that casual learners face daily.", "Inspired by: @ThomasFrank guides", "Provokes direct self-correction impulses in students trying to learn efficiently."),
                    TitleOption("Before you start studying $topic, watch this absolute guide!", 9, "High authority setup that establishes trust and saves time.", "Inspired by: @CrashCourse series", "Positioned as the absolute starting bookmark, capturing high recurring views."),
                    TitleOption("Why most people fail at $topic in 2026 (How to fix it!)", 9, "Combines demographic relevance, high curiosity, and immediate solution.", "Inspired by: @ImprovementPill strategies", "Drawn from frustration of constant trial-and-error by early learner circles."),
                    TitleOption("$topic vs the world: The ultimate comparison breakdown", 8, "Direct showdown triggers healthy debate and deep comment engagement.", "Inspired by: @ColdFusion comparison docs", "Sets up a direct competitive clash that fires up partisan discussions in comments."),
                    TitleOption("The smartest, fastest method to completely master $topic", 9, "Focuses on speed and efficiency which appeals directly to busy students.", "Inspired by: @TimFerriss methods", "Solves key stress point for busy professionals needing rapid technical skill mastery."),
                    TitleOption("9 secrets about $topic that modern professionals hide!", 8, "Classic curiosity hook targeting exclusive, unrevealed insider secrets.", "Inspired by: @VoxExplains reports", "Offers exclusive professional secrets that users feel they shouldn't miss out on."),
                    TitleOption("POV: Inside a day in the life mastering $topic", 7, "Immersive, narrative format creates close-knit viewer relationships.", "Inspired by: @JomaTech lifestyle vlogs", "Combines high visual comfort with aspirational career roleplay aesthetics."),
                    TitleOption("I tested 10 different $topic hacks to find the best one", 8, "Scientific trial layout appeals to analytical, curious audiences.", "Inspired by: @Veritasium experiments", "Appeals to inquisitive logical minds wanting proof before trying any trick."),
                    TitleOption("Is $topic really worth the absolute hype? (Honest review)", 9, "Pragmatic, honest title format reduces user buyer-hesitation.", "Inspired by: @SaraDietschy specs", "Offers a secure, calm sanctuary of neutral truth amidst extreme market branding hype.")
                )
            }
            blueprint = if (isReallyTech) {
                ThumbnailBlueprint(
                    "Cinematic, moody desk setup background with a key product shot of $topic reflecting warm ambient neon orange led lights, shot from a low 15-degree angle.",
                    "Deep Space Black, Aesthetic Amber Orange, Soft Studio White.",
                    "Premium hardware satisfaction, sleek futuristic workspace, clean minimalist vibes.",
                    "Clean sans-serif text overlay in the top-left corner: 'UNBIASED REVIEW' with soft amber glow. No clutter.",
                    "Minimal desktop setup featuring clean wooden textures, heavily blurred with a shallow depth-of-field lens.",
                    "No human face needed—clean, close-up product handling showcase in sharp resolution.",
                    "Top setup channels like MKBHD or Matthew Encina utilize sharp key lighting on the central item to score 18% higher clicks."
                )
            } else {
                ThumbnailBlueprint(
                    "Moody, cinematic dark slate background with a prominent glowing icon of $topic, flanked by dynamic neon lines pointing to a bold focal element.",
                    "Cosmic Slate, Accent Red, High-Contrast Cyan, Soft Gray.",
                    "Unlocking secrets, premium discovery, high developmental vibes.",
                    "Clean display heading text: 'MASTER IT' in neon outline placed in the bottom-third.",
                    "Elegant ambient card backdrop featuring subtle gradient vector shadows, softly blurred.",
                    "Soft illustrative vector or clean product face holding high conceptual curiosity.",
                    "Premium developers and creators utilize generous negative space around bold headings to increase visual scanning by 25%."
                )
            }
            desc = if (isReallyTech) {
                DescriptionOutput(
                    "💻 No sponsors. No biased specs. Meet the absolute honest review and setup of the brand-new '$topic'!",
                    "After spending 30 intensive days putting '$topic' through our daily software engineering and creative workstation tasks, we detail the real hardware bottlenecks, ergonomic adjustments, and productivity improvements. Watch our complete breakdown before you commit your hard-earned budget.",
                    "0:00 - True Specs Overview\n1:05 - Hardware Build & Materials Quality\n3:20 - Real Desk Setup Integration Hooks\n5:55 - Everyday Developer Usability Tests\n8:15 - Honest Verdict: Buy It?",
                    "🌐 Access my complete Desk Items Gear: https://gear.growtube-tech.demo\n📸 Instagram: @GrowTubeTech\n📦 Join our Setup Discord Channel: https://discord.gg/growtube-tech",
                    "If you love professional hardware setups and honest, raw reviews, subscribe, tap that notification bell, and leave your daily desk setups below! ⚡",
                    "💻 No sponsors. No biased specs. Meet the absolute honest review and setup of the brand-new '$topic'!\n\nAfter spending 30 intensive days putting '$topic' through our daily software engineering and creative workstation tasks, we detail the real bottlenecks.\n\nTimestamps:\n0:00 - True Specs Overview\n1:05 - Build Quality\n3:20 - Real Setup Integration\n5:55 - Everyday Usability Tests\n8:15 - Honest Verdict\n\n🌐 Socials & Gear:\n🌐 Complete Desk Items: https://gear.growtube-tech.demo\n📸 Instagram: @GrowTubeTech\n📦 Tech Discord: https://discord.gg/growtube-tech\n\nIf you love professional hardware setups and honest, raw reviews, subscribe, tap that notification bell, and leave your daily desk setup below! ⚡\n\n#techreview #desksetup #workspace #unboxinglife #minimalist #setupinspiration #keyboardasmr #developerkit #ergonomics"
                )
            } else {
                DescriptionOutput(
                    "💡 The absolute ultimate guide to mastering '$topic' is finally here! Stop guessing and save yourself hours of struggle.",
                    "In this highly detailed video, we break down the foundational principles, optimal practice routines, and common traps in '$topic' that slow down most beginners. We compile years of experience into a simple, error-free step roadmap.",
                    "0:00 - The Shocking Breakdown\n1:12 - Setting up the Workspace Correctly\n3:15 - Formulating the Master Strategy\n5:30 - Crucial Step Hacks To Speed up\n8:10 - Final Summary & My Golden Rule",
                    "🌐 Download the Complete Strategy Roadmap: https://roadmap.growtube.demo\n📸 Instagram: @GrowTubeStrategy\n📦 Join our Creative Community: https://community.growtube.demo",
                    "If you are ready to master this and achieve clean results fast, subscribe, hit the bell icon, and share this guide with a friend! 🚀",
                    "💡 The absolute ultimate guide to mastering '$topic' is finally here! Stop guessing and save yourself hours of struggle.\n\nIn this highly detailed video, we break down the foundational principles, optimal practice routines, and common traps in '$topic'.\n\nTimestamps:\n0:00 - Shocking Breakdown\n1:12 - Setting up the Workspace\n3:15 - Master Strategy Formulation\n5:30 - Crucial Speed Hacks\n8:10 - Final Summary\n\n🌐 Socials & Gear:\n🌐 Complete Roadmap: https://roadmap.growtube.demo\n📸 Instagram: @GrowTubeStrategy\n📦 Creative Community: https://community.growtube.demo\n\nIf you are ready to master this and achieve clean results fast, subscribe, hit the bell icon, and share this guide with a friend! 🚀\n\n#masterguide #learnfast #tutorialtips #roadmapto #expertadvice #avoidfails #quickhacks #creativelabs"
                )
            }
            keywords = if (isReallyTech) {
                listOf(
                    LaunchTag("unbiased review of $topic", "High", "Critical trust keyword for tech search.", "Inspired by: MKBHD"),
                    LaunchTag("custom $topic setups", "High", "Desk aesthetic setup query.", "Inspired by: RandomFrankP"),
                    LaunchTag("minimalist workspace tips", "High", "High-retention home setup design search.", "Inspired by: Matthew Encina"),
                    LaunchTag("hardware unboxing and testing", "High", "New release specification reviews.", "Inspired by: Unbox Therapy"),
                    LaunchTag("desk setup gear 2026", "Medium", "Compiles tech desk trends.", "Inspired by: Ali Abdaal desk tour"),
                    LaunchTag("before you buy reviews", "Medium", "Buyer reservation checklist keyword.", "Inspired by: Dave2D"),
                    LaunchTag("unreleased developer setups", "Medium", "Tech worker office design setups.", "Inspired by: JomaTech setup tour"),
                    LaunchTag("hidden software features", "Medium", "Tips listicle indexing.", "Inspired by: Mrwhosetheboss"),
                    LaunchTag("ASMR setup typing sound", "Medium", "Tactile keyboard sound seekers.", "Inspired by: TaehaTypes"),
                    LaunchTag("mechanical keyboards customizable", "Medium", "Niche typing gear trends.", "Inspired by: Keyboard Enthusiasts"),
                    LaunchTag("aesthetic ambient lighting", "Medium", "Lighting and backglow lookup.", "Inspired by: HueLightGuides"),
                    LaunchTag("ergonomic setup productivity", "Low", "Tech wellness and posture setups.", "Inspired by: ErgonomicOffice"),
                    LaunchTag("budget computer screens", "Low", "Affordable workspace displays.", "Inspired by: BudgetTechShow"),
                    LaunchTag("smartphone comparison specifications", "Low", "Standard benchmarks comparison.", "Inspired by: GSMArena"),
                    LaunchTag("desktop cable routing hacks", "Low", "Clean cable routing advice.", "Inspired by: TechTidyUp"),
                    LaunchTag("technology restorations vintage", "Low", "Vintage gear restoration niches.", "Inspired by: RestoreTech"),
                    LaunchTag("workspace design inspirations", "Low", "Aesthetically pleasing workspace blueprints.", "Inspired by: PinterestStudios"),
                    LaunchTag("creative software workstations", "Low", "Video editing/coding workstation builds.", "Inspired by: CreativeBuilds"),
                    LaunchTag("programmer mechanical keyboards", "Low", "Appeals directly to coding developers.", "Inspired by: ProgrammerKeyboards"),
                    LaunchTag("gear list review tests", "Low", "Aggregated tech listicles.", "Inspired by: TechListKings")
                )
            } else {
                listOf(
                    LaunchTag("ultimate guide $topic", "High", "The definitive lesson search query.", "Inspired by: CrashCourse"),
                    LaunchTag("how to master $topic", "High", "Direct self-improvement search.", "Inspired by: Improvement Pill"),
                    LaunchTag("beginner tutorials $topic", "High", "Friendly entry level walkthroughs.", "Inspired by: SimpleShow"),
                    LaunchTag("step roadmap to learn", "High", "Structured skills educational blueprints.", "Inspired by: RoadmapSh"),
                    LaunchTag("professional secret techniques", "Medium", "Insider knowledge categorization.", "Inspired by: Vox explaining series"),
                    LaunchTag("common mistake fixes", "Medium", "Troubleshooting key helper queries.", "Inspired by: Thomas Frank Study"),
                    LaunchTag("how to avoid failure tips", "Medium", "Failure precaution self-help.", "Inspired by: Tim Ferriss advice"),
                    LaunchTag("quick study practices", "Medium", "Time-saving fast study lookups.", "Inspired by: Ali Abdaal study"),
                    LaunchTag("world standards compared", "Medium", "Competitive level comparison lookup.", "Inspired by: WorldClassRatings"),
                    LaunchTag("creative development workspace", "Medium", "Workspace focus layouts.", "Inspired by: LifeOfADeveloper"),
                    LaunchTag("how to optimize speed", "Medium", "Efficiency indexer for builders.", "Inspired by: FastBuilders"),
                    LaunchTag("anatomy of learning tips", "Low", "Cognitive psychology indexing.", "Inspired by: Huberman Lab"),
                    LaunchTag("mindset training protocols", "Low", "Mental productivity framing search.", "Inspired by: MindsetAcademy"),
                    LaunchTag("daily exercise schedule", "Low", "Consistency tracker searches.", "Inspired by: Habitica"),
                    LaunchTag("mastery level checks", "Low", "Assessments check lists.", "Inspired by: Skill评估"),
                    LaunchTag("unreleased student guides", "Low", "Hidden curriculum student guides.", "Inspired by: SchoolHacksLab"),
                    LaunchTag("practical execution details", "Low", "Detailed structural case walkthroughs.", "Inspired by: ExecutionShowCase"),
                    LaunchTag("case studies overview", "Low", "Academic overview summaries.", "Inspired by: CaseStudyHub"),
                    LaunchTag("expert system architectures", "Low", "Higher tier professional mappings.", "Inspired by: ExpertSystemsHub"),
                    LaunchTag("ultimate tips and hacks", "Low", "Aggregated productivity tip listings.", "Inspired by: LifeHacker")
                )
            }
            hashtagsList = if (isReallyTech) {
                listOf(
                    LaunchHashtag("#techreview", true), LaunchHashtag("#desksetup", true), LaunchHashtag("#workspace", true),
                    LaunchHashtag("#unboxinglife", false), LaunchHashtag("#minimalist", false), LaunchHashtag("#setupinspiration", false),
                    LaunchHashtag("#keyboardasmr", false), LaunchHashtag("#developerkit", false), LaunchHashtag("#ergonomics", false),
                    LaunchHashtag("#gadgetlovers", false), LaunchHashtag("#honestreview", false), LaunchHashtag("#cablemgmt", false),
                    LaunchHashtag("#growtubetech", false), LaunchHashtag("#computersetup", false), LaunchHashtag("#gearlist", false)
                )
            } else {
                listOf(
                    LaunchHashtag("#masterguide", true), LaunchHashtag("#learnfast", true), LaunchHashtag("#tutorialtips", true),
                    LaunchHashtag("#roadmapto", false), LaunchHashtag("#expertadvice", false), LaunchHashtag("#avoidfails", false),
                    LaunchHashtag("#quickhacks", false), LaunchHashtag("#creativelabs", false), LaunchHashtag("#growtubelabs", false),
                    LaunchHashtag("#successdriven", false), LaunchHashtag("#studytok", false), LaunchHashtag("#careergoals", false),
                    LaunchHashtag("#learningsystems", false), LaunchHashtag("#workflowtech", false), LaunchHashtag("#howtoguide", false)
                )
            }
            scoreBreakdown = ScoreBreakdown(18, 17, 19, 18, 18, 90, "High Audience Fit")
        }

        return LaunchReport(
            channelHandle = report.stats.handle,
            topicName = topic,
            timestamp = System.currentTimeMillis(),
            recommendedTitleStyleReason = styleReason,
            titleOptions = titles,
            thumbnailAnalysis = null,
            thumbnailBlueprint = blueprint,
            description = desc,
            tags = keywords,
            hashtags = hashtagsList,
            score = scoreBreakdown
        )
    }

    fun generateLocalThumbnailAnalysis(
        topic: String,
        report: FullGrowTubeReport
    ): ThumbnailAnalysisResult {
        val nameLower = report.stats.name.lowercase()
        val allTitles = ((report.stats.recentVideos ?: emptyList()).map { it.title } + topic).joinToString(" ").lowercase()
        
        val isMusic = nameLower.contains("sidhu") || nameLower.contains("moosewala") || nameLower.contains("tribute") ||
                nameLower.contains("music") || nameLower.contains("song") || nameLower.contains("singer") ||
                allTitles.contains("punjabi") || allTitles.contains("song") || allTitles.contains("remix") ||
                allTitles.contains("vocal") || allTitles.contains("moosewala") || allTitles.contains("tribute") ||
                allTitles.contains("music") || allTitles.contains("ai cover") || allTitles.contains("beat")
                
        val isCooking = nameLower.contains("cook") || nameLower.contains("recipe") || nameLower.contains("kitchen") ||
                nameLower.contains("chef") || nameLower.contains("food") || 
                allTitles.contains("cook") || allTitles.contains("recipe") || allTitles.contains("biryani") ||
                allTitles.contains("paneer") || allTitles.contains("kitchen") || allTitles.contains("chef") ||
                allTitles.contains("dish") || allTitles.contains("food") || allTitles.contains("dinner") ||
                allTitles.contains("breakfast") || allTitles.contains("lunch")
                
        val isFitness = nameLower.contains("fit") || nameLower.contains("gym") || nameLower.contains("muscle") ||
                nameLower.contains("workout") || nameLower.contains("health") ||
                allTitles.contains("gym") || allTitles.contains("workout") || allTitles.contains("muscle") ||
                allTitles.contains("cardio") || allTitles.contains("exercise") || allTitles.contains("fitness") ||
                allTitles.contains("abs") || allTitles.contains("bench press") || allTitles.contains("deadlift")

        return when {
            isMusic -> ThumbnailAnalysisResult(
                predictedCtr = "8.8%",
                audienceScore = 9,
                whatWorks = "The central artist portrait is sharp and instantly recognizable. The neon split lighting grade matches contemporary urban Punjabi music visual trends perfectly.",
                whatNeedsToFix = "The text overlay is slightly covered by the YouTube duration video badge in the bottom-right corner. The background crops are a bit busy, which dilutes the focus drop shadow.",
                specificFixSuggestions = "1. Lift the typography overlay up by 25 pixels away from the lower right-hand corner.\n2. Apply a subtle black drop shadow vignette behind the text to increase daylight legibility.\n3. Turn down background crop saturation by 10% to make the central foreground face pop out."
            )
            isCooking -> ThumbnailAnalysisResult(
                predictedCtr = "9.2%",
                audienceScore = 10,
                whatWorks = "The cheese melt macro details look highly appetizing under the warm color grade. Warm golden color saturation is extremely appealing for casual organic searching dinner crowds.",
                whatNeedsToFix = "The top right of the frame is slightly empty, and the background cooking utensils look somewhat cluttered without adequate depth blur.",
                specificFixSuggestions = "1. Apply a stronger radial blur to background pots and pans to direct the human eye instantly to the central plate.\n2. Increase image highlights by 5% to make the food surface details glisten.\n3. Add a soft yellow stroke outline to your text overlay to stand out in tight grid arrays."
            )
            isFitness -> ThumbnailAnalysisResult(
                predictedCtr = "8.4%",
                audienceScore = 8,
                whatWorks = "The form contrast lines (Do This vs. Not This) are extremely prominent and readable. The high contrast lime-green color highlights are perfect to command immediate screen attention.",
                whatNeedsToFix = "The text overlays ('AVOID THIS') is font-size weighted too thin, which makes it blend into busy gym backdrops on compact mobile screens.",
                specificFixSuggestions = "1. Double the stroke thickness of the red and green alignment arrows.\n2. Change the font style to an ultra-bold block sans-serif to increase readability at 0.5s visual scanning.\n3. Darken the background gym wall shadows to create high-latitude depth."
            )
            else -> ThumbnailAnalysisResult(
                predictedCtr = "7.9%",
                audienceScore = 8,
                whatWorks = "The product layout utilizes generous negative space and looks highly premium. Clean wooden desk textures complement the tech vibes perfectly.",
                whatNeedsToFix = "The ambient light reflections are slightly washing out the screen spec numbers, reducing the high-contrast appeal.",
                specificFixSuggestions = "1. Turn down reflections using software filters, or dial up the product saturation by 15%.\n2. Shift the central item 5% to the left to maintain ideal rule-of-thirds visual balance.\n3. Apply a moody amber gradient glow on the left-third to frame the key heading text beautifully."
            )
        }
    }

    suspend fun analyzeLatestVideo(
        video: VideoItem,
        report: FullGrowTubeReport,
        apiKey: String
    ): LatestVideoAnalysis = withContext(Dispatchers.IO) {
        val stats = report.stats
        val analysis = report.analysis

        val systemInstruction = "You are GrowTube AI, the ultimate world-class YouTube content strategist and diagnostic engine. " +
                "You perform brutal, hyper-targeted, niche-specific performance analysis of the latest YouTube uploads."

        val promptText = """
            Perform a professional performance audit on this recently uploaded video for the channel "${stats.name}":
            
            LATEST VIDEO DETAILS:
            Title: "${video.title}"
            Total Views since upload: ${video.views}
            Like Count: ${video.likes}
            Comment Count: ${video.comments}
            Published Date: "${video.date}"
            
            CHANNEL CONTEXT:
            Channel Name: "${stats.name}"
            Channel Handle: "${stats.handle}"
            Channel Average Views per Video: ${stats.averageViews}
            Micro-Niche: "${analysis.microNiche ?: "Main Niche Content"}"
            Primary Category: "${analysis.primaryCategory ?: "Entertainment"}"
            
            You must evaluate this video's performance relative to the channel average. Provide an honest performance verdict, explain why it performs this way based on niche-specific aspects, and outline 3 highly tailored, niche-specific actions to boost this video's reach right now.
            
            Format your guidelines:
            - Actions must be niche-specific. (e.g. if cooking, suggest pinning a recipe link or community post; if coding, suggest linking a GitHub repo or specific forum; if music, suggest playlists or lyric overlays).
            
            You must output a raw JSON object string. Do not wrap in backticks or markdown, do not write prefaces. Just return raw parsable JSON matching this EXACT JSON structure:
            {
              "verdict": "Performance Verdict (1-2 lines description: Honest assessment of whether it is doing well or underperforming for this channel)",
              "reasons": "Why It's Performing This Way (2-3 lines description: Based on title, upload timing, channel history - what is driving or hurting performance)",
              "actions": [
                "Niche-specific Action 1 to boost reach today (e.g., Should they add to a playlist, pin a comment, share on specific platforms, update title/thumbnail, post at specific time for second push?)",
                "Niche-specific Action 2 to boost reach today",
                "Niche-specific Action 3 to boost reach today"
              ]
            }
        """.trimIndent()

        val service = GeminiRetrofitClient.service
        val request = GeminiRequest(
            contents = listOf(GeminiContent(listOf(GeminiPart(promptText)))),
            systemInstruction = GeminiContent(listOf(GeminiPart(systemInstruction))),
            generationConfig = GeminiGenerationConfig(responseMimeType = "application/json", temperature = 0.25f)
        )

        val response = service.generateContent(apiKey = apiKey, request = request)
        val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("Empty response returned from Gemini.")

        val cleanedJson = responseText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val adapter = moshi.adapter(LatestVideoAnalysis::class.java)
        
        adapter.fromJson(cleanedJson) ?: throw Exception("JSON Parsing Failed on Gemini latest video analysis.")
    }

    suspend fun callGeminiForCompetitors(
        stats: ChannelStats,
        primaryCategory: String,
        subCategory: String,
        microNiche: String,
        apiKey: String
    ): Call2Response {
        val systemInstruction = "You are GrowTube AI, the ultimate world-class YouTube content strategist and diagnostic engine. " +
                "You analyze channel metrics, map competition footprints, and extract high-performing actionable strategy models."

        val promptText = """
            Perform a competitor analysis and content gap detection for this channel:
            Channel: ${stats.name} (${stats.handle})
            Subscribers: ${stats.subscribers}
            Primary Category: $primaryCategory
            Sub-Category: $subCategory
            Micro-Niche: $microNiche

            Instructions:
            1. COMPETITOR SUGGESTIONS ("competitors"):
               - Provide exactly 4 realistic or real-world competitors matching this specific micro-niche.
               - Each competitor in the list must have:
                 - channelName
                 - handle (starting with @)
                 - subscribers (Long value)
                 - estimatedMonthlyViews (Long value)
                 - uploadFrequency (e.g. "2 vids/week")
                 - contentFormat (brief description)
                 - competitiveAdvantage (the specific edge this competitor holds)

            2. COMPETITIVE CONTENT GAPS ANALYSIS ("gapAnalysis"):
               - Provide a detailed 3-sentence analytical breakdown highlighting specific underserved topics, visual thumbnail opportunities, or performance angles this channel's competitors are currently neglecting and how the user can capture them.

            3. KEY STRATEGIES TO ADOPT ("strategiesToAdopt"):
               - Formulate exactly 3 distinct, highly actionable visual or scripting strategies to adopt to stand out.

            4. COMPETITIVE EDGE ("yourAdvantage"):
               - A clean 1-sentence description of the user's ultimate competitive edge based on their handle and niche.

            Output only raw JSON. Do not wrap in markdown or backticks. Return raw parsable JSON matching this EXACT schema:
            {
              "competitors": [
                {
                  "channelName": "Name",
                  "handle": "@handle",
                  "subscribers": 120000,
                  "estimatedMonthlyViews": 450000,
                  "uploadFrequency": "2 vids/week",
                  "contentFormat": "Description",
                  "competitiveAdvantage": "Advantage"
                }
              ],
              "gapAnalysis": "3-sentence gap analysis details...",
              "strategiesToAdopt": [
                "1st actionable strategy",
                "2nd actionable strategy",
                "3rd actionable strategy"
              ],
              "yourAdvantage": "The channel's direct advantage..."
            }
        """.trimIndent()

        var retries = 0
        val maxRetries = 3
        var responseText: String? = null

        while (true) {
            try {
                val service = GeminiRetrofitClient.service
                val request = GeminiRequest(
                    contents = listOf(GeminiContent(listOf(GeminiPart(promptText)))),
                    systemInstruction = GeminiContent(listOf(GeminiPart(systemInstruction))),
                    generationConfig = GeminiGenerationConfig(responseMimeType = "application/json", temperature = 0.25f)
                )

                val response = service.generateContent(apiKey = apiKey, request = request)
                responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw Exception("Empty response returned from Gemini on Competitors analysis.")
                break
            } catch (e: Exception) {
                val is429 = (e is retrofit2.HttpException && e.code() == 429) ||
                            e.message?.contains("429") == true ||
                            e.toString().contains("429")
                if (is429 && retries < maxRetries) {
                    retries++
                    delay(5000)
                } else {
                    throw e
                }
            }
        }

        val cleanedJson = responseText!!.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return call2Adapter.fromJson(cleanedJson)
            ?: throw Exception("JSON Parsing Failed on Competitors analysis.")
    }

    suspend fun callGeminiForStrategy(
        stats: ChannelStats,
        contentRoadblock: String,
        apiKey: String
    ): Call3Response {
        val systemInstruction = "You are GrowTube AI, the ultimate world-class YouTube content strategist and diagnostic engine. " +
                "You formulate elite channel growth blueprints and content schedules."

        val promptText = """
            Build a comprehensive 30-day content calendar and 10 viral video blueprints based on:
            Channel: ${stats.name} (${stats.handle})
            Subscribers: ${stats.subscribers}
            Core Strategic Roadblock / Weakness: $contentRoadblock

            Instructions:
            1. UPLOAD SCHEDULE STRATEGY:
               - "bestUploadDays": list of best days of week (e.g. ["Monday", "Wednesday", "Friday"])
               - "recommendedLength": recommended length of videos (e.g. "8-12 minutes")
               - "shortsVsLongRatio": recommended percentage/ratio of Shorts vs Long-form videos (e.g. "70% Long-form, 30% Shorts")

            2. 10 VIDEO BLUEPRINTS ("videoIdeas"):
               - Provide exactly 10 video ideas. Each must contain:
                 - title: A highly viral, high-CTR title tailored to their niche.
                 - whyItWillWork: A brief strategic explanation.
                 - format: "Long" or "Short".
                 - difficulty: "Easy", "Medium", or "Hard".

            3. 30-DAY CONTENT CALENDAR ("contentCalendar"):
               - A specific list of calendar entries pacing with their upload frequency.
               - Each entry must contain:
                 - dayNumber: day of schedule (e.g. 1, 5, 10, etc.)
                 - dateText: day identifier (e.g. "Day 1", "Day 5", etc.)
                 - videoTitle: title of the scheduled video.

            Output only raw JSON matching this EXACT schema:
            {
              "bestUploadDays": ["Day1", "Day2"],
              "recommendedLength": "duration descriptions",
              "shortsVsLongRatio": "ratio descriptions",
              "videoIdeas": [
                {"title": "Title", "whyItWillWork": "Why description", "format": "Long", "difficulty": "Medium"}
              ],
              "contentCalendar": [
                {"dayNumber": 1, "dateText": "Day 1", "videoTitle": "Video Title"}
              ]
            }
        """.trimIndent()

        var retries = 0
        val maxRetries = 3
        var responseText: String? = null

        while (true) {
            try {
                val service = GeminiRetrofitClient.service
                val request = GeminiRequest(
                    contents = listOf(GeminiContent(listOf(GeminiPart(promptText)))),
                    systemInstruction = GeminiContent(listOf(GeminiPart(systemInstruction))),
                    generationConfig = GeminiGenerationConfig(responseMimeType = "application/json", temperature = 0.3f)
                )

                val response = service.generateContent(apiKey = apiKey, request = request)
                responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw Exception("Empty response returned from Gemini on Strategy flow.")
                break
            } catch (e: Exception) {
                val is429 = (e is retrofit2.HttpException && e.code() == 429) ||
                            e.message?.contains("429") == true ||
                            e.toString().contains("429")
                if (is429 && retries < maxRetries) {
                    retries++
                    delay(5000)
                } else {
                    throw e
                }
            }
        }

        val cleanedJson = responseText!!.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return call3Adapter.fromJson(cleanedJson)
            ?: throw Exception("JSON Parsing Failed on Strategy flow.")
    }

    fun generateMockCompetitors(theme: String, stats: ChannelStats): Call2Response {
        return when (theme) {
            "Cooking" -> Call2Response(
                competitors = listOf(
                    CompetitorAnalysis("Tasty Bites Cooking", "@tastybites", 180000, 65000, "2 vids/week", "Cinematic b-roll + VO", "Highly aesthetic food closeups"),
                    CompetitorAnalysis("Epicurean Fast", "@epicureanfast", 95000, 22000, "1 vid/week", "Cinematic b-roll + VO", "Extremely fast-paced editing flow"),
                    CompetitorAnalysis("Chef Josh Cooks", "@joshcooks", 210000, 180000, "3 vids/month", "Cinematic b-roll + VO", "Elite culinary knife guides"),
                    CompetitorAnalysis("Easy Kitchen", "@easykitchen", 32000, 9500, "1 vid/week", "Cinematic b-roll + VO", "Super clean step-by-step overlays")
                ),
                gapAnalysis = "Competitors are heavily focused on complex multi-hour recipes, leaving a massive content gap for quick under-15-minute high-quality dinner meals. They also neglect custom typography over simple colored thumbnails.",
                strategiesToAdopt = listOf(
                    "Insert tight 4k macro ingredient sizzle shots inside the first 5 seconds to lock attention.",
                    "Use high-contrast bold yellow title overlays in thumbnails instead of small white font.",
                    "End videos with a clean 10-second taste test containing exact flavor rating cards."
                ),
                yourAdvantage = "Authentic home-style approach pairing rapid editing with extremely accessible ingredients."
            )
            "Fitness" -> Call2Response(
                competitors = listOf(
                    CompetitorAnalysis("Apex Athleisure", "@apexathleisure", 250000, 95000, "3 vids/week", "Lifting montages", "Heavy compound lift form guides"),
                    CompetitorAnalysis("FitLife Pro", "@fitlifepro", 140000, 48000, "1 vid/week", "Aesthetic Gym-Vlogs", "Extremely polished visual filters"),
                    CompetitorAnalysis("Coach Sarah", "@coachsarah", 85000, 31000, "2 vids/week", "Educational infographics", "Deep scientific literature breakdowns"),
                    CompetitorAnalysis("Iron Core", "@ironcore", 310000, 220000, "1 vid/month", "Calisthenics tutorials", "Insane strength demonstrations")
                ),
                gapAnalysis = "The local fitness niche focuses entirely on elite gym-goers, creating a serious gap for realistic home workouts using budget equipment. They also write highly formal text titles instead of viral emotional hooks.",
                strategiesToAdopt = listOf(
                    "Keep video opening focused on real progress photos instead of long channel logos.",
                    "Implement a continuous muscle-activation progress bar on the corner of the screen.",
                    "Introduce 5-second comic zoom-ins during high-strain moments to showcase personality."
                ),
                yourAdvantage = "Humor-infused, ultra-relatable fitness guidance designed for busy, real-world routines."
            )
            "Tech" -> Call2Response(
                competitors = listOf(
                    CompetitorAnalysis("TechnoBytes", "@technobytes", 450000, 180000, "4 vids/week", "Presenter desk review", "Immediate day-one product unboxings"),
                    CompetitorAnalysis("Gadget Lab", "@gadgetlab", 220000, 75000, "2 vids/week", "Cinematic b-roll reviews", "Extreme camera angles and focus pulls"),
                    CompetitorAnalysis("Null Pointer Tech", "@nulltech", 125000, 45000, "1 vid/week", "Deskside code guides", "Developer-focused productivity setups"),
                    CompetitorAnalysis("Gizmo Sphere", "@gizmosphere", 89000, 30000, "3 vids/month", "Budget tech lists", "Incredible Amazon custom discoveries")
                ),
                gapAnalysis = "Tech rivals focus heavily on raw specs and expensive setups, leaving a massive gap for cost-to-value desk optimizations. They also lack clear breakdown chapters on mobile screens.",
                strategiesToAdopt = listOf(
                    "Lead with a '30-second spec-sheet' rundown immediately preceding the physical review section.",
                    "Integrate interactive mouse-clicking and keyboard ASMR sound files inside the video intro.",
                    "Adopt high-contrast custom outline neon designs to wrap the gadgets inside thumbnails."
                ),
                yourAdvantage = "Relatable pragmatism focusing entirely on daily desk ergonomics and real budget setups."
            )
            else -> Call2Response(
                competitors = listOf(
                    CompetitorAnalysis("Nexus Prime", "@nexusprime", 600000, 250000, "1 vid/week", "List formats", "Elite animation and graphic slides"),
                    CompetitorAnalysis("Creator Lab", "@createlab", 350000, 110000, "2 vids/week", "Facecam talking", "Industry leak and analytical breakdowns"),
                    CompetitorAnalysis("Vibe Hub", "@vibehub", 180000, 70000, "3 vids/week", "Music background VO", "Fast paced trend reaction compilations"),
                    CompetitorAnalysis("Studio One", "@studioone", 950000, 410000, "2 vids/month", "Short film style", "Cinematic masterpiece level stories")
                ),
                gapAnalysis = "Competitors are pushing highly over-edited visual fatigue content, giving you an opening to win with intimate story-driven lessons. They also rely on generic templates.",
                strategiesToAdopt = listOf(
                    "Structure scripts strictly using a 3-act narrative journey with clear stakes.",
                    "Vibe-check your pacing by removing any silent visual screen gaps matching 1.5 seconds.",
                    "Incorporate warm, low-exposure ambient studio background lights for a premium cozy feel."
                ),
                yourAdvantage = "Warm, highly customized and educational content that builds intense user-trust."
            )
        }
    }

    fun generateMockStrategy(theme: String, stats: ChannelStats): Call3Response {
        return when (theme) {
            "Cooking" -> Call3Response(
                bestUploadDays = listOf("Wednesday", "Friday", "Sunday"),
                recommendedLength = "8-12 minutes for Long-form, 45-50 seconds for Shorts",
                shortsVsLongRatio = "60% Long-form, 40% Shorts",
                videoIdeas = listOf(
                    VideoIdea("I Tried Cooking a Gourmet Steak inside a Toaster", "An extreme culinary challenge that exploits high-interest organic search trends.", "Long", "Medium"),
                    VideoIdea("The 3-Ingredient Dessert My Family Begs For", "A high-CTR title tapping into rapid budget-friendly convenience.", "Long", "Easy"),
                    VideoIdea("Recreating the Iconic 15-Minute Garlic Pasta", "Direct continuation of your channel's most successful topic.", "Long", "Easy"),
                    VideoIdea("Knife hack: Perfect diced onions in seconds!", "Highly shareable, high-retention tutorial loop.", "Short", "Easy"),
                    VideoIdea("I Only Eat Leftover Fried Rice for 48 Hours", "Format-driven narrative with clear stakes and high lifestyle appeal.", "Long", "Hard"),
                    VideoIdea("The Ultimate ${'$'}5 Grocery Store Dinner Challenge", "Taps into financial awareness with extremely high click incentive.", "Long", "Medium"),
                    VideoIdea("Never make this massive pizza crust mistake!", "Negative constraint hook that drives intense user curiosity.", "Short", "Easy"),
                    VideoIdea("My Secret French Omelette Method (Unlocked)", "Authority-driven step-by-step masterclass format.", "Long", "Medium"),
                    VideoIdea("The Budget Food Tour of My Local Street Carts", "Locational food vlog format with highly dynamic pacing.", "Long", "Hard"),
                    VideoIdea("Why Gordon Ramsay's Beef Wellington is overrated", "Contrarian hook targeting mainstream figures to drive commenting debates.", "Long", "Hard")
                ),
                contentCalendar = listOf(
                    CalendarEntry(1, "Day 1", "The 3-Ingredient Dessert My Family Begs For"),
                    CalendarEntry(4, "Day 4", "Quick Garlic Pasta Remastered (Short)"),
                    CalendarEntry(8, "Day 8", "I Tried Cooking a Gourmet Steak inside a Toaster"),
                    CalendarEntry(11, "Day 11", "Knife hack: Perfect diced onions in seconds!"),
                    CalendarEntry(15, "Day 15", "The Ultimate ${'$'}5 Grocery Store Dinner Challenge"),
                    CalendarEntry(18, "Day 18", "Never make this massive pizza crust mistake!"),
                    CalendarEntry(22, "Day 22", "I Only Eat Leftover Fried Rice for 48 Hours"),
                    CalendarEntry(25, "Day 25", "Secret French Omelette Method"),
                    CalendarEntry(30, "Day 30", "Why Gordon Ramsay's Beef Wellington is overrated")
                )
            )
            "Fitness" -> Call3Response(
                bestUploadDays = listOf("Monday", "Thursday", "Saturday"),
                recommendedLength = "10-14 minutes for Long-form, 30-40 seconds for Shorts",
                shortsVsLongRatio = "50% Long-form, 50% Shorts",
                videoIdeas = listOf(
                    VideoIdea("The Only Dumbbell Exercise You Ever Need for Arms", "Simplification angle with extremely high visual click reward.", "Long", "Easy"),
                    VideoIdea("How to Squat Without Killing Your Left Knee", "Direct problem-solution title that earns search rankings.", "Long", "Medium"),
                    VideoIdea("Stretch: Fix your tight shoulders in 30 seconds", "Rapid user application with insane shareability.", "Short", "Easy"),
                    VideoIdea("My Full Day of Eating for 8% Bodyfat", "Polish-filled lifestyle food guide targeting aesthetics.", "Long", "Medium"),
                    VideoIdea("I Ran 10K Every Day For 30 Days (Real Results)", "Compelling case-study challenge format that spikes retention.", "Long", "Hard"),
                    VideoIdea("Is Creatine Actually Safe? (Scientific Truth)", "Answers a common high-volume beginner query objectively.", "Long", "Easy"),
                    VideoIdea("The Ultimate Push Workout for Busy Office workers", "Highly specific demographic niche customization.", "Long", "Medium"),
                    VideoIdea("The 3 Best Posture Hacks for Back Pain", "Physiological relief hook that gets massive organic shares.", "Short", "Easy"),
                    VideoIdea("Weights vs Calisthenics: My Honest 5-Year Review", "Evergreen head-to-head debate that fills comment sections.", "Long", "Medium"),
                    VideoIdea("I Tried Arnold's Schwarzenegger's Intense Shoulder Day", "Mainstream title-jack with huge curiosity click-through.", "Long", "Hard")
                ),
                contentCalendar = listOf(
                    CalendarEntry(1, "Day 1", "The Only Dumbbell Exercise You Ever Need for Arms"),
                    CalendarEntry(5, "Day 5", "Stretch: Fix your tight shoulders in 30 seconds"),
                    CalendarEntry(10, "Day 10", "My Full Day of Eating for 8% Bodyfat"),
                    CalendarEntry(15, "Day 15", "I Tried Arnold's Schwarzenegger's Shoulder Day"),
                    CalendarEntry(20, "Day 20", "Weights vs Calisthenics: Honest 5-Year Review"),
                    CalendarEntry(25, "Day 25", "The 3 Best Posture Hacks for Back Pain"),
                    CalendarEntry(30, "Day 30", "How to Squat Without Killing Your Left Knee")
                )
            )
            "Tech" -> Call3Response(
                bestUploadDays = listOf("Tuesday", "Thursday", "Sunday"),
                recommendedLength = "8-12 minutes for Long-form, 50-60 seconds for Shorts",
                shortsVsLongRatio = "70% Long-form, 30% Shorts",
                videoIdeas = listOf(
                    VideoIdea("Building a Setup My Boss Thought Cost ${'$'}2000", "Aesthetics on a budget hook, extremely high relative CTR.", "Long", "Medium"),
                    VideoIdea("Why I Finally Ditched the iPhone for Pixel", "Polarizing switch-vlog trigger that drives commenting storm.", "Long", "Medium"),
                    VideoIdea("Sound test: The clickiest keyboard on Earth!", "Sensory keyboard ASMR with immense loops-retention.", "Short", "Easy"),
                    VideoIdea("7 Productivity Apps That Save Me 10 Hours a Week", "Direct functional value that builds high trust.", "Long", "Easy"),
                    VideoIdea("The Truth About Apple Intelligence Features", "Newsjack on top-trending topics.", "Long", "Medium"),
                    VideoIdea("My Ultimate Daily Desk Organizer Backpack Setup", "High-conversion affiliate listing review format.", "Long", "Hard"),
                    VideoIdea("The Best Budget Mech Keyboard of 2026", "Direct search terms targeting purchase intent.", "Long", "Easy"),
                    VideoIdea("Stop setting up your workspace like this!", "Negative instruction hook that stops users scrolling feed.", "Short", "Easy"),
                    VideoIdea("We Built a Custom PC inside a Wooden Fruit Box", "Insane tech DIY project with huge curiosity payoff.", "Long", "Hard"),
                    VideoIdea("Is this ${'$'}50 Handheld Console the ultimate Switch?", "Underdog gadget review format.", "Long", "Medium")
                ),
                contentCalendar = listOf(
                    CalendarEntry(1, "Day 1", "Building a Setup My Boss Thought Cost ${'$'}2000"),
                    CalendarEntry(5, "Day 5", "Sound test: Clickiest keyboard on Earth!"),
                    CalendarEntry(10, "Day 10", "7 Productivity Apps That Save Me 10 Hours"),
                    CalendarEntry(15, "Day 15", "The Best Budget Mech Keyboard of 2026"),
                    CalendarEntry(20, "Day 20", "We Built a Custom PC inside a Wood Box"),
                    CalendarEntry(25, "Day 25", "Stop setting up your workspace like this!"),
                    CalendarEntry(30, "Day 30", "Is this ${'$'}50 Handheld Console the ultimate Switch?")
                )
            )
            else -> Call3Response(
                bestUploadDays = listOf("Tuesday", "Saturday"),
                recommendedLength = "12-15 minutes for Long-form, 40-50 seconds for Shorts",
                shortsVsLongRatio = "80% Long-form, 20% Shorts",
                videoIdeas = listOf(
                    VideoIdea("The Secret Strategy to Scaling Your Channel in 2026", "Direct tutorial blueprint for creator niches.", "Long", "Medium"),
                    VideoIdea("Why Most Small Creators Fail Before 1,000 Subs", "Truth-reveal negative hook that creators obsessively watch.", "Long", "Easy"),
                    VideoIdea("Thumbnail hack that instantly doubles your CTR!", "Immediate actionable visual value shortcut.", "Short", "Easy"),
                    VideoIdea("How I Pace My Videos for Absolute Max Retention", "Technical script editing guide.", "Long", "Medium"),
                    VideoIdea("I Tested 5 Viral Video Hooks (Which worked best?)", "Challenge-based case study layout.", "Long", "Hard"),
                    VideoIdea("How to Script the First 30 Seconds of Any Video", "Tutorial addressing the critical dropoff problem.", "Long", "Easy"),
                    VideoIdea("The Best Microphones for YouTubers under ${'$'}100", "Affiliate product listing layout.", "Long", "Easy"),
                    VideoIdea("The ultimate high-contrast thumbnail layout", "Visual layout blueprint overview.", "Short", "Easy"),
                    VideoIdea("My Entire Home Studio Equipment Unboxing Tour", "Polish-filled creator-insight b-roll showcase.", "Long", "Hard"),
                    VideoIdea("The YouTube algorithm update no one is talking about", "Curiosity reaction newsjack hook.", "Long", "Hard")
                ),
                contentCalendar = listOf(
                    CalendarEntry(1, "Day 1", "The Secret Strategy to Scaling Your Channel in 2026"),
                    CalendarEntry(6, "Day 6", "Thumbnail hack that instantly doubles CTR!"),
                    CalendarEntry(12, "Day 12", "Why Most Small Creators Fail Before 1000 Subs"),
                    CalendarEntry(18, "Day 18", "The Best Microphones under ${'$'}100"),
                    CalendarEntry(24, "Day 24", "How to Script First 30 Seconds of Any Video"),
                    CalendarEntry(30, "Day 30", "The YouTube algorithm update no one is talking about")
                )
            )
        }
    }
}

