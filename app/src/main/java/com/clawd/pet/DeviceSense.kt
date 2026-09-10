package com.clawd.pet

import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import java.text.SimpleDateFormat
import java.util.*

data class NowPlaying(val title:String,val artist:String,val packageName:String,val playing:Boolean)

object DeviceSense {
    fun now() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss E", Locale.getDefault()).format(Date())

    fun nowPlaying(c:Context):NowPlaying? {
        val m = c.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return null
        return try {
            val x = m.getActiveSessions(null).firstOrNull() ?: return null
            val md=x.metadata ?: return null
            NowPlaying(
                md.getString("android.media.metadata.TITLE") ?: "未知歌曲",
                md.getString("android.media.metadata.ARTIST") ?: "未知歌手",
                x.packageName,
                x.playbackState?.state == 3
            )
        } catch(_:SecurityException) { null }
    }
}
