package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.ui.GrowTubeAppContent
import com.example.ui.GrowTubeViewModel
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    composeTestRule.setContent { MyApplicationTheme { androidx.compose.material3.Text("Robolectric") } }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }

  @Test
  fun testAppLaunch() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val viewModel = GrowTubeViewModel(context.applicationContext as Application)
    composeTestRule.setContent {
      MyApplicationTheme {
        GrowTubeAppContent(viewModel = viewModel)
      }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/app_launch.png")
  }

  @Test
  fun testVideoAutopsyLaunch() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val viewModel = GrowTubeViewModel(context.applicationContext as Application)

    val mockVideo = com.example.data.VideoInsight("How to Code Awesome Mobile Apps in Kotlin", "Weak pacing and low hook presence in first 10 seconds.")
    val mockStats = com.example.data.ChannelStats(
      id = "123",
      name = "CodeChannel",
      handle = "@codechannel",
      subscribers = 5000L,
      totalViews = 50000L,
      totalVideos = 100L,
      avatarUrl = "",
      uploadFrequencyValue = 1.0,
      uploadFrequencyText = "1 video/week",
      averageViews = 1500L,
      recentVideos = emptyList()
    )
    val mockAnalysis = com.example.data.ChannelAnalysis(
      healthScore = 75,
      healthReason = "Good view growth relative to videos.",
      bestVideos = listOf(mockVideo),
      worstVideos = listOf(mockVideo),
      audienceProfile = "Developers seeking Kotlin tips.",
      mainWeakness = "Thumbnail layout is cluttered.",
      competitors = listOf(
        com.example.data.CompetitorAnalysis("TechVibe", "@techvibe", 50000L, 12000L, "2 videos/week")
      ),
      gapAnalysis = "More Jetpack Compose design patterns.",
      strategiesToAdopt = listOf("Keep tutorial pacing brisk"),
      yourAdvantage = "High coding precision",
      videoIdeas = emptyList(),
      contentCalendar = emptyList(),
      bestUploadDays = listOf("Monday"),
      recommendedLength = "10 minutes",
      shortsVsLongRatio = "1:1"
    )
    val mockReport = com.example.data.FullGrowTubeReport(mockStats, mockAnalysis)

    viewModel.navigateToVideoAutopsy(mockVideo, mockReport)

    composeTestRule.setContent {
      MyApplicationTheme {
        GrowTubeAppContent(viewModel = viewModel)
      }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/video_autopsy.png")
  }
}
