package com.ledgerdiary.app

import android.app.Application
import com.ledgerdiary.app.data.AppDatabase
import com.ledgerdiary.app.util.PhotoStore

/**
 * 全局 Application。
 *
 * 目前只做两件事：提前把数据库和照片目录准备好，避免首屏第一次点击时卡一下。
 * 以后要加「启动时检查备份」「全局崩溃日志」这类需求，入口都在这里。
 */
class LedgerDiaryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppDatabase.get(this)
        PhotoStore.photosDir(this)
    }
}
