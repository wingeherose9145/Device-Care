package com.system.helper

import android.content.pm.ActivityInfo
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var seekBar: SeekBar

    private lateinit var videoUris: ArrayList<String>
    private var currentIndex = 0

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.playerView)
        seekBar = findViewById(R.id.seekBar)

        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        playerView.useController = false

        videoUris = intent.getStringArrayListExtra("video_list") ?: arrayListOf()
        currentIndex = intent.getIntExtra("current_index", 0)

        if (videoUris.isEmpty()) {
            Toast.makeText(this, "没有视频可播放", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupGestureDetector()
        setupSeekBar()

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    playNextVideo()
                }
            }
        })

        playCurrentVideo()

        // 【优化点 1】参考文件一：点击屏幕，播放时隐藏进度条，暂停时显示进度条
        playerView.setOnClickListener {
            if (player.isPlaying) {
                player.pause()
                seekBar.visibility = View.VISIBLE
            } else {
                player.play()
                seekBar.visibility = View.GONE
            }
        }
    }

    private fun playCurrentVideo() {
        try {
            val uri = Uri.parse(videoUris[currentIndex])

            setVideoOrientation(uri)

            player.stop()
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.play()

            // 【优化点 3 补充】开始播放新视频时，默认隐藏进度条，确保切屏时不抢占主线程算力
            seekBar.visibility = View.GONE

        } catch (e: Exception) {
            Toast.makeText(this, "播放失败", Toast.LENGTH_SHORT).show()
            playNextVideo() // 如果播放失败自动尝试下一首
        }
    }

    private fun setVideoOrientation(uri: Uri) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, uri)

            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0

            requestedOrientation = if (height > width) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }

            retriever.release()
        } catch (e: Exception) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // 【优化点 2】参考文件一：修改为“取模算法”，实现无限循环滑动切换（不再在首尾卡死）
    private fun playNextVideo() {
        if (videoUris.isEmpty()) return
        currentIndex = (currentIndex + 1) % videoUris.size
        playCurrentVideo()
    }

    private fun playPreviousVideo() {
        if (videoUris.isEmpty()) return
        currentIndex = if (currentIndex > 0) currentIndex - 1 else videoUris.size - 1
        playCurrentVideo()
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (kotlin.math.abs(velocityX) > 700) {
                    if (velocityX > 0) playPreviousVideo() else playNextVideo()
                    return true
                }
                return false
            }
        })

        playerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    private fun setupSeekBar() {
        handler.post(object : Runnable {
            override fun run() {
                // 【优化点 3】参考文件一：增加 && seekBar.visibility == View.VISIBLE 判断
                // 只有在进度条可见时才允许刷新 UI。横竖屏切换时进度条是隐藏的，此时完全不占主线程算力，过渡极度顺滑
                if (player.duration > 0 && seekBar.visibility == View.VISIBLE) {
                    seekBar.max = player.duration.toInt()
                    seekBar.progress = player.currentPosition.toInt()
                }
                handler.postDelayed(this, 500)
            }
        })

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    player.seekTo(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    override fun onPause() {
        super.onPause()
        player.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        player.release()
    }
}
