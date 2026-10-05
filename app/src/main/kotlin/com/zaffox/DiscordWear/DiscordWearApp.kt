package com.zaffox.discordwear

import android.app.Application
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.zaffox.discordwear.api.DiscordRepository

class DiscordWearApp : Application() {
    var repository: DiscordRepository? = null
        private set

    // One shared image loader for the whole app so memory/disk caches are shared across screens
    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(this)
            .components {
                if (android.os.Build.VERSION.SDK_INT >= 28)
                    add(ImageDecoderDecoder.Factory())
                else
                    add(GifDecoder.Factory())
            }
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        UpdateChecker.start(this)
    }

    fun initRepository(token: String) {
        repository?.disconnect()
        val repo = DiscordRepository(token, context = this)
        repo.connect()
        repository = repo
        NotificationService.start(this)
    }

    fun initMockRepository() {
        clearRepository()
        val repo = DiscordRepository("mock-token", context = this, mock = true)
        repo.connect()
        repository = repo
    }

    fun clearRepository() {
        NotificationService.stop(this)
        repository?.disconnect()
        repository = null
    }
}

val android.content.Context.discordApp: DiscordWearApp
    get() = applicationContext as DiscordWearApp
