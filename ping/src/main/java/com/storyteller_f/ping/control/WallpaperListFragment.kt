package com.storyteller_f.ping.control

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.doOnPreDraw
import androidx.datastore.preferences.core.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.flowWithLifecycle
import androidx.navigation.findNavController
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.storyteller_f.common_ui.SimpleFragment
import com.storyteller_f.common_ui.cycle
import com.storyteller_f.common_ui.scope
import com.storyteller_f.ping.R
import com.storyteller_f.ping.bookDataStore
import com.storyteller_f.ping.database.Wallpaper
import com.storyteller_f.ping.database.requireMainDatabase
import com.storyteller_f.ping.databinding.FragmentWallpaperListBinding
import com.storyteller_f.ping.databinding.ViewHolderWallpaperBinding
import com.storyteller_f.ping.pagerDataStore
import com.storyteller_f.ping.preview
import com.storyteller_f.ping.selected
//import com.storyteller_f.ping.wallpaper.PingBookService
import com.storyteller_f.ping.wallpaper.PingPagerService
import com.storyteller_f.ping.wallpaper.PingWorldService
import com.storyteller_f.ping.worldDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

class WallpaperListFragment :
    SimpleFragment<FragmentWallpaperListBinding>(FragmentWallpaperListBinding::inflate) {

    override fun onBindViewEvent(binding: FragmentWallpaperListBinding) {
        postponeEnterTransition()
        val adapter = object : ListAdapter<WallpaperHolder, RecyclerView.ViewHolder>(
            object : DiffUtil.ItemCallback<WallpaperHolder>() {
                override fun areItemsTheSame(
                    oldItem: WallpaperHolder,
                    newItem: WallpaperHolder
                ): Boolean {
                    return oldItem.areItemsTheSame(newItem)
                }

                override fun areContentsTheSame(
                    oldItem: WallpaperHolder,
                    newItem: WallpaperHolder
                ): Boolean {
                    return oldItem.areContentsTheSame(newItem)
                }
            }
        ) {
            override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int
            ): RecyclerView.ViewHolder {
                return buildWallpaperHolder(parent)
            }

            override fun onBindViewHolder(
                holder: RecyclerView.ViewHolder,
                position: Int
            ) {
                val wallpaperHolder = getItem(position)
                (holder as WallpaperViewHolder).bindData(wallpaperHolder)
            }
        }
        binding.content.adapter = adapter
        val mainDao = requireContext().requireMainDatabase.dao()
        scope.launch {
            mainDao.selectAll()
                .flowWithLifecycle(cycle)
                .shareIn(scope, SharingStarted.WhileSubscribed())
                .onStart {
                    (binding.root.parent as? ViewGroup)?.doOnPreDraw {
                        startPostponedEnterTransition()
                    }
                }
                .collectLatest { wallpaperList ->
                    adapter.submitList(wallpaperList.map {
                        WallpaperHolder(it)
                    })
                }
        }
    }

    private val pagerDataStore by lazy { requireContext().pagerDataStore }
    private val worldDataStore by lazy { requireContext().worldDataStore }
    private val bookDataStore by lazy { requireContext().bookDataStore }

    private val setWallpaper =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.i(TAG, "choose: result ${result.resultCode}")
            if (result.resultCode == Activity.RESULT_OK) {
                scope.launch {
                    val first = pagerDataStore.data.mapNotNull {
                        it[preview]
                    }.first()
                    pagerDataStore.edit {
                        it[selected] = first
                        it[preview] = ""
                    }
                }
            }
        }

    fun previewWallpaper(itemHolder: WallpaperHolder) {
        val uri = itemHolder.wallpaper.uri
        scope.launch {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            if (uri.endsWith("gltf")) {
                worldDataStore.edit {
                    it[preview] = uri
                }
                intent.putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(requireActivity(), PingWorldService::class.java)
                )
            } else if (uri.endsWith("json")) {
                return@launch
//                bookDataStore.edit {
//                    it[preview] = uri
//                }
//                intent.putExtra(
//                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
//                    ComponentName(requireActivity(), PingBookService::class.java)
//                )
            } else {
                pagerDataStore.edit {
                    it[preview] = uri
                }
                intent.putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(requireActivity(), PingPagerService::class.java)
                )
            }
            setWallpaper.launch(intent)
        }

    }

    fun openWallpaperInfo(
        holderView: View,
        itemHolder: WallpaperHolder,
        binding: ViewHolderWallpaperBinding
    ) {
        holderView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        val fragmentNavigatorExtras =
            FragmentNavigatorExtras(binding.root to "wallpaper-preview")
        holderView.findNavController()
            .navigate(
                R.id.action_WallpaperListFragment_to_WallpaperInfoFragment,
                WallpaperInfoFragmentArgs(itemHolder.wallpaper.uri).toBundle(),
                null,
                navigatorExtras = fragmentNavigatorExtras
            )
    }

    companion object {
        private const val TAG = "WallpaperListFragment"
    }
}

class WallpaperHolder(val wallpaper: Wallpaper) {
    fun areItemsTheSame(other: WallpaperHolder): Boolean {
        return other.wallpaper == wallpaper
    }

    fun areContentsTheSame(other: WallpaperHolder): Boolean {
        return other.wallpaper == wallpaper
    }
}

class WallpaperViewHolder(private val binding: ViewHolderWallpaperBinding) :
    RecyclerView.ViewHolder(binding.root) {
    var itemHolder: WallpaperHolder? = null
    fun bindData(itemHolder: WallpaperHolder) {
        this.itemHolder = itemHolder
        binding.flash(itemHolder.wallpaper)
        ViewCompat.setTransitionName(
            binding.root,
            "wallpaper-${itemHolder.wallpaper.uri}"
        )
    }

}

fun ViewHolderWallpaperBinding.flash(wallpaper: Wallpaper, fragment: Fragment? = null) {
    wallpaperUri.text = wallpaper.uri
    wallpaperName.text = wallpaper.name
    Glide.with(wallpaperPreview).load(wallpaper.thumbnail).listener(object :
        RequestListener<Drawable> {
        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            target: Target<Drawable>,
            isFirstResource: Boolean
        ): Boolean {
            fragment?.startPostponedEnterTransition()
            return false
        }

        override fun onResourceReady(
            resource: Drawable,
            model: Any,
            target: Target<Drawable>?,
            dataSource: DataSource,
            isFirstResource: Boolean
        ): Boolean {
            fragment?.startPostponedEnterTransition()
            return false
        }
    }).into(wallpaperPreview)
}