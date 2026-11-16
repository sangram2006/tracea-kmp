package com.mobile.ktracea.ios

import androidx.compose.ui.window.ComposeUIViewController
import com.mobile.ktracea.KTracea
import com.mobile.ktracea.model.KTraceaConfig
import com.mobile.ktracea.ui.KTraceaScreen
import platform.UIKit.UIViewController

object KTraceaIOS {

    fun init(config: KTraceaConfig = KTraceaConfig()) {
        KTracea.configure(config)
    }

    fun createViewController(onClose: (() -> Unit)? = null): UIViewController {
        return ComposeUIViewController {
            KTraceaScreen(onClose = onClose)
        }
    }
}
