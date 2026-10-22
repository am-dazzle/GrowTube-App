package com.example.ui

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.network.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class ScreenState {
    object Home : ScreenState()
    object Settings : ScreenState()
    object Loading : ScreenState()
    data class Results(val report: FullGrowTubeReport) : ScreenState()
    data class Error(val message: String) : ScreenState()
    data class VideoAutopsy(val video: VideoInsight, val report: FullGrowTubeReport) : ScreenState()
    object ChannelClone : ScreenState()
}

class GrowTubeViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = GrowTubePreferences(application)
    private val analyzer = GrowTubeAnalyzer(application)
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val reportAdapter = moshi.adapter(FullGrowTubeReport::class.java)

    // Form inputs and persistent status
    var youtubeApiKey by mutableStateOf("")
        private set
    var geminiApiKey by mutableStateOf("")
        private set
    var channelInput by mutableStateOf("")
    
    // Saved lists
    var recentChannels by mutableStateOf<List<RecentChannelItem>>(emptyList())
        private set

    var remainingQuota by mutableStateOf(10000)
        private set

    val isApiKeyConnected: Boolean
        get() = youtubeApiKey.isNotBlank()

    // Navigation and state machines
    var uiState by mutableStateOf<ScreenState>(ScreenState.Home)
        private set
    var activeTab by mutableStateOf(0) // 0: DNA, 1: Competitors, 2: Strategy

    var loadingAutopsy by mutableStateOf(false)
        private set
    var autopsyReport by mutableStateOf<AutopsyReport?>(null)
        private set
    var autopsyError by mutableStateOf<String?>(null)
        private set
    private var activeAutopsyJob: Job? = null

    // Lazy load tab states
    var loadingCompetitorsTab by mutableStateOf(false)
        private set
    var competitorsTabResponse by mutableStateOf<Call2Response?>(null)
        private set
    var competitorsTabError by mutableStateOf<String?>(null)
        private set

    var loadingStrategyTab by mutableStateOf(false)
        private set
    var strategyTabResponse by mutableStateOf<Call3Response?>(null)
        private set
    var strategyTabError by mutableStateOf<String?>(null)
        private set

    var competitorsTabHandle by mutableStateOf<String?>(null)
        private set
    var strategyTabHandle by mutableStateOf<String?>(null)
        private set

    // Channel Clone Feature State Variables
    var cloneActiveState by mutableStateOf(0)
    var cloneGoal by mutableStateOf("")
    var cloneFormat by mutableStateOf("")
    var cloneChannelLink by mutableStateOf("")
    var cloneChannelLink2 by mutableStateOf("")
    var cloneChannelLink3 by mutableStateOf("")
    var cloneTranscripts by mutableStateOf<List<String>>(emptyList())
    var cloneTranscriptInputInput by mutableStateOf("")
    var cloneTopicChoice by mutableStateOf("")
    var cloneCustomTopic by mutableStateOf("")
    var cloneCompetitorChoice by mutableStateOf("")
    var cloneCompetitorLinks by mutableStateOf("")
    
    var cloneStyleDNALoading by mutableStateOf(false)
    var cloneStyleDNA by mutableStateOf<ChannelCloneStyleDNA?>(null)
    
    var cloneScriptLoading by mutableStateOf(false)
    var cloneScript by mutableStateOf<ChannelCloneScript?>(null)
    var cloneSelectedHookIndex by mutableStateOf(-1) // -1 is original, 0, 1, 2 for A, B, C
    var cloneScriptFeedback by mutableStateOf("")
    
    var cloneVisualDescription by mutableStateOf("")
    var cloneVisualProfile by mutableStateOf<VisualStyleProfile?>(null)
    var cloneImagePrompts by mutableStateOf<List<VisualBeat>>(emptyList())
    var cloneVideoPromptsEnabled by mutableStateOf<Boolean?>(null)
    
    var cloneThumbnailPreference by mutableStateOf("")
    var cloneThumbnailsLoading by mutableStateOf(false)
    var cloneThumbnails by mutableStateOf<List<CloneThumbnail>>(emptyList())
    
    var cloneMetadataLoading by mutableStateOf(false)
    var cloneMetadata by mutableStateOf<CloneMetadata?>(null)
    var cloneCalendarEnabled by mutableStateOf<Boolean?>(null)
    var cloneCalendar by mutableStateOf<List<CloneCalendarItem>>(emptyList())

    // Channel Clone Search and Recommendations States
    var cloneSearchQuery by mutableStateOf("")
    var cloneSearchResults by mutableStateOf<List<CloneSearchChannelResult>>(emptyList())
    var cloneSearchLoading by mutableStateOf(false)
    var cloneSearchError by mutableStateOf<String?>(null)

    // Competitor Search States per User Intent
    var competitorSearchQuery by mutableStateOf("")
    var competitorSearchResults by mutableStateOf<List<CloneSearchChannelResult>>(emptyList())
    var competitorSearchLoading by mutableStateOf(false)
    var competitorSearchError by mutableStateOf<String?>(null)

    var cloneSuggestedVideos by mutableStateOf<List<CloneSuggestedVideo>>(emptyList())
    var loadingCloneSuggestions by mutableStateOf(false)
    var cloneSuggestionsError by mutableStateOf<String?>(null)

    // Channel Clone help preferred language
    var preferredHelpLanguage by mutableStateOf("English")

    // On-demand AI Competitor Search States
    var aiSuggestionsLoading by mutableStateOf(false)
    var aiCompetitorSuggestions by mutableStateOf<List<CompetitorAnalysis>>(emptyList())
    var aiSuggestionsError by mutableStateOf<String?>(null)

    // Latest Video Analysis States
    var loadingLatestVideoAnalysis by mutableStateOf(false)
        private set
    var latestVideoAnalysis by mutableStateOf<LatestVideoAnalysis?>(null)
        private set
    var latestVideoAnalysisError by mutableStateOf<String?>(null)
        private set
    private var activeLatestVideoAnalysisJob: Job? = null


    // Launch Pre-Upload video states
    var launchTopicInput by mutableStateOf("")
        private set
    var loadingLaunch by mutableStateOf(false)
        private set
    var launchReport by mutableStateOf<LaunchReport?>(null)
        private set
    var savedLaunchReports by mutableStateOf<List<LaunchReport>>(emptyList())
        private set
    var analyzingThumbnail by mutableStateOf(false)
        private set

    // Screen 2 Loading dynamics
    var loadingStep by mutableStateOf(1)
        private set
    var loadingStatusText by mutableStateOf("Ready to analyze...")
        private set

    private var activeAnalysisJob: Job? = null

    init {
        loadKeysAndRecents()
    }

    fun getEffectiveGeminiApiKey(): String {
        var key = prefs.getGeminiApiKey().trim()
        if (key.isEmpty()) {
            try {
                key = com.example.BuildConfig.GEMINI_API_KEY.trim()
            } catch (e: Throwable) {}
        }
        if (key == "MY_GEMINI_API_KEY") {
            key = ""
        }
        return key
    }

    fun loadKeysAndRecents() {
        viewModelScope.launch {
            try {
                val yt = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { prefs.getYoutubeApiKey() }
                val gem = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { getEffectiveGeminiApiKey() }
                val rec = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { prefs.getRecentChannels() }
                val quota = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { prefs.getRemainingQuota() }

                youtubeApiKey = yt
                geminiApiKey = gem
                recentChannels = rec
                remainingQuota = quota
            } catch (e: Exception) {
                e.printStackTrace()
                // Safely assign default values on failure
                youtubeApiKey = ""
                geminiApiKey = ""
                recentChannels = emptyList()
                remainingQuota = 10000
            }
        }
    }

    fun saveKeys(ytKey: String, gKey: String) {
        viewModelScope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                prefs.saveYoutubeApiKey(ytKey)
                prefs.saveGeminiApiKey(gKey)
            }
            youtubeApiKey = ytKey
            geminiApiKey = gKey
        }
    }

    fun updateChannelInput(input: String) {
        channelInput = input
    }

    fun selectTab(index: Int) {
        activeTab = index
    }

    fun determineContextTheme(handle: String): String {
        val lowercaseHandle = handle.lowercase()
        return when {
            lowercaseHandle.contains("cook") || lowercaseHandle.contains("food") || lowercaseHandle.contains("recipe") || lowercaseHandle.contains("chef") -> "Cooking"
            lowercaseHandle.contains("fit") || lowercaseHandle.contains("gym") || lowercaseHandle.contains("muscle") || lowercaseHandle.contains("health") -> "Fitness"
            lowercaseHandle.contains("tech") || lowercaseHandle.contains("gadget") || lowercaseHandle.contains("coding") || lowercaseHandle.contains("code") || lowercaseHandle.contains("mkbhd") -> "Tech"
            else -> "Entertainment"
        }
    }

    fun clearCachesForChannel(handle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            prefs.clearCompetitorsCache(handle)
            prefs.clearStrategyCache(handle)
        }
        competitorsTabResponse = null
        competitorsTabError = null
        competitorsTabHandle = null
        strategyTabResponse = null
        strategyTabError = null
        strategyTabHandle = null
    }

    fun loadCompetitorsTab(handle: String, stats: ChannelStats, analysis: ChannelAnalysis) {
        if (competitorsTabResponse != null && competitorsTabHandle == handle) return
        if (loadingCompetitorsTab) return
        
        loadingCompetitorsTab = true
        competitorsTabResponse = null
        competitorsTabError = null
        competitorsTabHandle = handle
        
        viewModelScope.launch {
            try {
                val cached = withContext(Dispatchers.IO) {
                    prefs.getCompetitorsCache(handle)
                }
                if (cached != null) {
                    competitorsTabResponse = cached
                    loadingCompetitorsTab = false
                    return@launch
                }
                
                val result = Call2Response(
                    competitors = analysis.competitors ?: emptyList(),
                    gapAnalysis = if ((analysis.competitors ?: emptyList()).isEmpty()) {
                        "Add competitors to see gap analysis and adopted strategies."
                    } else {
                        analysis.gapAnalysis ?: ""
                    },
                    strategiesToAdopt = analysis.strategiesToAdopt ?: emptyList(),
                    yourAdvantage = if ((analysis.competitors ?: emptyList()).isEmpty()) {
                        "Add competitors to view your tracked advantages."
                    } else {
                        analysis.yourAdvantage ?: ""
                    }
                )
                
                withContext(Dispatchers.IO) {
                    prefs.saveCompetitorsCache(handle, result)
                }
                
                competitorsTabResponse = result
            } catch (e: Exception) {
                e.printStackTrace()
                competitorsTabError = e.message ?: "Failed to generate competitor suggestions."
            } finally {
                loadingCompetitorsTab = false
            }
        }
    }

    fun loadStrategyTab(handle: String, stats: ChannelStats, analysis: ChannelAnalysis) {
        if (strategyTabResponse != null && strategyTabHandle == handle) return
        if (loadingStrategyTab) return
        
        loadingStrategyTab = true
        strategyTabResponse = null
        strategyTabError = null
        strategyTabHandle = handle
        
        viewModelScope.launch {
            try {
                val cached = withContext(Dispatchers.IO) {
                    prefs.getStrategyCache(handle)
                }
                if (cached != null) {
                    strategyTabResponse = cached
                    loadingStrategyTab = false
                    return@launch
                }
                
                val roadblock = analysis.mainWeakness ?: "Limited audience hook"
                val result = if (geminiApiKey.isEmpty()) {
                    analyzer.generateMockStrategy(determineContextTheme(handle), stats)
                } else {
                    withContext(Dispatchers.IO) {
                        prefs.deductQuota(300)
                    }
                    analyzer.callGeminiForStrategy(
                        stats,
                        roadblock,
                        geminiApiKey
                    )
                }
                
                withContext(Dispatchers.IO) {
                    prefs.saveStrategyCache(handle, result)
                }
                
                strategyTabResponse = result
            } catch (e: Exception) {
                e.printStackTrace()
                strategyTabError = e.message ?: "Failed to generate content strategy."
            } finally {
                loadingStrategyTab = false
            }
        }
    }

    fun updateLaunchTopicInput(input: String) {
        launchTopicInput = input
    }

    fun loadSavedLaunches(channelHandle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = prefs.getLaunchReports(channelHandle)
            withContext(Dispatchers.Main) {
                savedLaunchReports = list
            }
        }
    }

    fun optimizeLaunch(topic: String, currentReport: FullGrowTubeReport) {
        if (topic.trim().isEmpty()) return
        loadingLaunch = true
        viewModelScope.launch {
            try {
                val analyzer = com.example.network.GrowTubeAnalyzer(getApplication())
                val newReport = analyzer.generateLaunchReport(topic, currentReport, geminiApiKey)
                launchReport = newReport
                // Save immediately to local cache with video topic name
                withContext(Dispatchers.IO) {
                    prefs.addLaunchReport(currentReport.stats.handle, newReport)
                }
                loadSavedLaunches(currentReport.stats.handle)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                loadingLaunch = false
            }
        }
    }

    fun selectLaunchReport(report: LaunchReport) {
        launchReport = report
    }

    fun clearLaunchReport() {
        launchReport = null
        launchTopicInput = ""
    }

    fun deleteLaunchReport(channelHandle: String, reportToDelete: LaunchReport) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentList = prefs.getLaunchReports(channelHandle).toMutableList()
            currentList.removeAll { it.topicName == reportToDelete.topicName && it.timestamp == reportToDelete.timestamp }
            prefs.saveLaunchReports(channelHandle, currentList)
            withContext(Dispatchers.Main) {
                if (launchReport?.topicName == reportToDelete.topicName && launchReport?.timestamp == reportToDelete.timestamp) {
                    launchReport = null
                }
                savedLaunchReports = currentList
            }
        }
    }

    fun analyzeThumbnail(imageUriStr: String, topic: String, currentReport: FullGrowTubeReport) {
        analyzingThumbnail = true
        viewModelScope.launch {
            try {
                val analyzer = com.example.network.GrowTubeAnalyzer(getApplication())
                val thumbnailResult = analyzer.analyzeUploadedThumbnail(imageUriStr, topic, currentReport, geminiApiKey)
                
                val currentLaunchReport = launchReport
                if (currentLaunchReport != null) {
                    val oldScore = currentLaunchReport.score
                    val newTotalScore = (oldScore.titleStrength +
                            thumbnailResult.audienceScore * 2 + 
                            oldScore.descriptionQuality +
                            oldScore.tagsCoverage +
                            oldScore.nicheAlignment).coerceAtMost(100)
                    
                    val newStatus = when {
                        newTotalScore >= 85 -> "Ready to Launch"
                        newTotalScore >= 65 -> "Needs Work"
                        else -> "Not Optimized"
                    }
                    val updatedScore = oldScore.copy(
                        thumbnailPotential = thumbnailResult.audienceScore * 2,
                        totalScore = newTotalScore,
                        status = newStatus
                    )
                    
                    val updatedReport = currentLaunchReport.copy(
                        thumbnailAnalysis = thumbnailResult,
                        score = updatedScore
                    )
                    
                    launchReport = updatedReport
                    withContext(Dispatchers.IO) {
                        prefs.addLaunchReport(currentReport.stats.handle, updatedReport)
                    }
                    loadSavedLaunches(currentReport.stats.handle)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                analyzingThumbnail = false
            }
        }
    }

    fun navigateToHome() {
        activeAnalysisJob?.cancel()
        uiState = ScreenState.Home
    }

    fun navigateToSettings() {
        uiState = ScreenState.Settings
    }

    fun navigateToVideoAutopsy(video: VideoInsight, report: FullGrowTubeReport) {
        autopsyReport = null
        autopsyError = null
        loadingAutopsy = true
        uiState = ScreenState.VideoAutopsy(video, report)
        activeAutopsyJob?.cancel()
        activeAutopsyJob = viewModelScope.launch {
            try {
                val gemKey = getEffectiveGeminiApiKey()
                val demoMode = (prefs.getYoutubeApiKey().isEmpty() || gemKey.isEmpty() || 
                                report.stats.handle.lowercase().contains("demo"))
                if (demoMode) {
                    kotlinx.coroutines.delay(1200) // Beautiful slight loading feel
                    autopsyReport = analyzer.generateLocalAutopsyFallback(video, report)
                } else {
                    autopsyReport = analyzer.generateVideoAutopsy(video, report, gemKey)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                e.printStackTrace()
                try {
                    // Fallback to local template if Gemini call fails, ensuring zero crashes
                    autopsyReport = analyzer.generateLocalAutopsyFallback(video, report)
                } catch (fallbackEx: Exception) {
                    if (fallbackEx is kotlinx.coroutines.CancellationException) throw fallbackEx
                    autopsyError = e.localizedMessage ?: "Failed to generate video autopsy reports."
                }
            } finally {
                loadingAutopsy = false
            }
        }
    }

    fun navigateBackToResults(report: FullGrowTubeReport) {
        uiState = ScreenState.Results(report)
    }

    fun getLatestVideoAnalysis(report: FullGrowTubeReport) {
        val latestVideo = report.stats.recentVideos.firstOrNull() ?: return
        if (latestVideoAnalysis != null || loadingLatestVideoAnalysis) return
        
        activeLatestVideoAnalysisJob?.cancel()
        loadingLatestVideoAnalysis = true
        latestVideoAnalysisError = null

        val demoMode = (youtubeApiKey.isEmpty() || geminiApiKey.isEmpty() || 
                        report.stats.handle.lowercase().contains("demo"))

        activeLatestVideoAnalysisJob = viewModelScope.launch {
            try {
                if (demoMode) {
                    kotlinx.coroutines.delay(1200)
                    latestVideoAnalysis = generateLocalLatestVideoAnalysisFallback(latestVideo, report)
                } else {
                    latestVideoAnalysis = analyzer.analyzeLatestVideo(latestVideo, report, geminiApiKey)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                e.printStackTrace()
                latestVideoAnalysisError = e.localizedMessage ?: "Failed to generate latest video performance analysis."
                // Safe local fallback on Gemini error
                latestVideoAnalysis = generateLocalLatestVideoAnalysisFallback(latestVideo, report)
            } finally {
                loadingLatestVideoAnalysis = false
            }
        }
    }

    fun generateLocalLatestVideoAnalysisFallback(video: VideoItem, report: FullGrowTubeReport): LatestVideoAnalysis {
        val niche = report.analysis.microNiche ?: "Main Niche Content"
        val channelAvg = report.stats.averageViews
        val isStrong = video.views > channelAvg

        val verdict = if (isStrong) {
            "Your latest video \"${video.title}\" is starting off exceptionally strong, exceeding your typical baseline by showing higher view velocity."
        } else {
            "Your latest upload \"${video.title}\" is having a slightly slower start than average. It is currently underperforming relative to your historical benchmarks."
        }

        val reasons = if (isStrong) {
            "High keyword alignment and direct interest in \"$niche\" are boosting initial impressions. The click-through rate is sustained by high topical relevance and active community engagement in the first few hours."
        } else {
            "The initial CTR might be hindered by a less descriptive title or suboptimal publishing time. A high density of competitors in \"$niche\" uploading around the same window has temporarily diluted the home feed discovery."
        }

        val actions = listOf(
            "Pin an engaging comment with a question or link to your top video to direct viewers and boost retention.",
            "Add this video to a thematic high-performing playlist like \"Best of ${report.stats.name}\" to drive autoplay traffic.",
            "Promote this upload directly with customized graphics or community posts targeting your most active audience segment today."
        )

        return LatestVideoAnalysis(verdict, reasons, actions)
    }

    fun triggerAnalysis(channelQuery: String = channelInput) {
        if (channelQuery.trim().isEmpty()) {
            uiState = ScreenState.Error("Please enter a YouTube Channel handle, URL, or ID.")
            return
        }

        clearCachesForChannel(channelQuery)

        latestVideoAnalysis = null
        latestVideoAnalysisError = null


        uiState = ScreenState.Loading
        loadingStep = 1
        loadingStatusText = "Initializing analysis..."
        activeTab = 0

        activeAnalysisJob = viewModelScope.launch {
            val oldCompetitors = mutableListOf<CompetitorAnalysis>()
            try {
                val list = prefs.getRecentChannels()
                val matched = list.firstOrNull { it.handle.lowercase().trim() == channelQuery.lowercase().trim() }
                if (matched != null) {
                    val oldReport = reportAdapter.fromJson(matched.reportJson)
                    if (oldReport != null) {
                        val comps = oldReport.analysis.competitors ?: emptyList()
                        comps.forEach { comp ->
                            if (oldCompetitors.none { it.handle.lowercase().trim() == comp.handle.lowercase().trim() }) {
                                oldCompetitors.add(comp)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    prefs.deductQuota(300)
                }
                remainingQuota = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    prefs.getRemainingQuota()
                }

                val result = analyzer.analyzeChannel(channelQuery, object : GrowTubeAnalyzer.ProgressListener {
                    override fun onProgressUpdate(step: Int, text: String) {
                        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            loadingStep = step
                            loadingStatusText = text
                        }
                    }
                })

                var finalReport = result
                if (oldCompetitors.isNotEmpty()) {
                    val resultCompetitors = result.analysis.competitors ?: emptyList()
                    val combinedList = oldCompetitors.toMutableList()
                    resultCompetitors.forEach { rc ->
                        if (combinedList.none { it.handle.lowercase().trim() == rc.handle.lowercase().trim() }) {
                            combinedList.add(rc)
                        }
                    }
                    finalReport = regenerateGapAnalysisAndStrategies(result, combinedList)
                }

                saveReportLocal(finalReport)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                e.printStackTrace()
                uiState = ScreenState.Error(e.message ?: "Analysis failed unexpected. Please try again.")
            }
        }
    }

    fun cancelActiveAnalysis() {
        activeAnalysisJob?.cancel()
        uiState = ScreenState.Home
    }

    fun loadFromCache(item: RecentChannelItem) {
        latestVideoAnalysis = null
        latestVideoAnalysisError = null
        competitorsTabResponse = null
        competitorsTabError = null
        competitorsTabHandle = null
        strategyTabResponse = null
        strategyTabError = null
        strategyTabHandle = null

        uiState = ScreenState.Loading
        loadingStep = 1
        loadingStatusText = "Loading cached project DNA..."
        activeTab = 0

        viewModelScope.launch {
            try {
                val report = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    reportAdapter.fromJson(item.reportJson)
                }
                if (report != null) {
                    channelInput = item.handle
                    uiState = ScreenState.Results(report)
                    
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        prefs.addRecentChannel(item)
                    }
                    recentChannels = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        prefs.getRecentChannels()
                    }
                } else {
                    uiState = ScreenState.Error("Failed to parse cached report.")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                uiState = ScreenState.Error("Error loading cached channel run: ${e.message}")
            }
        }
    }

    fun deleteRecentChannel(handle: String) {
        viewModelScope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                prefs.deleteRecentChannel(handle)
            }
            recentChannels = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                prefs.getRecentChannels()
            }
        }
    }

    fun reanalyzeChannel(handle: String) {
        channelInput = handle
        triggerAnalysis(handle)
    }

    fun shareExportReport(context: android.content.Context, report: FullGrowTubeReport) {
        val s = report.stats
        val a = report.analysis
        
        val videoItemsText = s.recentVideos.joinToString("\n") { 
            "- ${it.title} | Views: ${it.views.formatNumber()} | Likes: ${it.likes.formatNumber()}" 
        }

        val bestVideosText = a.bestVideos.joinToString("\n") {
            "🌟 ${it.title}\n   Why: ${it.reason}"
        }

        val worstVideosText = a.worstVideos.joinToString("\n") {
            "⚠️ ${it.title}\n   Why: ${it.reason}"
        }

        val competitorTableText = a.competitors.joinToString("\n") {
            "- ${it.name} (${it.handle}) | Subs: ${it.subscribers.formatNumber()} | Avg Views: ${it.avgViews.formatNumber()} | Freq: ${it.uploadFrequency}"
        }

        val designIdeasText = a.videoIdeas.joinToString("\n") {
            "- [${it.format}] ${it.title} (${it.difficulty})\n  Why click: ${it.whyItWillWork}"
        }

        val calendarText = a.contentCalendar.joinToString("\n") {
            "- Day ${it.dayNumber} (${it.dateText}): ${it.videoTitle}"
        }

        val exportBody = """
            ==================================================
                   GROWTUBE AI CHANNEL STRATEGY REPORT
            ==================================================
            CHANNEL DNA:
            - Channel Name: ${s.name}
            - Channel Handle: ${s.handle}
            - Subscriber Count: ${s.subscribers.formatNumber()}
            - Total Video Count: ${s.totalVideos.formatNumber()}
            - Total View Count: ${s.totalViews.formatNumber()}
            - Average View Metric: ${s.averageViews.formatNumber()}
            - Calculated Upload Cadence: ${s.uploadFrequencyText}
            
            DIAGNOSTICS & AUDIENCE INDEX:
            - Health Score Rank: ${a.healthScore}/100
            - Health Rationale: ${a.healthReason}
            - Target Audience Profile: ${a.audienceProfile}
            - Key Strategic Roadblock: ${a.mainWeakness}
            
            HISTORICAL ANALYTICAL BREAKDOWNS:
            Top 3 Performing Video Concepts:
            $bestVideosText
            
            Lowest 3 Underperforming Video Concepts:
            $worstVideosText
            
            COMPETITIVE MARKET GAPS:
            Niche Competitors Comparison Profile:
            $competitorTableText
            
            Competitive Unserved Content Gaps:
            ${a.gapAnalysis}
            
            Key Competitor Strengths to Adopt:
            ${a.strategiesToAdopt.joinToString("\n") { "👉 $it" }}
            
            Your Unique Competitive Advantage:
            ${a.yourAdvantage}
            
            30-DAY CONTENT INCUBATOR PLANS:
            10 Highly Tuned Video Blueprints:
            $designIdeasText
            
            30-Day Content Calendar Road-map:
            $calendarText
            
            UPLOAD AND OPTIMIZATION CADENCE:
            - Best Performing Upload Weekdays: ${a.bestUploadDays.joinToString(", ")}
            - Recommended Length: ${a.recommendedLength}
            - Recommended Shorts vs Longs Distribution Ratio: ${a.shortsVsLongRatio}
            
            Generated via GrowTube — YouTube Channel Analyzer
            ==================================================
        """.trimIndent()

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "GrowTube Blueprint - ${s.name}")
            putExtra(Intent.EXTRA_TEXT, exportBody)
        }
        
        val chooserIntent = Intent.createChooser(shareIntent, "Share Strategy Report via...")
        // Since we are launching from standard UI context, no FLAG_ACTIVITY_NEW_TASK is required
        context.startActivity(chooserIntent)
    }

    private fun Long.formatNumber(): String {
        return when {
            this >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", this / 1_000_000_000.0)
            this >= 1_000_000 -> String.format(Locale.US, "%.1fM", this / 1_000_000.0)
            this >= 1_000 -> String.format(Locale.US, "%.1fK", this / 1_000.0)
            else -> this.toString()
        }
    }

    private fun extractHandleOrChannelIdLocal(input: String): Pair<String, Boolean> {
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

    suspend fun fetchAndAddManualCompetitor(
        input: String, 
        currentReport: FullGrowTubeReport
    ): String = withContext(Dispatchers.IO) {
        val trimmedInput = input.trim()
        if (trimmedInput.isEmpty()) {
            return@withContext "Please enter a channel @handle or URL."
        }

        val currentCompetitors = currentReport.analysis.competitors ?: emptyList()
        if (currentCompetitors.size >= 15) {
            return@withContext "Maximum limit of 15 competitors reached."
        }

        val ytKey = prefs.getYoutubeApiKey().trim()
        val isDemoMode = ytKey.isEmpty() || trimmedInput.lowercase().contains("demo") || currentReport.stats.handle.lowercase().contains("demo")

        val parsedInput = extractHandleOrChannelIdLocal(trimmedInput)

        val newCompetitor = if (isDemoMode) {
            val nameClean = if (parsedInput.first.startsWith("@")) parsedInput.first.substring(1) else parsedInput.first
            val formattedName = nameClean.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } + " Tech"
            val mockSubs = (100_000..900_000).random().toLong()
            val mockAvgViews = (20_000..250_000).random().toLong()
            val mockFreq = listOf("2 vids/week", "1 vid/week", "3 vids/month", "1 vid/month").random()
            val mockFormat = listOf("Shorts Format", "Long-Form Guides", "Podcasts", "Standard Videos").random()
            val mockStrength = listOf("Very engaging thumbnails and titles.", "Great content pacing keeps viewers hooked.", "Excellent visuals and clean audio engineering.").random()
            
            CompetitorAnalysis(
                name = formattedName,
                handle = parsedInput.first,
                subscribers = mockSubs,
                avgViews = mockAvgViews,
                uploadFrequency = mockFreq,
                contentFormat = mockFormat,
                biggestStrength = mockStrength,
                isManual = true
            )
        } else {
            try {
                val ytService = YouTubeRetrofitClient.service
                val channelResponse = if (parsedInput.second) {
                    ytService.getChannels(part = "snippet,statistics,contentDetails", id = parsedInput.first, apiKey = ytKey)
                } else {
                    ytService.getChannels(part = "snippet,statistics,contentDetails", forHandle = parsedInput.first, apiKey = ytKey)
                }

                val channelItem = channelResponse.items?.firstOrNull()
                    ?: return@withContext "Channel not found. Check handle and try again."

                val chName = channelItem.snippet?.title ?: "Unknown Channel"
                val chHandle = channelItem.snippet?.customUrl ?: parsedInput.first
                val subs = channelItem.statistics?.subscriberCount?.toLongOrNull() ?: 0L
                val uploadsPlaylistId = channelItem.contentDetails?.relatedPlaylists?.uploads

                var calculatedAvgViews = 0L
                var calculatedFreqStr = "1 vid/week"

                if (!uploadsPlaylistId.isNullOrEmpty()) {
                    try {
                        val playlistResponse = ytService.getPlaylistItems(
                            part = "snippet,contentDetails",
                            playlistId = uploadsPlaylistId,
                            maxResults = 10,
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

                            if (totalFetched > 0) {
                                calculatedAvgViews = sumViews / totalFetched
                            }
                            
                            if (dates.size >= 2) {
                                try {
                                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
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
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                CompetitorAnalysis(
                    name = chName,
                    handle = chHandle,
                    subscribers = subs,
                    avgViews = calculatedAvgViews,
                    uploadFrequency = calculatedFreqStr,
                    contentFormat = "Standard Video",
                    biggestStrength = "Custom tracked channel performance.",
                    isManual = true
                )
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext "Channel not found. Check handle and try again."
            }
        }

        val updatedList = currentCompetitors.toMutableList().apply { add(newCompetitor) }
        val updatedReport = regenerateGapAnalysisAndStrategies(currentReport, updatedList)
        saveReportLocal(updatedReport)

        return@withContext "SUCCESS"
    }

    fun removeManualCompetitor(competitor: CompetitorAnalysis, currentReport: FullGrowTubeReport) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentCompetitors = currentReport.analysis.competitors ?: emptyList()
            val updatedList = currentCompetitors.toMutableList().apply { remove(competitor) }
            val updatedReport = regenerateGapAnalysisAndStrategies(currentReport, updatedList)
            saveReportLocal(updatedReport)
        }
    }

    fun addAISelectedCompetitors(selectedList: List<CompetitorAnalysis>, currentReport: FullGrowTubeReport) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentCompetitors = currentReport.analysis.competitors ?: emptyList()
            
            // Filter out duplicates by handle (case insensitive, trimmed)
            val filteredNew = selectedList.filter { newComp ->
                currentCompetitors.none { it.handle.lowercase().trim() == newComp.handle.lowercase().trim() }
            }
            
            val updatedList = currentCompetitors.toMutableList()
            val totalAllowed = 15 - updatedList.size
            if (totalAllowed > 0) {
                val truncatedNew = filteredNew.take(totalAllowed)
                updatedList.addAll(truncatedNew.map { it.copy(isManual = true) })
            }
            
            val updatedReport = regenerateGapAnalysisAndStrategies(currentReport, updatedList)
            saveReportLocal(updatedReport)
        }
    }

    private suspend fun saveReportLocal(updatedReport: FullGrowTubeReport) {
        try {
            val list = prefs.getRecentChannels()
            val matchedIndex = list.indexOfFirst { it.handle == updatedReport.stats.handle }
            if (matchedIndex != -1) {
                val updatedItem = list[matchedIndex].copy(reportJson = reportAdapter.toJson(updatedReport))
                val updatedListRecents = list.toMutableList().apply { set(matchedIndex, updatedItem) }
                prefs.saveRecentChannels(updatedListRecents)
                withContext(Dispatchers.Main) {
                    recentChannels = updatedListRecents
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        withContext(Dispatchers.Main) {
            val compRes = competitorsTabResponse
            if (compRes != null) {
                val newCompRes = compRes.copy(
                    competitors = updatedReport.analysis.competitors ?: emptyList(),
                    gapAnalysis = updatedReport.analysis.gapAnalysis ?: "",
                    strategiesToAdopt = updatedReport.analysis.strategiesToAdopt ?: emptyList(),
                    yourAdvantage = updatedReport.analysis.yourAdvantage ?: ""
                )
                competitorsTabResponse = newCompRes
                withContext(Dispatchers.IO) {
                    prefs.saveCompetitorsCache(updatedReport.stats.handle, newCompRes)
                }
            }
            
            val stratRes = strategyTabResponse
            if (stratRes != null) {
                val newStratRes = stratRes.copy(
                    bestUploadDays = updatedReport.analysis.bestUploadDays ?: emptyList(),
                    recommendedLength = updatedReport.analysis.recommendedLength ?: "",
                    shortsVsLongRatio = updatedReport.analysis.shortsVsLongRatio ?: "",
                    videoIdeas = updatedReport.analysis.videoIdeas ?: emptyList(),
                    contentCalendar = updatedReport.analysis.contentCalendar ?: emptyList()
                )
                strategyTabResponse = newStratRes
                withContext(Dispatchers.IO) {
                    prefs.saveStrategyCache(updatedReport.stats.handle, newStratRes)
                }
            }
            
            uiState = ScreenState.Results(updatedReport)
        }
    }

    fun runAISuggestionsSearch(currentReport: FullGrowTubeReport) {
        viewModelScope.launch {
            aiSuggestionsLoading = true
            aiSuggestionsError = null
            aiCompetitorSuggestions = emptyList()
            try {
                val suggestions = fetchAICompetitorSuggestions(currentReport)
                aiCompetitorSuggestions = suggestions
                if (suggestions.isEmpty()) {
                    aiSuggestionsError = "No verified competitor channels found. Try manual search or try again."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                aiSuggestionsError = e.message ?: "An error occurred during search."
            } finally {
                aiSuggestionsLoading = false
            }
        }
    }

    suspend fun fetchAICompetitorSuggestions(
        currentReport: FullGrowTubeReport
    ): List<CompetitorAnalysis> = withContext(Dispatchers.IO) {
        val ytKey = prefs.getYoutubeApiKey().trim()
        val geminiKey = getEffectiveGeminiApiKey()
        val isDemoMode = ytKey.isEmpty() || geminiKey.isEmpty() || currentReport.stats.handle.lowercase().contains("demo")
        
        if (isDemoMode) {
            return@withContext getMockAISuggestions(currentReport.analysis.primaryCategory ?: currentReport.analysis.microNiche)
        }
        
        val promptText = """
            Suggest exactly 20 real YouTube channel handles that create content in the exact same micro-niche as this channel:
            Channel Name: ${currentReport.stats.name}
            Handle: ${currentReport.stats.handle}
            Micro-niche: ${currentReport.analysis.microNiche ?: "unknown"}
            Content Format: ${currentReport.analysis.contentFormat ?: "unknown"}
            
            Return a raw JSON array of objects. Do not write any prefaces, markdown, or code block formatting.
            The JSON structure MUST be exactly:
            [
              {
                "name": "Channel Name",
                "handle": "@handle",
                "contentFormat": "Specific content format matching user's style",
                "biggestStrength": "Their biggest strength"
              }
            ]
        """.trimIndent()
        
        val systemInstruction = "You are a professional YouTube analyst. Generate 20 real or extremely realistic competitor channel handles starting with '@' based on the user's micro-niche."
        
        val suggestedList = try {
            val service = GeminiRetrofitClient.service
            val request = GeminiRequest(
                contents = listOf(GeminiContent(listOf(GeminiPart(promptText)))),
                systemInstruction = GeminiContent(listOf(GeminiPart(systemInstruction))),
                generationConfig = GeminiGenerationConfig(responseMimeType = "application/json", temperature = 0.5f)
            )
            val response = service.generateContent(apiKey = geminiKey, request = request)
            val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            val cleanedJson = responseText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, CompetitorAnalysis::class.java)
            val adapter = moshi.adapter<List<CompetitorAnalysis>>(type)
            adapter.fromJson(cleanedJson) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
        
        if (suggestedList.isEmpty()) {
            return@withContext emptyList()
        }
        
        val verifiedCompetitors = mutableListOf<CompetitorAnalysis>()
        val ytService = YouTubeRetrofitClient.service
        for (suggestedComp in suggestedList) {
            try {
                val verifiedComp = analyzer.verifyAndFetchCompetitorStats(
                    suggestedHandle = suggestedComp.handle,
                    suggestedName = suggestedComp.name,
                    userSubs = currentReport.stats.subscribers,
                    apiKey = ytKey,
                    ytService = ytService
                )
                if (verifiedComp != null) {
                    verifiedCompetitors.add(verifiedComp.copy(
                        contentFormat = suggestedComp.contentFormat ?: "Verified Format",
                        biggestStrength = suggestedComp.biggestStrength ?: "Strong niche presence"
                    ))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        return@withContext verifiedCompetitors
    }

    suspend fun regenerateGapAnalysisAndStrategies(
        currentReport: FullGrowTubeReport,
        updatedCompetitors: List<CompetitorAnalysis>
    ): FullGrowTubeReport = withContext(Dispatchers.IO) {
        if (updatedCompetitors.isEmpty()) {
            val emptyAnalysis = currentReport.analysis.copy(
                competitors = emptyList(),
                gapAnalysis = "Add competitors to see analysis. Use manual search or let AI suggest channels.",
                strategiesToAdopt = emptyList()
            )
            return@withContext currentReport.copy(analysis = emptyAnalysis)
        }

        val ytKey = prefs.getYoutubeApiKey().trim()
        val geminiKey = getEffectiveGeminiApiKey()
        val isDemoMode = ytKey.isEmpty() || geminiKey.isEmpty() || currentReport.stats.handle.lowercase().contains("demo")

        if (isDemoMode) {
            val category = currentReport.analysis.primaryCategory ?: ""
            val (gapText, strategies) = when {
                category.lowercase().contains("cook") -> Pair(
                    "Most of your added cooking competitors focus on traditional stove-top b-roll recipes. Your quick-paced voiceover style has a massive unfilled advantage by offering rapid, single-dish convenience shortcuts that save 30+ minutes of cleanups.",
                    listOf(
                        "Frame thumbnails with high-contrast text overlays directly indicating time savings.",
                        "Add a sensory ASMR audio layer to emphasize first-bite satisfaction points.",
                        "Standardize your ingredient cards into single-swipe visual overlays inside of the first 10 seconds of video playtime."
                    )
                )
                category.lowercase().contains("fit") -> Pair(
                    "Your added competitors frequently demo general exercises with excessive cinematic cuts that hide joint movement. A massive niche gap exists for unbroken side-angle posture comparisons showing common structural failures.",
                    listOf(
                        "Introduce anatomical highlight lines on-screen to mark real-time postural alignment.",
                        "Structure descriptions with timestamps mapped to progressive joint relief.",
                        "Co-develop vertical short-forms focusing entirely on anti-injury corrective guides."
                    )
                )
                category.lowercase().contains("gam") -> Pair(
                    "Added competitors rely heavily on standard gameplay b-rolls with generic EDM loops. A clear content gap exists for structured glitch-by-frame TAS tutorials narrated with deep tech mechanics.",
                    listOf(
                        "Include standard frame indicators on-screen for precise glitch activation windows.",
                        "Design custom graphics summarizing setup requirements for complex skips.",
                        "Consistently challenge popular speedrun myths with side-by-side evidence."
                    )
                )
                category.lowercase().contains("tech") -> Pair(
                    "Existing competitors cover general Jetpack Compose syntax, leaving a large gap for enterprise-scale architecture examples highlighting memory leak debugging and side-effect optimization.",
                    listOf(
                        "Incorporate visual code-diff banners in the first 5 seconds to highlight optimizations.",
                        "Establish standard dark themes with high-contrast pastel colors for typography.",
                        "Publish monthly complete single-file scaffold templates for visual builders."
                    )
                )
                else -> Pair(
                    "Most music rivals publish standard looping audio tracks with raw static wallpapers. A high-priority gap exists for dynamic lyric visualization paired with high-quality AI vocal tutorials explaining remix stems.",
                    listOf(
                        "Animate lyric texts in sync with active tempo beats.",
                        "Highlight production stems in the title structure to capture searching bedroom producers.",
                        "Adopt a consistent dark retro cyberpunk visual canvas across artwork uploads."
                    )
                )
            }
            
            val updatedAnalysis = currentReport.analysis.copy(
                competitors = updatedCompetitors,
                gapAnalysis = gapText,
                strategiesToAdopt = strategies
            )
            return@withContext currentReport.copy(analysis = updatedAnalysis)
        }

        val promptText = """
            You are a premier YouTube strategy consultant.
            Analyze the following channel and its manually added competitors to find the competitive gaps and strategies.
            
            USER CHANNEL:
            Name: ${currentReport.stats.name} (${currentReport.stats.handle})
            Identity: ${currentReport.analysis.contentIdentity ?: ""}
            Format: ${currentReport.analysis.contentFormat ?: ""}
            
            ADDED COMPETITORS:
            ${updatedCompetitors.joinToString("\n") { "- ${it.name} (${it.handle}) | Avg Views: ${it.avgViews} | Format: ${it.contentFormat} | Strength: ${it.biggestStrength}" }}
            
            Please formulate:
            1. gapAnalysis: A detailed 2-sentence description of competitive content gaps (what topics or formats those specific competitors are completely missing, written respecting the user's content format style).
            2. strategiesToAdopt: A JSON array of exactly 3 distinct, highly actionable production or marketing strategies that directly resolve user's disadvantage or leverage those competitive gaps.
            
            Return raw JSON with exactly these keys:
            {
              "gapAnalysis": "The gap analysis...",
              "strategiesToAdopt": [
                "Strategy 1...",
                "Strategy 2...",
                "Strategy 3..."
              ]
            }
            Do not include prefaces, markdown, or code block formatting.
        """.trimIndent()

        val systemInstruction = "You are a professional YouTube analyst. Formulate highly specific competitive gap analysis and strategies based on the provided list of competitor channels."

        try {
            val service = GeminiRetrofitClient.service
            val request = GeminiRequest(
                contents = listOf(GeminiContent(listOf(GeminiPart(promptText)))),
                systemInstruction = GeminiContent(listOf(GeminiPart(systemInstruction))),
                generationConfig = GeminiGenerationConfig(responseMimeType = "application/json", temperature = 0.3f)
            )
            val response = service.generateContent(apiKey = geminiKey, request = request)
            val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            val cleanedJson = responseText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

            val mapType = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            val adapter = moshi.adapter<Map<String, Any>>(mapType)
            val map = adapter.fromJson(cleanedJson) ?: emptyMap()

            val gap = map["gapAnalysis"] as? String ?: "No verified gaps analyzed yet."
            val rawStrategies = map["strategiesToAdopt"] as? List<*>
            val strategies = rawStrategies?.mapNotNull { it as? String } ?: emptyList()

            val updatedAnalysis = currentReport.analysis.copy(
                competitors = updatedCompetitors,
                gapAnalysis = gap,
                strategiesToAdopt = strategies
            )
            currentReport.copy(analysis = updatedAnalysis)
        } catch (e: Exception) {
            e.printStackTrace()
            val updatedAnalysis = currentReport.analysis.copy(competitors = updatedCompetitors)
            currentReport.copy(analysis = updatedAnalysis)
        }
    }

    private fun getMockAISuggestions(category: String?): List<CompetitorAnalysis> {
        val cat = category?.lowercase() ?: ""
        val (names, handles) = when {
            cat.contains("cook") -> Pair(
                listOf("Tasty Bites Cooking", "Epicurean Fast", "Chef Josh Cooks", "Easy Kitchen", "Pan & Fire", "Rapid Recipes", "Bistro Shorts", "Green Spatula", "Simply Seared", "Sizzle & Spice", "Home Chef Classics", "Gourmet Express", "Pan Craft", "Flavour Lab", "Noodle Quest", "Baker Street", "Grill Masters", "The Urban Skillet", "Spoon & Whisk", "Sweet Treats AI"),
                listOf("@tastybites", "@epicureanfast", "@joshcooks", "@easykitchen", "@panfire", "@rapidrecipes", "@bistroshorts", "@greenspatula", "@simplyseared", "@sizzlespice", "@homechefclassics", "@gourmetexpress", "@pancraft", "@flavourlab", "@noodlequest", "@bakerstreet", "@grillmasters", "@urbanskillet", "@spoonwhisk", "@sweettreats")
            )
            cat.contains("fit") || cat.contains("phys") || cat.contains("work") || cat.contains("posture") -> Pair(
                listOf("Apex Posture", "BioMechanics Coach", "The Form Correct", "Posture Fix Pro", "Ergo Health", "Spine alignment Clinic", "Daily Mobilise", "Anatomy Athletics", "Iron Form", "Kinetic Posture", "TheraFit AI", "Zenith Posture", "Spinal Craft", "Form First Daily", "Mobile Mechanics", "Joint Health Pro", "Functional Fit", "Vertebrae Co", "Ergonomic Life", "Active Posture"),
                listOf("@apexposture", "@biomechcoach", "@formcorrect", "@posturefixpro", "@ergohealth", "@spineclinic", "@dailymobilise", "@anatomyathletics", "@ironform", "@kineticposture", "@therafitai", "@zenithposture", "@spinalcraft", "@formfirstdaily", "@mobilemechanics", "@jointhealthpro", "@functionalfit", "@vertebraeco", "@ergonomiclife", "@activeposture")
            )
            cat.contains("gam") -> Pair(
                listOf("Retro Speedruns", "Gamer Pro Guides", "Speedrun Mechanics", "No damage Runner", "Sora's Speedruns", "Glitched Out", "Super Gamer Bros", "Pro Gamer Hacks", "The Speed Lab", "TAS Runs Explained", "Pixel Perfect", "Boss Masters", "Platformer Pro", "Glitch Factory", "Checkpoint Hub", "Speed Limit", "High Score AI", "Next Level Gaming", "Save State Pro", "Infinite Retries"),
                listOf("@retrospeedruns", "@gamerguides", "@speedrunmech", "@nodamagerunner", "@soraspeedruns", "@glitchedout", "@supergamerbros", "@progamerhacks", "@speedlab", "@tasrunsexplained", "@pixelperfect", "@bossmasters", "@platformerpro", "@glitchfactory", "@checkpointhub", "@speedlimit", "@highscoreai", "@nextlevelgaming", "@savestatepro", "@infinitetry")
            )
            cat.contains("tech") || cat.contains("soft") || cat.contains("code") || cat.contains("program") || cat.contains("compose") -> Pair(
                listOf("Compose Coders", "Kotlin Kraft", "The Tech Architect", "Android Craft", "Stack Overflow Pro", "UI Masters Academy", "Jetpack Gurus", "The Coding Engine", "ByteSized Android", "UI Design Hub", "Mobile Wizards", "Dev Flow", "Code Pulse", "Logic Loop", "Algorithm Pro", "Pixel Craft", "App Builders AI", "Debug Academy", "Compose Pro", "The APK Lab"),
                listOf("@composecoders", "@kotlinkraft", "@techarchitect", "@androidcraft", "@stackoverflowpro", "@uimasters", "@jetpackgurus", "@codingengine", "@bytesizedandroid", "@uidesignhub", "@mobilewizards", "@devflow", "@codepulse", "@logicloop", "@algorithmpro", "@pixelcraft", "@appbuildersai", "@debugacademy", "@composepro", "@apklab")
            )
            else -> Pair(
                listOf("Punjabi AI Beats", "Urban Folk Fusion", "Legends Alive AI", "Sidhu Moosewala AI Tribute", "Desi AI Remixes", "Sufi Electronic", "High Bass AI", "Folk AI Remade", "Modern Desi Beats", "Vocal Magic AI", "Tune Craft Punjabi", "AI Voice Lab", "Retro Vocal Remix", "Future Desi Studio", "Bhangra Beatmaker", "Soundscape Punjab", "Acoustic AI Tribute", "Groove Syndicate", "Rhythm & AI", "Pure Punjabi Audio"),
                listOf("@punjabiaibeats", "@urbanfolkfusion", "@legendaliveai", "@sidhumoosewalaai", "@desiairemixes", "@sufielectronic", "@highbassaibeats", "@folkairemades", "@moderndesibeats", "@vocalmagicai", "@tunecraft", "@aivoicelab", "@retrovocal", "@futuredesi", "@bhangrabeatmaker", "@soundscapepunjab", "@acousticaitribute", "@groovesyndicate", "@rhythmai", "@purepunjabi")
            )
        }
        
        val formatList = when {
            cat.contains("cook") -> listOf("Rapid visual cut instructionals", "Cinematic cooking shorts", "Long-form guide recipes")
            cat.contains("fit") -> listOf("Anatomic text overlays", "Interactive posture charts", "Slow-motion correction tutorials")
            cat.contains("gam") -> listOf("Speedrun frame breakdowns", "Live commentaries & tips", "Short game-break tutorial videos")
            cat.contains("tech") -> listOf("Jetpack Compose live coding b-rolls", "Visual UI animation guides", "Deep-dive Kotlin concepts")
            else -> listOf("AI vocal synthetic remixes", "Modern Desi beats fusion", "Karaoke background lyric edits")
        }

        val strengthsList = listOf(
            "Vibrant, crisp, high-contrast visual framing.",
            "Instant attention-grabbing hook b-roll pacing.",
            "Exceptionally clear call to action.",
            "Very engaging community conversation interaction.",
            "Excellent storytelling backstory pacing."
        )

        val list = mutableListOf<CompetitorAnalysis>()
        for (i in 0 until 20) {
            val name = names.getOrNull(i) ?: "Competitor $i"
            val handle = handles.getOrNull(i) ?: "@competitor$i"
            val subs = (50_000..850_000).random().toLong()
            val views = (15_000..280_000).random().toLong()
            val freq = listOf("2.0 vids/week", "1.5 vids/week", "3.0 vids/month", "4.0 vids/month").random()
            val formatTag = formatList.random()
            val strength = strengthsList.random()
            
            list.add(
                CompetitorAnalysis(
                    name = name,
                    handle = handle,
                    subscribers = subs,
                    avgViews = views,
                    uploadFrequency = freq,
                    contentFormat = formatTag,
                    biggestStrength = strength,
                    isManual = false
                )
            )
        }
        return list
    }

    // ==========================================
    // CHANNEL CLONE WORKFLOW ACTION METHODS
    // ==========================================

    fun navigateToChannelClone() {
        cloneActiveState = 0
        cloneGoal = ""
        cloneFormat = ""
        cloneChannelLink = ""
        cloneChannelLink2 = ""
        cloneChannelLink3 = ""
        cloneTranscripts = emptyList()
        cloneTranscriptInputInput = ""
        cloneTopicChoice = ""
        cloneCustomTopic = ""
        cloneCompetitorChoice = ""
        cloneCompetitorLinks = ""
        cloneStyleDNA = null
        cloneScript = null
        cloneSelectedHookIndex = -1
        cloneScriptFeedback = ""
        cloneVisualDescription = ""
        cloneVisualProfile = null
        cloneImagePrompts = emptyList()
        cloneVideoPromptsEnabled = null
        cloneThumbnailPreference = ""
        cloneThumbnails = emptyList()
        cloneMetadata = null
        cloneCalendarEnabled = null
        cloneCalendar = emptyList()

        uiState = ScreenState.ChannelClone
    }

    fun navigateToHomeFromClone() {
        uiState = ScreenState.Home
    }

    fun proceedToState1(goal: String, format: String) {
        cloneGoal = goal
        cloneFormat = format
        cloneActiveState = 1
    }

    fun proceedToState2(link: String, link2: String, link3: String) {
        cloneChannelLink = link
        cloneChannelLink2 = link2
        cloneChannelLink3 = link3
        cloneActiveState = 2
        fetchSuggestionsForCloneChannels()
    }

    fun searchYouTubeChannelsForCompetitors(query: String) {
        competitorSearchQuery = query
        val q = query.trim()
        if (q.isEmpty()) {
            competitorSearchResults = emptyList()
            competitorSearchError = null
            return
        }
        
        competitorSearchLoading = true
        competitorSearchError = null
        
        viewModelScope.launch {
            val ytKey = prefs.getYoutubeApiKey().trim()
            val isDemo = ytKey.isEmpty() || q.lowercase().contains("demo")
            if (isDemo) {
                kotlinx.coroutines.delay(800)
                val demoResults = generateLocalSearchMocks(q)
                competitorSearchResults = demoResults
                competitorSearchLoading = false
            } else {
                try {
                    val ytService = YouTubeRetrofitClient.service
                    val searchResponse = ytService.searchChannels(query = q, apiKey = ytKey)
                    val items = searchResponse.items ?: emptyList()
                    val results = mutableListOf<CloneSearchChannelResult>()
                    
                    for (item in items) {
                        val chId = item.id.channelId
                        if (!chId.isNullOrEmpty()) {
                            val name = item.snippet?.title ?: "Unknown Channel"
                            val avatar = item.snippet?.thumbnails?.high?.url 
                                ?: item.snippet?.thumbnails?.medium?.url ?: ""
                            
                            var subCountText = "100K+ subscribers"
                            try {
                                val chDetails = ytService.getChannels(part = "statistics,snippet", id = chId, apiKey = ytKey)
                                val stats = chDetails.items?.firstOrNull()?.statistics
                                val customUrl = chDetails.items?.firstOrNull()?.snippet?.customUrl
                                val handle = customUrl ?: "@${name.lowercase().replace(" ", "")}"
                                val subs = stats?.subscriberCount?.toLongOrNull() ?: 100000L
                                subCountText = subs.formatNumber() + " subscribers"
                                results.add(
                                    CloneSearchChannelResult(
                                        name = name,
                                        handle = handle,
                                        avatarUrl = avatar,
                                        subscribersText = subCountText
                                    )
                                )
                            } catch (e: Exception) {
                                val handle = "@${name.lowercase().replace(" ", "")}"
                                results.add(
                                    CloneSearchChannelResult(
                                        name = name,
                                        handle = handle,
                                        avatarUrl = avatar,
                                        subscribersText = "Subscribers hidden"
                                    )
                                )
                            }
                        }
                    }
                    competitorSearchResults = results
                    competitorSearchLoading = false
                } catch (e: Exception) {
                    e.printStackTrace()
                    competitorSearchError = e.message ?: "An error occurred fetching search results."
                    competitorSearchLoading = false
                    competitorSearchResults = generateLocalSearchMocks(q)
                }
            }
        }
    }

    fun searchYouTubeChannelsForClone(query: String) {
        cloneSearchQuery = query
        val q = query.trim()
        if (q.isEmpty()) {
            cloneSearchResults = emptyList()
            cloneSearchError = null
            return
        }
        
        cloneSearchLoading = true
        cloneSearchError = null
        
        viewModelScope.launch {
            val ytKey = prefs.getYoutubeApiKey().trim()
            val isDemo = ytKey.isEmpty() || q.lowercase().contains("demo")
            if (isDemo) {
                kotlinx.coroutines.delay(800)
                val demoResults = generateLocalSearchMocks(q)
                cloneSearchResults = demoResults
                cloneSearchLoading = false
            } else {
                try {
                    val ytService = YouTubeRetrofitClient.service
                    val searchResponse = ytService.searchChannels(query = q, apiKey = ytKey)
                    val items = searchResponse.items ?: emptyList()
                    val results = mutableListOf<CloneSearchChannelResult>()
                    
                    for (item in items) {
                        val chId = item.id.channelId
                        if (!chId.isNullOrEmpty()) {
                            val name = item.snippet?.title ?: "Unknown Channel"
                            val avatar = item.snippet?.thumbnails?.high?.url 
                                ?: item.snippet?.thumbnails?.medium?.url ?: ""
                            
                            var subCountText = "100K+ subscribers"
                            try {
                                val chDetails = ytService.getChannels(part = "statistics,snippet", id = chId, apiKey = ytKey)
                                val stats = chDetails.items?.firstOrNull()?.statistics
                                val customUrl = chDetails.items?.firstOrNull()?.snippet?.customUrl
                                val handle = customUrl ?: "@${name.lowercase().replace(" ", "")}"
                                val subs = stats?.subscriberCount?.toLongOrNull() ?: 100000L
                                subCountText = subs.formatNumber() + " subscribers"
                                results.add(
                                    CloneSearchChannelResult(
                                        name = name,
                                        handle = handle,
                                        avatarUrl = avatar,
                                        subscribersText = subCountText
                                    )
                                )
                            } catch (e: Exception) {
                                val handle = "@${name.lowercase().replace(" ", "")}"
                                results.add(
                                    CloneSearchChannelResult(
                                        name = name,
                                        handle = handle,
                                        avatarUrl = avatar,
                                        subscribersText = "Subscribers hidden"
                                    )
                                )
                            }
                        }
                    }
                    cloneSearchResults = results
                } catch (e: Exception) {
                    e.printStackTrace()
                    cloneSearchResults = generateLocalSearchMocks(q)
                } finally {
                    cloneSearchLoading = false
                }
            }
        }
    }

    private fun generateLocalSearchMocks(query: String): List<CloneSearchChannelResult> {
        val q = query.lowercase().trim()
        val defaultList = listOf(
            CloneSearchChannelResult("MKBHD", "@mkbhd", "", "18.5M subscribers"),
            CloneSearchChannelResult("Gordon Ramsay", "@gordonramsay", "", "20.2M subscribers"),
            CloneSearchChannelResult("Coder Compose", "@composecoders", "", "120K subscribers"),
            CloneSearchChannelResult("Chef Culinary Secrets", "@chefculinary", "", "45K subscribers"),
            CloneSearchChannelResult("Punjabi Beats Studio", "@punjabi_beats", "", "230K subscribers"),
            CloneSearchChannelResult("Tech World Guides", "@techworld", "", "89K subscribers")
        )
        val filtered = defaultList.filter { it.name.lowercase().contains(q) || it.handle.lowercase().contains(q) }
        return if (filtered.isNotEmpty()) {
            filtered
        } else {
            val formatted = query.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            listOf(
                CloneSearchChannelResult(formatted, "@${query.lowercase().replace(" ", "")}", "", "150K subscribers"),
                CloneSearchChannelResult("$formatted Vlogs", "@${query.lowercase().replace(" ", "")}_vlogs", "", "42K subscribers"),
                CloneSearchChannelResult("$formatted Recipes", "@${query.lowercase().replace(" ", "")}_recipes", "", "95K subscribers")
            )
        }
    }

    fun fetchSuggestionsForCloneChannels() {
        loadingCloneSuggestions = true
        cloneSuggestionsError = null
        cloneSuggestedVideos = emptyList()
        
        viewModelScope.launch {
            val targets = listOf(cloneChannelLink, cloneChannelLink2, cloneChannelLink3).filter { it.isNotBlank() }
            if (targets.isEmpty()) {
                loadingCloneSuggestions = false
                return@launch
            }
            
            val ytKey = prefs.getYoutubeApiKey().trim()
            val list = mutableListOf<CloneSuggestedVideo>()
            
            for (target in targets) {
                val isDemo = ytKey.isEmpty() || target.lowercase().contains("demo")
                if (isDemo) {
                    list.addAll(generateMockVideosForChannel(target))
                } else {
                    try {
                        val parsedInput = extractHandleOrChannelIdLocal(target)
                        val ytService = YouTubeRetrofitClient.service
                        val channelResponse = if (parsedInput.second) {
                            ytService.getChannels(part = "snippet,statistics,contentDetails", id = parsedInput.first, apiKey = ytKey)
                        } else {
                            ytService.getChannels(part = "snippet,statistics,contentDetails", forHandle = parsedInput.first, apiKey = ytKey)
                        }
                        
                        val channelItem = channelResponse.items?.firstOrNull()
                        if (channelItem != null) {
                            val chName = channelItem.snippet?.title ?: target
                            val uploadsPlaylistId = channelItem.contentDetails?.relatedPlaylists?.uploads
                            if (!uploadsPlaylistId.isNullOrEmpty()) {
                                val playlistResponse = ytService.getPlaylistItems(
                                    part = "snippet,contentDetails",
                                    playlistId = uploadsPlaylistId,
                                    maxResults = 2,
                                    apiKey = ytKey
                                )
                                val items = playlistResponse.items ?: emptyList()
                                for (item in items) {
                                    val title = item.snippet?.title ?: "Upload"
                                    val vidId = item.snippet?.resourceId?.videoId ?: ""
                                    var viewsStr = "120K views"
                                    var ageStr = "3 days ago"
                                    
                                    if (vidId.isNotEmpty()) {
                                        try {
                                            val videoDet = ytService.getVideos(part = "statistics,snippet", ids = vidId, apiKey = ytKey)
                                            val vItem = videoDet.items?.firstOrNull()
                                            val views = vItem?.statistics?.viewCount?.toLongOrNull() ?: 120000L
                                            viewsStr = views.formatNumber() + " views"
                                            val pubAt = vItem?.snippet?.publishedAt?.take(10) ?: ""
                                            if (pubAt.isNotEmpty()) {
                                                ageStr = pubAt
                                            }
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }
                                    
                                    list.add(
                                        CloneSuggestedVideo(
                                            id = if (vidId.isNotEmpty()) vidId else java.util.UUID.randomUUID().toString(),
                                            title = title,
                                            channelName = chName,
                                            originalChannel = target,
                                            viewsText = viewsStr,
                                            ageText = ageStr,
                                            thumbnail = "https://img.youtube.com/vi/$vidId/hqdefault.jpg"
                                        )
                                    )
                                }
                            } else {
                                list.addAll(generateMockVideosForChannel(target))
                            }
                        } else {
                            list.addAll(generateMockVideosForChannel(target))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        list.addAll(generateMockVideosForChannel(target))
                    }
                }
            }
            
            if (list.isEmpty()) {
                targets.forEach { target ->
                    list.addAll(generateMockVideosForChannel(target))
                }
            }
            
            cloneSuggestedVideos = list
            loadingCloneSuggestions = false
        }
    }

    private fun generateMockVideosForChannel(target: String): List<CloneSuggestedVideo> {
        val cleanName = if (target.startsWith("@")) target.substring(1) else target
        val titleClean = cleanName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        
        val linkLower = target.lowercase()
        return when {
            linkLower.contains("cook") || linkLower.contains("food") || linkLower.contains("recipe") -> {
                listOf(
                    CloneSuggestedVideo(
                        id = "mock_cook_${(10..99).random()}",
                        title = "How to make a 5-Minute Crispy Cheese Pan Garlic Bread",
                        channelName = "$titleClean Culinary",
                        originalChannel = target,
                        viewsText = "${(50..950).random()}K views",
                        ageText = "${(2..20).random()} days ago",
                        thumbnail = ""
                    ),
                    CloneSuggestedVideo(
                        id = "mock_cook_${(10..99).random()}",
                        title = "My Secret Desi Tarka Midnight Noodles Recipe",
                        channelName = "$titleClean Culinary",
                        originalChannel = target,
                        viewsText = "${(100..450).random()}K views",
                        ageText = "${(1..5).random()} weeks ago",
                        thumbnail = ""
                    )
                )
            }
            linkLower.contains("tech") || linkLower.contains("code") || linkLower.contains("android") || linkLower.contains("compose") -> {
                listOf(
                    CloneSuggestedVideo(
                        id = "mock_tech_${(10..99).random()}",
                        title = "The New KSP Kotlin Trick That Speeds Up Builds by 80%",
                        channelName = "$titleClean Dev",
                        originalChannel = target,
                        viewsText = "${(20..150).random()}K views",
                        ageText = "${(2..10).random()} days ago",
                        thumbnail = ""
                    ),
                    CloneSuggestedVideo(
                        id = "mock_tech_${(10..99).random()}",
                        title = "Avoid This Massive Memory Leak inside your Compose Canvas!",
                        channelName = "$titleClean Dev",
                        originalChannel = target,
                        viewsText = "${(40..300).random()}K views",
                        ageText = "${(1..3).random()} weeks ago",
                        thumbnail = ""
                    )
                )
            }
            else -> {
                listOf(
                    CloneSuggestedVideo(
                        id = "mock_gen_${(10..99).random()}",
                        title = "Recreating Vintage Desi Classic Synth Loop (Production Breakdown)",
                        channelName = "$titleClean Studio",
                        originalChannel = target,
                        viewsText = "${(80..1200).random()}K views",
                        ageText = "${(1..10).random()} days ago",
                        thumbnail = ""
                    ),
                    CloneSuggestedVideo(
                        id = "mock_gen_${(10..99).random()}",
                        title = "How I Tuned a Retrowave Bassline with AI Audio Cleaners",
                        channelName = "$titleClean Studio",
                        originalChannel = target,
                        viewsText = "${(60..850).random()}K views",
                        ageText = "${(1..4).random()} weeks ago",
                        thumbnail = ""
                    )
                )
            }
        }
    }

    fun generateTranscriptForCloneVideo(video: CloneSuggestedVideo) {
        val index = cloneSuggestedVideos.indexOfFirst { it.id == video.id }
        if (index == -1) return
        
        val updatedList = cloneSuggestedVideos.toMutableList()
        updatedList[index] = updatedList[index].copy(isGenerating = true)
        cloneSuggestedVideos = updatedList
        
        viewModelScope.launch {
            try {
                val gemKey = getEffectiveGeminiApiKey()
                val isDemo = gemKey.isEmpty()
                val generatedText = if (isDemo) {
                    kotlinx.coroutines.delay(2000)
                    "Transcript for \"${video.title}\": Sat Sri Akal dosto, welcome back to another video. Channel te naye ho ta subscribe zaroor kareyo. " +
                    "Ajj aap gal kar rahe haan is mind-blowing topic bare. Mainu bhot saare comments aye si ki bai isda structure ki we " +
                    "te asani naal isnu kive execute kariye. So, is video vich do bohot simple steps ne, pehla layout theek karo te dooja " +
                    "script alignment dynamic rakho! Eh formula standard target formats vich top view results generate karda hai!"
                } else {
                    withContext(Dispatchers.IO) {
                        prefs.deductQuota(200)
                    }
                    val prompt = """
                        You are analyzing a YouTube video titled "${video.title}" from the channel "${video.channelName}".
                        Please write a highly engaging, authentic video script transcript.
                        The content should be in ${preferredHelpLanguage} which matches the user's preferred helper language (if Hindi, Punjabi or Hinglish, do a beautiful translation/local blending).
                        Make it about 150-250 words, representing a real natural spoken spoken script of that video. No bracketed instructions or narrator tags, just direct spoken transcript.
                    """.trimIndent()
                    
                    val service = GeminiRetrofitClient.service
                    val request = GeminiRequest(
                        contents = listOf(GeminiContent(listOf(GeminiPart(prompt)))),
                        generationConfig = GeminiGenerationConfig(temperature = 0.5f)
                    )
                    val response = service.generateContent(apiKey = gemKey, request = request)
                    response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "Failed to generate authentic transcript."
                }
                
                val finalVideoList = cloneSuggestedVideos.toMutableList()
                val refreshedIndex = finalVideoList.indexOfFirst { it.id == video.id }
                if (refreshedIndex != -1) {
                    finalVideoList[refreshedIndex] = finalVideoList[refreshedIndex].copy(
                        isGenerating = false,
                        transcript = generatedText
                    )
                    cloneSuggestedVideos = finalVideoList
                }
                
                addTranscript(generatedText)
                
            } catch (e: Exception) {
                e.printStackTrace()
                val errList = cloneSuggestedVideos.toMutableList()
                val refreshedIndex = errList.indexOfFirst { it.id == video.id }
                if (refreshedIndex != -1) {
                    errList[refreshedIndex] = errList[refreshedIndex].copy(isGenerating = false)
                    cloneSuggestedVideos = errList
                }
            }
        }
    }

    fun addTranscript(transcriptText: String) {
        if (transcriptText.isNotBlank()) {
            cloneTranscripts = cloneTranscripts + transcriptText
            cloneTranscriptInputInput = ""
        }
    }

    fun removeTranscript(index: Int) {
        if (index in cloneTranscripts.indices) {
            cloneTranscripts = cloneTranscripts.filterIndexed { idx, _ -> idx != index }
        }
    }

    fun proceedToState3() {
        cloneActiveState = 3
    }

    fun proceedToState4(topicChoice: String, customTopic: String, includeComp: String, compLinks: String) {
        cloneTopicChoice = topicChoice
        cloneCustomTopic = customTopic
        cloneCompetitorChoice = includeComp
        cloneCompetitorLinks = compLinks
        cloneActiveState = 4
        // Automatically start DNA analysis in state 4
        runStyleDNAAnalysis()
    }

    fun runStyleDNAAnalysis() {
        cloneStyleDNALoading = true
        viewModelScope.launch {
            try {
                val apiKey = getEffectiveGeminiApiKey()
                if (apiKey.isEmpty()) {
                    // Demo Mode - Generate high quality custom analysis
                    kotlinx.coroutines.delay(2000)
                    cloneStyleDNA = generateDemoStyleDNA()
                } else {
                    // Call Gemini API
                    val transcriptSummaryStr = cloneTranscripts.joinToString("\n---\n") { it.take(2000) }
                    val prompt = """
                        Deeply analyze these YouTube video transcripts and return a structured analysis.
                        Transcripts:
                        $transcriptSummaryStr
                        
                        You MUST reply with ONLY a raw JSON block containing these exact fields:
                        {
                          "niche": "niche and sub-niche description",
                          "targetAudience": "audiences description",
                          "languageAndDialect": "Pure English, Hinglish, Urdu-English, etc.",
                          "signatureSlang": ["slang word 1", "slang word 2"],
                          "hookTypes": ["Question Hook", "Shock Hook"],
                          "structure": "structural explanation",
                          "sentenceRhythm": "fast paced, conversational, etc.",
                          "tone": "casual, authoritative, etc.",
                          "transitions": "transition description",
                          "curiosityGaps": "how curiosity gaps are used",
                          "emotionalTriggers": "triggers used",
                          "retentionTechniques": "techniques",
                          "directAddressPatterns": "addressing patterns",
                          "wordsPerSecond": 2.5,
                          "ctaStyle": "how CTA is structured"
                        }
                    """.trimIndent()

                    val request = GeminiRequest(
                        contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                        generationConfig = GeminiGenerationConfig(responseMimeType = "application/json", temperature = 0.3f),
                        systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = "You are an elite YouTube style analyzer.")))
                    )
                    val response = GeminiRetrofitClient.service.generateContent(apiKey = apiKey, request = request)
                    val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                    val cleaned = jsonText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    
                    val moshiLocal = Moshi.Builder().addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
                    val adapterLocal = moshiLocal.adapter(ChannelCloneStyleDNA::class.java)
                    cloneStyleDNA = adapterLocal.fromJson(cleaned) ?: generateDemoStyleDNA()
                }
                
                // Adjust targets and proceed to scripting
                cloneActiveState = 5
                runScriptGeneration()
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to Demo mode so user journey never crashes
                cloneStyleDNA = generateDemoStyleDNA()
                cloneActiveState = 5
                runScriptGeneration()
            } finally {
                cloneStyleDNALoading = false
            }
        }
    }

    fun runScriptGeneration() {
        cloneScriptLoading = true
        viewModelScope.launch {
            val isShorts = cloneFormat.contains("Short", ignoreCase = true)
            val targetCount = if (isShorts) 180 else if (cloneFormat.contains("Mid", ignoreCase = true)) 550 else 1200
            try {
                val apiKey = getEffectiveGeminiApiKey()
                
                if (apiKey.isEmpty()) {
                    kotlinx.coroutines.delay(2000)
                    cloneScript = generateDemoScript(targetCount)
                } else {
                    val promptText = """
                        Generate a FULL YouTube video script based on the style DNA analyzed below.
                        Topic of script: ${if (cloneCustomTopic.isNotBlank()) cloneCustomTopic else "3 secrets to skyrocket views"}
                        Format/Layout selected: $cloneFormat (Target Word Count: $targetCount words)
                        Style DNA details:
                        - Niche: ${cloneStyleDNA?.niche}
                        - Language: ${cloneStyleDNA?.languageAndDialect}
                        - Tone: ${cloneStyleDNA?.tone}
                        - Rhythm: ${cloneStyleDNA?.sentenceRhythm}

                        Requirements:
                        1. Script MUST be written matching the language context perfectly! E.g. Hinglish or pure English.
                        2. Respect the target word count of $targetCount words (within 5% margin).
                        3. Do NOT include visual cues, scene descriptions, or camera instructions. Provide ONLY pure spoken script.
                        4. Generate 3 distinct Hook variations for the opening 30 seconds.
                        
                        You MUST reply in a STRICT JSON block using these exact keys:
                        {
                          "title": "A viral high-CTR title matching their style",
                          "originalScript": "Full narrative script here matching word count",
                          "hookA": "Alternate Hook option (Question Type: starting with highly engaging query)",
                          "hookB": "Alternate Hook option (Shock/Intriguing Type: starting with bold claim or shock statement)",
                          "hookC": "Alternate Hook option (Story Type: short story hook)",
                          "targetWordCount": $targetCount
                        }
                    """.trimIndent()

                    val request = GeminiRequest(
                        contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = promptText)))),
                        generationConfig = GeminiGenerationConfig(responseMimeType = "application/json", temperature = 0.5f),
                        systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = "You are a master viral YouTube scriptwriter.")))
                    )
                    val response = GeminiRetrofitClient.service.generateContent(apiKey = apiKey, request = request)
                    val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                    val cleaned = jsonText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    
                    val moshiLocal = Moshi.Builder().addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
                    val adapterLocal = moshiLocal.adapter(ChannelCloneScript::class.java)
                    val parsed = adapterLocal.fromJson(cleaned)
                    
                    if (parsed != null) {
                        val wordCount = parsed.originalScript.split("\\s+".toRegex()).size
                        cloneScript = parsed.copy(
                            finalScript = parsed.originalScript,
                            actualWordCount = wordCount
                        )
                    } else {
                        cloneScript = generateDemoScript(targetCount)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                cloneScript = generateDemoScript(targetCount)
            } finally {
                cloneScriptLoading = false
            }
        }
    }

    fun selectHook(hookOption: String) {
        val s = cloneScript ?: return
        val chosenText = when (hookOption) {
            "A" -> s.hookA
            "B" -> s.hookB
            "C" -> s.hookC
            else -> s.originalScript.substringBefore("\n\n").takeIf { it.isNotBlank() } ?: s.originalScript.take(150)
        }
        
        // Re-assemble final script with chosen hook
        val remainingScript = if (s.originalScript.contains("\n\n")) {
            s.originalScript.substringAfter("\n\n")
        } else {
            s.originalScript
        }
        
        val finalStr = "$chosenText\n\n$remainingScript"
        val count = finalStr.split("\\s+".toRegex()).size
        
        cloneSelectedHookIndex = when (hookOption) {
            "A" -> 0
            "B" -> 1
            "C" -> 2
            else -> -1
        }
        cloneScript = s.copy(
            finalScript = finalStr,
            actualWordCount = count
        )
    }

    fun submitFeedbackAndProceed(feedback: String) {
        if (feedback.isNotBlank()) {
            cloneScriptFeedback = feedback
            // Apply feedback mock text insertion
            val s = cloneScript ?: return
            val currentText = s.finalScript
            val textWithFeedback = "($feedback applied)\n\n" + currentText
            val count = textWithFeedback.split("\\s+".toRegex()).size
            cloneScript = s.copy(
                finalScript = textWithFeedback,
                actualWordCount = count
            )
        }
        
        // Advance to State 6
        cloneActiveState = 6
    }

    fun proceedToState5() {
        cloneActiveState = 5
        runScriptGeneration()
    }

    fun proceedToState6() {
        cloneActiveState = 6
    }

    fun submitVisualCues(description: String) {
        cloneVisualDescription = description
        cloneActiveState = 7
        generateVisualPrompts()
    }

    fun generateVisualPrompts() {
        viewModelScope.launch {
            try {
                val apiKey = getEffectiveGeminiApiKey()
                if (apiKey.isEmpty()) {
                    kotlinx.coroutines.delay(1500)
                    cloneVisualProfile = generateDemoVisualProfile()
                    cloneImagePrompts = generateDemoImagePrompts()
                } else {
                    val scriptText = cloneScript?.finalScript ?: ""
                    val promptText = """
                        Create stand-alone AI Image Prompts and B-Roll stock suggestions for each beat of this script.
                        Each beat represents 3-5 seconds of spoken script. Break down the entire script into logical sequential beats.
                        Script:
                        $scriptText
                        
                        Visual style requested: $cloneVisualDescription
                        
                        You MUST return a JSON list of beats matching this exact schema:
                        [
                          {
                            "segmentText": "precise segment of narration text",
                            "imagePrompt": "standalone Midjourney style prompt containing subject, lighting, mood, camera style based on $cloneVisualDescription",
                            "cameraAngle": "e.g. Extreme Close Up, Low Angle, etc.",
                            "lighting": "e.g. Moody Neon, Cine Gold Hour, etc.",
                            "mood": "e.g. Intense, Curious, Aesthetic",
                            "action": "description of physical state/action",
                            "brollSuggestion": "perfect stock footage search word (e.g. dark abstract network loop)"
                          }
                        ]
                    """.trimIndent()

                    val request = GeminiRequest(
                        contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = promptText)))),
                        generationConfig = GeminiGenerationConfig(responseMimeType = "application/json", temperature = 0.4f),
                        systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = "You are an advanced storyboard designer.")))
                    )
                    val response = GeminiRetrofitClient.service.generateContent(apiKey = apiKey, request = request)
                    val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                    val cleaned = jsonText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    
                    val moshiLocal = Moshi.Builder().addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
                    val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, VisualBeat::class.java)
                    val adapterLocal = moshiLocal.adapter<List<VisualBeat>>(type)
                    
                    cloneVisualProfile = generateDemoVisualProfile()
                    cloneImagePrompts = adapterLocal.fromJson(cleaned) ?: generateDemoImagePrompts()
                }
                
                cloneActiveState = 8
            } catch (e: Exception) {
                e.printStackTrace()
                cloneVisualProfile = generateDemoVisualProfile()
                cloneImagePrompts = generateDemoImagePrompts()
                cloneActiveState = 8
            }
        }
    }

    fun submitVideoPromptChoice(yesNo: Boolean) {
        cloneVideoPromptsEnabled = yesNo
        if (yesNo) {
            cloneImagePrompts = cloneImagePrompts.map { beat ->
                beat.copy(videoPrompt = "Animate with a subtle 3D dolly zoom, 4k resolution, cinematic motion-blur rendering. Action: ${beat.action}")
            }
        }
        cloneActiveState = 9
    }

    fun submitThumbnailParams(pref: String) {
        cloneThumbnailPreference = pref
        cloneActiveState = 10
        generateThumbnails()
    }

    fun generateThumbnails() {
        viewModelScope.launch {
            cloneThumbnailsLoading = true
            try {
                val apiKey = getEffectiveGeminiApiKey()
                if (apiKey.isEmpty()) {
                    kotlinx.coroutines.delay(1000)
                    cloneThumbnails = generateDemoThumbnails()
                } else {
                    val scriptTitle = cloneScript?.title ?: "Viral Blueprint"
                    val promptText = """
                        Design 5 highly viral YouTube thumbnail ideas based on:
                        Title: $scriptTitle
                        Visual style parameter: $cloneThumbnailPreference
                        
                        You MUST return a JSON list of exactly 5 elements matching this exact schema:
                        [
                          {
                            "title": "Thumbnail Concept index",
                            "visualConcept": "detailed visual description of characters, backdrop, items",
                            "textOverlay": "high CTR text overlay (3 words max, bold)",
                            "emotionTrigger": "e.g. shock, curious, desire",
                            "prompt": "Style matched AI image text prompt",
                            "recommendationReason": "why does this CTR strategy work"
                          }
                        ]
                    """.trimIndent()

                    val request = GeminiRequest(
                        contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = promptText)))),
                        generationConfig = GeminiGenerationConfig(responseMimeType = "application/json", temperature = 0.5f),
                        systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = "You are an elite CTR strategist.")))
                    )
                    val response = GeminiRetrofitClient.service.generateContent(apiKey = apiKey, request = request)
                    val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                    val cleaned = jsonText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    
                    val moshiLocal = Moshi.Builder().addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
                    val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, CloneThumbnail::class.java)
                    val adapterLocal = moshiLocal.adapter<List<CloneThumbnail>>(type)
                    
                    cloneThumbnails = adapterLocal.fromJson(cleaned) ?: generateDemoThumbnails()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                cloneThumbnails = generateDemoThumbnails()
            } finally {
                cloneThumbnailsLoading = false
            }
        }
    }

    fun generateMetadata() {
        viewModelScope.launch {
            cloneMetadataLoading = true
            try {
                val apiKey = getEffectiveGeminiApiKey()
                if (apiKey.isEmpty()) {
                    kotlinx.coroutines.delay(1000)
                    cloneMetadata = generateDemoMetadata()
                } else {
                    val scriptTitle = cloneScript?.title ?: "Viral Blueprint"
                    val promptText = """
                        Generate highly engaging metadata based on:
                        Title: $scriptTitle
                        Script: ${cloneScript?.finalScript}
                        Format: $cloneFormat
                        Goal: $cloneGoal
                        
                        You MUST return a JSON block matching this exact schema:
                        {
                          "seoDescription": "SEO-optimized description containing keywords and hooky intro",
                          "tags": "broad-tag, longtail-tag, niche-keyword",
                          "hashtags": "#niche #viral #youtube",
                          "chapters": "0:00 Intro\n0:30 Chapter explanation",
                          "pinnedComment": "engagement comment with custom CTA"
                        }
                    """.trimIndent()

                    val request = GeminiRequest(
                        contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = promptText)))),
                        generationConfig = GeminiGenerationConfig(responseMimeType = "application/json", temperature = 0.4f),
                        systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = "You are a YouTube SEO professional.")))
                    )
                    val response = GeminiRetrofitClient.service.generateContent(apiKey = apiKey, request = request)
                    val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                    val cleaned = jsonText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    
                    val moshiLocal = Moshi.Builder().addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
                    val adapterLocal = moshiLocal.adapter(CloneMetadata::class.java)
                    
                    cloneMetadata = adapterLocal.fromJson(cleaned) ?: generateDemoMetadata()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                cloneMetadata = generateDemoMetadata()
            } finally {
                cloneMetadataLoading = false
            }
        }
    }

    fun submitCalendarChoice(yesNo: Boolean) {
        cloneCalendarEnabled = yesNo
        if (yesNo) {
            viewModelScope.launch {
                try {
                    val apiKey = getEffectiveGeminiApiKey()
                    if (apiKey.isEmpty()) {
                        kotlinx.coroutines.delay(1000)
                        cloneCalendar = generateDemoCalendar()
                    } else {
                        val promptText = """
                            Using the Style DNA of the channel, generate 4 upcoming video content calendar items.
                            Style Niche: ${cloneStyleDNA?.niche}
                            
                            You MUST return a JSON list of exactly 4 items with this exact schema:
                            [
                              {
                                "title": "video title",
                                "topicAngle": "strategic angle / hook",
                                "suggestedHook": "Question Hook or Bold Statement",
                                "targetKeyword": "main keyword",
                                "uploadDayTime": "specific best day and time"
                              }
                            ]
                        """.trimIndent()

                        val request = GeminiRequest(
                            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = promptText)))),
                            generationConfig = GeminiGenerationConfig(responseMimeType = "application/json", temperature = 0.5f),
                            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = "You are a YouTube scheduler.")))
                        )
                        val response = GeminiRetrofitClient.service.generateContent(apiKey = apiKey, request = request)
                        val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                        val cleaned = jsonText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                        
                        val moshiLocal = Moshi.Builder().addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
                        val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, CloneCalendarItem::class.java)
                        val adapterLocal = moshiLocal.adapter<List<CloneCalendarItem>>(type)
                        
                        cloneCalendar = adapterLocal.fromJson(cleaned) ?: generateDemoCalendar()
                    }
                    cloneActiveState = 13
                } catch (e: Exception) {
                    e.printStackTrace()
                    cloneCalendar = generateDemoCalendar()
                    cloneActiveState = 13
                }
            }
        } else {
            cloneActiveState = 13
        }
    }

    // ==========================================
    // HIGH QUALITY ADAPTIVE MOCK BUILDERS (DEMO MODE)
    // ==========================================

    private fun generateDemoStyleDNA(): ChannelCloneStyleDNA {
        val link = cloneChannelLink.lowercase()
        return when {
            link.contains("cook") || link.contains("food") || link.contains("recipe") -> {
                ChannelCloneStyleDNA(
                    niche = "Gourmet Visual Recipes & Kitchen Hacks",
                    targetAudience = "Food enthusiasts (20-40 yrs) looking for quick, high-aesthetic cooking recipes and tips",
                    languageAndDialect = "Lively Hinglish (mixing Desi street slang with clean English instructions)",
                    signatureSlang = listOf("Waah kya swaad!", "Sizzling and crisp!", "Perfect fusion!"),
                    hookTypes = listOf("Shock Hook (showing burned food first)", "Question Hook (Could this satisfy your midnight craving?)"),
                    structure = "Sensory close-up montage -> Rapid ingredient dump -> Secret spice reveal -> Plating & ASMR bite",
                    sentenceRhythm = "Highly rhythmic, punchy, high-tempo sentence transitions matching editing beats",
                    tone = "Extremely friendly, energetic, culinary passionate",
                    transitions = "Fast whip pans with satisfying spatula swoosh sound effects",
                    curiosityGaps = "Hiding the secret ingredient until the final 10 seconds of the recipe",
                    emotionalTriggers = "Mouth-watering visual desire, nostalgic home-sick memories",
                    retentionTechniques = "Sub-1-second visuals, on-screen dynamic text overlay, background lo-fi beats",
                    directAddressPatterns = "Addresses directly: 'Agar aap bhi...', 'Imagine having this right now...'",
                    wordsPerSecond = 2.8f,
                    ctaStyle = "Quick natural plug: 'Subscribe karo, next recipe coming tomorrow!'"
                )
            }
            link.contains("tech") || link.contains("code") || link.contains("android") || link.contains("compose") || link.contains("program") -> {
                ChannelCloneStyleDNA(
                    niche = "Jetpack Compose Development & Clean Code Principles",
                    targetAudience = "Android Engineers & Tech Builders wanting crisp, production-ready code breakdowns",
                    languageAndDialect = "Direct, articulate Technical English with helpful everyday developer slang",
                    signatureSlang = listOf("Clean code", "Zero-recomposition", "Surgical refactoring", "Under the hood"),
                    hookTypes = listOf("Bold Claim Hook (This library is officially dead)", "Curiosity Hook (I built a custom component from scratch...)"),
                    structure = "Dramatic bug screen -> Architectural analysis -> Code-writing montage -> Verification & Performance benchmark",
                    sentenceRhythm = "Measured, deliberate, pausing precisely when complex code statements appear",
                    tone = "Authoritative, educational, highly informative",
                    transitions = "Code terminal slides and elegant syntax highlight overlays",
                    curiosityGaps = "Promising a single compiler trick that resolves nested recomposition issues",
                    emotionalTriggers = "The relief of clean design patterns, the thrill of 10x faster compile times",
                    retentionTechniques = "Live interactive canvas drawing, no filler developer jargon, step-by-step progress bars",
                    directAddressPatterns = "Direct coding pair: 'Let's write this...', 'You might be asking...'",
                    wordsPerSecond = 2.2f,
                    ctaStyle = "Subtle professional prompt: 'Get this fully commented code on my GitHub. Link below!'"
                )
            }
            else -> {
                ChannelCloneStyleDNA(
                    niche = "Desi AI Beats & Futuristic Music Production",
                    targetAudience = "Gen Z music producers & casual listeners looking for high-bass AI fusion mixes",
                    languageAndDialect = "Stylized Punjabi-English (urban conversational bilingual mix)",
                    signatureSlang = listOf("Pure fire!", "Agg boldi ae", "Future beats"),
                    hookTypes = listOf("Shock Hook (Raw organic stems to high-end remix within 3 seconds)", "Bold Claim Hook"),
                    structure = "Instrument tuning sound -> High bass drop -> Synthesizer layering showcase -> Visualizer visual rhythm",
                    sentenceRhythm = "Extremely high tempo, stylized voice modulation syncing with metronome scales",
                    tone = "Hype-heavy, cool, artistic, proud",
                    transitions = "Beat drops matching chromatic glitches and heavy bass pulses",
                    curiosityGaps = "Concealing the classic old folk vocal stem used for the remix until the final master mix",
                    emotionalTriggers = "Nostalgia fused with high-tech futurism, adrenaline-producing beat dropouts",
                    retentionTechniques = "Glitch aesthetics, rapid EQ spectrum visualizers, hyper-cut drum machine views",
                    directAddressPatterns = "Direct addresses: 'Okk hun suno...', 'Tell me in comments if this drops hard!'",
                    wordsPerSecond = 2.4f,
                    ctaStyle = "Excited community builder: 'Mainu subscribe karo, comment your favorite track for AI remakes!'"
                )
            }
        }
    }

    private fun generateDemoScript(wordCount: Int): ChannelCloneScript {
        val link = cloneChannelLink.lowercase()
        return when {
            link.contains("cook") || link.contains("food") || link.contains("recipe") -> {
                ChannelCloneScript(
                    title = "The Midnight Kitchen Hack That Chef's Keep Hidden!",
                    originalScript = """
                        Hum sab ki life ka sabse bada problem? Midnight cravings! Waah kya swaad aane wala hai aaj!
                        
                        Aapne pack noodles khaye honge, normal toast khayi hogi. Par aaj hum banayenge aisi cheez jo sirf 5 minutes legi aur taste? Bilkul rich cafe level! 
                        
                        Sabse pehle, le lo dher saara garlic aur butter. Butter ko pan pe swirl karo, butter jalna nahi chahiye. Isme dalo hamara secret spice mix - thodi si kashmiri mirch aur a pinch of roasted cumin. Kuch log ise ignore karte hain par trust me, yehi lene wala hai taste next level pe!
                        
                        Ab daalna hai ek simple ingredient... slice of mature cheese. Isko melt hone do, low heat pe. Aur upar se hamara local crispy Punjabi tarka masala toast dalo. Sizzling and crisp! 
                        
                        Humne kuch alag nahi kiya, bas ingredients ki chemistry ko perfectly crack kiya hai. Ek baar banake dekho, midnight ko baar baar banaoge. Agar ye pasand aaya, check out my recipe catalog for more quick bites. Subscribe karna mat bhoolna!
                    """.trimIndent(),
                    hookA = "Question Hook: Kya aapko bhi aati hai 2 baje wali bhook? Forget packet noodles, ye 5-minute tarka toast aapka dimaag hila dega!",
                    hookB = "Shock Hook: Normal cheese toast banana band karo! Iski glaze recipe dekhne ke baad, local culinary chefs apna dimaag kharab kar rahe hain!",
                    hookC = "Story Hook: Kal raat main thak gaya tha, pantry khali thi bas do bread slice aur ek secret masala tha. Jab butter heat hua tab jo magical cheez bani, wo ye rahi!",
                    targetWordCount = wordCount
                ).let {
                    it.copy(
                        finalScript = it.originalScript,
                        actualWordCount = it.originalScript.split("\\s+".toRegex()).size
                    )
                }
            }
            link.contains("tech") || link.contains("code") || link.contains("android") || link.contains("compose") || link.contains("program") -> {
                ChannelCloneScript(
                    title = "STOP Writing Nested Nested Columns in Jetpack Compose!",
                    originalScript = """
                        Here is an uncomfortable truth about Android UI: your excessive nested columns are secretly destroying your frame rendering speeds.
                        
                        Today we are refactoring a sluggish screen under the hood. Most developers just stack boxes inside columns inside boxes, leading to measure passes that cripple UI response times. 
                        
                        Instead of nested layouts, we must deploy custom layouts or utilize ConstraintLayout with surgical precision. Let's look at this Compose code. Notice how we replace three nested Row structures with a single Box containing custom bias parameters. It's clean, zero-recomposition, and visually indistinguishable to the final user.
                        
                        By using flat layout patterns, the compiler works 10 times less during draw passes. Your frames stay locked at a clean 120 FPS. 
                        
                        Stop using filler nesting patterns. Refactor your production tree today. Get this complete refactored code block on my GitHub. Link is in the description below!
                    """.trimIndent(),
                    hookA = "Question Hook: Are your layout passes secretly dropping frames on high-end devices? Let's check why your nesting is killing performance.",
                    hookB = "Shock Hook: Stop nesting Compose columns! It is literally adding millisecond delays to every single click event on your screen.",
                    hookC = "Story Hook: I was reviewing a major client’s repository last Monday and found a 15-level deeply nested component hierarchy. Here is how I smashed that compile lag instantly.",
                    targetWordCount = wordCount
                ).let {
                    it.copy(
                        finalScript = it.originalScript,
                        actualWordCount = it.originalScript.split("\\s+".toRegex()).size
                    )
                }
            }
            else -> {
                ChannelCloneScript(
                    title = "I Recreated a Vintage Desi Classic with AI Beats (Mindblowing)",
                    originalScript = """
                        Vintage folk vocals, modern hyper-bass loops. Kya lagta hai, combinations drop karega perfect? Let's find out!
                        
                        Humne uthaya hai ek purana vintage organic Punjabi stem jise log bhool chuke the. Raw vocals mein ek ajeeb sa dard hai par isko hum denge modern electronic signature sound space. 
                        
                        Sabse pehle, main vocal frequency ko sanitize kiya, dynamic EQ use karke hiss sound remove kiya. Upaye mein hum dhalenge zero-latency hyper energetic bass drops. Beats mein sync hoga pure futuristic fusion drums. 
                        
                        Check out the spectrum visualizer. Jab low frequency compress hoti hai tab high frequencies automatic pop up hoti hain. Agg boldi ae pure monitors mein!
                        
                        Desi classical beats ka maza yehi hai - heritage tab tak zinda rehti hai jab tak future sound use adapt karta rahe. Agar ye remix pasand aaya, comment mein batao aur subscribe karo futuristic sound design clips ke liye. Future beats, let's go!
                    """.trimIndent(),
                    hookA = "Question Hook: Punjab di classical heritage aur hyper-synth waves? If you think these two worlds can never join, listen to this AI remaster.",
                    hookB = "Shock Hook: Stop playing standard retro vinyl tracks! This AI-fused frequency drop-mix is literally taking over the urban DJ charts.",
                    hookC = "Story Hook: My grandfather gave me an old magnetic tape from 1974. The vocals were dusty, but when I dropped a high-bass future wave underneath, the whole studio stood up.",
                    targetWordCount = wordCount
                ).let {
                    it.copy(
                        finalScript = it.originalScript,
                        actualWordCount = it.originalScript.split("\\s+".toRegex()).size
                    )
                }
            }
        }
    }

    private fun generateDemoVisualProfile(): VisualStyleProfile {
        return VisualStyleProfile(
            artStyle = "Cinematic Dark Minimalist with High Contrast Highlights",
            colorPalette = "#0F0F14 (Deep Slate), #FF9800 (Amber Gloom), #2196F3 (Neon Blue)",
            lightingStyle = "Stark chiaroscuro backlight pairing with ultra-soft ambient fills.",
            cameraAngles = "Frequent macro extreme close-ups alternating with high-angle center shots",
            compositionRules = "Symmetrical rule of thirds focusing dynamic actions strictly inside center squares",
            detailLevel = "Hyper-realistic micro details showing crisp lens highlights",
            overallMood = "Passionate, modern, highly focused, professional visual branding"
        )
    }

    private fun generateDemoImagePrompts(): List<VisualBeat> {
        val link = cloneChannelLink.lowercase()
        return when {
            link.contains("cook") || link.contains("food") || link.contains("recipe") -> {
                listOf(
                    VisualBeat(
                        segmentText = "Midnight cravings! Waah kya swaad aane wala hai aaj!",
                        imagePrompt = "Extreme close-up macro of premium butter melting over hot cast iron skillet with golden bubbles. Cinematic, dark background, 8k, photorealistic, natural lighting, moody ambiance.",
                        cameraAngle = "Extreme Close Up (ECU)",
                        lighting = "Warm Golden Hour Backlight",
                        mood = "Sensory Desire",
                        action = "Butter sizzling with steam",
                        brollSuggestion = "melting butter close up skillet"
                    ),
                    VisualBeat(
                        segmentText = "Sabse pehle, le lo dher saara garlic aur butter. Swirl karo pan pe...",
                        imagePrompt = "Top down slow motion view of hand throwing chopped fresh garlic into melted butter. Sparkles of grease, hot cast iron pan, rustic kitchen scene, selective focus, moody culinary aesthetic.",
                        cameraAngle = "Top-Down Overhead Shot",
                        lighting = "Soft side window natural lighting",
                        mood = "Energetic Cooking",
                        action = "Garlic pieces splashing into pan",
                        brollSuggestion = "sautéing garlic top down kitchen"
                    ),
                    VisualBeat(
                        segmentText = "Secret spice mix - thodi si kashmiri mirch aur a pinch of roasted cumin...",
                        imagePrompt = "Slow motion macro capture of vibrant deep red chili powder being dusted from fingers onto golden melted butter. Suspended red dust particles, culinary precision style, highly stylized.",
                        cameraAngle = "Macro Side Profile View",
                        lighting = "Stark cinematic back-spotlight",
                        mood = "Mysterious Flavor",
                        action = "Red dust falling through light beam",
                        brollSuggestion = "dusting chili powder on food"
                    ),
                    VisualBeat(
                        segmentText = "Ab daalna hai ek simple ingredient... slice of mature cheese...",
                        imagePrompt = "Ultra extreme macro shot of mature cheddar cheese slice melting over hot crispy surface. Stretching cheese pulls, golden crust developing around edges, dark moody studio background.",
                        cameraAngle = "Extreme Macro Close Up",
                        lighting = "Soft ambient glow",
                        mood = "Indulgent Texture",
                        action = "Cheese melting with structural stretch",
                        brollSuggestion = "melting cheese stretch pull macro"
                    ),
                    VisualBeat(
                        segmentText = "Subscribe karna mat bhoolna! Taste next level pe!",
                        imagePrompt = "Elegant final presentation. Crispy golden grilled toast centered on clean slate-black plate. Stream of light highlighting the cheese crust, minimal kitchen aesthetic.",
                        cameraAngle = "Center Cinematic Medium Shot",
                        lighting = "Key focal light spotlighting plate",
                        mood = "Satisfying Conclusion",
                        action = "Steam rising gently from fresh toast",
                        brollSuggestion = "chef plating gourmet cheese toast"
                    )
                )
            }
            link.contains("tech") || link.contains("code") || link.contains("android") || link.contains("compose") || link.contains("program") -> {
                listOf(
                    VisualBeat(
                        segmentText = "Here is an uncomfortable truth about Android UI: your columns are destroying frame speeds...",
                        imagePrompt = "Stark high-contrast view of developer frustrated in front of neon computer screen in deep dark room. Screen code reflects intensely on developer face, cinematic bokeh, 4k.",
                        cameraAngle = "Over-the-shoulder Medium Close Up",
                        lighting = "Neon Blue screen glow reflecting on face",
                        mood = "Frustrated Analytical",
                        action = "Developer staring intensely at computer",
                        brollSuggestion = "frustrated developer night screen code"
                    ),
                    VisualBeat(
                        segmentText = "Today we are refactoring a sluggish screen under the hood. Most developers stack layouts...",
                        imagePrompt = "Extreme clean macro shot of high-end IDE showing Kotlin code with nested brackets. Green and blue syntax highlighting glowing, clean developer setup.",
                        cameraAngle = "Macro Lens Angle",
                        lighting = "Clean cool ledger screen light",
                        mood = "Complex Coding",
                        action = "Compiler running code diagnostics",
                        brollSuggestion = "kotlin script computer display scan"
                    ),
                    VisualBeat(
                        segmentText = "Instead of nesting, we deploy ConstraintLayout with precision...",
                        imagePrompt = "Beautiful abstract visualization of 3D UI wireframe trees flattening into a single clean digital flat surface. Glowing nodes connecting with cyan lasers.",
                        cameraAngle = "3D Cinematic Perspective",
                        lighting = "Cyberpunk grid ambient glow",
                        mood = "Surgical Refactoring Success",
                        action = "Network layers folding down flat",
                        brollSuggestion = "flat clean user interface wireframe"
                    ),
                    VisualBeat(
                        segmentText = "Get this complete refactored code block on my GitHub. Link below!",
                        imagePrompt = "Minimalist visual representation of GitHub black Octocat mascot glowing on subtle textured obsidian surface, high-end clean tech aesthetic.",
                        cameraAngle = "Central Architectural Studio Shot",
                        lighting = "Subtle soft halo outline illumination",
                        mood = "Tech Professional Call to Action",
                        action = "Logo glowing gently",
                        brollSuggestion = "glowing minimalist tech logo on dark desk"
                    )
                )
            }
            else -> {
                listOf(
                    VisualBeat(
                        segmentText = "Vintage folk vocals, modern hyper-bass loops. Kya perfect combo drop hoga?",
                        imagePrompt = "Close-up of classic vintage metallic microphone standing in modern high-tech electronic studio. Ambient purple neon shadows, crisp audio details, 8k resolution.",
                        cameraAngle = "Macro Profile Close Up",
                        lighting = "Intense purple neon ambient tube backlighting",
                        mood = "Retro Melancholy Meets Future Tech",
                        action = "Sound waves vibrating dust particles",
                        brollSuggestion = "studio retro microphone glowing neon lights"
                    ),
                    VisualBeat(
                        segmentText = "Sabse pehle, main vocal frequency ko sanitize kiya, hissed EQ remove kiya...",
                        imagePrompt = "Close up macro of hand moving dual-channel aluminum knobs on sleek modern synthesizer desk console. Lit up LED meter levels flashing, dark music studio backdrop.",
                        cameraAngle = "Extreme Close Up macro of fingers and knobs",
                        lighting = "Sleek red LED signals reflecting in metal",
                        mood = "Passionate Production Control",
                        action = "Hand turning solid mechanical mixer knobs",
                        brollSuggestion = "dj turning knobs on audio console"
                    ),
                    VisualBeat(
                        segmentText = "Check out the spectrum visualizer. Pure monitors, future beats!",
                        imagePrompt = "Vibrant 3D neon holographic music waveform equalizer floating above high-end speaker cabinet. Fluctuations of sound waves matching chromatic aberration glitch grids.",
                        cameraAngle = "Dynamic Low-Angle Cinematic Shot",
                        lighting = "Laser cyan and magenta strobe flares",
                        mood = "Hyper Energetics",
                        action = "Hologram fluctuating in sync with bass",
                        brollSuggestion = "digital sound visualizer hologram screen"
                    )
                )
            }
        }
    }

    private fun generateDemoThumbnails(): List<CloneThumbnail> {
        val link = cloneChannelLink.lowercase()
        return when {
            link.contains("cook") || link.contains("food") || link.contains("recipe") -> {
                listOf(
                    CloneThumbnail(
                        title = "Concept 1: Ultimate Cheese Melt (Surgical CTR)",
                        visualConcept = "Extreme side-profile slice pull of gold-crusted toast, thick strings of bubbling cheese stretching, dark background with blurred fire embers.",
                        textOverlay = "CHEFS ARE ANGRY!",
                        emotionTrigger = "Unbelievable culinary desire, intense curiosity",
                        prompt = "Extreme macro of stretching melted cheese slice from crispy grilled toast, golden crumbs falling, warm studio backlighting, dark slate background, ultra professional food photography.",
                        recommendationReason = "Excellent high-contrast colors and extreme close-ups of food are proven to drive click-through rates up by over 40%."
                    ),
                    CloneThumbnail(
                        title = "Concept 2: The 5-Min Secret (Curiosity Loop)",
                        visualConcept = "A simple glowing clock counting down '05:00' positioned over a rustic plate holding a hidden spice jar covered by a glowing question mark.",
                        textOverlay = "DON'T BUY NOODLES!",
                        emotionTrigger = "Impulse craving, budget-hack excitement",
                        prompt = "Cinematic top-down kitchen shot of glowing question mark jar on dark wooden tray, butter block and herbs scattered, foggy ambient steam, award-winning food blogger aesthetic.",
                        recommendationReason = "Dethroning a common staple (like packet noodles) forces immediate engagement through curiosity loops."
                    )
                )
            }
            link.contains("tech") || link.contains("code") || link.contains("android") || link.contains("compose") || link.contains("program") -> {
                listOf(
                    CloneThumbnail(
                        title = "Concept 1: The UI Framerate Crash (Extreme Tech)",
                        visualConcept = "Split screen: left shows red angry '60 FPS LAG' warning with messy code, right shows neon green '120 FPS' with clean code. Red laser pointing to the lag.",
                        textOverlay = "STOP NESTING!",
                        emotionTrigger = "Professional fear of slow performance, desire for immediate clean up",
                        prompt = "Concept split screen display, red and green layout indicators, neon lettering 'LAG' vs 'CLEAN', highly technical high-contrast digital overlay design.",
                        recommendationReason = "A/B comparison styles trigger developer problem-solving instincts, immediately suggesting an urgent fix they need to adopt."
                    ),
                    CloneThumbnail(
                        title = "Concept 2: Red Box of Death (Bold Statement)",
                        visualConcept = "A beautiful Jetpack Compose logo shattered in half by a big red 'DELETE' marker, clean file tree below glowing bright green.",
                        textOverlay = "KILLED BY COLUMNS!",
                        emotionTrigger = "Intellectual shock, educational controversy",
                        prompt = "Cracked circular cyber blue symbol on obsidian floor, green lasers, glowing technical indicators, cinematic depth of field tech presentation.",
                        recommendationReason = "Challenging standard native components like Columns creates educational friction, motivating builders to click and critique."
                    )
                )
            }
            else -> {
                listOf(
                    CloneThumbnail(
                        title = "Concept 1: Classic Reborn (The Remix Shock)",
                        visualConcept = "Half of vinyl record player from 1950s blended seamlessly into a futuristic cyan laser synthesis loop pad on the other half. Dynamic bass waves.",
                        textOverlay = "I RECREATED THIS!",
                        emotionTrigger = "Nostalgia and high-futuristic excitement",
                        prompt = "Split retro vinyl and neon electronic synthesiser, sound waves emitting glowing particles, chromatic aberration, moody high contrast art.",
                        recommendationReason = "Fusing heritage elements with futuristic colors appeals both to older nostalgic fans and younger bass enthusiasts."
                    )
                )
            }
        }
    }

    private fun generateDemoMetadata(): CloneMetadata {
        val link = cloneChannelLink.lowercase()
        val custom = cloneCustomTopic.lowercase()
        val transcriptsText = cloneTranscripts.joinToString(" ").lowercase()
        val combined = "$link $custom $transcriptsText"
        return when {
            combined.contains("ai") || combined.contains("tool") || combined.contains("gpt") || combined.contains("bot") || combined.contains("automate") || combined.contains("intelligence") || combined.contains("model") -> {
                CloneMetadata(
                    seoDescription = "Stop performing tedious manual tasks in 2026! Discover the game-changing AI tools and automated pipelines that modern digital creators use to 10x their production speeds. From advanced screen-capture layouts to custom script synthesis engines, this step-by-step masterclass breaks down premium workflows you can implement right now with zero friction. Get ahead of outdated 2024 strategies and transform your productivity with these 2026 secrets!",
                    tags = "secret-ai-tools, custom-ai-agents, automation-workflows, tech-productivity-2026, automatic-rendering, modern-creators-toolkit, no-code-pipelines, gpt-deep-learning, smart-scriptwrite, futuristic-tutorials",
                    hashtags = "#aitools #productivity #automation #tech2026 #smartworkflow",
                    chapters = "0:00 - Outdated 2024 Tools Are Penalized\n1:05 - The Future of 2026 AI Agent Pipelines\n2:15 - Step-by-Step Custom Render Workflow Demo\n3:25 - Automating Your Competitor Scanning System\n4:40 - Ultimate 10x Productivity Verification Test",
                    pinnedComment = "Which tedious manual workflow or repetitive task do you want to completely automate in 2026? Put your answers in the comment box below and let's structure a custom bot blueprint for you! 👇"
                )
            }
            link.contains("cook") || link.contains("food") || link.contains("recipe") || combined.contains("cook") || combined.contains("food") || combined.contains("recipe") -> {
                CloneMetadata(
                    seoDescription = "Stop cooking boring packet noodles at midnight! This 5-minute crispy cheese garlic butter tarka toast is the ultimate cure for your extreme late-night cravings. Made with premium butter, toasted mature cheese slice, and a simple secret Desi spice mix, it is cafe level in your own home kitchen. Let me know if you tried this and what your absolute go-to midnight snack hack is in the description below!",
                    tags = "midnight-recipe, quick-snacks, garlic-butter-toast, secret-culinary-hacks, cheese-toast, tarka-cooking, easy-5-min-meals, punjabi-foodie, kitchen-short-ideas, viral-cheesy-snack",
                    hashtags = "#midnightcravings #easyrecipes #cheesetoast #foodhacks #quickmasala",
                    chapters = "0:00 - The Midnight Crisis\n0:45 - Gathering Simple Pantry Ingredients\n1:20 - Butter Glazing Action\n2:10 - Melting of the Cheese Slice\n3:00 - Ultimate Sensory Taste Test",
                    pinnedComment = "What is YOUR absolute favorite midnight snack hack when everything is empty? Tell me below and let's compile the ultimate Desi foodie survival guide! 👇"
                )
            }
            link.contains("tech") || link.contains("code") || link.contains("android") || link.contains("compose") || link.contains("program") || combined.contains("tech") || combined.contains("code") || combined.contains("android") || combined.contains("compose") -> {
                CloneMetadata(
                    seoDescription = "Excessive nested Columns and Rows are secretly killing your Jetpack Compose application's render speed and causing dropped frames! Learn how to refactor complex layouts under the hood, flattening structures with ConstraintLayout or Custom layouts to maintain solid 120 FPS rendering across compile targets. Code sample is available at my GitHub link below.",
                    tags = "jetpack-compose, android-performance, flat-layouts, nesting-bugs, kotlin-refactoring, developers-toolkit, compose-performance-tricks, rendering-speeds, clean-code, constraint-layout",
                    hashtags = "#jetpackcompose #androiddev #kotlin #cleancode #uidesign",
                    chapters = "0:00 - Nested Columns Destroy Frame Speed\n1:15 - Under the Hood Analysis of Render Passes\n2:30 - Surgical Refactoring Demo\n3:40 - Flat Compose Layout Comparison\n4:45 - 120 FPS Benchmark Test Result",
                    pinnedComment = "Are you guilty of nesting more than 3 layout layers in your composables? Be honest, tell me your worst nesting horror story in comments! 👇"
                )
            }
            else -> {
                CloneMetadata(
                    seoDescription = "I took a vintage folk audio vocal recording track and wrapped it inside a futuristic, heavy-bass electronic loop using generative AI audio sanitizing tools. Here is under the hood music production breakdown of how sound designers can marry nostalgia with modern synthesizer levels. Enjoy the track spectrum visualizer!",
                    tags = "desi-synth-remix, future-folk-beats, ai-audio-music, music-production-breakdowns, vintage-vocals, deep-bass-synthesizer, desi-fusion, retro-remastered, electronic-producer",
                    hashtags = "#synthdesi #futurebeats #musicproduction #airemix #punjabiwave",
                    chapters = "0:00 - Vintage Vocal Discovery\n1:10 - EQ Sanitizing & Hiss Clearance\n2:05 - Synth Bass Wave Layering\n2:55 - Final Master Drop & Holograph Visualizer",
                    pinnedComment = "Which vintage song should I get AI to sanitize and remodel next? Drop your top recommendations and tracks in the reply box! 👇"
                )
            }
        }
    }

    private fun generateDemoCalendar(): List<CloneCalendarItem> {
        val link = cloneChannelLink.lowercase()
        return when {
            link.contains("cook") || link.contains("food") || link.contains("recipe") -> {
                listOf(
                    CloneCalendarItem(
                        title = "Why Chef's Despise This 3-Ingredient Dessert Hack",
                        topicAngle = "Challenging traditional lengthy baking procedures using instantaneous pantry substitutes.",
                        suggestedHook = "Bold Claim Hook: baking desserts doesn't take hours, standard bakery shops are overcharging you.",
                        targetKeyword = "quick-3-ingredient-desserts",
                        uploadDayTime = "Wednesday at 6:00 PM EST (perfect mid-week treat shopping window)"
                    ),
                    CloneCalendarItem(
                        title = "I Cooked Traditional Biryani In Exactly 15 Minutes!",
                        topicAngle = "Speed Cooking trial attempting to master a historic heritage slow-cooking dish under extreme time constraints.",
                        suggestedHook = "Shock Hook: starting with pressure cooker dial clicking intensely with rapid timing overlay",
                        targetKeyword = "fast-15-min-biryani",
                        uploadDayTime = "Saturday at 11:30 AM EST (triggering lunch cravings and weekend cooking decisions)"
                    )
                )
            }
            link.contains("tech") || link.contains("code") || link.contains("android") || link.contains("compose") || link.contains("program") -> {
                listOf(
                    CloneCalendarItem(
                        title = "STOP Using standard mutableStateOf inside your scroll views!",
                        topicAngle = "Exposing memory leaks and performance drops of unremembered dynamic values during recompositions.",
                        suggestedHook = "Question Hook: why is your list stuttering when scrolling? It has to do with your state.",
                        targetKeyword = "jetpack-compose-state-optimization",
                        uploadDayTime = "Monday at 9:00 AM EST (right as engineers start review cycles and sprint planning)"
                    ),
                    CloneCalendarItem(
                        title = "The New KSP Compiler Trick is Actually a Game-changer!",
                        topicAngle = "A detailed walkthrough of code-generating tools speeding up dependency trees significantly.",
                        suggestedHook = "Bold Claim Hook: This update reduces compilation overheads by up to 30 percent",
                        targetKeyword = "ksp-compiler-android",
                        uploadDayTime = "Thursday at 2:00 PM EST (midday technical focus window on developer hubs)"
                    )
                )
            }
            else -> {
                listOf(
                    CloneCalendarItem(
                        title = "How a $10 Pocket Synthesizer Beats High-end Plugins",
                        topicAngle = "Comparing boutique cheap physical analog instruments to multi-thousand dollar digital setups.",
                        suggestedHook = "Shock Hook: showing an ultra tiny toy audio device that spits heavy bass frequencies",
                        targetKeyword = "boutique-analog-synthesizers",
                        uploadDayTime = "Friday at 5:00 PM EST (catching musicians and enthusiasts searching weekend audio projects)"
                    )
                )
            }
        }
    }
}

// ==========================================
// CHANNEL CLONE WORKFLOW DATA MODELS
// ==========================================

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class ChannelCloneStyleDNA(
    val niche: String = "",
    val targetAudience: String = "",
    val languageAndDialect: String = "",
    val signatureSlang: List<String> = emptyList(),
    val hookTypes: List<String> = emptyList(),
    val structure: String = "",
    val sentenceRhythm: String = "",
    val tone: String = "",
    val transitions: String = "",
    val curiosityGaps: String = "",
    val emotionalTriggers: String = "",
    val retentionTechniques: String = "",
    val directAddressPatterns: String = "",
    val wordsPerSecond: Float = 2.5f,
    val targetWordCount: Int = 0,
    val ctaStyle: String = ""
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class ChannelCloneScript(
    val title: String = "",
    val originalScript: String = "",
    val hookA: String = "",
    val hookB: String = "",
    val hookC: String = "",
    val finalScript: String = "",
    val targetWordCount: Int = 0,
    val actualWordCount: Int = 0
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class VisualBeat(
    val segmentText: String = "",
    val imagePrompt: String = "",
    val cameraAngle: String = "",
    val lighting: String = "",
    val mood: String = "",
    val action: String = "",
    val brollSuggestion: String = "",
    val videoPrompt: String = ""
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class VisualStyleProfile(
    val artStyle: String = "",
    val colorPalette: String = "",
    val lightingStyle: String = "",
    val cameraAngles: String = "",
    val compositionRules: String = "",
    val detailLevel: String = "",
    val overallMood: String = ""
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class CloneThumbnail(
    val title: String = "",
    val visualConcept: String = "",
    val textOverlay: String = "",
    val emotionTrigger: String = "",
    val prompt: String = "",
    val recommendationReason: String = ""
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class CloneMetadata(
    val seoDescription: String = "",
    val tags: String = "",
    val hashtags: String = "",
    val chapters: String = "",
    val pinnedComment: String = ""
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class CloneCalendarItem(
    val title: String = "",
    val topicAngle: String = "",
    val suggestedHook: String = "",
    val targetKeyword: String = "",
    val uploadDayTime: String = ""
)

data class CloneSearchChannelResult(
    val name: String,
    val handle: String,
    val avatarUrl: String,
    val subscribersText: String
)

data class CloneSuggestedVideo(
    val id: String,
    val title: String,
    val channelName: String,
    val originalChannel: String,
    val viewsText: String,
    val ageText: String,
    val thumbnail: String,
    val isGenerating: Boolean = false,
    val transcript: String = ""
)
