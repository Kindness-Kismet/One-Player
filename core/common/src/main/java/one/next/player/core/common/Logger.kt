package one.next.player.core.common

import android.util.Log

object Logger {

    fun debug(tag: String, message: String) {
        Log.d("Logger - $tag", message)
    }

    fun info(tag: String, message: String) {
        Log.i("Logger - $tag", message)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        Log.e("Logger - $tag", message, throwable)
    }
}
