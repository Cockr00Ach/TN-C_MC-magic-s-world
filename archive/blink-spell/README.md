# 归档：闪现法术（第一个原创法术）

这是我们**跑通的第一条完整原创法术链路**，2026-09-14 从整合包工作区归档出来。

## 为什么留着

不是因为这个法术本身重要（魔法系统正在重新设计），而是因为它是**「接入 SpellEngine + spellanvil 的完整范例」**。

以后做新法术，照这套路复制即可。

## 文件清单与作用

| 文件 | 放到整合包的哪 | 作用 |
|---|---|---|
| `kubejs/data/tnc/spells/blink_*.json` | `kubejs/data/tnc/spells/` | 法术本体（纯数据）|
| `kubejs/data/spellanvil/tags/items/scroll.json` | 同路径 | **关键**：把我们的卷轴加进 spellanvil 的物品标签，否则注册台不认 |
| `kubejs/startup_scripts/item/tnc_blink_scroll.js` | 同路径 | 注册三个卷轴物品 |
| `kubejs/server_scripts/spell/tnc_blink_bind.js` | 同路径 | `SpellAnvilEvents` 绑定卷轴 → 法术 |
| `kubejs/client_scripts/tnc_blink_tooltip.js` | 同路径 | 卷轴提示文字 |

## 完整链路（照这个复制）

```
1. data/tnc/spells/<法术名>.json            ← 写法术本体
2. startup_scripts/.../xxx_scroll.js         ← 注册卷轴物品
3. data/spellanvil/tags/items/scroll.json    ← ★ 加入标签（最容易漏）
4. server_scripts/spell/xxx_bind.js          ← 绑定卷轴→法术
5. client_scripts/xxx_tooltip.js             ← tooltip（可选）
6. 重启游戏 → 法术注册台 → 法术书 → 施放
```

## 法术参数（当时的值）

| 法术 | 距离 | tier | 冷却 | 消耗 |
|---|---|---|---|---|
| `tnc:blink_1` | 8 格 | 1 | 6s | 0.2 |
| `tnc:blink_2` | 16 格 | 2 | 9s | 0.3 |
| `tnc:blink_3` | 24 格 | 3 | 12s | 0.4 |

## 还原方法

把上表的文件按 `kubejs/` 下的相对路径复制回整合包，然后 `sync.cmd push` + 重启游戏。

## 已知遗留

- **没有法术图标** —— SpellEngine 会在 `assets/tnc/textures/spell/blink_*.png` 找图标，当时没做，日志里有 FileNotFoundException
