package com.mobile.ktracea.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mobile.ktracea.KTracea
import com.mobile.ktracea.model.KTraceaConfig
import com.mobile.ktracea.ui.KTraceaScreen

class KTraceaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KTraceaScreen(
                onClose = { finish() }
            )
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, KTraceaActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
}

object KTraceaAndroid {
    fun init(context: Context, config: KTraceaConfig = KTraceaConfig()) {
        KTracea.configure(config)
    }

    fun openInspector(context: Context) {
        KTraceaActivity.start(context)
    }
}
