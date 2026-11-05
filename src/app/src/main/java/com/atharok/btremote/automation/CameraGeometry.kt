// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import kotlin.math.*

data class CameraPoint(val x: Float, val y: Float)

/** Clockwise screen corners in normalized, already-oriented camera coordinates. */
data class CameraQuad(val points: List<CameraPoint>) {
    fun sufficientlyDetailed(width:Int,height:Int,landscape:Boolean):Boolean {
        fun edge(a:Int,b:Int):Float {
            val x=(points[a].x-points[b].x)*width;val y=(points[a].y-points[b].y)*height
            return sqrt(x*x+y*y)
        }
        return min(edge(0,1),edge(2,3)) >= (if(landscape)480 else 240) &&
            min(edge(1,2),edge(3,0)) >= (if(landscape)240 else 480)
    }
    fun validate(): CameraQuad {
        require(points.size == 4) { "请按左上、右上、右下、左下选择四角" }
        require(points.all { it.x.isFinite() && it.y.isFinite() && it.x in 0f..1f && it.y in 0f..1f }) { "屏幕角点超出画面" }
        val crosses = points.indices.map { i ->
            val a=points[i];val b=points[(i+1)%4];val c=points[(i+2)%4]
            (b.x-a.x)*(c.y-b.y)-(b.y-a.y)*(c.x-b.x)
        }
        require(crosses.all { it > .002f }) { "四角需顺时针形成凸四边形，请重新选择" }
        val area=abs(points.indices.sumOf { i -> val a=points[i];val b=points[(i+1)%4];(a.x*b.y-a.y*b.x).toDouble() })/2
        require(area >= .08) { "受控屏幕太小，请靠近并重新框选" }
        return this
    }
}

/** Measured using the exact same fixed-step, paced HID motion used for later positioning. */
data class CameraPointerMap(val origin: CameraPoint, val xEnd: CameraPoint, val yEnd: CameraPoint) {
    private val xx=(xEnd.x-origin.x)/PROBE_UNITS
    private val xy=(xEnd.y-origin.y)/PROBE_UNITS
    private val yx=(yEnd.x-origin.x)/PROBE_UNITS
    private val yy=(yEnd.y-origin.y)/PROBE_UNITS
    private val determinant=xx*yy-xy*yx
    fun validate(): CameraPointerMap {
        require(listOf(origin,xEnd,yEnd).all { it.x.isFinite() && it.y.isFinite() && it.x in -.03f..1f && it.y in -.03f..1f }) { "请点击画面内鼠标箭头尖端" }
        require(origin.x < .1f && origin.y < .1f) { "鼠标尚未回到左上角，请重新校准" }
        require(xEnd.x-origin.x in .025f.. .85f && yEnd.y-origin.y in .025f.. .85f) { "鼠标位移过小或已触边，请检查框选和蓝牙连接" }
        require(abs(xEnd.y-origin.y)<.04 && abs(yEnd.x-origin.x)<.04 && determinant>1e-7) { "鼠标方向或屏幕四角不匹配，请重新校准" }
        return this
    }
    fun units(point: CameraPoint): Pair<Int,Int> {
        require(point.x in 0f..1f && point.y in 0f..1f) { "坐标超出受控屏幕" }
        validate()
        val x=point.x-origin.x;val y=point.y-origin.y
        val dx=((x*yy-y*yx)/determinant).roundToInt();val dy=((y*xx-x*xy)/determinant).roundToInt()
        require(abs(dx)<=2000 && abs(dy)<=2000) { "校准位移异常，请重新校准" }
        return dx to dy
    }
    companion object {
        const val PROBE_UNITS=160f
        // Keep every measured arrow tip away from rounded screen corners.
        const val BASE_UNITS=160
        fun fromInterior(base:CameraPoint,right:CameraPoint,down:CameraPoint):CameraPointerMap {
            require(listOf(base,right,down).all { it.x.isFinite() && it.y.isFinite() && it.x in 0f..1f && it.y in 0f..1f }) { "请点击画面内鼠标箭头尖端" }
            val dx=CameraPoint(right.x-base.x,right.y-base.y)
            val dy=CameraPoint(down.x-base.x,down.y-base.y)
            val ratio=BASE_UNITS/PROBE_UNITS
            val origin=CameraPoint(base.x-ratio*(dx.x+dy.x),base.y-ratio*(dx.y+dy.y))
            return CameraPointerMap(origin,CameraPoint(origin.x+dx.x,origin.y+dx.y),CameraPoint(origin.x+dy.x,origin.y+dy.y)).validate()
        }
        val CHECK_POINTS=listOf(CameraPoint(.5f,.5f),CameraPoint(.8f,.8f))
        fun accurate(actual: CameraPoint, expected: CameraPoint) = abs(actual.x-expected.x)<=.025f && abs(actual.y-expected.y)<=.025f
    }
}

/** Camera actions deliberately stay on HID even if an old companion is still online. */
interface CameraPointer {
    fun enabled(): Boolean
    suspend fun position(x: Int, y: Int)
}

/** Ignore small sensor/brightness noise when guarding against repeatedly ineffective camera actions. */
object CameraSceneFingerprint {
    fun encode(luma:List<Int>):String {
        require(luma.size==64 && luma.all { it in 0..255 })
        return "camera:"+luma.joinToString("") { (it/16).coerceIn(0,15).toString(16) }
    }
    fun similarOrSame(a:String,b:String):Boolean {
        if(a==b)return true
        if(!a.startsWith("camera:") || !b.startsWith("camera:") || a.length!=71 || b.length!=71)return false
        var difference=0;var changed=0
        for(i in 7 until 71) {
            val x=a[i].digitToIntOrNull(16)?:return false;val y=b[i].digitToIntOrNull(16)?:return false
            val d=abs(x-y);difference+=d;if(d>1)changed++
        }
        return difference<=32 && changed<=4
    }
}
