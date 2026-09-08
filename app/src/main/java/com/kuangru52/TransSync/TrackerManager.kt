package com.kuangru52.transsync

import android.content.res.ColorStateList
import android.graphics.BlurMaskFilter
import android.view.Gravity
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import com.kuangru52.transsync.databinding.ActivityTorrentListBinding
import com.kuangru52.transsync.databinding.NavHeaderBinding

class TrackerManager(
    private var activity: AppCompatActivity?,
    private var binding: ActivityTorrentListBinding?,
    private var headerBinding: NavHeaderBinding?,
    private val viewModel: TorrentListViewModel
) {
    private var appNameClickCount = 0
    
    private var lastTrackerData: Map<String, Int>? = null
    private var lastBlurState: Boolean = false
    private var lastRevealedNames: Set<String> = emptySet()

    fun setupTrackerPrivacy() {
        headerBinding?.tvAppName?.setOnClickListener {
            appNameClickCount++
            if (appNameClickCount >= 5) {
                viewModel.toggleTrackerBlur()
                val msg = if (viewModel.isTrackerBlurEnabled.value == true) "Privacy mode enabled" else "Privacy mode disabled"
                activity?.let { Toast.makeText(it, msg, Toast.LENGTH_SHORT).show() }
                appNameClickCount = 0
            }
        }
    }

    fun updateTrackerChips(trackerMap: Map<String, Int>, isDrawerMoving: Boolean) {
        if (isDrawerMoving) return
        val a = activity ?: return
        val b = binding ?: return

        val isBlurEnabled = viewModel.isTrackerBlurEnabled.value ?: false
        val revealedNames = viewModel.revealedTrackerNames.value ?: emptySet()
        
        val stateChanged = trackerMap != lastTrackerData || 
                         isBlurEnabled != lastBlurState || 
                         revealedNames != lastRevealedNames
                         
        if (!stateChanged) return
        
        lastTrackerData = trackerMap
        lastBlurState = isBlurEnabled
        lastRevealedNames = revealedNames

        val sortedEntries = trackerMap.entries.sortedByDescending { it.value }
        val newNames = sortedEntries.map { it.key }.toSet()
        val chipsToRemove = mutableListOf<View>()
        for (i in 0 until b.cgTrackers.childCount) {
            val child = b.cgTrackers.getChildAt(i)
            if (child.tag !in newNames) chipsToRemove.add(child)
        }
        chipsToRemove.forEach { b.cgTrackers.removeView(it) }

        sortedEntries.forEach { entry ->
            val trackerName = entry.key
            val displayCount = entry.value
            val isRevealed = revealedNames.contains(trackerName)
            
            var existingChip: com.google.android.material.chip.Chip? = null
            for (i in 0 until b.cgTrackers.childCount) {
                val child = b.cgTrackers.getChildAt(i) as? com.google.android.material.chip.Chip
                if (child?.tag == trackerName) {
                    existingChip = child
                    break
                }
            }

            val chip = existingChip ?: com.google.android.material.chip.Chip(a).apply {
                tag = trackerName
            }

            chip.apply {
                val shouldBlur = isBlurEnabled && !isRevealed
                val isCurrentlyBlur = paint.maskFilter != null
                val targetText = a.getString(R.string.tracker_count_format, trackerName, displayCount)

                if (shouldBlur) {
                    if (!isCurrentlyBlur || text != targetText) {
                        text = targetText
                        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                        post {
                            paint.maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)
                            invalidate()
                        }
                    }
                } else {
                    if (isCurrentlyBlur || text != targetText) {
                        setLayerType(View.LAYER_TYPE_NONE, null)
                        paint.maskFilter = null
                        text = targetText
                    }
                }

                isClickable = true
                isFocusable = true
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                
                val bgColor = ContextCompat.getColor(a, R.color.bg_main)
                chipBackgroundColor = ColorStateList.valueOf(bgColor)
                
                val strokeColor = ContextCompat.getColor(a, R.color.stroke_tag)
                chipStrokeColor = ColorStateList.valueOf(strokeColor)
                chipStrokeWidth = resources.displayMetrics.density * 1.0f 
                
                val textColor = ContextCompat.getColor(a, R.color.text_secondary)
                setTextColor(textColor)
                textSize = 12f 
                includeFontPadding = false
                
                setEnsureMinTouchTargetSize(false)
                
                minHeight = 0
                minimumHeight = 0
                chipMinHeight = resources.displayMetrics.density * 32f
                minimumWidth = 0 
                
                elevation = 0f
                stateListAnimator = null
                
                isChipIconVisible = false
                chipIcon = null
                
                chipStartPadding = resources.displayMetrics.density * 8f
                chipEndPadding = resources.displayMetrics.density * 8f
                textStartPadding = 0f
                textEndPadding = 0f
                iconStartPadding = 0f
                iconEndPadding = 0f

                shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                    .setAllCornerSizes(100f * resources.displayMetrics.density) 
                    .build()

                setOnClickListener {
                    val currentRevealed = viewModel.revealedTrackerNames.value ?: emptySet()
                    if (isBlurEnabled && !currentRevealed.contains(trackerName)) {
                        viewModel.revealTracker(trackerName)
                    } else {
                        viewModel.setFilter("tracker:$trackerName")
                        binding?.drawerLayout?.closeDrawer(GravityCompat.START)
                    }
                }
            }
            if (existingChip == null) {
                b.cgTrackers.addView(chip)
            }
        }
    }
    
    fun onDestroy() {
        activity = null
        binding = null
        headerBinding = null
    }

    fun clearRevealedTrackers() {
        viewModel.clearRevealedTrackers()
    }
}
