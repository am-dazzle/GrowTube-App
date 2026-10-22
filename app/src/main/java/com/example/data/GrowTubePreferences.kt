package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.Calendar
import java.util.TimeZone

class GrowTubePreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("growtube_prefs", Context.MODE_PRIVATE)
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    
    private val recentListAdapter = moshi.adapter<List<RecentChannelItem>>(
        Types.newParameterizedType(List::class.java, RecentChannelItem::class.java)
    )

    private val launchListAdapter = moshi.adapter<List<LaunchReport>>(
        Types.newParameterizedType(List::class.java, LaunchReport::class.java)
    )

    private val call2Adapter = moshi.adapter(Call2Response::class.java)
    private val call3Adapter = moshi.adapter(Call3Response::class.java)

    companion object {
        private const val KEY_YOUTUBE_API_KEY = "youtube_api_key"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_RECENT_CHANNELS = "recent_channels"
    }

    fun saveYoutubeApiKey(key: String) {
        prefs.edit().putString(KEY_YOUTUBE_API_KEY, key).apply()
    }

    fun getYoutubeApiKey(): String {
        return prefs.getString(KEY_YOUTUBE_API_KEY, "") ?: ""
    }

    fun saveGeminiApiKey(key: String) {
        prefs.edit().putString(KEY_GEMINI_API_KEY, key).apply()
    }

    fun getGeminiApiKey(): String {
        return prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
    }

    fun getRecentChannels(): List<RecentChannelItem> {
        val json = prefs.getString(KEY_RECENT_CHANNELS, null) ?: return emptyList()
        return try {
            recentListAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveRecentChannels(list: List<RecentChannelItem>) {
        try {
            val json = recentListAdapter.toJson(list)
            prefs.edit().putString(KEY_RECENT_CHANNELS, json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addRecentChannel(item: RecentChannelItem) {
        val currentList = getRecentChannels().toMutableList()
        // Remove duplicate handle to reposition it at the top
        currentList.removeAll { it.handle.lowercase() == item.handle.lowercase() }
        // Add to the front
        currentList.add(0, item)
        // Keep only last 5
        val limitedList = if (currentList.size > 5) currentList.take(5) else currentList
        saveRecentChannels(limitedList)
    }

    fun deleteRecentChannel(handle: String) {
        val currentList = getRecentChannels().toMutableList()
        currentList.removeAll { it.handle.lowercase() == handle.lowercase() }
        saveRecentChannels(currentList)
    }

    fun getLaunchReports(channelHandle: String): List<LaunchReport> {
        val json = prefs.getString("launch_reports_${channelHandle.lowercase()}", null) ?: return emptyList()
        return try {
            launchListAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveLaunchReports(channelHandle: String, list: List<LaunchReport>) {
        try {
            val json = launchListAdapter.toJson(list)
            prefs.edit().putString("launch_reports_${channelHandle.lowercase()}", json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addLaunchReport(channelHandle: String, report: LaunchReport) {
        val current = getLaunchReports(channelHandle).toMutableList()
        current.removeAll { it.topicName.lowercase() == report.topicName.lowercase() }
        current.add(0, report)
        saveLaunchReports(channelHandle, current)
    }

    fun getCompetitorsCache(handle: String): Call2Response? {
        val json = prefs.getString("competitors_cache_${handle.lowercase()}", null) ?: return null
        return try {
            call2Adapter.fromJson(json)
        } catch (e: Exception) {
            null
        }
    }

    fun saveCompetitorsCache(handle: String, data: Call2Response) {
        try {
            val json = call2Adapter.toJson(data)
            prefs.edit().putString("competitors_cache_${handle.lowercase()}", json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearCompetitorsCache(handle: String) {
        prefs.edit().remove("competitors_cache_${handle.lowercase()}").apply()
    }

    fun getStrategyCache(handle: String): Call3Response? {
        val json = prefs.getString("strategy_cache_${handle.lowercase()}", null) ?: return null
        return try {
            call3Adapter.fromJson(json)
        } catch (e: Exception) {
            null
        }
    }

    fun saveStrategyCache(handle: String, data: Call3Response) {
        try {
            val json = call3Adapter.toJson(data)
            prefs.edit().putString("strategy_cache_${handle.lowercase()}", json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearStrategyCache(handle: String) {
        prefs.edit().remove("strategy_cache_${handle.lowercase()}").apply()
    }

    fun getRemainingQuota(): Int {
        checkAndResetQuota()
        return prefs.getInt("quota_remaining", 10000)
    }

    fun deductQuota(amount: Int = 300) {
        checkAndResetQuota()
        val current = getRemainingQuota()
        val updated = (current - amount).coerceAtLeast(0)
        prefs.edit().putInt("quota_remaining", updated).apply()
    }

    private fun checkAndResetQuota() {
        val lastResetMs = prefs.getLong("last_reset_timestamp", 0L)
        val nowMs = System.currentTimeMillis()

        val tz = TimeZone.getTimeZone("America/Los_Angeles")
        val lastResetCal = Calendar.getInstance(tz).apply { timeInMillis = lastResetMs }
        val nowCal = Calendar.getInstance(tz).apply { timeInMillis = nowMs }

        val isDifferentDay = lastResetMs == 0L ||
                lastResetCal.get(Calendar.YEAR) != nowCal.get(Calendar.YEAR) ||
                lastResetCal.get(Calendar.DAY_OF_YEAR) != nowCal.get(Calendar.DAY_OF_YEAR)

        if (isDifferentDay) {
            prefs.edit()
                .putInt("quota_remaining", 10000)
                .putLong("last_reset_timestamp", nowMs)
                .apply()
        }
    }
}
