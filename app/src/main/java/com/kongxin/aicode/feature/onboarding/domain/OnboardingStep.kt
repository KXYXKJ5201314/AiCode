package com.kongxin.aicode.feature.onboarding.domain

import androidx.annotation.StringRes
import com.kongxin.aicode.R

/**
 * 首次启动全链路引导的完整模拟演示流程：
 *
 * 1. [OPEN_SIDEBAR]: 主页聚焦左上角汉堡按钮，提示打开侧边栏。
 * 2. [ENTER_SETTINGS]: 侧边栏展开状态，聚焦底部「设置」项，提示进入系统设置。
 * 3. [CONFIG_PROVIDER]: 设置页聚焦「AI 提供商」管理行，提示进入提供商列表。
 * 4. [PROVIDER_ADD]: 提供商列表聚焦顶栏「+」添加按钮，提示添加服务商。
 * 5. [PROVIDER_CONFIG_INFO]: 提供商编辑页聚焦基本信息分组（名称 / API Key / Base URL），
 *    演示自定义供应商的接入方式——重点填接口地址。
 * 6. [PROVIDER_FETCH_MODELS]: 提供商模型标签页聚焦「拉取模型」按钮。
 * 7. [SIMULATE_FETCH_DIALOG]: 蒙版上模拟拉取模型弹窗，用演示数据展示模型并引导添加。
 *    此步不发起网络请求，也不会写入供应商配置。
 * 8. [OPEN_MODEL_PICKER]: 回到主页，聚焦模型选择按钮，提示打开面板。
 * 9. [SIMULATE_CHOOSE_MODEL]: 蒙版上模拟主页模型选择面板，演示选择模型。
 * 10. [OPEN_WORKSPACE_PICKER]: 聚焦顶栏工作区按钮，提示打开工作区面板。
 * 11. [SIMULATE_CHOOSE_WORKSPACE]: 蒙版上模拟工作区面板，用演示目录展示如何指定 AI 的操作目录。
 *     此步只在蒙版上演示，不会真正切换工作区、也不会写入任何工作区记录。
 * 12. [SEND_MESSAGE]: 主页聚焦底部输入框，提示输入内容发送第一条消息开启对话。
 */
enum class OnboardingStep(
    val route: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descRes: Int
) {
    OPEN_SIDEBAR(
        route = "chat",
        titleRes = R.string.onboarding_open_sidebar_title,
        descRes = R.string.onboarding_open_sidebar_desc
    ),
    ENTER_SETTINGS(
        route = "chat",
        titleRes = R.string.onboarding_enter_settings_title,
        descRes = R.string.onboarding_enter_settings_desc
    ),
    CONFIG_PROVIDER(
        route = "settings",
        titleRes = R.string.onboarding_config_provider_title,
        descRes = R.string.onboarding_config_provider_desc
    ),
    PROVIDER_ADD(
        route = "settings",
        titleRes = R.string.onboarding_provider_add_title,
        descRes = R.string.onboarding_provider_add_desc
    ),
    PROVIDER_CONFIG_INFO(
        route = "settings",
        titleRes = R.string.onboarding_provider_config_info_title,
        descRes = R.string.onboarding_provider_config_info_desc
    ),
    PROVIDER_FETCH_MODELS(
        route = "settings",
        titleRes = R.string.onboarding_provider_fetch_models_title,
        descRes = R.string.onboarding_provider_fetch_models_desc
    ),
    SIMULATE_FETCH_DIALOG(
        route = "settings",
        titleRes = R.string.onboarding_provider_select_fetched_model_title,
        descRes = R.string.onboarding_provider_select_fetched_model_desc
    ),
    OPEN_MODEL_PICKER(
        route = "chat",
        titleRes = R.string.onboarding_open_model_picker_title,
        descRes = R.string.onboarding_open_model_picker_desc
    ),
    SIMULATE_CHOOSE_MODEL(
        route = "chat",
        titleRes = R.string.onboarding_choose_model_in_sheet_title,
        descRes = R.string.onboarding_choose_model_in_sheet_desc
    ),
    OPEN_WORKSPACE_PICKER(
        route = "chat",
        titleRes = R.string.onboarding_open_workspace_picker_title,
        descRes = R.string.onboarding_open_workspace_picker_desc
    ),
    SIMULATE_CHOOSE_WORKSPACE(
        route = "chat",
        titleRes = R.string.onboarding_choose_workspace_in_sheet_title,
        descRes = R.string.onboarding_choose_workspace_in_sheet_desc
    ),
    SEND_MESSAGE(
        route = "chat",
        titleRes = R.string.onboarding_send_message_title,
        descRes = R.string.onboarding_send_message_desc
    );

    /** 当前步的序号（1-based）。 */
    val stepIndex: Int get() = ordinal + 1

    /** 是否是最后一步。 */
    val isLastStep: Boolean get() = this == entries.last()

    companion object {
        val totalSteps: Int get() = entries.size
        val all: List<OnboardingStep> = entries
    }
}
