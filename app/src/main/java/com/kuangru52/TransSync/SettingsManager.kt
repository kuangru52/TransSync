package com.kuangru52.transsync

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat

/**
 * 100% 统一标准的应用主题 (深色/浅色/跟随系统) 与多语言 (中文/英文/跟随系统) 设置管理器
 */
object SettingsManager {

    private const val PREFS_NAME = "settings_prefs"
    private const val KEY_THEME = "app_theme" // "system", "light", "dark"
    private const val KEY_LANG = "app_language" // "system", "zh", "en"
    private const val KEY_PRIVACY = "privacy_mode"
    private const val KEY_DEVELOPER_MODE = "developer_mode"
    private const val KEY_CUSTOM_TRACKERS = "custom_tracker_mappings"

    // 1. 底部网速条独立液态玻璃 Shader 参数 Key
    private const val KEY_SPEEDBAR_REFRACTION = "speedbar_refraction"
    private const val KEY_SPEEDBAR_HEIGHT = "speedbar_height"
    private const val KEY_SPEEDBAR_BLUR = "speedbar_blur"
    private const val KEY_SPEEDBAR_SATURATION = "speedbar_saturation"

    // 2. 所有通用弹窗独立液态玻璃 Shader 参数 Key
    private const val KEY_DIALOG_REFRACTION = "dialog_refraction"
    private const val KEY_DIALOG_HEIGHT = "dialog_height"
    private const val KEY_DIALOG_BLUR = "dialog_blur"
    private const val KEY_DIALOG_SATURATION = "dialog_saturation"

    fun getCustomTrackerMappings(context: Context): Map<String, String> {
        val jsonStr = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_TRACKERS, null) ?: return emptyMap()
        return try {
            val jsonObj = org.json.JSONObject(jsonStr)
            val map = mutableMapOf<String, String>()
            val keys = jsonObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = jsonObj.getString(key)
                if (key.isNotBlank() && value.isNotBlank()) {
                    map[key.trim()] = value.trim()
                }
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveCustomTrackerMappings(context: Context, mappings: Map<String, String>) {
        val jsonObj = org.json.JSONObject()
        for ((key, value) in mappings) {
            if (key.isNotBlank() && value.isNotBlank()) {
                jsonObj.put(key.trim(), value.trim())
            }
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_CUSTOM_TRACKERS, jsonObj.toString()) }
    }

    @Suppress("unused")
    fun setCustomTrackerMapping(context: Context, trackerDomain: String, labelName: String) {
        val current = getCustomTrackerMappings(context).toMutableMap()
        if (trackerDomain.isNotBlank() && labelName.isNotBlank()) {
            current[trackerDomain.trim()] = labelName.trim()
            saveCustomTrackerMappings(context, current)
        }
    }

    @Suppress("unused")
    fun removeCustomTrackerMapping(context: Context, trackerDomain: String) {
        val current = getCustomTrackerMappings(context).toMutableMap()
        current.remove(trackerDomain.trim())
        saveCustomTrackerMappings(context, current)
    }

    fun isPrivacyMode(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PRIVACY, false)
    }

    fun setPrivacyMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_PRIVACY, enabled) }
    }

    fun isDeveloperMode(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DEVELOPER_MODE, false)
    }

    fun setDeveloperMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_DEVELOPER_MODE, enabled) }
    }

    // --- 底部网速条独立液态玻璃参数 Getter & Setter ---
    fun getSpeedbarRefraction(context: Context, isDark: Boolean): Float {
        val defaultVal = if (isDark) 51f else 14f
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_SPEEDBAR_REFRACTION, defaultVal)
    }

    fun setSpeedbarRefraction(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putFloat(KEY_SPEEDBAR_REFRACTION, value) }
    }

    @Suppress("UNUSED_PARAMETER")
    fun getSpeedbarHeight(context: Context, isDark: Boolean): Float {
        val defaultVal = 3f
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_SPEEDBAR_HEIGHT, defaultVal)
    }

    fun setSpeedbarHeight(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putFloat(KEY_SPEEDBAR_HEIGHT, value) }
    }

    @Suppress("UNUSED_PARAMETER")
    fun getSpeedbarBlur(context: Context, isDark: Boolean): Float {
        val defaultVal = 14f
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_SPEEDBAR_BLUR, defaultVal)
    }

    fun setSpeedbarBlur(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putFloat(KEY_SPEEDBAR_BLUR, value) }
    }

    @Suppress("UNUSED_PARAMETER")
    fun getSpeedbarSaturation(context: Context, isDark: Boolean): Float {
        val defaultVal = 1.60f
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_SPEEDBAR_SATURATION, defaultVal)
    }

    fun setSpeedbarSaturation(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putFloat(KEY_SPEEDBAR_SATURATION, value) }
    }

    // --- 全局弹窗独立液态玻璃参数 Getter & Setter ---
    fun getDialogRefraction(context: Context, isDark: Boolean): Float {
        val defaultVal = if (isDark) -11f else -27f
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_DIALOG_REFRACTION, defaultVal)
    }

    fun setDialogRefraction(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putFloat(KEY_DIALOG_REFRACTION, value) }
    }

    fun getDialogHeight(context: Context, isDark: Boolean): Float {
        val defaultVal = if (isDark) 21f else 30f
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_DIALOG_HEIGHT, defaultVal)
    }

    fun setDialogHeight(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putFloat(KEY_DIALOG_HEIGHT, value) }
    }

    fun getDialogBlur(context: Context, isDark: Boolean): Float {
        val defaultVal = if (isDark) 22f else 19f
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_DIALOG_BLUR, defaultVal)
    }

    fun setDialogBlur(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putFloat(KEY_DIALOG_BLUR, value) }
    }

    fun getDialogSaturation(context: Context, isDark: Boolean): Float {
        val defaultVal = if (isDark) 2.1f else 2.5f
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_DIALOG_SATURATION, defaultVal)
    }

    fun setDialogSaturation(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putFloat(KEY_DIALOG_SATURATION, value) }
    }

    fun getThemeMode(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME, "system") ?: "system"
    }

    fun setThemeMode(context: Context, themeMode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_THEME, themeMode) }

        val nightMode = when (themeMode) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    fun getLanguage(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANG, "system") ?: "system"
    }

    fun setLanguage(context: Context, lang: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_LANG, lang) }

        val locales = when (lang) {
            "zh" -> LocaleListCompat.forLanguageTags("zh-CN")
            "en" -> LocaleListCompat.forLanguageTags("en")
            else -> LocaleListCompat.getEmptyLocaleList()
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun applySettingsOnAppStart(context: Context) {
        val nightMode = when (getThemeMode(context)) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)

        val locales = when (getLanguage(context)) {
            "zh" -> LocaleListCompat.forLanguageTags("zh-CN")
            "en" -> LocaleListCompat.forLanguageTags("en")
            else -> LocaleListCompat.getEmptyLocaleList()
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
