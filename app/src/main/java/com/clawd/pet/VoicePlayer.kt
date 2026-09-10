package com.clawd.pet

import android.media.MediaPlayer
import java.io.File

object VoicePlayer {
    @Volatile private var player: MediaPlayer? = null

    fun play(file: File, onDone: (() -> Unit)? = null) {
        stop()
        runCatching {
            val p=MediaPlayer()
            player=p
            p.setDataSource(file.absolutePath)
            p.setOnCompletionListener { it.release(); player=null; file.delete(); onDone?.invoke() }
            p.setOnErrorListener { mp,_,_-> mp.release(); player=null; file.delete(); onDone?.invoke(); true }
            p.prepare(); p.start()
        }.onFailure { file.delete(); onDone?.invoke() }
    }

    fun stop(){ runCatching{player?.stop()}; runCatching{player?.release()}; player=null }
}
