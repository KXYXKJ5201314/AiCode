package com.kongxin.aicode.feature.settings.presentation.component

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.kongxin.aicode.R
import com.kongxin.aicode.core.theme.Radius
import com.kongxin.aicode.core.theme.Spacing
import compose.icons.FeatherIcons
import compose.icons.feathericons.Folder

/**
 * 启动时自动检查并引导开启「所有文件访问权限」。
 *
 * ### 为什么必须是 MANAGE_EXTERNAL_STORAGE
 *
 * 本项目 targetSdk = 28，原本靠 READ/WRITE_EXTERNAL_STORAGE 直读 `/storage/emulated/0`。
 * 但从 **Android 11（API 30）** 起，系统对存储根目录的访问做了硬限制：即使 targetSdk < 30
 * 走兼容模式，`/sdcard` 全盘读写依然被拒，且这两个权限在 11+ 上**不会弹出授权框**，
 * 授权后也没有实际能力（表现为「设置页里永远显示未授权、点了没反应」）。
 *
 * Android 11+ 读全盘的唯一途径是 `MANAGE_EXTERNAL_STORAGE`，它**无法用运行时弹窗授予**，
 * 必须由用户在系统设置「所有文件访问」里手动开启。因此本弹窗只负责：
 * 1. 说清楚为什么需要；
 * 2. 一键跳到对应的系统设置页；
 * 3. 用户返回后自动复查，已开启则消失。
 *
 * ### 版本分支
 *
 * - Android 11+：检测 `Environment.isExternalStorageManager()`，引导跳设置页。
 * - Android 6–10：MANAGE_EXTERNAL_STORAGE 不存在，退回运行时权限申请流程。
 * - Android 6 以下：安装即授，无需处理。
 */
@Composable
internal fun StoragePermissionPrompt() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(checkStorageAccess(context)) }
    var showDialog by remember { mutableStateOf(false) }

    // 从系统设置页返回后复查，已开启则自动关闭弹窗。
    LifecycleResumeEffect(Unit) {
        granted = checkStorageAccess(context)
        if (granted) showDialog = false
        onPauseOrDispose { }
    }

    // Android 6–10 的运行时权限申请（11+ 不适用，见 checkStorageAccess）。
    val legacyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        granted = checkStorageAccess(context)
        if (granted) showDialog = false
    }

    // 首帧判断：未授权且用户没选过「以后再说」才弹。
    LaunchedEffect(Unit) {
        if (!checkStorageAccess(context) && !isPromptDismissed(context)) {
            showDialog = true
        }
    }

    if (!showDialog) return

    val needsSettingsPage = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    AlertDialog(
        onDismissRequest = { showDialog = false },
        icon = {
            Icon(
                FeatherIcons.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.storage_permission_prompt_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = stringResource(
                        if (needsSettingsPage) R.string.storage_permission_prompt_desc_all_files
                        else R.string.storage_permission_prompt_desc
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 具体用途，让用户知道这不是无理索取。
                Surface(
                    shape = RoundedCornerShape(Radius.mdLarge),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PermissionUsageRow(stringResource(R.string.storage_permission_use_workspace))
                        PermissionUsageRow(stringResource(R.string.storage_permission_use_container))
                        PermissionUsageRow(stringResource(R.string.storage_permission_use_export))
                    }
                }
                if (needsSettingsPage) {
                    Text(
                        text = stringResource(R.string.storage_permission_all_files_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (needsSettingsPage) {
                    // 设置页返回时由 LifecycleResumeEffect 复查；这里不立即关闭，
                    // 否则用户没真正开启就看不到复查结果。
                    openAllFilesAccessSettings(context)
                } else {
                    legacyLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    )
                }
            }) {
                Text(
                    stringResource(
                        if (needsSettingsPage) R.string.storage_permission_open_settings
                        else R.string.storage_permission_grant
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = {
                setPromptDismissed(context)
                showDialog = false
            }) {
                Text(stringResource(R.string.storage_permission_later))
            }
        }
    )
}

/** 用途说明的一行：小圆点 + 文本。 */
@Composable
private fun PermissionUsageRow(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "·",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private const val PREFS_NAME = "storage_permission_prompt"
private const val KEY_DISMISSED = "dismissed"

/**
 * 是否已具备存储访问能力。
 *
 * - Android 11+：只看 `Environment.isExternalStorageManager()`。此版本下
 *   READ/WRITE_EXTERNAL_STORAGE 始终报「已授予」却无实际能力，不能用它判断。
 * - Android 6–10：沿用运行时权限判定（读写任一即可）。
 * - Android 6 以下：安装即授。
 */
internal fun checkStorageAccess(context: Context): Boolean = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    else -> true
}

private fun prefs(context: Context): SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

/** 用户是否选择过「以后再说」。 */
internal fun isPromptDismissed(context: Context): Boolean =
    prefs(context).getBoolean(KEY_DISMISSED, false)

/** 记录「以后再说」，之后不再自动弹（设置页仍可手动开启）。 */
internal fun setPromptDismissed(context: Context) {
    prefs(context).edit().putBoolean(KEY_DISMISSED, true).apply()
}

/** 清除「以后再说」标记，下次进入主页会重新提示。 */
internal fun resetPromptDismissed(context: Context) {
    prefs(context).edit().remove(KEY_DISMISSED).apply()
}

/**
 * 跳到系统「所有文件访问」设置页。
 *
 * 优先用带包名的 Intent（部分 ROM 不认全局页）；失败时退回全局页，再失败退回应用详情页。
 */
private fun openAllFilesAccessSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val direct = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(direct) }.isSuccess) return
        val global = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(global) }.isSuccess) return
    }
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}