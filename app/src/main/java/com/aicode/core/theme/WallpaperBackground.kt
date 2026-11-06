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

/**
 * 侧边栏卡片（会话行、入口分组）的底色。
 *
 * 有背景图时把 `cardSurface` 降到半透明，让壁纸透出来；无背景图时保持原样。
 * 半透明而非全透明，是为了让「这是可点的卡片」这件事仍然看得出来。
 */
@Composable
fun appSidebarCardSurface(): Color =
    if (LocalWallpaperActive.current) MaterialTheme.semanticColors.cardSurface.copy(alpha = 0.55f)
    else MaterialTheme.semanticColors.cardSurface

/**
 * 侧边栏 / 设置页里的胶囊控件底色（搜索框、分段 Tab 轨道、Tab 滑块）。
 *
 * 这些控件原本用 `capsuleSurface` / `mutedSurface` 这类接近实色底，会整块盖住壁纸。
 * 有背景图时降到 0.5，壁纸能透出来，同时仍看得出这是个独立的胶囊控件。
 */
@Composable
fun appCapsuleSurface(base: Color): Color =
    if (LocalWallpaperActive.current) base.copy(alpha = 0.5f) else base
