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
var TNC_STARTUP_QUESTS = ['tnc:main/s1_self']
var $TNCQuestManager = Java.loadClass('com.lirxowo.whisperingquests.quest.QuestManager')
var $TNCQuestSavedData = Java.loadClass('com.lirxowo.whisperingquests.quest.QuestSavedData')

// 3.2 compatibility: retain the legacy task and its reward record. Only copy completion
// to the renamed opening task; never call claimReward or completeObjective during migration.
function tncMigrateOpening(player) {
    if (player.persistentData.getBoolean('tncOpeningMigrationV1')) { return }
    // Initializes 3.2's personal state and imports any legacy team-held solo progress.
    $TNCQuestManager.getTeamState(player)
    var personal = $TNCQuestManager.getPlayerState(player)
    if (!personal.isPresent()) { return }
    var owner = personal.get()
    var state = owner.questState()
    var oldId = $TNCLocation.parse('tnc:main/self_talk')
    var newId = $TNCLocation.parse('tnc:main/s1_self')
    if (state.completedQuests().contains(oldId) && !state.completedQuests().contains(newId)) {
        state.activeQuests().remove(newId)
        state.completedQuests().add(newId)
        state.markCompletedThisCycle(newId)
        state.setProgress(newId, 'talk_to_self', 1)
        owner.markRewardClaimed(newId, state.completionSeed(newId))
        $TNCQuestSavedData.get(player.serverLevel()).markPlayerClaimedReward(player.getUUID(), newId, state.completionSeed(newId))
        // Old completed/reward records stay available under their original ID.
        tncStartQuest(player, 'tnc:main/s1_guild')
        $TNCQuestManager.requestFullSync(player)
        console.info('[TN-C quest] opening completion migrated without awarding rewards')
    }
    if (state.activeQuests().contains(oldId)) {
        // Preserve old definition and records, but avoid two active copies of the opening.
        if ($WhisperingQuestsApi.startQuest(player, newId) || state.activeQuests().contains(newId) || state.completedQuests().contains(newId)) {
            if (state.getProgress(oldId, 'talk_to_self') > 0 && !state.completedQuests().contains(newId)) {
                state.setProgress(newId, 'talk_to_self', 1)
            }
            state.activeQuests().remove(oldId)
            $TNCQuestManager.requestFullSync(player)
        } else { return }
    }
    player.persistentData.putBoolean('tncOpeningMigrationV1', true)
}

// The retained old definition must not auto-start a second opening on story/root.
// Completed legacy tasks keep their real seed/reward records. An unused old task receives
// only a non-repeatable retirement sentinel: no completion seed, no reward, no objective.
function tncRetireLegacyOpening(player) {
    var personal = $TNCQuestManager.getPlayerState(player)
    if (!personal.isPresent()) { return }
    var state = personal.get().questState()
    var oldId = $TNCLocation.parse('tnc:main/self_talk')
    var newId = $TNCLocation.parse('tnc:main/s1_self')
    if (!state.completedQuests().contains(oldId) && (state.activeQuests().contains(newId) || state.completedQuests().contains(newId))) {
        state.activeQuests().remove(oldId)
        state.completedQuests().add(oldId)
        player.persistentData.putBoolean('tncLegacyOpeningRetiredV1', true)
        $TNCQuestManager.requestFullSync(player)
    }
}

function tncStartQuest(player, idStr) {
    try {
        var questId = $TNCLocation.parse(idStr)
        var stateOwner = $TNCQuestManager.getPlayerState(player)
        if (!stateOwner.isPresent()) { return }
        var st = stateOwner.get().questState()
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
    try { tncMigrateOpening(event.player) } catch (err) { console.error('[TN-C quest] migration deferred: ' + err); return }
    for (var i = 0; i < TNC_STARTUP_QUESTS.length; i++) {
        tncStartQuest(event.player, TNC_STARTUP_QUESTS[i])
    }
    try { tncRetireLegacyOpening(event.player) } catch (err) { console.error('[TN-C quest] legacy retirement deferred: ' + err) }
})
