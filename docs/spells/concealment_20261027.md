# 通用法术「隐匿」（2026-10-27）

## 一句话

施法者隐身 **60 秒（1200 tick）**，期间**敌对生物不会把施法者选作目标**；只要施法者**造成伤害**或**受到伤害**，效果立即结束。

## 文件

| 文件 | 作用 |
| --- | --- |
| `src/main/java/com/tnc/tnc/magic/ConcealmentEffect.java` | 效果本体：每秒补一次隐身、清掉附近怪物对施法者的锁定；结束时恢复可见 |
| `src/main/java/com/tnc/tnc/magic/TNConcealmentEvents.java` | `LivingChangeTargetEvent` 拦索敌；`LivingHurtEvent` 破隐 |
| `src/main/java/com/tnc/tnc/magic/TNEffects.java` | 注册 `tnc:concealment` |
| `src/main/java/com/tnc/tnc/magic/SpellCatalog.java` | 目录条目（`Chain.INDEPENDENT`，无元素、无亲和力要求） |
| `src/main/resources/data/tnc/spells/concealment.json` | 法术定义：`SELF` + `STATUS_EFFECT 1200 tick`，冷却 90 秒 |
| `assets/tnc/lang/{zh_cn,en_us}.json` | 法术名/描述、效果名 |

## 为什么这么实现

1. **隐身**沿用 `LivingEntity#setInvisible`，与原版隐身同一条渲染路径，不额外做模型/材质。
2. **不被索敌**是原版隐身做不到的（原版只降低探测距离，贴脸仍会被锁）。所以：
   - `LivingChangeTargetEvent` 里，新目标是带隐匿的实体 → `setNewTarget(null)` + `cancel`；
   - 效果每 20 tick 扫 32 格内 `Mob`，把 `getTarget() == 施法者` 的清空。
3. **破隐**只在 `LivingHurtEvent`（真实伤害）里判断：受伤者带效果 → 移除；攻击者带效果 → 移除。治疗、给 buff、放方块都不破隐。
4. **结束清理**走 `removeAttributeModifiers`：自然到期、手动 `/effect clear`、破隐都走同一条路径，恢复可见时若还带着原版隐身则不抢着显形。

## 参数

| 参数 | 值 | 位置 |
| --- | --- | --- |
| 持续时间 | 1200 tick | `concealment.json` 的 `status_effect.duration` |
| 冷却 | 90 秒 | `concealment.json` 的 `cost.cooldown_duration` |
| 清目标间隔 | 20 tick | `ConcealmentEffect.PULSE_TICKS` |
| 清目标半径 | 32 格 | `ConcealmentEffect.SUPPRESS_RADIUS` |
| 破隐条件 | 造成/受到 >0 伤害 | `TNConcealmentEvents.onHurt` |

## 怎么拿 / 怎么测

- 目录条目是独立魔法（`Chain.INDEPENDENT`），不占七元素 NBT 数组，也不要求亲和力；
- 现有魔法石界面若只列已学法术，可先用命令学：
  ```
  /tnc magic spells learn tnc:concealment
  ```
  然后正常施法（法杖/法术书）即可。
- 验收要点：隐身 60 秒；僵尸/掠夺者贴脸也不转身；打一下怪立刻显形；被打一下立刻显形；到期自动显形。

## 已知限制

- 只处理 `Mob` 的索敌；Boss 类自定义 AI 若不走 `setTarget`，需要单独接（火山巨龟的 `AttackGoal` 走标准 `getTarget`，已覆盖）。
- 隐身不隐藏粒子/音效；施法瞬间的烟雾是故意保留的“入场提示”。
