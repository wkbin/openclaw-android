package com.openclaw.android.util

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 记录 App 是否在前台。由 MainActivity 的 onResume/onPause 更新；
 * 供后台事件推送判断是否值得发通知（前台且正看着就走 App 内渲染，不发）。
 */
@Singleton
class AppForegroundTracker @Inject constructor() {
    @Volatile
    var isForeground: Boolean = false
}
