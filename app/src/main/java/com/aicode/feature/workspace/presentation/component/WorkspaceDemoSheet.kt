package com.aicode.feature.workspace.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aicode.R
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.core.ui.AdaptiveModalBottomSheet
import compose.icons.FeatherIcons
import compose.icons.feathericons.Folder
import compose.icons.feathericons.HardDrive

/**
 * 新手引导「工作区」这一步的纯演示面板。
 *
 * 第 7 步（拉取模型）改成纯演示后，第 9 步靠注入演示提供商才没卡住；工作区这一步同理——
 * 新手此时通常还没有添加过任何外部目录，真实面板里只会显示一句「暂无工作区」，
 * 既讲不清工作区是干什么的，也没有任何可点行可以推进引导。
 *
 * 因此这里用两条固定示例目录撑起面板结构，让新手看到「面板长什么样、选中后会怎样」。
 * 本组件**不持有任何 ViewModel、不读数据库、不写任何记录**，点击示例行只回调
 * [onDemoSelected] 交给引导流程推进，绝不会真的切换工作区。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceDemoSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onDemoSelected: () -> Unit
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState()

    // 两条演示目录：一条模拟用户自己选的设备目录，一条模拟 App 私有目录。
    // 刻意都带路径展示，就是为了让新手看懂「工作区 = 一个真实目录」这件事。
    val demoRows = listOf(
        Triple(
            stringResource(R.string.onboarding_choose_workspace_demo_myapp),
            "/storage/emulated/0/AiCode_开发源码/my-app",
            false
        ),
        Triple(
            stringResource(R.string.onboarding_choose_workspace_demo_website),
            "/storage/emulated/0/AiCode_开发源码/website",
            true
        )
    )

    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.common_workspace),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                // 真实面板本地模式下这里有「添加外部工作区」，演示态只做视觉占位，点击不触发系统目录选择器。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        FeatherIcons.HardDrive,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        text = stringResource(R.string.workspace_add_external),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = stringResource(R.string.onboarding_choose_workspace_in_sheet_demo_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.sm)
            )

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                demoRows.forEachIndexed { index, (name, path, external) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.sm))
                            // 点哪一行都只是推进引导，绝不切换工作区。
                            .clickable(onClick = onDemoSelected)
                            .padding(horizontal = Spacing.md, vertical = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (external) FeatherIcons.HardDrive else FeatherIcons.Folder,
                            contentDescription = null,
                            tint = if (index == 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(Spacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (index == 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = path,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
