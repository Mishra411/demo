/*
 * MainActivity.kt
 * Single activity of the app. Hosts the Compose UI and the navigation shell.
 * Device identity is shared by owner and lottery flows inside the navigation shell.
 * The user's accessibility mode choices are kept on the device and applied
 * to the whole app through the theme.
 * AI use: saving and applying the accessibility choices was written with
 * Claude (Anthropic), Nov 2026; reviewed by Aalok Mishra.
 */
package com.example.code

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.code.model.AccessibilitySettings
import com.example.code.navigation.AppNavHost
import com.example.code.ui.theme.CodeTheme
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            startApp()
        } else {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    startApp()
                }
                .addOnFailureListener {
                    Log.e("MainActivity", "Firebase login failed", it)
                    // Still open the app so the user isn't stuck on a blank screen
                    startApp()
                }
        }
    }
    private fun startApp() {
        setContent {
            var accessibility by remember { mutableStateOf(loadAccessibility()) }
            CodeTheme(settings = accessibility) {
                AppNavHost(
                    accessibility = accessibility,
                    onAccessibilityChange = { changed ->
                        accessibility = changed.normalized()
                        saveAccessibility(accessibility)
                    },
                )
            }
        }
    }

    /** Reads the accessibility choices saved on this device. */
    private fun loadAccessibility(): AccessibilitySettings {
        val saved = getSharedPreferences(ACCESSIBILITY_PREFS, Context.MODE_PRIVATE)
        return AccessibilitySettings(
            colorBlind = saved.getBoolean(KEY_COLOR_BLIND, false),
            textScale = saved.getFloat(KEY_TEXT_SCALE, 1f),
            uiScale = saved.getFloat(KEY_UI_SCALE, 1f),
        ).normalized()
    }

    /** Saves the accessibility choices on this device. */
    private fun saveAccessibility(settings: AccessibilitySettings) {
        getSharedPreferences(ACCESSIBILITY_PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_COLOR_BLIND, settings.colorBlind)
            .putFloat(KEY_TEXT_SCALE, settings.textScale)
            .putFloat(KEY_UI_SCALE, settings.uiScale)
            .apply()
    }

    private companion object {
        const val ACCESSIBILITY_PREFS = "accessibility"
        const val KEY_COLOR_BLIND = "colorBlind"
        const val KEY_TEXT_SCALE = "textScale"
        const val KEY_UI_SCALE = "uiScale"
    }
}
