package com.clawd.pet

import android.content.Context
import android.graphics.Color
import android.media.MediaPlayer
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import java.io.File

object VoiceBubble {
    fun render(context:Context,text:String,onRemove:()->Unit):LinearLayout{
        val box=LinearLayout(context).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(14,10,14,10)}
        val play=Button(context).apply{text="播放";setTextColor(Color.WHITE);setBackgroundColor(Color.rgb(216,143,170))}
        val label=TextView(context).apply{text="语音 · ${text.take(24)}";textSize=13f;setTextColor(Color.rgb(90,70,80));setPadding(12,0,0,0)}
        box.addView(play,LinearLayout.LayoutParams(76,50));box.addView(label,LinearLayout.LayoutParams(0,50).apply{weight=1f})
        play.setOnClickListener{
            play.isEnabled=false
            VoiceApi.synthesize(context,text).onSuccess{file->
                MediaPlayer().apply{
                    setDataSource(file.absolutePath);prepare();start()
                    setOnCompletionListener{mp->mp.release();play.isEnabled=true;file.delete()}
                    setOnErrorListener{mp,_,_->mp.release();play.isEnabled=true;file.delete();true}
                }
            }.onFailure{play.isEnabled=true}
        }
        return box
    }
}
