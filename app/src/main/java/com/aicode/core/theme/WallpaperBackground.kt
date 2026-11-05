package com.aicode.core.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme

/**
 * 是否有全局背景图（壁纸）正在显示。
 *
 * 背景图绘制在所有页面内容**下方**，因此页面级容器的底色必须透出去，否则整块不透明
 * 底色会把壁纸完全盖住。各页面通过 [appPageBackground] 取底色即可自动适配，
 * 不需要各自去读设置仓库。
 */
val LocalWallpaperActive = staticCompositionLocalOf { false }

/**
 * 页面级容器的底色。
 *
 * - 没有背景图时返回主题 background，与改造前完全一致；
 * - 有背景图时返回 [Color.Transparent]，让下方的壁纸与它的遮罩透出来。
 *
 * 文字对比度由壁纸之上的主题色遮罩（见 `BackgroundSettingsRepository.scrimAlphaFor`）
 * 保证，不依赖页面底色，所以透明化不会让正文变得看不清。
 */
@Composable
fun appPageBackground(): Color =
    if (LocalWallpaperActive.current) Color.Transparent
    else MaterialTheme.colorScheme.background
