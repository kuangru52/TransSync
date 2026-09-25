package com.kuangru52.transsync

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat

/**
 * 液态玻璃着色器与光学参数聚合数据类
 */
data class GlassParams(
    val refraction: Float,
    val refractionHeight: Float,
    val blurRadius: Float,
    val saturationBoost: Float,
    val contrast: Float,
    val whitePoint: Float,
)

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

    // 壁纸配置 Key
    private const val KEY_WALLPAPER_MODE = "wallpaper_mode" // "none", "bing", "local"
    private const val KEY_WALLPAPER_LOCAL_URIS = "wallpaper_local_uris"
    private const val KEY_WALLPAPER_BLUR = "wallpaper_blur"

    var wallpaperStateVersion by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    // 1. 底部网速条独立液态玻璃 Shader 参数 Key
    private const val KEY_SPEEDBAR_REFRACTION = "speedbar_refraction"
    private const val KEY_SPEEDBAR_HEIGHT = "speedbar_height"
    private const val KEY_SPEEDBAR_BLUR = "speedbar_blur"
    private const val KEY_SPEEDBAR_SATURATION = "speedbar_saturation"
    private const val KEY_SPEEDBAR_CONTRAST = "speedbar_contrast"
    private const val KEY_SPEEDBAR_WHITE_POINT = "speedbar_white_point"

    // 2. 所有通用弹窗独立液态玻璃 Shader 参数 Key
    private const val KEY_DIALOG_REFRACTION = "dialog_refraction"
    private const val KEY_DIALOG_HEIGHT = "dialog_height"
    private const val KEY_DIALOG_BLUR = "dialog_blur"
    private const val KEY_DIALOG_SATURATION = "dialog_saturation"
    private const val KEY_DIALOG_CONTRAST = "dialog_contrast"
    private const val KEY_DIALOG_WHITE_POINT = "dialog_white_point"

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
    @Suppress("UNUSED_PARAMETER")
    fun getSpeedbarRefraction(context: Context, isDark: Boolean): Float {
        val defaultVal = -30f
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
        val defaultVal = 28f
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

    @Suppress("UNUSED_PARAMETER")
    fun getSpeedbarContrast(context: Context, isDark: Boolean): Float {
        val defaultVal = 0.0f
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_SPEEDBAR_CONTRAST, defaultVal)
    }

    fun setSpeedbarContrast(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putFloat(KEY_SPEEDBAR_CONTRAST, value) }
    }

    @Suppress("UNUSED_PARAMETER")
    fun getSpeedbarWhitePoint(context: Context, isDark: Boolean): Float {
        val defaultVal = if (isDark) 0.10f else 0.20f
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_SPEEDBAR_WHITE_POINT, defaultVal)
    }

    fun setSpeedbarWhitePoint(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putFloat(KEY_SPEEDBAR_WHITE_POINT, value) }
    }

    // --- FAB 按钮独立液态玻璃参数 ---
    private const val KEY_FAB_REFRACTION = "fab_refraction"
    private const val KEY_FAB_HEIGHT = "fab_height"
    private const val KEY_FAB_BLUR = "fab_blur"
    private const val KEY_FAB_SATURATION = "fab_saturation"
    private const val KEY_FAB_CONTRAST = "fab_contrast"
    private const val KEY_FAB_WHITE_POINT = "fab_white_point"

    fun getFabRefraction(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat(KEY_FAB_REFRACTION, 18f)

    fun setFabRefraction(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putFloat(KEY_FAB_REFRACTION, value) }
    }

    fun getFabHeight(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat(KEY_FAB_HEIGHT, 20f)

    fun setFabHeight(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putFloat(KEY_FAB_HEIGHT, value) }
    }

    fun getFabBlur(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat(KEY_FAB_BLUR, 16f)

    fun setFabBlur(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putFloat(KEY_FAB_BLUR, value) }
    }

    fun getFabSaturation(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat(KEY_FAB_SATURATION, 1.4f)

    fun setFabSaturation(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putFloat(KEY_FAB_SATURATION, value) }
    }

    fun getFabContrast(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat(KEY_FAB_CONTRAST, 0.12f)

    fun setFabContrast(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putFloat(KEY_FAB_CONTRAST, value) }
    }

    fun getFabWhitePoint(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat(KEY_FAB_WHITE_POINT, 0.08f)

    fun setFabWhitePoint(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putFloat(KEY_FAB_WHITE_POINT, value) }
    }

    // --- 全局 GlassParams 数据类集中管理 ---
    fun getSpeedbarGlassParams(context: Context, isDark: Boolean): GlassParams {
        val defRefraction = if (isDark) 51f else 14f
        val defHeight = 3f
        val defBlur = 14f
        val defSaturation = 1.60f
        val defContrast = 1.0f
        val defWhitePoint = if (isDark) 0.10f else 0.20f

        return GlassParams(
            refraction = getSpeedbarRefraction(context, isDark),
            refractionHeight = getSpeedbarHeight(context, isDark),
            blurRadius = getSpeedbarBlur(context, isDark),
            saturationBoost = getSpeedbarSaturation(context, isDark),
            contrast = getSpeedbarContrast(context, isDark),
            whitePoint = getSpeedbarWhitePoint(context, isDark),
        )
    }

    fun saveSpeedbarGlassParams(context: Context, params: GlassParams) {
        setSpeedbarRefraction(context, params.refraction)
        setSpeedbarHeight(context, params.refractionHeight)
        setSpeedbarBlur(context, params.blurRadius)
        setSpeedbarSaturation(context, params.saturationBoost)
        setSpeedbarContrast(context, params.contrast)
        setSpeedbarWhitePoint(context, params.whitePoint)
    }

    fun getDialogGlassParams(context: Context, isDark: Boolean): GlassParams {
        return GlassParams(
            refraction = getDialogRefraction(context, isDark),
            refractionHeight = getDialogHeight(context, isDark),
            blurRadius = getDialogBlur(context, isDark),
            saturationBoost = getDialogSaturation(context, isDark),
            contrast = getDialogContrast(context, isDark),
            whitePoint = getDialogWhitePoint(context, isDark),
        )
    }

    fun saveDialogGlassParams(context: Context, params: GlassParams) {
        setDialogRefraction(context, params.refraction)
        setDialogHeight(context, params.refractionHeight)
        setDialogBlur(context, params.blurRadius)
        setDialogSaturation(context, params.saturationBoost)
        setDialogContrast(context, params.contrast)
        setDialogWhitePoint(context, params.whitePoint)
    }
    @Suppress("UNUSED_PARAMETER")
    fun getDialogRefraction(context: Context, isDark: Boolean): Float {
        val defaultVal = -60f
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_DIALOG_REFRACTION, defaultVal)
    }

    fun setDialogRefraction(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putFloat(KEY_DIALOG_REFRACTION, value) }
    }

    fun getDialogHeight(context: Context, isDark: Boolean): Float {
        val defaultVal = if (isDark) 50f else 4f
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_DIALOG_HEIGHT, defaultVal)
    }

    fun setDialogHeight(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putFloat(KEY_DIALOG_HEIGHT, value) }
    }

    fun getDialogBlur(context: Context, isDark: Boolean): Float {
        val defaultVal = 32f
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_DIALOG_BLUR, defaultVal)
    }

    fun setDialogBlur(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putFloat(KEY_DIALOG_BLUR, value) }
    }

    @Suppress("UNUSED_PARAMETER")
    fun getDialogSaturation(context: Context, isDark: Boolean): Float {
        val defaultVal = 3.0f
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_DIALOG_SATURATION, defaultVal)
    }

    fun setDialogSaturation(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putFloat(KEY_DIALOG_SATURATION, value) }
    }

    @Suppress("UNUSED_PARAMETER")
    fun getDialogContrast(context: Context, isDark: Boolean): Float {
        val defaultVal = 1.0f
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_DIALOG_CONTRAST, defaultVal)
    }

    fun setDialogContrast(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putFloat(KEY_DIALOG_CONTRAST, value) }
    }

    @Suppress("UNUSED_PARAMETER")
    fun getDialogWhitePoint(context: Context, isDark: Boolean): Float {
        val defaultVal = if (isDark) 0.10f else 0.20f
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_DIALOG_WHITE_POINT, defaultVal)
    }

    fun setDialogWhitePoint(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putFloat(KEY_DIALOG_WHITE_POINT, value) }
    }

    fun getWallpaperMode(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_WALLPAPER_MODE, "none") ?: "none"
    }

    fun setWallpaperMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_WALLPAPER_MODE, mode) }
        wallpaperStateVersion++
    }

    fun getLocalWallpaperUris(context: Context): List<String> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_WALLPAPER_LOCAL_URIS, null) ?: return emptyList()
        return try {
            val jsonArray = org.json.JSONArray(json)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
            list
        } catch (_: Exception) { emptyList() }
    }

    fun setLocalWallpaperUris(context: Context, uris: List<String>) {
        val jsonArray = org.json.JSONArray()
        uris.forEach { jsonArray.put(it) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_WALLPAPER_LOCAL_URIS, jsonArray.toString()) }
        wallpaperStateVersion++
    }

    private var currentWallpaperBlurState = androidx.compose.runtime.mutableFloatStateOf(-1f)

    fun getWallpaperBlur(context: Context): Float {
        if (currentWallpaperBlurState.floatValue < 0f) {
            val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getFloat(KEY_WALLPAPER_BLUR, 20f)
            currentWallpaperBlurState.floatValue = saved
        }
        return currentWallpaperBlurState.floatValue
    }

    fun setWallpaperBlur(context: Context, blurRadius: Float) {
        currentWallpaperBlurState.floatValue = blurRadius
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putFloat(KEY_WALLPAPER_BLUR, blurRadius) }
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
