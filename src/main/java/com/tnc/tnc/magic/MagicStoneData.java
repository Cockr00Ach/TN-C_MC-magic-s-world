package com.tnc.tnc.magic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 魔法石的全部玩家数据（设计文档第三、四节）。
 *
 * <p>设计要点：
 * <ul>
 *   <li>魔法石本身是<b>隐藏数据</b>，不是背包物品 —— 所以这里就是一个纯数据对象。</li>
 *   <li>亲和力 1–6，<b>天生固定</b>，每人每元素一个值。</li>
 *   <li>魔力值上限 = f(亲和力总和) + 原版等级成长；魔力值在上限内随时间恢复。</li>
 *   <li>魔法点数 = 按魔力上限的档位给（100→1 / 300→2 / 500→3 …），用来<b>解锁法术</b>。</li>
 *   <li>每元素有独立进度（学到第几级），学习<b>不可撤回</b>，只能靠遗忘药水重置。</li>
 * </ul>
 *
 * <p>数值都放在 {@link com.tnc.tnc.Config} 里，方便按设计文档第十三节的待确认清单调。
 */
public class MagicStoneData {

    public static final int ELEMENT_COUNT = Element.values().length;

    /** 一个元素下有几条链（雷系现在有主链/雷球/雷速三条）。 */
    public static final int CHAIN_COUNT = SpellCatalog.Chain.values().length;

    /** 亲和力下限 / 上限（设计文档第二节：1–6）。 */
    public static final int MIN_AFFINITY = 1;
    public static final int MAX_AFFINITY = 6;

    // ---------------- 持久化字段 ----------------

    private final int[] affinity = new int[ELEMENT_COUNT];

    /**
     * 每元素<b>每条链</b>各自已学到的等级 0–5（0 = 这条链还没学）。
     *
     * <p>为什么不是"每元素一个进度"：雷系有三条互相独立的链，
     * 若共用一条进度就会出现"学了雷球 1 级以后，三条链的 2 级全都学不了"的死锁
     * （{@code progress+1} 的规则会把整条元素卡在 1 级）。
     */
    private final int[][] progress = new int[ELEMENT_COUNT][CHAIN_COUNT];
    private final Set<ResourceLocation> learned = new LinkedHashSet<>();
    private final Set<ResourceLocation> learningHistory = new LinkedHashSet<>();
    private final Set<ResourceLocation> explicitlyForgotten = new LinkedHashSet<>();

    /**
     * 已获得的「领域魔法」（设计总纲 §12.F 特殊魔法）。
     *
     * <p>和 learned 的区别：领域魔法**无法升级**（没有 1~5 级）、**获得路线即获得**。
     * 存元素名而不是法术 id —— 每个元素只有一条领域魔法，用元素当键最直观。
     */
    private final Set<String> specials = new LinkedHashSet<>();

    // ------------------------------------------------------------------
    //  配装（loadout）—— "哪个键放哪个法术"，玩家自己排
    // ------------------------------------------------------------------

    /** 一共几页热键。2 页 × 9 键 = 18 个槽（2026-09-29 用户拍板）。 */
    public static final int PAGE_COUNT = 2;

    /** 一页几个键。引擎只给了 9 个施法热键（右键 + 2~9），改不动。 */
    public static final int SLOTS_PER_PAGE = 9;

    /** 一共几个槽。 */
    public static final int LOADOUT_SLOTS = PAGE_COUNT * SLOTS_PER_PAGE;

    /**
     * 18 个配装槽，元素是法术 id 字符串，{@code null} = 空槽。
     *
     * <p>用 List 而不是数组：NBT 读写 + 定长截断最省事，
     * 而且要保证<b>定长</b> —— 槽位号就是键位号，不能挤位。
     *
     * <p>为什么空槽留 null 而不是"把有内容的往前挤"：
     * 玩家把位移放在 7 号键，就不能因为前面空着而被挪到 2 号键。
     */
    private final List<String> loadout = new ArrayList<>(Collections.nCopies(LOADOUT_SLOTS, (String) null));

    /** 当前翻到第几页（0 基）。服务端权威，客户端只是镜像。 */
    private int loadoutPage;

    // ---- 配装（loadout）对外方法 ----

    public int getLoadoutPage() {
        return clamp(loadoutPage, 0, PAGE_COUNT - 1);
    }

    /** 设置当前页；真的变了才返回 true（调用方据此决定要不要重写杖）。 */
    public boolean setLoadoutPage(int page) {
        int clamped = clamp(page, 0, PAGE_COUNT - 1);
        if (clamped == getLoadoutPage()) {
            return false;
        }
        loadoutPage = clamped;
        return true;
    }

    /** 切到下一页（循环）。返回切换后的页号。 */
    public int cycleLoadoutPage() {
        setLoadoutPage((getLoadoutPage() + 1) % PAGE_COUNT);
        return getLoadoutPage();
    }

    /** 全局槽位下标（0..17）上放的是什么，空槽返回 null。 */
    public ResourceLocation getSlot(int index) {
        if (index < 0 || index >= LOADOUT_SLOTS) {
            return null;
        }
        String id = loadout.get(index);
        return id == null ? null : ResourceLocation.tryParse(id);
    }

    /**
     * 把一个法术放进某个槽。
     *
     * <p>同一个法术<b>只能占一个槽</b>（防止"3 个键全绑雷暴"刷冷却）——
     * 所以放进新槽时会顺手清掉它在别处的旧位置 ✓。
     *
     * @return 真的改动了才返回 true
     */
    public boolean setSlot(int index, ResourceLocation spell) {
        if (index < 0 || index >= LOADOUT_SLOTS) {
            return false;
        }
        String id = spell == null ? null : spell.toString();
        if (java.util.Objects.equals(loadout.get(index), id)) {
            return false;
        }
        if (id != null) {
            for (int i = 0; i < LOADOUT_SLOTS; i++) {
                if (id.equals(loadout.get(i))) {
                    loadout.set(i, null);
                }
            }
        }
        loadout.set(index, id);
        return true;
    }

    /**
     * <b>把配装拉回合法状态</b> —— 这是"一条链一个法术"的强制执行点。
     *
     * <h2>为什么不变量写在 setSlot/learn 里就够</h2>
     * 作者 2026-09-29 实测的真实顺序是"**先学完几个档，再去配键页手动摆**"：
     * 学法那一刻低档各占一个空槽，之后手动摆键时谁也没去清掉那些已经过时的低档 ✗
     * —— 结果是<a>同一条链的 t1 / t4 / t5 三个档同时挂在法杖上</a>，
     * 玩家当然"学了高级的，低级还能放"。
     *
     * <p>所以不做"在某个时刻替换"，而是定一条<b>不变量</b>，在每次写杖前强制执行：
     * <blockquote>每条链在配装里最多出现一次，且只能是它当前的最高档。</blockquote>
     *
     * <p>具体三步：
     * <ol>
     *   <li>槽位上的法术如果是自己那条链的<b>低档</b> → 换成该链当前链顶</li>
     *   <li>同一条链出现多次 → 优先保留玩家已绑定的<b>链顶</b>槽，否则保留第一个槽，其余清空</li>
     *   <li>某条链有链顶但没被绑过 → 补进第一个空槽（不挤掉任何已绑的）</li>
     * </ol>
     *
     * <p>放在 {@link SpellCatalog#wandSpellIds} 的入口上，所有路径（学法 / 手动配键 /
     * 切页 / 登录补杖 / 老存档）都会自愈 ✓。
     *
     * @return 真的改动过配装才返回 true
     */
    public boolean normalizeLoadout() {
        java.util.List<String> before = new java.util.ArrayList<>(loadout);
        java.util.Map<String,Integer> existingTops=new java.util.HashMap<>();
        for(int i=0;i<LOADOUT_SLOTS;i++){
            ResourceLocation id=loadout.get(i)==null?null:ResourceLocation.tryParse(loadout.get(i));
            SpellCatalog.Entry entry=id==null?null:SpellCatalog.byId(id);
            if(entry==null||entry.independent()||entry.element()==null||!hasLearned(id))continue;
            SpellCatalog.Entry top=SpellCatalog.topLearned(this,entry.element(),entry.chain());
            if(top!=null&&top.id().equals(id))existingTops.putIfAbsent(top.id().toString(),i);
        }
        java.util.Set<String> seen=new java.util.HashSet<>();
        for(int i=0;i<LOADOUT_SLOTS;i++){
            String bound=loadout.get(i);if(bound==null)continue;
            ResourceLocation id=ResourceLocation.tryParse(bound);
            if(id==null||!hasLearned(id)){loadout.set(i,null);continue;}
            SpellCatalog.Entry entry=SpellCatalog.byId(id);
            if(entry!=null&&!entry.independent()&&entry.element()!=null){
                SpellCatalog.Entry top=SpellCatalog.topLearned(this,entry.element(),entry.chain());
                if(top==null){loadout.set(i,null);continue;}
                bound=top.id().toString();
                if(existingTops.containsKey(bound)&&existingTops.get(bound)!=i){loadout.set(i,null);continue;}
            }
            loadout.set(i,seen.add(bound)?bound:null);
        }
        for (SpellCatalog.Entry top : SpellCatalog.effective(this)) {
            if (!isBound(top.id())) {
                int free = firstFreeSlot();
                if (free < 0) break;
                loadout.set(free, top.id().toString());
            }
        }
        return !before.equals(loadout);
    }

    /** 清空某个槽；本来就空返回 false。 */
    public boolean clearSlot(int index) {
        if (index < 0 || index >= LOADOUT_SLOTS || loadout.get(index) == null) {
            return false;
        }
        loadout.set(index, null);
        return true;
    }

    /** 第一个空槽的全局下标；18 个槽全满返回 -1。 */
    public int firstFreeSlot() {
        for (int i = 0; i < LOADOUT_SLOTS; i++) {
            if (loadout.get(i) == null) {
                return i;
            }
        }
        return -1;
    }

    /** 这个法术是不是已经配在某个槽里。 */
    public boolean isBound(ResourceLocation spell) {
        return spell != null && loadout.contains(spell.toString());
    }

    /** 已经配好的槽数（跨两页）。 */
    public int loadoutCount() {
        int count = 0;
        for (String id : loadout) {
            if (id != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * 默认配装：把新学会的法术放进<b>第一个空槽</b>。
     *
     * <p>★ 铁律：<b>绝不覆盖玩家已经配好的槽</b> —— 玩家手动排过键位之后，
     * 再学一个法术不该把他排好的东西顶掉（作者 2026-09-29 要的就是"自己调整"）✓。
     * 18 个槽全满就什么都不做：法术仍然在魔法石里，玩家自己去配键页腾位置。
     *
     * <p>⚠️ 但"同一条链学了更高档"是<b>例外</b>：那时要换掉这个键上的低档
     * （见 {@link #rebindChainToTop}）—— 那不是覆盖玩家的选择，
     * 玩家配的是"我的这条链"，链顶是哪一档由他学到哪儿决定 ✓。
     *
     * <p>还有一条收敛规则：<b>非链顶的法术自动不去占新槽</b>。
     * 因为配键列表只列链顶（{@link SpellCatalog#chainTopAssignable}），
     * 自动把过时的低档塞进槽里只会产生"列表里没有、却占着一个键"的怪状态 ✗。
     *
     * @return 真的放进去了才返回 true
     */
    public boolean fillFirstFree(ResourceLocation spell) {
        if (spell == null || isBound(spell)) {
            return false;
        }
        if (!SpellCatalog.isChainTop(this, spell)) {
            return false;                       // 不是链顶：不进新槽（学了更高档时会被换掉）
        }
        int free = firstFreeSlot();
        if (free < 0) {
            return false;
        }
        loadout.set(free, spell.toString());
        return true;
    }

    /**
     * <b>只给测试用</b>：直接写一个槽，绕过"同一法术只占一个槽"的清理。
     *
     * <p>为什么需要它：{@link #setSlot} 会顺手清掉同一个法术的旧位置，
     * 所以<b>造不出</b>"一个法术挂在两个槽上"那种坏状态 ——
     * 而 {@link #normalizeLoadout} 恰恰要能修好这种状态（老存档 / 异常路径）✓。
     */
    void forceSlotForTest(int index, ResourceLocation spell) {
        if (index >= 0 && index < LOADOUT_SLOTS) {
            loadout.set(index, spell == null ? null : spell.toString());
        }
    }

    /**
     * 裁掉"已经绑不了"的槽（换了链、法术被移除、被遗忘之后用）。
     *
     * @param assignable 现在还能绑的法术集合
     * @return 被清掉的槽数
     */
    public int pruneLoadout(Collection<ResourceLocation> assignable) {
        int removed = 0;
        for (int i = 0; i < LOADOUT_SLOTS; i++) {
            String id = loadout.get(i);
            if (id == null) {
                continue;
            }
            ResourceLocation parsed = ResourceLocation.tryParse(id);
            if (parsed == null || !assignable.contains(parsed)) {
                loadout.set(i, null);
                removed++;
            }
        }
        return removed;
    }

    /**
     * <b>某一页</b>该写进法杖的内容：定长 9 项，空槽是 {@code null}。
     *
     * <p>这是"法杖上到底有什么"的<b>唯一口径</b> —— 施法、切页、补杖、界面全走它，
     * 免得几处各算一套慢慢跑偏 ✗。
     */
    public List<ResourceLocation> pageSpellIds(int page) {
        int base = clamp(page, 0, PAGE_COUNT - 1) * SLOTS_PER_PAGE;
        List<ResourceLocation> result = new ArrayList<>(SLOTS_PER_PAGE);
        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            String id = loadout.get(base + i);
            result.add(id == null ? null : ResourceLocation.tryParse(id));
        }
        return result;
    }

    /** 配装的调试摘要（`/tnc loadout list` 与魔法石界面用）。 */
    public List<String> describeLoadout() {
        List<String> lines = new ArrayList<>();
        int page = getLoadoutPage();
        lines.add("配装  第 " + (page + 1) + "/" + PAGE_COUNT + " 页 · 已配 " + loadoutCount() + "/" + LOADOUT_SLOTS + " 个槽");
        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            int global = page * SLOTS_PER_PAGE + i;
            String id = loadout.get(global);
            String key = i == 0 ? "右键" : String.valueOf(i + 1);
            String name = "（空）";
            if (id != null) {
                ResourceLocation parsed = ResourceLocation.tryParse(id);
                SpellCatalog.Entry entry = parsed == null ? null : SpellCatalog.byId(parsed);
                name = entry != null ? entry.displayName() : id + "（不在目录里）";
            }
            lines.add("  " + key + " → " + name);
        }
        return lines;
    }

    // ---- 领域魔法（§12.F）对外方法 ----

    /** 有没有获得这个元素的领域魔法。 */
    public boolean hasSpecial(Element element) {
        return element != null && specials.contains(element.id());
    }

    /** 获得（重复获得返回 false）。 */
    public boolean grantSpecial(Element element) {
        return element != null && specials.add(element.id());
    }

    /** 撤销（本来就没有返回 false）。 */
    public boolean revokeSpecial(Element element) {
        return element != null && specials.remove(element.id());
    }

    /** 已获得的领域魔法对应的元素（界面/命令用）。 */
    public List<Element> specialElements() {
        List<Element> result = new ArrayList<>();
        for (Element element : Element.values()) {
            if (hasSpecial(element)) {
                result.add(element);
            }
        }
        return result;
    }

    private int mana;
    private int maxMana;
    private int pointsSpent;

    /**
     * 额外赠送的魔法点数（调试/剧情奖励用）。
     *
     * <p>为什么单独一个字段而不是去改 {@link #pointsSpent}：{@code pointsSpent} 是"花掉了多少"，
     * 把它改成负数只是让账面对不上；单独记"额外给了多少"账目才是清楚的。
     * 可用点数 = 档位总点数 + 额外赠送 − 已花掉。
     */
    private int bonusPoints;
    /** -1 denotes an account not yet migrated to permanent adventure growth. */
    private int adventurePoints = -1;

    public void setAdventurePoints(int points) { adventurePoints = Math.max(0, points); }

    /**
     * 亲和力是否已经分配过。亲和力天生固定，只在第一次进游戏时掷一次，
     * 之后永久不变（除非用遗忘药水之类的道具重置）。
     */
    private boolean initialized;

    public MagicStoneData() {
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            affinity[i] = 0;
            for (int c = 0; c < CHAIN_COUNT; c++) {
                progress[i][c] = 0;
            }
        }
        this.mana = 0;
        this.maxMana = 0;
        this.pointsSpent = 0;
        this.initialized = false;
    }

    // ------------------------------------------------------------------
    //  亲和力
    // ------------------------------------------------------------------

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * 某元素的亲和力。
     *
     * <p>⚠️ {@code element == null} 是**合法输入**：独立魔法（乱魔）的 element 就是 null ✗
     * （它不属于七元素之一）。2026-09-29 18:27 实机崩服就是 {@code null.ordinal()} ——
     * 所以这里直接返回 0，任何调用方都不该因为这个崩服务器 ✓。
     */
    public int getAffinity(Element element) {
        if (element == null) {
            return 0;
        }
        return affinity[element.ordinal()];
    }

    public void setAffinity(Element element, int value) {
        affinity[element.ordinal()] = clamp(value, MIN_AFFINITY, MAX_AFFINITY);
    }

    public int affinitySum() {
        int sum = 0;
        for (int value : affinity) {
            sum += value;
        }
        return sum;
    }

    /**
     * 第一次进游戏时分配亲和力。目前就是按配置给所有人同一个值（主角 S/M 七元素全为 3），
     * 以后要做"天生随机/剧情决定"就改这里。
     */
    public void assignDefaultAffinities(int value) {
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            affinity[i] = clamp(value, MIN_AFFINITY, MAX_AFFINITY);
        }
        initialized = true;
        recomputeMaxMana(0, 10, 10, 0);
        mana = maxMana;
    }

    /** 亲和力 → 该元素能学到的最高等级（设计文档第二节的对照表）。 */
    public int maxTierFor(Element element) {
        if (element == null) {
            return 5;                           // 独立魔法没有亲和力门槛（学习路径另有 entry.independent() 闸门 ✓）
        }
        int a = getAffinity(element);
        if (a <= 0) {
            return 0;
        }
        // 亲和力 5 与 6 都是神级；6 额外解锁"神级奇遇任务"（那部分以后单独做）
        return Math.min(a, 5);
    }

    // ------------------------------------------------------------------
    //  魔力值 / 魔法点数
    // ------------------------------------------------------------------

    public int getMana() {
        return mana;
    }

    public void setMana(int value) {
        this.mana = clamp(value, 0, Math.max(0, maxMana));
    }

    public void addMana(int delta) {
        setMana(this.mana + delta);
    }

    /** 施法消耗；不够就返回 false（调用方决定怎么提示）。 */
    public boolean spendMana(int cost) {
        if (cost < 0 || mana < cost) {
            return false;
        }
        mana -= cost;
        return true;
    }

    public int getMaxMana() {
        return maxMana;
    }

    public void setMaxMana(int value) {
        this.maxMana = Math.max(0, value);
        if (mana > maxMana) {
            mana = maxMana;
        }
    }

    /**
     * 重算魔力上限：亲和力总和 × manaPerAffinity + 原版等级 × manaPerVanillaLevel。
     * 亲和力总和是天生固定的，所以成长只来自原版等级。
     */
    public void recomputeMaxMana(int vanillaLevel, int manaPerAffinity, int manaPerVanillaLevel, int flatBonus) {
        int computed = affinitySum() * manaPerAffinity + Math.max(0, vanillaLevel) * manaPerVanillaLevel + flatBonus;
        setMaxMana(computed);
    }

    /** 已经花掉的点数。 */
    public int getPointsSpent() {
        return pointsSpent;
    }

    /**
     * 总点数 = 魔力上限跨过了几个档位。例如档位 [100,300,500]：
     * 上限 99 → 0 点；120 → 1 点；310 → 2 点；520 → 3 点。
     */
    public int getPointsTotal(List<? extends Integer> thresholds) {
        if (adventurePoints >= 0) return adventurePoints;
        int total = 0;
        for (Integer threshold : thresholds) {
            if (threshold != null && maxMana >= threshold) {
                total++;
            }
        }
        return total;
    }

    public int getPointsAvailable(List<? extends Integer> thresholds) {
        return Math.max(0, getPointsTotal(thresholds) + bonusPoints - pointsSpent);
    }

    /** 额外赠送的点数（调试/奖励）。 */
    public int getBonusPoints() {
        return bonusPoints;
    }

    public void setBonusPoints(int value) {
        this.bonusPoints = Math.max(0, value);
    }

    public void addBonusPoints(int delta) {
        setBonusPoints(this.bonusPoints + delta);
    }

    /** 投点数解锁法术。点数不够返回 false。 */
    public boolean spendPoints(int cost) {
        if (cost < 0) {
            return false;
        }
        pointsSpent += cost;
        return true;
    }

    /** 遗忘药水：退还该元素所有点数并清空该元素进度与已学法术。 */
    public int resetElement(Element element) {
        int refund = 0;
        List<ResourceLocation> toRemove = new ArrayList<>();
        for (ResourceLocation spell : learned) {
            if (isSpellOfElement(spell, element)) {
                toRemove.add(spell);
            }
        }
        for (ResourceLocation spell : toRemove) {
            learned.remove(spell);
            refund += 1; // 具体退多少等做遗忘药水时再按真实花费算
        }
        for (int c = 0; c < CHAIN_COUNT; c++) {
            progress[element.ordinal()][c] = 0;
        }
        learningHistory.removeIf(id -> isSpellOfElement(id, element));
        explicitlyForgotten.removeIf(id -> isSpellOfElement(id, element));
        // 配装里属于这个元素的槽一起清掉（否则法杖留着放不出来的死槽 ✗）
        for (int i = 0; i < LOADOUT_SLOTS; i++) {
            String id = loadout.get(i);
            if (id == null) {
                continue;
            }
            ResourceLocation parsed = ResourceLocation.tryParse(id);
            if (parsed != null && isSpellOfElement(parsed, element)) {
                loadout.set(i, null);
            }
        }
        pointsSpent = Math.max(0, pointsSpent - refund);
        return refund;
    }

    /**
     * 现阶段只按法术 id 的前缀猜元素（tnc:lightning_xxx 之类）。
     * 等做学习系统时应该改成读法术数据里的学派字段。
     */
    private static boolean isSpellOfElement(ResourceLocation spell, Element element) {
        return spell.getPath().contains(element.id());
    }

    // ------------------------------------------------------------------
    //  进度与已学法术
    // ------------------------------------------------------------------

    /** 进度：**主链**的已学等级（兼容旧调用）。 */
    public int getProgress(Element element) {
        return getProgress(element, SpellCatalog.Chain.CORE);
    }

    /** 进度：某元素某条链的已学等级 0–5。 */
    public int getProgress(Element element, SpellCatalog.Chain chain) {
        return progress[element.ordinal()][chain.ordinal()];
    }

    /** 设置：**主链**的进度（兼容旧调用）。 */
    public void setProgress(Element element, int value) {
        setProgress(element, SpellCatalog.Chain.CORE, value);
    }

    /** 设置：某元素某条链的进度。 */
    public void setProgress(Element element, SpellCatalog.Chain chain, int value) {
        progress[element.ordinal()][chain.ordinal()] = clamp(value, 0, SpellCatalog.maxTier());
    }

    /** 这条链里已经解锁的最高等级（跨该元素的**所有链**取最大，界面/命令展示用）。 */
    public int getProgressMax(Element element) {
        int max = 0;
        for (int c = 0; c < CHAIN_COUNT; c++) {
            max = Math.max(max, progress[element.ordinal()][c]);
        }
        return max;
    }

    /** 能不能学这个元素这条链这个等级的法术：等级 ≤ 亲和力允许的上限，且不能跳级。 */
    public boolean canLearnTier(Element element, SpellCatalog.Chain chain, int tier) {
        return tier >= 1 && tier <= maxTierFor(element) && tier <= getProgress(element, chain) + 1;
    }

    public Set<ResourceLocation> getLearned() {
        return Collections.unmodifiableSet(learned);
    }

    public boolean hasLearned(ResourceLocation spell) {
        return learned.contains(spell);
    }

    public boolean learn(ResourceLocation spell) {
        learningHistory.add(spell);
        explicitlyForgotten.remove(spell);
        boolean added = learned.add(spell);
        if (!added) {
            // 已经学过：那次学习可能是"补记录"，但链顶仍可能变（例如老存档迁移），照样同步一次
            rebindChainToTop(spell);
            return false;
        }
        // ★ 链式替换（作者 2026-09-29 定的规则）：
        //   学会某条链的更高档时，**原来绑着这条链低档的那些键自动换成新的最高档**。
        //   这不是"覆盖玩家手配的键位" ✗ —— 它换的是**同一条链**的东西：
        //   玩家配的是"我的雷系主链"，至于主链当前是哪一档，由他学到哪儿决定 ✓。
        //   不同链、别的元素的键一律不动 ✓。
        if (!rebindChainToTop(spell)) {
            // 独立魔法 / 不在链上的法术：没有"链顶"概念，按普通新法术填空槽
            fillFirstFree(spell);
        }
        return true;
    }

    /**
     * 把配装里所有"绑着 {@code spell} 所属链的低档"的槽改成 {@code spell}。
     *
     * @return 这个法术属于某条链（做了链式替换）返回 true；独立魔法/目录外返回 false
     */
    private boolean rebindChainToTop(ResourceLocation spell) {
        SpellCatalog.Entry entry = SpellCatalog.byId(spell);
        if (entry == null || entry.independent() || entry.element() == null) {
            return false;
        }
        SpellCatalog.Entry top = SpellCatalog.topLearned(this, entry.element(), entry.chain());
        if (top == null || !top.id().equals(spell)) {
            return false;                       // 学会的不是链顶（理论上不该发生）→ 不替换
        }
        String newId = spell.toString();
        boolean changed = false;
        for (int i = 0; i < LOADOUT_SLOTS; i++) {
            String bound = loadout.get(i);
            if (bound == null) {
                continue;
            }
            ResourceLocation parsed = ResourceLocation.tryParse(bound);
            SpellCatalog.Entry boundEntry = parsed == null ? null : SpellCatalog.byId(parsed);
            if (boundEntry == null || boundEntry.independent() || boundEntry.element() == null) {
                continue;
            }
            if (boundEntry.element() == entry.element() && boundEntry.chain() == entry.chain()) {
                loadout.set(i, newId);
                changed = true;
            }
        }
        if (!changed) {
            // 这条链还没在任何键上 → 当成新法术填空槽（玩家不手动配也能用上）
            fillFirstFree(spell);
        }
        return true;
    }

    /**
     * 遗忘时顺手把配装里的槽清掉。
     *
     * <p>不清的话法杖会留一个"绑着已遗忘法术"的死槽 ——
     * 放不出来、也没有任何提示，玩家只会觉得"这个键坏了" ✗。
     */
    public boolean forget(ResourceLocation spell) {
        if (learned.contains(spell)) {
            explicitlyForgotten.add(spell);
        }
        for (int i = 0; i < LOADOUT_SLOTS; i++) {
            if (spell.toString().equals(loadout.get(i))) {
                loadout.set(i, null);
            }
        }
        return learned.remove(spell);
    }

    public boolean hasPreviouslyLearned(ResourceLocation spell) {
        return learningHistory.contains(spell);
    }

    public boolean isExplicitlyForgotten(ResourceLocation spell) {
        return explicitlyForgotten.contains(spell);
    }

    /** Also suppress replacement IDs when forgetting all after a catalog migration. */
    public void suppressElementalCatchUp() {
        for (SpellCatalog.Entry entry : SpellCatalog.all()) {
            if (!entry.independent() && entry.element() != null
                    && entry.tier() <= getProgress(entry.element(), entry.chain())) {
                explicitlyForgotten.add(entry.id());
            }
        }
    }

    // ------------------------------------------------------------------
    //  复制（死亡重生 / 换维度时用）
    // ------------------------------------------------------------------

    public void copyFrom(MagicStoneData other) {
        System.arraycopy(other.affinity, 0, this.affinity, 0, ELEMENT_COUNT);
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            System.arraycopy(other.progress[i], 0, this.progress[i], 0, CHAIN_COUNT);
        }
        this.learned.clear();
        this.specials.clear();
        this.learned.addAll(other.learned);
        this.learningHistory.clear();
        this.learningHistory.addAll(other.learningHistory);
        this.explicitlyForgotten.clear();
        this.explicitlyForgotten.addAll(other.explicitlyForgotten);
        this.specials.clear();
        this.specials.addAll(other.specials);
        // 配装：死亡重生/换维度必须原样带过去，否则一死键位就全空了
        this.loadout.clear();
        this.loadout.addAll(other.loadout);
        this.loadoutPage = other.loadoutPage;
        this.mana = other.mana;
        this.maxMana = other.maxMana;
        this.pointsSpent = other.pointsSpent;
        this.bonusPoints = other.bonusPoints;
        this.adventurePoints = other.adventurePoints;
        this.initialized = other.initialized;
    }

    // ------------------------------------------------------------------
    //  NBT
    // ------------------------------------------------------------------

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();

        ListTag affinityList = new ListTag();
        for (int value : affinity) {
            affinityList.add(net.minecraft.nbt.IntTag.valueOf(value));
        }
        tag.put("Affinity", affinityList);

        // 旧字段：只写主链，保持向后兼容（老版本读到它也不会出错）
        int[] coreTiers = new int[ELEMENT_COUNT];
        for (int e = 0; e < ELEMENT_COUNT; e++) {
            coreTiers[e] = progress[e][SpellCatalog.Chain.CORE.ordinal()];
        }
        tag.put("Progress", toList(coreTiers));

        // 新字段：每条链一个数组。链名当 key，加新链/改顺序都不会串位。
        CompoundTag chainTag = new CompoundTag();
        for (SpellCatalog.Chain chain : SpellCatalog.Chain.values()) {
            int[] tiers = new int[ELEMENT_COUNT];
            for (int e = 0; e < ELEMENT_COUNT; e++) {
                tiers[e] = progress[e][chain.ordinal()];
            }
            chainTag.put(chain.name(), toList(tiers));
        }
        tag.put("ChainProgress", chainTag);

        ListTag learnedList = new ListTag();
        for (ResourceLocation spell : learned) {
            learnedList.add(StringTag.valueOf(spell.toString()));
        }
        tag.put("Learned", learnedList);
        tag.put("LearningHistory", spellList(learningHistory));
        tag.put("ExplicitlyForgotten", spellList(explicitlyForgotten));
        // 领域魔法（§12.F）：存元素 id 字符串集合；老存档没有这个键 → 读出来是空集 ✓
        ListTag specialList = new ListTag();
        for (String element : specials) {
            specialList.add(StringTag.valueOf(element));
        }
        tag.put("Specials", specialList);

        // 配装（loadout）：定长 18 项，空槽写空串 —— 空串表示"这个槽是空的"，
        // 不能用跳过的方式存，否则槽位号会被压缩（玩家排好的键位就乱了）✓
        ListTag loadoutList = new ListTag();
        for (String id : loadout) {
            loadoutList.add(StringTag.valueOf(id == null ? "" : id));
        }
        tag.put("Loadout", loadoutList);
        tag.putInt("LoadoutPage", getLoadoutPage());

        tag.putInt("Mana", mana);
        tag.putInt("MaxMana", maxMana);
        tag.putInt("PointsSpent", pointsSpent);
        tag.putInt("BonusPoints", bonusPoints);
        tag.putInt("AdventurePoints", adventurePoints);
        tag.putBoolean("Initialized", initialized);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        readIntArray(tag.getList("Affinity", Tag.TAG_INT), affinity);

        // 先清零，再按"新格式优先、旧格式兜底"读
        for (int e = 0; e < ELEMENT_COUNT; e++) {
            for (int c = 0; c < CHAIN_COUNT; c++) {
                progress[e][c] = 0;
            }
        }
        if (tag.contains("ChainProgress", Tag.TAG_COMPOUND)) {
            CompoundTag chainTag = tag.getCompound("ChainProgress");
            for (SpellCatalog.Chain chain : SpellCatalog.Chain.values()) {
                int[] tiers = new int[ELEMENT_COUNT];
                readIntArray(chainTag.getList(chain.name(), Tag.TAG_INT), tiers);
                for (int e = 0; e < ELEMENT_COUNT; e++) {
                    progress[e][chain.ordinal()] = tiers[e];
                }
            }
        } else {
            // 老存档只有主链进度：读进主链，新链从 0 开始（正是我们想要的）
            int[] coreTiers = new int[ELEMENT_COUNT];
            readIntArray(tag.getList("Progress", Tag.TAG_INT), coreTiers);
            for (int e = 0; e < ELEMENT_COUNT; e++) {
                progress[e][SpellCatalog.Chain.CORE.ordinal()] = coreTiers[e];
            }
        }

        learned.clear();
        specials.clear();
        for (Tag entry : tag.getList("Specials", Tag.TAG_STRING)) {
            specials.add(entry.getAsString());
        }
        ListTag learnedList = tag.getList("Learned", Tag.TAG_STRING);
        for (int i = 0; i < learnedList.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(currentSpellId(learnedList.getString(i)));
            if (id != null) {
                learned.add(id);
            }
        }

        mana = tag.getInt("Mana");
        loadoutPage = clamp(tag.getInt("LoadoutPage"), 0, PAGE_COUNT - 1);
        // 配装：老存档没有 Loadout 键 → 空表 → 走下面的"按链进度自动补默认配装"✓
        loadout.clear();
        if (tag.contains("Loadout", Tag.TAG_LIST)) {
            ListTag loadoutList = tag.getList("Loadout", Tag.TAG_STRING);
            for (int i = 0; i < LOADOUT_SLOTS; i++) {
                String raw = i < loadoutList.size() ? loadoutList.getString(i) : "";
                loadout.add(raw.isEmpty() ? null : currentSpellId(raw));
            }
        } else {
            loadout.addAll(Collections.nCopies(LOADOUT_SLOTS, (String) null));
            // 迁移：老存档从来没有配装概念 → 用"每条链的已学最高档"当第一版默认配装。
            // 只补能进目录的（防呆闸门那套由调用方 prune），且忠于链式替换的老观感 ✓。
            for (SpellCatalog.Entry entry : SpellCatalog.effective(this)) {
                fillFirstFree(entry.id());
            }
        }
        learningHistory.clear();        readSpellSet(tag.getList("LearningHistory", Tag.TAG_STRING), learningHistory);
        learningHistory.addAll(learned); // Legacy saves retain ownership of current spells.
        explicitlyForgotten.clear();
        readSpellSet(tag.getList("ExplicitlyForgotten", Tag.TAG_STRING), explicitlyForgotten);
        explicitlyForgotten.removeAll(learned);
        maxMana = tag.getInt("MaxMana");
        pointsSpent = tag.getInt("PointsSpent");
        bonusPoints = Math.max(0, tag.getInt("BonusPoints"));
        adventurePoints = tag.contains("AdventurePoints") ? Math.max(-1, tag.getInt("AdventurePoints")) : -1;
        initialized = tag.getBoolean("Initialized");
    }

    private static void readIntArray(ListTag list, int[] target) {
        for (int i = 0; i < target.length && i < list.size(); i++) {
            target[i] = list.getInt(i);
        }
    }

    private static ListTag spellList(Set<ResourceLocation> spells) {
        ListTag list = new ListTag();
        for (ResourceLocation spell : spells) list.add(StringTag.valueOf(spell.toString()));
        return list;
    }

    /** Preserve ownership, custom keys and explicit forgetting across the fireball-chain rename. */
    private static String currentSpellId(String id) {
        return switch (id) {
            case "tnc:giant_fireball" -> "tnc:lava_fireball";
            case "tnc:self_destruct" -> "tnc:molten_skyfall";
            case "tnc:meteor_fireball" -> "tnc:meteor_fall";
            default -> id;
        };
    }

    private static void readSpellSet(ListTag list, Set<ResourceLocation> target) {
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(currentSpellId(list.getString(i)));
            if (id != null) target.add(id);
        }
    }

    /** 每元素一个 int 打成 NBT 列表。 */
    private static ListTag toList(int[] values) {
        ListTag list = new ListTag();
        for (int value : values) {
            list.add(net.minecraft.nbt.IntTag.valueOf(value));
        }
        return list;
    }

    // ------------------------------------------------------------------
    //  工具
    // ------------------------------------------------------------------

    /** 玩家原版等级（0 起）。 */
    public static int vanillaLevelOf(Player player) {
        return player.experienceLevel;
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** 给命令/调试用的一行摘要。 */
    public List<String> describe(List<? extends Integer> thresholds) {
        List<String> lines = new ArrayList<>();
        StringBuilder aff = new StringBuilder();
        StringBuilder prog = new StringBuilder();
        for (Element e : Element.values()) {
            if (aff.length() > 0) {
                aff.append(" ");
                prog.append(" ");
            }
            aff.append(e.cn()).append(":").append(getAffinity(e));
            prog.append(e.cn()).append(":");
            // 每条链各一个数字，链之间用 / 隔开（雷系现在有主链/雷球/雷速三条）
            for (int c = 0; c < CHAIN_COUNT; c++) {
                if (c > 0) {
                    prog.append("/");
                }
                prog.append(progress[e.ordinal()][c]);
            }
        }
        lines.add("亲和力   " + aff);
        lines.add("链进度   " + prog + "（每元素按 "
                + java.util.Arrays.stream(SpellCatalog.Chain.values()).map(SpellCatalog.Chain::cn)
                        .collect(java.util.stream.Collectors.joining("/"))
                + " 的顺序）");
        lines.add("魔力     " + mana + " / " + maxMana);
        lines.add("点数     " + getPointsAvailable(thresholds) + " 可用（总 " + getPointsTotal(thresholds)
                + (bonusPoints > 0 ? " + 赠送 " + bonusPoints : "")
                + "，已投 " + pointsSpent + "）");
        lines.add("已学法术 " + (learned.isEmpty() ? "（无）" : joinSpells()));
        lines.addAll(describeLoadout());
        // 领域魔法（§12.F）：只列"已获得"的，没获得的不刷屏
        List<Element> domains = specialElements();
        StringBuilder domainText = new StringBuilder();
        for (Element element : domains) {
            if (domainText.length() > 0) {
                domainText.append(" ");
            }
            SpellCatalog.Special special = SpellCatalog.specialOf(element);
            domainText.append(special != null ? special.name() : element.cn() + "领域");
        }
        lines.add("领域魔法 " + (domains.isEmpty() ? "（无）" : domainText.toString()));
        return lines;
    }

    private String joinSpells() {
        StringBuilder sb = new StringBuilder();
        for (ResourceLocation spell : learned) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(spell);
        }
        return sb.toString();
    }

    public Collection<ResourceLocation> learnedView() {
        return Collections.unmodifiableCollection(learned);
    }
}
