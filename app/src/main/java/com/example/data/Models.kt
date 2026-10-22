package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class VideoItem(
    val title: String,
    val views: Long,
    val likes: Long,
    val comments: Long,
    val date: String
)

@JsonClass(generateAdapter = true)
data class ChannelStats(
    val id: String,
    val name: String,
    val handle: String,
    val subscribers: Long,
    val totalViews: Long,
    val totalVideos: Long,
    val avatarUrl: String,
    val uploadFrequencyValue: Double, // uploads per week
    val uploadFrequencyText: String,  // e.g. "2.4 videos/week"
    val averageViews: Long,
    val recentVideos: List<VideoItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class VideoInsight(
    val title: String = "",
    val reason: String = ""
)

@JsonClass(generateAdapter = true)
data class CompetitorAnalysis(
    @com.squareup.moshi.Json(name = "channelName") val name: String = "",
    val handle: String = "",
    val subscribers: Long = 0L,
    @com.squareup.moshi.Json(name = "estimatedMonthlyViews") val avgViews: Long = 0L,
    val uploadFrequency: String = "",
    val contentFormat: String? = null,
    @com.squareup.moshi.Json(name = "competitiveAdvantage") val biggestStrength: String? = null,
    val isManual: Boolean? = false
)

@JsonClass(generateAdapter = true)
data class VideoIdea(
    val title: String = "",
    val whyItWillWork: String = "",
    val format: String = "", // Short or Long
    val difficulty: String = "" // Easy, Medium, Hard
)

@JsonClass(generateAdapter = true)
data class CalendarEntry(
    val dayNumber: Int = 0,
    val dateText: String = "",
    val videoTitle: String = ""
)

@JsonClass(generateAdapter = true)
data class ChannelAnalysis(
    val healthScore: Int,
    val healthReason: String,
    val bestVideos: List<VideoInsight>,
    val worstVideos: List<VideoInsight>,
    val audienceProfile: String,
    val mainWeakness: String,
    val competitors: List<CompetitorAnalysis>,
    val gapAnalysis: String,
    val strategiesToAdopt: List<String>,
    val yourAdvantage: String,
    val videoIdeas: List<VideoIdea>,
    val contentCalendar: List<CalendarEntry>,
    val bestUploadDays: List<String>,
    val recommendedLength: String,
    val shortsVsLongRatio: String,
    val primaryCategory: String? = null,
    val subCategory: String? = null,
    val microNiche: String? = null,
    val contentIdentity: String? = null,
    val contentFormat: String? = null
)

@JsonClass(generateAdapter = true)
data class FullGrowTubeReport(
    val stats: ChannelStats,
    val analysis: ChannelAnalysis,
    val latestVideoAnalysis: LatestVideoAnalysis? = null
)

@JsonClass(generateAdapter = true)
data class RecentChannelItem(
    val handle: String,
    val channelName: String,
    val avatarUrl: String,
    val subscribers: Long,
    val reportJson: String // Cached full GrowTubeReport JSON
)

@JsonClass(generateAdapter = true)
data class AlternateTitleItem(
    val suggestedTitle: String,
    val inspiredBy: String
)

@JsonClass(generateAdapter = true)
data class AutopsyReport(
    val primaryCategory: String,
    val subCategory: String,
    val microNiche: String,
    val contentIdentity: String,
    val topicsCovered: String,
    val bestVideoFormat: String,
    val languageCulture: String,
    val coreDemographic: String,
    
    val viralTitleStructures: String,
    val viralEmotionalTriggers: String,
    val viralThumbnailStyles: String,
    val viralDescriptionPatterns: String,
    val viralTagsDiscovery: String,
    
    val titleCritique: String,
    val alternateTitles: List<AlternateTitleItem>,
    val thumbnailCritique: String,
    val thumbnailFixes: String,
    val descriptionCritique: String,
    val recommendedDescription: String,
    val tagsCritique: String,
    val suggestedTags: List<String>
)

@JsonClass(generateAdapter = true)
data class TitleOption(
    val suggestedTitle: String,
    val ctrScore: Int, // 1-10
    val whyItWillWork: String,
    val inspiredBy: String? = null,
    val whyAudienceClick: String? = null
)

@JsonClass(generateAdapter = true)
data class ThumbnailAnalysisResult(
    val predictedCtr: String, // e.g., "7.5%"
    val audienceScore: Int, // 1-10
    val whatWorks: String,
    val whatNeedsToFix: String,
    val specificFixSuggestions: String
)

@JsonClass(generateAdapter = true)
data class ThumbnailBlueprint(
    val visualDescription: String,
    val colors: String,
    val emotion: String,
    val textPlacement: String,
    val backgroundStyle: String,
    val faceRecommendation: String,
    val referenceNicheStandard: String
)

@JsonClass(generateAdapter = true)
data class DescriptionOutput(
    val primaryHook: String,
    val bodyText: String,
    val timestamps: String,
    val socialLinksPlaceholder: String,
    val cta: String,
    val fullText: String
)

@JsonClass(generateAdapter = true)
data class LaunchTag(
    val name: String,
    val searchVolume: String, // High / Medium / Low
    val relevanceReason: String? = null,
    val inspirationVideo: String? = null
)

@JsonClass(generateAdapter = true)
data class LaunchHashtag(
    val name: String,
    val isHighlighted: Boolean
)

@JsonClass(generateAdapter = true)
data class ScoreBreakdown(
    val titleStrength: Int,
    val thumbnailPotential: Int,
    val descriptionQuality: Int,
    val tagsCoverage: Int,
    val nicheAlignment: Int,
    val totalScore: Int,
    val status: String
)

@JsonClass(generateAdapter = true)
data class LaunchReport(
    val channelHandle: String,
    val topicName: String,
    val timestamp: Long,
    val recommendedTitleStyleReason: String,
    val titleOptions: List<TitleOption>,
    val thumbnailAnalysis: ThumbnailAnalysisResult?,
    val thumbnailBlueprint: ThumbnailBlueprint,
    val description: DescriptionOutput,
    val tags: List<LaunchTag>,
    val hashtags: List<LaunchHashtag>,
    val score: ScoreBreakdown
)

@JsonClass(generateAdapter = true)
data class LatestVideoAnalysis(
    val verdict: String,
    val reasons: String,
    val actions: List<String>
)

@JsonClass(generateAdapter = true)
data class Call1Response(
    val healthScore: Int,
    val healthReason: String,
    val bestVideos: List<VideoInsight>,
    val worstVideos: List<VideoInsight>,
    val audienceProfile: String,
    val mainWeakness: String,
    val primaryCategory: String? = null,
    val subCategory: String? = null,
    val microNiche: String? = null,
    val contentIdentity: String? = null,
    val contentFormat: String? = null,
    val latestVideoAnalysis: LatestVideoAnalysis? = null
)

@JsonClass(generateAdapter = true)
data class Call2Response(
    val competitors: List<CompetitorAnalysis>,
    val gapAnalysis: String,
    val strategiesToAdopt: List<String>,
    val yourAdvantage: String
)

@JsonClass(generateAdapter = true)
data class Call3Response(
    val bestUploadDays: List<String>,
    val recommendedLength: String,
    val shortsVsLongRatio: String,
    val videoIdeas: List<VideoIdea>,
    val contentCalendar: List<CalendarEntry>
)



