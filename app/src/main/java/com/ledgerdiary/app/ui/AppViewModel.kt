package com.ledgerdiary.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerdiary.app.data.AppDatabase
import com.ledgerdiary.app.data.CategoryEntity
import com.ledgerdiary.app.data.DiaryEntryEntity
import com.ledgerdiary.app.data.ProfileEntity
import com.ledgerdiary.app.data.Repository
import com.ledgerdiary.app.data.TransactionEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 整个 App 只用一个 ViewModel。
 * 理由：数据量小（个人账本，几千条），一个 VM 足够；页面之间共享同一份 StateFlow，
 * 不需要在页面间传参或做复杂的 nav graph 参数。
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    val repo: Repository = Repository.get(app)
    val db: AppDatabase = AppDatabase.get(app)

    val transactions: StateFlow<List<TransactionEntity>> = repo.transactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = repo.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val diaryEntries: StateFlow<List<DiaryEntryEntity>> = repo.diaryEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val profile: StateFlow<ProfileEntity?> = repo.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val photoCount: StateFlow<Int> = repo.photoCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 按日期分组的流水，列表和日历都用它 */
    val byDate: StateFlow<Map<String, List<TransactionEntity>>> = transactions
        .map { list -> list.groupBy { it.date } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}
