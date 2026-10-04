package dev.yomu.spike

import android.content.Context
import android.os.Build
import android.os.SystemClock
import org.json.JSONObject
import java.io.File

object Evidence {
    @Synchronized fun write(context: Context, test: String, fields: Map<String, Any?>) {
        val row = JSONObject(fields).put("test", test).put("uptime_ms", SystemClock.elapsedRealtime())
            .put("model", Build.MODEL).put("sdk", Build.VERSION.SDK_INT).put("pid", android.os.Process.myPid())
        File(context.filesDir, "evidence.jsonl").appendText(row.toString() + "\n")
        android.util.Log.i("YomuSpike", row.toString())
    }
}
