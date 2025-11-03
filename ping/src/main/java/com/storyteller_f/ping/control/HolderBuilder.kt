package com.storyteller_f.ping.control

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.findFragment
import com.storyteller_f.ping.databinding.ViewHolderWallpaperBinding

@Suppress("UNUSED_ANONYMOUS_PARAMETER")
fun buildWallpaperHolder(parent: ViewGroup): WallpaperViewHolder {
    val context = parent.context
    val binding = ViewHolderWallpaperBinding.inflate(LayoutInflater.from(context), parent, false)

    val viewHolder = WallpaperViewHolder(binding)
    binding.root.setOnClickListener { v ->
        val itemHolder = viewHolder.itemHolder ?: return@setOnClickListener
        v.findFragment<WallpaperListFragment>()
            .openWallpaperInfo(v, itemHolder, binding)
    }
    binding.root.setOnLongClickListener { v ->
        val itemHolder = viewHolder.itemHolder ?: return@setOnLongClickListener false
        v.findFragment<WallpaperListFragment>()
            .previewWallpaper(itemHolder)
        true
    }
    return viewHolder
}

