package com.kuangru52.transsync

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.kuangru52.transsync.databinding.ActivityTorrentListBinding

class SearchManager(
    private var activity: AppCompatActivity?,
    private var binding: ActivityTorrentListBinding?,
    private val viewModel: TorrentListViewModel
) {
    fun setupSearch() {
        val b = binding ?: return
        val a = activity ?: return

        b.ivSearch.setOnClickListener {
            val layoutSearch = b.layoutSearch
            layoutSearch.translationX = layoutSearch.width.toFloat()
            layoutSearch.visibility = View.VISIBLE
            
            b.etSearch.requestFocus()
            val imm = a.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(b.etSearch, InputMethodManager.SHOW_IMPLICIT)

            layoutSearch.animate()
                .translationX(0f)
                .setDuration(300)
                .start()
        }

        b.ivCloseSearch.setOnClickListener {
            closeSearch()
        }

        b.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = b.etSearch.text.toString().lowercase()
                viewModel.setSearchQuery(query)
                val imm = a.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(b.etSearch.windowToken, 0)
                true
            } else {
                false
            }
        }

        b.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().lowercase()
                viewModel.setSearchQuery(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    fun closeSearch() {
        val b = binding ?: return
        val a = activity ?: return
        val imm = a.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(b.etSearch.windowToken, 0)
        
        b.layoutSearch.animate()
            .translationX(b.layoutSearch.width.toFloat())
            .setDuration(300)
            .withEndAction {
                binding?.let {
                    it.layoutSearch.visibility = View.GONE
                    it.etSearch.text.clear()
                }
                viewModel.setSearchQuery("")
            }
            .start()
    }

    fun onDestroy() {
        binding?.layoutSearch?.animate()?.cancel()
        activity = null
        binding = null
    }

    fun isSearchVisible(): Boolean {
        return binding?.layoutSearch?.isVisible ?: false
    }

    fun getSearchQuery(): String {
        return binding?.etSearch?.text?.toString()?.lowercase() ?: ""
    }
}
