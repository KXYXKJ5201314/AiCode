package com.aicode.feature.settings.presentation.component

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import com.aicode.R
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import compose.icons.FeatherIcons
import compose.icons.feathericons.Folder

/**
 * 启动时自动检查并申请「存储空间」权限的弹窗。
 *
 * ### 为什么需要它
 *
 * 本项目 targetSdk = 28，外部工作区、容器镜像、日志导出等功能都依赖
 * READ/WRITE_EXTERNAL_STORAGE 直读 `/storage/emulated/0`。权限未授予时这些功能会静默失败
 * （例如添加外部目录时写入探测失败），而用户通常不知道要去「设置 → 软件权限」里手动开启。
 *
 * 因此在进入主页时主动弹一次，把「为什么需要」和「怎么给」说清楚，而不是等用户踩坑。
 *
 * ### 何时不弹
 *
 * - 权限已授予；
 * - 用户选过「不再提示」（持久化在 SharedPreferences，可随时在设置页手动授权）；
 * - Android 10+ 且 targetSdk 28 仍走传统存储，权限依然有效，故不区分版本。
 *
 * ### 永久拒绝的处理
 *
 * 系统不再弹授权框时（`shouldShowRequestPermissionRationale` 为 false 且未授予），
 * 仅靠 launcher 无法解决，必须引导到系统设置页手动开启。
 */
@Composable
internal fun StoragePermissionPrompt() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(checkStoragePermission(context)) }
    var showDialog by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }

    // 从系统设置页返回后重新检查，已授权则自动关闭弹窗。
    LifecycleResumeEffect(Unit) {
        granted = checkStoragePermission(context)
        if (granted) showDialog = false
        onPauseOrDispose { }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        granted = checkStoragePermission(context)
        if (granted) {
            showDialog = false
        } else {
            // 系统不再弹框（永久拒绝）→ 提示去设置页手动开启。
            permanentlyDenied = !shouldShowStorageRationale(context)
        }
    }

    // 首帧判断：未授权且用户没勾过「不再提示」才弹。
    LaunchedEffect(Unit) {
        if (!checkStoragePermission(context) && !isPromptDismissed(context)) {
            showDialog = true
        }
    }

    if (!showDialog) return

    AlertDialog(
        onDismissRequest = { showDialog = false },
        icon = {
            androidx.compose.material3.Icon(
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
                    text = stringResource(R.string.storage_permission_prompt_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 需要授权的具体用途，让用户知道这不是无理索取。
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
                if (permanentlyDenied) {
                    Text(
                        text = stringResource(R.string.settings_permission_storage_denied_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (permanentlyDenied) {
                    openAppSettings(context)
                    showDialog = false
                } else {
                    launcher.launch(
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    )
                }
            }) {
                Text(
                    stringResource(
                        if (permanentlyDenied) R.string.storage_permission_open_settings
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

/** 是否已授予存储权限（读写任一即可，与设置页判定一致）。 */
internal fun checkStoragePermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
        PackageManager.PERMISSION_GRANTED

/** 系统是否还会展示授权说明弹窗；false 且未授权即为永久拒绝。 */
private fun shouldShowStorageRationale(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
    val activity = context as? android.app.Activity ?: return false
    return androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
}

private fun prefs(context: Context): SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

/** 用户是否选择过「以后再说」。 */
internal fun isPromptDismissed(context: Context): Boolean =
    prefs(context).getBoolean(KEY_DISMISSED, false)

/** 记录「以后再说」，之后不再自动弹（设置页仍可手动授权）。 */
internal fun setPromptDismissed(context: Context) {
    prefs(context).edit().putBoolean(KEY_DISMISSED, true).apply()
}

/** 清除「以后再说」标记，下次进入主页会重新提示（用于用户主动重置）。 */
internal fun resetPromptDismissed(context: Context) {
    prefs(context).edit().remove(KEY_DISMISSED).apply()
}

/** 跳转到本应用的系统设置页，供用户手动开启权限。 */
private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}