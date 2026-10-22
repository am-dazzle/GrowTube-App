package com.example

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.GrowTubeAppContent
import com.example.ui.GrowTubeViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: GrowTubeViewModel by viewModels()
    private val crashErrorState = mutableStateOf<Pair<String, String>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup bulletproof uncaught exception catcher for ALL threads
        var isHandlingCrash = false
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("GrowTubeCrash", "Uncaught exception on thread: ${thread.name}", throwable)
            
            if (isHandlingCrash) {
                defaultHandler?.uncaughtException(thread, throwable)
                return@setDefaultUncaughtExceptionHandler
            }
            
            // If it is a CancellationException, do NOT crash the app! Coroutines cancel themselves safely.
            if (throwable is kotlinx.coroutines.CancellationException || 
                throwable.cause is kotlinx.coroutines.CancellationException) {
                return@setDefaultUncaughtExceptionHandler
            }
            
            isHandlingCrash = true
            
            // Generate clean stack trace string
            val s = java.io.StringWriter()
            val p = java.io.PrintWriter(s)
            throwable.printStackTrace(p)
            val stackTrace = s.toString()
            val message: String = throwable.localizedMessage ?: throwable.message ?: "Unknown Exception"
            
            try {
                // Persist the crash trace and message synchronously using commit()
                getSharedPreferences("growtube_prefs", MODE_PRIVATE)
                    .edit()
                    .putString("last_crash_trace", stackTrace)
                    .putString("last_crash_message", message)
                    .commit()
            } catch (e: Throwable) {
                // Ignore failure in saving so we proceed to the default handler
            }

            // Propagate the exception to the system default handler to let the process terminate cleanly.
            // This prevents zombie loops, hanging UI threads, or broken InputDispatcher channels.
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Check if we started from a crash 
        val prefs = getSharedPreferences("growtube_prefs", MODE_PRIVATE)
        val savedCrashTrace = prefs.getString("last_crash_trace", null)
        val savedCrashMessage = prefs.getString("last_crash_message", "Unknown Exception") ?: "Unknown Exception"
        if (savedCrashTrace != null) {
            crashErrorState.value = Pair(savedCrashMessage, savedCrashTrace)
            // Immediately consume and clear the crash record from preferences so subsequent cold starts launch fresh
            prefs.edit()
                .remove("last_crash_trace")
                .remove("last_crash_message")
                .apply()
        }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val crashError by crashErrorState
                if (crashError != null) {
                    RecoveryScreen(
                        message = crashError!!.first,
                        stackTrace = crashError!!.second
                    ) {
                        // Quick Reset Callback: Clear preferences and the crash log
                        getSharedPreferences("growtube_prefs", MODE_PRIVATE).edit()
                            .clear()
                            .apply()
                        
                        crashErrorState.value = null
                        
                        // Cleanly restart MainActivity to re-instantiate VM and context fresh
                        val intent = packageManager.getLaunchIntentForPackage(packageName)
                        if (intent != null) {
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            startActivity(intent)
                        }
                        finish()
                    }
                } else {
                    GrowTubeAppContent(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun RecoveryScreen(message: String, stackTrace: String, onReset: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .padding(16.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = "Crash Icon",
                tint = Color(0xFFFF1744),
                modifier = Modifier.size(64.dp)
            )

            Text(
                text = "GrowTube Encountered an Error Screen",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFFFD600),
                fontWeight = FontWeight.Medium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .background(Color.Black, RoundedCornerShape(8.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = stackTrace,
                    color = Color.White.copy(alpha = 0.85f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(stackTrace))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Copy Code")
                }

                Button(
                    onClick = onReset,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset & Restart")
                }
            }
        }
    }
}
