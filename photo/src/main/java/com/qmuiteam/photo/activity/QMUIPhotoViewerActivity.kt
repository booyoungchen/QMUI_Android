/*
 * Tencent is pleased to support the open source community by making QMUI_Android available.
 *
 * Copyright (C) 2017-2018 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the MIT License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/MIT
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qmuiteam.photo.activity

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.qmuiteam.photo.data.*
import com.qmuiteam.photo.data.PhotoTransitionDelivery
import com.qmuiteam.photo.util.asBitmap

private const val PHOTO_CURRENT_INDEX = "qmui_photo_current_index"
private const val PHOTO_TRANSITION_DELIVERY_KEY = "qmui_photo_transition_delivery"
private const val PHOTO_COUNT = "qmui_photo_count"
private const val PHOTO_META_KEY_PREFIX = "qmui_photo_meta_"
private const val PHOTO_PROVIDER_RECOVER_CLASS_KEY_PREFIX = "qmui_photo_provider_recover_cls_"

class PhotoViewerViewModel(val state: SavedStateHandle) : ViewModel() {

    val enterIndex = state.get<Int>(PHOTO_CURRENT_INDEX) ?: 0
    val data: PhotoViewerData?

    private val transitionDeliverKey = state.get<Long>(PHOTO_TRANSITION_DELIVERY_KEY) ?: -1

    init {
        val transitionDeliverData = PhotoTransitionDelivery.getAndRemove(transitionDeliverKey)
        data = if(transitionDeliverData != null){
            transitionDeliverData
        }else{
            val count = state.get<Int>(PHOTO_COUNT) ?: 0
            if(count > 0){
                val list = arrayListOf<PhotoProvider>()
                for(i in 0 until count){
                    try{
                        val meta = state.get<Bundle>("${PHOTO_META_KEY_PREFIX}${i}")
                        val clsName = state.get<String>("${PHOTO_PROVIDER_RECOVER_CLASS_KEY_PREFIX}${i}")
                        if(meta == null || clsName.isNullOrBlank()){
                            list.add(LossPhotoProvider.instance)
                        }else{
                            val cls = Class.forName(clsName)
                            val recover = cls.newInstance() as PhotoProviderRecover
                            list.add(recover.recover(meta) ?: LossPhotoProvider.instance)
                        }

                    }catch (e: Throwable){
                        list.add(LossPhotoProvider.instance)
                    }
                }
                PhotoViewerData(list, enterIndex, null)
            }else{
                null
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        PhotoTransitionDelivery.remove(transitionDeliverKey)
    }
}

open class QMUIPhotoViewerActivity : AppCompatActivity() {

    companion object {

        fun intentOf(
            activity: AppCompatActivity,
            cls: Class<in QMUIPhotoViewerActivity>,
            list: List<PhotoProvider>,
            index: Int
        ): Intent {
            val data = PhotoViewerData(list, index, activity.window.decorView.asBitmap())
            val intent = Intent(activity, cls)
            intent.putExtra(PHOTO_TRANSITION_DELIVERY_KEY, PhotoTransitionDelivery.put(data))
            intent.putExtra(PHOTO_CURRENT_INDEX, index)
            intent.putExtra(PHOTO_COUNT, list.size)
            list.forEachIndexed { i, provider ->
                val meta = provider.meta()
                val recoverCls = provider.recoverCls()
                if (meta != null && recoverCls != null) {
                    intent.putExtra("${PHOTO_META_KEY_PREFIX}${i}", meta)
                    intent.putExtra("${PHOTO_PROVIDER_RECOVER_CLASS_KEY_PREFIX}${i}", recoverCls.name)
                }
            }
            return intent
        }
    }

    private val viewModel by viewModels<PhotoViewerViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView)?.let {
            it.hide(WindowInsetsCompat.Type.statusBars())
            it.isAppearanceLightNavigationBars = false
        }
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            window.navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                window.navigationBarDividerColor = Color.TRANSPARENT
            }
        }

        setContent {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                val data = viewModel.data
                if (data == null || data.list.isEmpty()) {
                    Text(text = "没有图片数据")
                } else {
                    viewModel.data?.background?.let {
                        Image(
                            painter = BitmapPainter(it.asImageBitmap()),
                            contentDescription = "",
                            contentScale = ContentScale.FillWidth,
                            alignment = Alignment.TopCenter,
                            modifier = Modifier.fillMaxSize()
                        )
                        PhotoViewer(list = data.list, index = data.index)
                    }

                }
            }
        }
    }

    @OptIn(ExperimentalPagerApi::class)
    @Composable
    protected open fun PhotoViewer(list: List<PhotoProvider>, index: Int) {
        val currentIndex = rememberSaveable {
            mutableStateOf(index)
        }
        val pagerState = rememberPagerState(index)
        HorizontalPager(
            count = list.size,
            state = pagerState
        ) { page ->

        }
    }
}