> **维护者**：**动画系统MMM**（agent `dsh` · 会话主题「动画系统MMM」）· **最后更新** 2026-09-22
> **归属**：本文件由**动画系统MMM** 维护 ✓；其他专题请勿在此改工序 ✗。
> **上游**：动作由**用户**在 Blockbench 里做 ✓；本文件规定"做成什么格式、放到哪、怎么验" ✓。

# NPC 动作产线（Blockbench → 游戏）· 配方

> 一句话：**你在 Blockbench 里录关键帧 → 导出动画 JSON 放进 `assets/tnc/animations/entity/` → 我接线让它在该播的时候播。**

---

## 一、为什么每个 NPC 需要一份"模型文件"

| 现状 | 说明 |
|---|---|
| `tnc:self` / `cava` / `huai` | 走**原版人形模型**（Java 里烘焙的 `TnHumanoidNpcModel`）+ **64×64 经典皮肤**。**没有任何 `.geo.json`** ✗ |
| `tnc:zhuangquerang` | 有 `.geo.json`（从 TLM 女仆模型抄的，128×128）✓ |

⇒ **Blockbench 里没有模型可开、没有骨骼可录关键帧** ✗ —— 所以先用生成器把"标准人形骨头"生成出来 ✓。

### 生成器

```powershell
python tools\gen_npc_bedrock.py self cava huai
```

| 产出 | 位置 |
|---|---|
| Bedrock 模型（**ASCII 骨骼名**）| `src\main\resources\assets\tnc\geo\entity\<名字>.geo.json` |
| Blockbench 用的 128×128 皮肤 | `src\main\resources\assets\tnc\textures\entity\<名字>_bedrock.png` |

- **几何数值**：逐条抄自 `TnHumanoidNpcModel.createBodyLayer()`（头 8³、身 8×12×4、四肢 4×12×4、经典 4 像素手臂）✓
- **不动任何现有文件** ✓：只新增；原来那张 64×64 皮肤还留着（原版人形渲染器还在用）
- **经典皮肤 → 现代皮肤的转换**：每个部位 2 倍放大搬到现代位置，**左肢那两块额外做一次水平翻转** ✓
  （经典皮肤没有左肢贴图区，游戏靠镜像标志翻；现代皮肤有独立区域，图必须是已翻转的样子）

### 骨骼名（★ 锁死，改 Java 时按这个写）

```
head / hat / body / armRight / armLeft / legRight / legLeft
```

- `hat` 是 `head` 的**子骨** —— 转头时会跟着动 ✓
- 模型坐标：**脚底 y=0、头顶 y=32**（游戏里 2 格高）✓

---

## 二、Blockbench 步骤（用户在本地做）

> ⚠️ ★ **Blockbench 会翻译骨骼名**：只有 ASCII 名字（`armRight` 这种）才不会被改名，
> 中文名会被转写成 `bone` / `bone2`，导出的动画就跟模型对不上了 ✗ —— 所以**别改骨头名字**。

1. **新建**：`File → New` → **Bedrock Entity** → 名字随便（如 `self`）→ **Bone 名字保持默认 ASCII** ✓
2. **导入骨架**：`File → Import → Bedrock Geometry` → 选 `...\assets\tnc\geo\entity\self.geo.json`
   （若它问"Import as model / animation"，选 **model**）
3. **贴图**：左侧 `Textures` 面板 → 加一张 → 选 `...\textures\entity\self_bedrock.png`
   → 模型上应该**正常上色**（不是紫黑、不是错位）✓
   - 顺带看一眼**左臂/左腿的贴图对不对** ★ —— 这里是我唯一没法在本机验证的一环（见 §五）
4. **录关键帧**：
   - 切到 **Animate** 标签页（右上）
   - 左下 **Animations** 面板 → `+` 新建一个动画 → **名字用 ASCII**：`wave` / `push` / `turn_away` …
   - 在 **Timeline** 里拖时间轴到某一帧 → 转骨骼（旋转/位移）→ **自动打上关键帧**
   - 时间轴总长 = 这个动作的时长（20 帧 = 1 秒；**游戏是 20 tick/秒**）
5. **预览**：点播放键，在 Blockbench 里看 ✓（这就是你要的可视化）
6. **导出**：`Animation → Export Animations` → 得到动画 JSON
7. **交付**：把导出的 JSON 给我（或直接放进下面的路径）

---

## 三、交付格式与命名（★ 照这个来，我不返工）

| 项 | 规定 |
|---|---|
| 放哪 | `src\main\resources\assets\tnc\animations\entity\<NPC名>.animation.json` |
| 一个文件能放几个动作 | **可以放多个** ✓（同一个 `animations` 块里并列即可）—— 建议一个 NPC 一个文件 |
| 动作名 | **ASCII**，`idle` / `wave` / `push_book` …（**中文名我不接** ✗，代码里要按名字引用） |
| 骨架名 | 必须就是上面那七个 ✓（导出时若被翻译成 `bone`，说明名字没保 ASCII） |
| 格式版本 | GeckoLib 4.x 读的是 Bedrock 动画格式（`"format_version": "1.8.0"`，`loop` / `animation_length` / `bones` 块）—— 项目里已有范例：`assets\tnc\animations\entity\zhuangquerang.animation.json` ✓ |

**范例（现在挂在庄鹊让身上的那条）**：

```json
{
  "format_version": "1.8.0",
  "animations": {
    "idle": {
      "loop": true,
      "animation_length": 6.0,
      "bones": {
        "head": { "rotation": { "0.0": [0,0,0], "1.5": [0.6,3.0,0], "3.0": [0,0,0] } }
      }
    }
  }
}
```

---

## 四、我负责什么 / 你负责什么

| | 谁 |
|---|---|
| 动作好不好看、节奏对不对 | **你**（Blockbench 里当场看）|
| 演员在场景里站哪、什么时候播哪个动作、镜头 | **你**（先用文字/截图给我，工具化以后再说）|
| 模型骨架能不能用、动画有没有被游戏读到、接线、命令、验收步骤 | **我** |
| 出问题（进游戏不动、紫黑、隐形、播错名字）| **我** |

---

## 五、待验证的一环（诚实记账）

★ **左肢贴图方向**：我把经典皮肤的右臂/右腿**翻转后**放进现代布局的左肢位置，
几何里**没有**写 `mirror` 标志 —— 这个组合在本机**没法验证**（我看不到游戏实例）✗，
理论依据是"现代皮肤的渲染方式 = 经典 + 翻转标志"这条约定。

**若进游戏发现左臂/左腿的花纹反了**：修复 = 把 `<名字>_bedrock.png` 里左肢那两块
**再翻回来**（脚本里 `SKIN_PARTS` 的 `flip` 改成 `False` 重跑）✓ 一行的事，不用重新建模。

---

## 六、版本记录

| 日期 | 做了什么 | 人 |
|---|---|---|
| 2026-09-22 | 建立产线；`tools\gen_npc_bedrock.py` 生成 self/cava/huai 的 Bedrock 模型与 128×128 皮肤 | 动画系统MMM |
