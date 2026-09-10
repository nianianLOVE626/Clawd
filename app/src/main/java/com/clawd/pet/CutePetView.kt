package com.clawd.pet

import android.content.Context
import android.graphics.*
import android.view.View

class CutePetView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    var mood: String = "neutral"; set(v){field=v;invalidate()}
    var action: String = "idle"; set(v){field=v;invalidate()}

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w=width.toFloat(); val h=height.toFloat(); val cx=w/2f
        p.color=Color.argb(35,210,125,157); c.drawOval(cx-55,h-36,cx+55,h-12,p)
        // body
        p.color=Color.rgb(235,151,178); c.drawOval(cx-48,h*.28f,cx+48,h*.78f,p)
        // claws
        p.color=Color.rgb(224,133,165)
        c.drawOval(cx-82,h*.42f,cx-35,h*.59f,p); c.drawOval(cx+35,h*.42f,cx+82,h*.59f,p)
        // face
        p.color=Color.WHITE; c.drawCircle(cx-20,h*.47f,12f,p); c.drawCircle(cx+20,h*.47f,12f,p)
        p.color=Color.rgb(91,65,78); c.drawCircle(cx-20,h*.47f,5f,p); c.drawCircle(cx+20,h*.47f,5f,p)
        p.style=Paint.Style.STROKE; p.strokeWidth=4f
        val happy=mood.contains("happy")||mood.contains("开心")||action.contains("happy")
        if(happy) c.drawArc(cx-18,h*.53f,cx+18,h*.67f,15f,150f,false,p) else c.drawLine(cx-9,h*.60f,cx+9,h*.60f,p)
        p.style=Paint.Style.FILL
        // antennae
        p.color=Color.rgb(210,125,157); p.strokeWidth=4f; p.style=Paint.Style.STROKE
        c.drawLine(cx-20,h*.32f,cx-34,h*.17f,p); c.drawLine(cx+20,h*.32f,cx+34,h*.17f,p)
        p.style=Paint.Style.FILL; c.drawCircle(cx-34,h*.17f,5f,p); c.drawCircle(cx+34,h*.17f,5f,p)
        if(action.contains("sleep")||mood.contains("sleep")||mood.contains("困")){
            p.color=Color.rgb(91,65,78); p.textSize=22f; c.drawText("z",cx+46,h*.23f,p); c.drawText("z",cx+62,h*.13f,p)
        }
    }
}
