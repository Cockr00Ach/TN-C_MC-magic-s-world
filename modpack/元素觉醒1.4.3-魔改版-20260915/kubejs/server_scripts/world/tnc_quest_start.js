// ---------------------------------------------------------------------------
//  TN-C 任务线 · 开局接取第一个任务（「第二杯酒」—— 和 Self 对话）
//
//  为什么需要它：
//    WhisperingQuests 的任务书界面**只显示三种任务**
//      · 已接取进行中的 activeEntries
//      · 有奖励可领的   claimableRewardEntries
//      · 刷新池里的     refreshedEntries
//    **不是**"定义里有的都显示"。我们的 refresh_pool 是 "triggered"，
//    没被接取就永远不出现 —— 这就是"任务书里看不到我们任务"的原因。
//    （服务端解析 ✓、下发 ✓，三层诊断 mixin 已确认。）
//
//  ⚠️ KubeJS 语法坑（连踩两次）：
//    这个脚本里**不要**写 `const` / `let` 声明局部变量 ——
//    实测会抛 `InternalError: TypeError: redeclaration of var xxx`
//    （先报 `id`，改名成 `questId` 后改口报 `questId`，说明是"文件内声明重复"）。
//    改用：接收函数参数 + 顶层 `var`（KubeJS 的脚本重载会重复求值，顶层 var 可重入）。
// ---------------------------------------------------------------------------

const $WhisperingQuestsApi = Java.loadClass('com.lirxowo.whisperingquests.api.WhisperingQuestsApi')
const $TNCLocation = Java.loadClass('net.minecraft.resources.ResourceLocation')

// ★ 想加任务：往这里加 id
// ⚠️ 2026-09-29：原来是 'tnc:main/self_talk'，那条任务已并入主线、改名成
//    'tnc:main/s1_self'（第一章第一环）。**改任务 id 时这里必须一起改** ✗ ——
//    接取一条不存在的任务只会打一行 warn，任务书看起来就是"空的"。
var TNC_STARTUP_QUESTS = ['tnc:main/s1_self']

function tncStartQuest(player, idStr) {
    try {
        var questId = $TNCLocation.parse(idStr)
        var st = $WhisperingQuestsApi.getTeamState(player)
        if (st.activeQuests().contains(questId)) { return }
        if (st.completedQuests().contains(questId)) { return }
        if ($WhisperingQuestsApi.startQuest(player, questId)) {
            console.info('[TN-C quest] 已接取 ' + idStr)
        } else {
            console.warn('[TN-C quest] startQuest 返回 false：' + idStr)
        }
    } catch (err) {
        console.error('[TN-C quest] 接取 ' + idStr + ' 失败：' + err)
    }
}

PlayerEvents.loggedIn(function (event) {
    for (var i = 0; i < TNC_STARTUP_QUESTS.length; i++) {
        tncStartQuest(event.player, TNC_STARTUP_QUESTS[i])
    }
})
