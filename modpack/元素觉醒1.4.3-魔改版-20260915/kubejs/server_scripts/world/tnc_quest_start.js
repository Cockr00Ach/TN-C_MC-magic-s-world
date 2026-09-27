// ---------------------------------------------------------------------------
//  TN-C 任务线 · 开局接取第一个任务（「第二杯酒」—— 和 Self 对话）
//
//  为什么需要这个脚本：
//    WhisperingQuests 的任务书界面**只显示三种任务**
//      · 已接取、进行中的（activeEntries）
//      · 有奖励可领的（claimableRewardEntries）
//      · 刷新池里的（refreshedEntries）
//    **不是**"定义里有的都显示"。我们的任务 refresh_pool 是 "triggered"，
//    没人接取就永远不出现 —— 这就是"任务书里看不到我们任务"的真正原因
//    （服务端解析 ✓、下发 ✓，三层诊断已确认，问题只在"没被接取"）。
//
//  做法：玩家进世界时调框架 API 把它接上，之后它就在任务书里了。
//  幂等：已接取 / 已完成则跳过，不会每次登录都重复接。
//
//  API 签名（javap 确认过）：
//    WhisperingQuestsApi.startQuest(ServerPlayer, ResourceLocation) -> boolean
//    WhisperingQuestsApi.getTeamState(ServerPlayer) -> TeamQuestState
//    TeamQuestState.activeQuests() / completedQuests() -> Set<ResourceLocation>
// ---------------------------------------------------------------------------

const $WhisperingQuestsApi = Java.loadClass('com.lirxowo.whisperingquests.api.WhisperingQuestsApi')
const $ResourceLocation = Java.loadClass('net.minecraft.resources.ResourceLocation')

// ★ 想加新任务：往这个数组里加 id 即可（顺序即接取顺序）
const TNC_STARTUP_QUESTS = [
    'tnc:main/self_talk'
]

PlayerEvents.loggedIn(event => {
    const player = event.player
    TNC_STARTUP_QUESTS.forEach(idStr => {
        try {
            // ⚠️ 变量名不能叫 id —— KubeJS 脚本作用域里已有 var id，
            //    用 const id 会抛 "redeclaration of var id"
            //    （真踩过：任务因此一直没被接取，界面上就永远看不到）
            const questId = $ResourceLocation.parse(idStr)
            const questState = $WhisperingQuestsApi.getTeamState(player)

            // 幂等：已接取 / 已完成 就不重复接
            if (questState.activeQuests().contains(questId)) return
            if (questState.completedQuests().contains(questId)) return

            const ok = $WhisperingQuestsApi.startQuest(player, questId)
            if (ok) {
                console.info(`[TN-C quest] 已接取 ${idStr}（任务书里现在应该能看到它了）`)
            } else {
                console.warn(`[TN-C quest] startQuest 返回 false：${idStr} —— 可能未解锁或条件不满足`)
            }
        } catch (err) {
            // 不能让一条任务把整个脚本搞崩（KubeJS 里未捕获异常会中断后续脚本）
            console.error(`[TN-C quest] 接取 ${idStr} 失败：${err}`)
        }
    })
})
