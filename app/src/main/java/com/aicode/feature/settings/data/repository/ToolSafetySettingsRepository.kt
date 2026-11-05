package com.aicode.feature.settings.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.aicode.core.datastore.preferencesCorruptionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.toolSafetyDataStore by preferencesDataStore(
    name = "tool_safety_prefs",
    corruptionHandler = preferencesCorruptionHandler
)

/**
 * 持久化工具授权相关的两个开关。
 *
 * 1. 「禁用安全拦截」（默认关闭）
 *    开启后，AUTO（自动）模式下连灾难性 `rm`（删除根目录、系统关键目录、工作区根目录等）
 *    也不再拦截，命令一律放行，`Shizuku` 工具也不再逐次弹窗。仅作用于 AUTO 模式：
 *    BUILD / PLAN 模式的安全拦截不受影响，Shizuku 在 BUILD 下的「不可记忆」也不受影响。
 *
 * 2. 「工作区内自动放行」（默认开启）
 *    开启后，BUILD 模式下**完全落在当前会话工作区内部**的文件读写与命令执行不再逐次弹窗：
 *    命令的工作目录、路径参数、重定向目标、`rm` 目标都必须在工作区内，且不含
 *    `$()`/反引号/子 shell 等无法静态判定的构造。任何一处越出工作区即回到原来的逐次询问。
 *    这样既不用切 AUTO（避免忘记切回来导致全权放行），又不用为每次 `npm install` 点允许。
 */
@Singleton
class ToolSafetySettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private companion object {
        val DISABLED_KEY = booleanPreferencesKey("disable_safety_interception")
        val WORKSPACE_AUTO_APPROVE_KEY = booleanPreferencesKey("auto_approve_in_workspace")
    }

    /** 当前开关流；未设置时回退到 false（默认保留安全拦截）。 */
    val disableSafetyInterceptionFlow: Flow<Boolean> =
        context.toolSafetyDataStore.data.map { it[DISABLED_KEY] ?: false }

    suspend fun setDisableSafetyInterception(disabled: Boolean) {
        context.toolSafetyDataStore.edit { it[DISABLED_KEY] = disabled }
    }

    /** 读取一次当前值（权限评估时用）。 */
    suspend fun isSafetyInterceptionDisabled(): Boolean = disableSafetyInterceptionFlow.first()

    /** 工作区内自动放行开关；未设置时回退到 true（默认开启，减少无谓弹窗）。 */
    val autoApproveInWorkspaceFlow: Flow<Boolean> =
        context.toolSafetyDataStore.data.map { it[WORKSPACE_AUTO_APPROVE_KEY] ?: true }

    suspend fun setAutoApproveInWorkspace(enabled: Boolean) {
        context.toolSafetyDataStore.edit { it[WORKSPACE_AUTO_APPROVE_KEY] = enabled }
    }

    /** 读取一次当前值（权限评估时用）。 */
    suspend fun isAutoApproveInWorkspace(): Boolean = autoApproveInWorkspaceFlow.first()
}