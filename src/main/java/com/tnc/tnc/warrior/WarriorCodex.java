package com.tnc.tnc.warrior;

import java.util.ArrayList;
import java.util.List;

/**
 * 战士 · 武器图鉴（对应魔法石界面的"元素 → 链 → 法术"，这里换成"武器 → 链 → 招式"）。
 *
 * <p><b>不花磨砺点数。</b>魔法石是"投点数解锁"，战士走的是另一条经济线：磨砺值只负责
 * 成长与减伤（{@link TemperingRules}），招式本身<b>靠条件开放</b>——磨砺值到线、训练课
 * 完成几次、盾牌试炼毕业、最佳评分够高。所以这里没有"已投点数"，也没有"点一下扣费"，
 * 每个条目只有三种状态：
 *
 * <ul>
 *   <li>{@link State#MET} 条件已满足（界面里亮起来）；</li>
 *   <li>{@link State#ONGOING} 条件未满足（显示差多少）；</li>
 *   <li>{@link State#PLANNED} 还没实装（列在这里是为了让路线图可见，
 *       不参与"达成"判定，也不会骗玩家去练一个不存在的东西）。</li>
 * </ul>
 *
 * <p>状态是<b>算出来的</b>：没有新的存档字段、没有新的网络包。所有输入都在
 * {@link Progress} 里，服务端从 {@link TemperingData} 取、客户端从
 * {@code TemperingClient} 的镜像取，两边用同一段代码判定——这是刻意设计的：
 * 图鉴本质是"进度的另一面镜子"，一旦它自己存一份独立进度，就一定会和真实进度对不上。
 */
public final class WarriorCodex {

    private WarriorCodex() {
    }

    // ------------------------------------------------------------------
    //  武器
    // ------------------------------------------------------------------

    /**
     * 六条武器线。顺序＝界面底部页签的顺序，也是"从最基础到最特殊"的阅读顺序。
     *
     * <p>长柄与弓目前<b>没有</b>任何机制，条目全部 {@code planned}：页签照样列出来，
     * 免得玩家以为图鉴漏了武器；点进去只会看到规划中的条目。
     */
    public enum Weapon {
        SWORD("剑", "直刺、横扫、反击都归这条线"),
        SHIELD("盾", "挡、格、压制；盾牌自身不耗耐久"),
        AXE("重武器", "斧、重剑、战锤共用重武器的反击与压制"),
        POLEARM("长柄", "长枪走这条线：距离更长、突刺更远"),
        BOW("弓", "远程，尚未开课"),
        WIND("风刃", "风灌注武器的主动能力");

        private final String cn;
        private final String note;

        Weapon(String cn, String note) {
            this.cn = cn;
            this.note = note;
        }

        /** 页签/标题上的中文名。 */
        public String cn() {
            return cn;
        }

        /** 一句话定位，显示在左栏标题下。 */
        public String note() {
            return note;
        }

        /** 这条线有没有任何已实装的条目（没有的话界面上会提示"尚未开课"）。 */
        public boolean anyImplemented() {
            for (Entry entry : of(this)) {
                if (entry.implemented()) {
                    return true;
                }
            }
            return false;
        }
    }

    // ------------------------------------------------------------------
    //  玩家进度（判定条件的唯一输入）
    // ------------------------------------------------------------------

    /**
     * 参与判定的玩家状态快照。
     *
     * <p>字段刻意与 {@code TemperingPacket} 已有的字段一一对应 —— 图鉴能显示什么，
     * 完全取决于服务端早就在同步什么，不需要为了界面新增协议字段。
     *
     * @param talent      天赋 0..6（决定磨砺上限）
     * @param points      当前磨砺值
     * @param completions 专项训练完成次数
     * @param bestScore   最佳评分 0..100
     * @param graduate    盾牌试炼是否毕业
     */
    public record Progress(int talent, int points, int completions, int bestScore, boolean graduate) {

        public static final Progress EMPTY = new Progress(0, 0, 0, 0, false);

        public Progress {
            talent = WarriorTalentRules.clamp(talent);
            points = Math.max(0, Math.min(TemperingRules.MAX_POINTS, points));
            completions = Math.max(0, completions);
            bestScore = Math.max(0, Math.min(100, bestScore));
        }

        /** 当前天赋下的磨砺上限。 */
        public int cap() {
            return WarriorTalentRules.cap(talent);
        }

        /** 有没有战士天赋（没天赋的角色不积累磨砺）。 */
        public boolean enrolled() {
            return talent > 0;
        }

        /** 段位名（初习/扎根/坚韧/百炼/久战）。 */
        public String rankName() {
            return TemperingRules.rankName(points);
        }

        /** 额外减伤（0..0.30）。 */
        public double reduction() {
            return TemperingRules.reduction(points);
        }
    }

    // ------------------------------------------------------------------
    //  条件
    // ------------------------------------------------------------------

    /** 一条判定条件。全部条件都满足，条目才算开放。 */
    public enum Gate {
        /** 磨砺值 ≥ N（永久积累值，不扣不花）。 */
        TEMPER("磨砺"),
        /** 天赋 ≥ N（角色创建/剧情给的，不能靠训练提升）。 */
        TALENT("天赋"),
        /** 专项训练完成次数 ≥ N。 */
        COMPLETIONS("完成训练"),
        /** 最佳评分 ≥ N。 */
        BEST_SCORE("最佳评分"),
        /** 盾牌试炼毕业。 */
        GRADUATE("盾牌试炼");

        private final String cn;

        Gate(String cn) {
            this.cn = cn;
        }

        public String cn() {
            return cn;
        }
    }

    /** 一条"闸门"：{@link Gate} + 阈值。 */
    public record Clause(Gate gate, int value) {

        public Clause {
            value = Math.max(0, value);
        }

        /** 玩家当前值（用于"还差多少"）。 */
        public int current(Progress progress) {
            return switch (gate) {
                case TEMPER -> progress.points();
                case TALENT -> progress.talent();
                case COMPLETIONS -> progress.completions();
                case BEST_SCORE -> progress.bestScore();
                case GRADUATE -> progress.graduate() ? 1 : 0;
            };
        }

        public boolean met(Progress progress) {
            return current(progress) >= value;
        }

        /** 例如「磨砺 1000（当前 320）」。 */
        public String text(Progress progress) {
            if (gate == Gate.GRADUATE) {
                return gate.cn() + " 毕业（当前 " + (progress.graduate() ? "已毕业" : "未毕业") + "）";
            }
            return gate.cn() + " " + value + "（当前 " + current(progress) + "）";
        }

        /** 只要条件名，不带当前值（目录页/测试用）。 */
        public String label() {
            return gate == Gate.GRADUATE ? gate.cn() + " 毕业" : gate.cn() + " " + value;
        }
    }

    /** 条目状态：条件已满足 / 还在路上 / 尚未实装。 */
    public enum State {
        MET, ONGOING, PLANNED
    }

    // ------------------------------------------------------------------
    //  条目
    // ------------------------------------------------------------------

    /**
     * 一个招式条目。
     *
     * @param id          存档/测试用的稳定 id（<b>不要改</b>）
     * @param weapon      属于哪条武器线
     * @param chain       武器内的"链"（界面里一列）
     * @param tier        链内第几级（1 起）
     * @param name        显示名
     * @param effect      一句话效果
     * @param source      怎么练/谁教（来源说明）
     * @param implemented 是否已实装；{@code false} 只是路线图
     * @param gates       开放条件（全部满足才开放；空列表 = 无条件）
     * @param role        对应教官（{@code counter/guard/blade/wind}），没有则空串
     */
    public record Entry(String id, Weapon weapon, String chain, int tier, String name, String effect,
                        String source, boolean implemented, List<Clause> gates, String role) {

        public Entry {
            gates = List.copyOf(gates);
            role = role == null ? "" : role;
        }

        public State state(Progress progress) {
            if (!implemented) {
                return State.PLANNED;
            }
            return met(progress) ? State.MET : State.ONGOING;
        }

        public boolean met(Progress progress) {
            for (Clause clause : gates) {
                if (!clause.met(progress)) {
                    return false;
                }
            }
            return true;
        }

        /** 还没满足的条件（空 = 全部满足）。 */
        public List<Clause> unmet(Progress progress) {
            List<Clause> out = new ArrayList<>();
            for (Clause clause : gates) {
                if (!clause.met(progress)) {
                    out.add(clause);
                }
            }
            return out;
        }

        /**
         * 进度百分比 0..100 = 各条件完成度的最小值。
         *
         * <p>取最小值而不是平均值：条件之间是"与"关系，任何一条没到线，整体就没开放，
         * 平均值会让"差一条"看起来像"快好了"。
         *
         * <p>规划中的条目恒为 0：它没有"进度"可言，显示 60% 只会让人以为快能用了。
         */
        public int percent(Progress progress) {
            if (!implemented) {
                return 0;
            }
            if (gates.isEmpty()) {
                return 100;
            }
            int best = 100;
            for (Clause clause : gates) {
                int need = clause.value();
                if (need <= 0) {
                    continue;
                }
                best = Math.min(best, (int) (100L * Math.min(clause.current(progress), need) / need));
            }
            return Math.max(0, Math.min(100, best));
        }

        /** 例如「剑 · 守势反击 · 第 2 级」。 */
        public String title() {
            return weapon.cn() + " · " + chain + " · 第 " + tier + " 级";
        }
    }

    // ------------------------------------------------------------------
    //  目录内容
    // ------------------------------------------------------------------

    /**
     * 全部条目，按武器 → 链 → 级排好。
     *
     * <p>只写<b>这套模组里真实存在</b>或<b>设计文档明确列过</b>的东西：
     * {@code implemented = true} 的必须能在代码里找到对应机制，{@code false} 的是
     * 文档里的提案（剑气、压服、返锋、火/土灌注、长柄、弓……）。写目录时别顺手把
     * 提案标成已实装 —— 图鉴是玩家判断"现在能不能练"的唯一依据。
     */
    private static final List<Entry> ENTRIES = List.of(
            // ---------------- 剑 ----------------
            entry("sword_counter_1", Weapon.SWORD, "守势反击", 1, "反击入门",
                    "三次有效盾挡后，在窗口内持剑或斧击中最后一个敌人即可领磨砺。",
                    "陪练的守势反击课，场内任意难度", true, "counter", temper(200)),
            entry("sword_counter_2", Weapon.SWORD, "守势反击", 2, "窗口读秒",
                    "反击窗口随难度变长，命中判定与领奖规则不变。",
                    "陪练难度调到标准或更高", true, "counter", temper(1000), completions(1)),
            entry("sword_counter_3", Weapon.SWORD, "守势反击", 3, "返锋",
                    "时机准确时把明确标记为可反弹的投射物打回去。",
                    "设计文档提案，尚未实装", false, "", temper(3000), completions(5), bestScore(80)),
            entry("sword_charge_1", Weapon.SWORD, "蓄力剑", 1, "蓄满剑击",
                    "五次蓄满的剑击，完成莉娅的专项课。",
                    "莉娅的专项课（限时 45 秒，潜行右键取消）", true, "blade", temper(200)),
            entry("sword_charge_2", Weapon.SWORD, "蓄力剑", 2, "剑气",
                    "蓄力或连招终段打出短程剑气，用武器自身冷却。",
                    "设计文档提案，尚未实装", false, "", temper(1000), completions(3)),
            entry("sword_charge_3", Weapon.SWORD, "蓄力剑", 3, "剑气贯通",
                    "剑气延长一格，命中后短暂减速。",
                    "设计文档提案，尚未实装", false, "", temper(3000), bestScore(90)),
            entry("sword_combo_1", Weapon.SWORD, "长剑连招", 1, "破锋式",
                    "顺序 QZE（左斩·横斩·右斩）后自动衔接：向前斩出三重剑势，扇形范围伤害并小幅击退。",
                    "持任意剑类按顺序出招；磨砺 200 自动点亮，不耗磨砺", true, "", temper(200)),
            entry("sword_combo_2", Weapon.SWORD, "长剑连招", 2, "旋转斩击",
                    "顺序 QEZZZZ（左斩·右斩·四记横斩）后自动衔接：原地旋身一周，扫开近身的所有敌人。",
                    "持任意剑类按顺序出招；磨砺 1000 自动点亮，不耗磨砺", true, "", temper(1000)),
            entry("sword_combo_3", Weapon.SWORD, "长剑连招", 3, "逆鳞斩",
                    "顺序 ZZQQEE（横斩两次·左斩两次·右斩两次）后自动衔接：回身大范围重劈，击退更强；"
                            + "命中正在振刀的人会打散对方的架势并压 2 秒僵直（破振刀）。",
                    "持任意剑类按顺序出招；磨砺 3000 自动点亮，不耗磨砺", true, "", temper(3000)),
            entry("sword_combo_4", Weapon.SWORD, "长剑连招", 4, "架势·振刀",
                    "顺序 EQ + 长按 E（按住半秒）后自动衔接：架刀震开身前的攻击，打断施法/连招并压出僵直，"
                            + "自身获得短时抗性。放出后 2 秒内被攻击链/突击链打到会把对方架住："
                            + "这一下不掉血，对方连招作废并僵直 1 秒。",
                    "持任意剑类按顺序出招，最后一下按住；磨砺 1500 自动点亮，不耗磨砺", true, "", temper(1500)),

            // ---------------- 盾 ----------------
            entry("shield_ready_1", Weapon.SHIELD, "盾挡", 1, "举盾就绪",
                    "举盾立刻出现一次盾面轮廓；举盾期间盾牌不耗耐久。",
                    "洛克专项课，或场内任意举盾", true, "guard", temper(200)),
            entry("shield_ready_2", Weapon.SHIELD, "盾挡", 2, "精准格挡",
                    "举盾生效后 10 刻内挡住有效伤害，追加亮闪与晶体提示音。",
                    "洛克专项课（五次有效盾挡）", true, "guard", temper(1000), completions(1)),
            entry("shield_ready_3", Weapon.SHIELD, "盾挡", 3, "盾墙",
                    "持续举盾形成短时护体，改为减伤而不是完全格挡。",
                    "设计文档提案，尚未实装", false, "", temper(3000)),
            entry("shield_suppress_1", Weapon.SHIELD, "压服", 1, "盾击压制",
                    "盾击把普通敌人短暂压住；对 Boss 转换为削韧。",
                    "设计文档提案，尚未实装", false, "", temper(1000)),
            entry("shield_suppress_2", Weapon.SHIELD, "压服", 2, "压服·削韧",
                    "压服对 Boss 的削韧提高到固定值，带抗性与冷却。",
                    "设计文档提案，尚未实装", false, "", temper(3000), completions(3)),

            // ---------------- 斧 ----------------
            entry("axe_counter_1", Weapon.AXE, "重劈", 1, "斧反",
                    "守势反击对斧同样有效，反击伤害按武器本身结算。",
                    "陪练的守势反击课，手持斧", true, "counter", temper(200)),
            entry("axe_counter_2", Weapon.AXE, "重劈", 2, "蓄力重劈",
                    "蓄满后一次重劈，前摇更长、削韧更高。",
                    "设计文档提案，尚未实装", false, "", temper(1000)),
            entry("axe_counter_3", Weapon.AXE, "重劈", 3, "削韧",
                    "重击积累削韧值，达到阈值后打断敌人动作。",
                    "设计文档提案，尚未实装", false, "", temper(3000), bestScore(80)),
            entry("axe_stance_1", Weapon.AXE, "站稳", 1, "稳足",
                    "站稳蓄力时更难被击退，代价是移动变慢。",
                    "设计文档提案，尚未实装", false, "", temper(2000)),

            // ---------------- 重剑（挂重武器页签） ----------------
            entry("greatsword_combo_1", Weapon.AXE, "重剑连招", 1, "破阵斩",
                    "顺序 ZQE（横斩·左斩·右斩）后自动衔接：大范围横扫，不位移，靠范围压住身前的敌人。",
                    "持重剑按顺序出招；磨砺 400 自动点亮，不耗磨砺", true, "", temper(400)),
            entry("greatsword_combo_2", Weapon.AXE, "重剑连招", 2, "山崩",
                    "顺序 QQZ（左斩两次·横斩）后自动衔接：双手过顶重劈，伤害最高、前摇也最长。",
                    "持重剑按顺序出招；磨砺 1200 自动点亮，不耗磨砺", true, "", temper(1200)),
            entry("greatsword_combo_3", Weapon.AXE, "重剑连招", 3, "铁壁",
                    "顺序 EQZ（右斩·左斩·横斩）后自动衔接：架剑前顶，打断施法/连招并压出僵直，自身短时抗性；"
                            + "命中正在振刀/壁立/沉渊的人会打散对方的架势并压 2 秒僵直（破架势）。",
                    "持重剑按顺序出招；磨砺 2400 自动点亮，不耗磨砺", true, "", temper(2400)),
            entry("greatsword_combo_4", Weapon.AXE, "重剑连招", 4, "架势·壁立",
                    "顺序 QEE（左斩两次·右斩）后自动衔接：竖剑当门摆出架势，打断施法并顶开身前的敌人，"
                            + "自身获得短时抗性。放出后 1.8 秒内被攻击链/突击链打到会把对方架住："
                            + "这一下不掉血，对方连招作废并僵直 1 秒。",
                    "持重剑按顺序出招；磨砺 3000 自动点亮，不耗磨砺", true, "", temper(3000)),

            // ---------------- 战锤（挂重武器页签） ----------------
            entry("hammer_combo_1", Weapon.AXE, "锤连招", 1, "碎骨",
                    "顺序 QZZ（左斩·横斩两次）后自动衔接：过顶砸击，范围比剑宽但不位移。",
                    "持战锤按顺序出招；磨砺 500 自动点亮，不耗磨砺", true, "", temper(500)),
            entry("hammer_combo_2", Weapon.AXE, "锤连招", 2, "震地",
                    "顺序 EEZ（右斩两次·横斩）后自动衔接：砸地一圈震波，360 度击退。",
                    "持战锤按顺序出招；磨砺 1500 自动点亮，不耗磨砺", true, "", temper(1500)),
            entry("hammer_combo_3", Weapon.AXE, "锤连招", 3, "断招",
                    "顺序 ZQQ（横斩·左斩两次）后自动衔接：短距离震击，打断施法/连招并压出长僵直；"
                            + "命中正在振刀/壁立/沉渊的人会打散对方的架势并压 2 秒僵直（破架势）。",
                    "持战锤按顺序出招；磨砺 3000 自动点亮，不耗磨砺", true, "", temper(3000)),
            entry("hammer_combo_4", Weapon.AXE, "锤连招", 4, "架势·沉渊",
                    "顺序 QEQ（左斩·右斩·横斩）后自动衔接：锤横胸前沉腰成架势，打断施法并顶出僵直，"
                            + "自身获得短时抗性。放出后 2 秒内被攻击链/突击链打到会把对方架住："
                            + "这一下不掉血，对方连招作废并僵直 1 秒。",
                    "持战锤按顺序出招；磨砺 2600 自动点亮，不耗磨砺", true, "", temper(2600)),

            // ---------------- 长弓（挂弓页签） ----------------
            entry("bow_combo_1", Weapon.BOW, "弓连招", 1, "穿云箭",
                    "顺序 QZ（左斩·横斩）后自动衔接：消耗一支箭，射出高伤害箭矢；没有箭就放不出来。",
                    "持长弓按顺序出招；磨砺 300 自动点亮，不耗磨砺", true, "", temper(300)),
            entry("bow_combo_2", Weapon.BOW, "弓连招", 2, "疾影射",
                    "顺序 EQ（右斩·左斩）后自动衔接：向前突进半步并射出一箭。",
                    "持长弓按顺序出招；磨砺 1000 自动点亮，不耗磨砺", true, "", temper(1000)),
            entry("bow_combo_3", Weapon.BOW, "弓连招", 3, "后跃射",
                    "顺序 ZE（横斩·右斩）后自动衔接：短时无敌并向后跃开，落地前射出一箭（同样耗一支箭）。",
                    "持长弓按顺序出招；磨砺 2200 自动点亮，不耗磨砺", true, "", temper(2200)),

            // ---------------- 长柄 ----------------
            entry("polearm_thrust_1", Weapon.POLEARM, "枪术", 1, "突刺",
                    "距离比剑长一格，命中判定更窄。",
                    "尚未开课：需要先设计长柄武器分类", false, "", temper(1000)),
            entry("polearm_thrust_2", Weapon.POLEARM, "枪术", 2, "回马",
                    "命中后向后撤半步并保留朝向前方。",
                    "尚未开课", false, "", temper(3000)),
            entry("polearm_thrust_3", Weapon.POLEARM, "枪术", 3, "挑落",
                    "把骑乘/浮空的目标挑落，带冷却。",
                    "尚未开课", false, "", temper(10000), completions(5)),

            // ---------------- 弓 ----------------
            entry("bow_steady_1", Weapon.BOW, "弓术", 1, "定射",
                    "拉满后短时稳定准星，移动不再散开。",
                    "尚未开课：需要先设计远程武器判定", false, "", temper(1000)),
            entry("bow_steady_2", Weapon.BOW, "弓术", 2, "破甲箭",
                    "对护甲值高的目标额外削韧。",
                    "尚未开课", false, "", temper(3000)),
            entry("bow_steady_3", Weapon.BOW, "弓术", 3, "连珠",
                    "连续命中同一目标三次后，第四箭加速。",
                    "尚未开课", false, "", temper(10000), completions(5)),

            // ---------------- 风刃 ----------------
            entry("wind_blade_1", Weapon.WIND, "风刃", 1, "风刃",
                    "持风灌注武器潜行对空右键，打出一道短程直线风刃。",
                    "岚的专项课（三格外三次风刃命中）", true, "wind", temper(200)),
            entry("wind_blade_2", Weapon.WIND, "风刃", 2, "双股螺旋",
                    "风刃轨迹改成双股螺旋青色云轨，仍按墙体裁切与射程结算。",
                    "拿到风灌注武器即可看到", true, "wind", temper(200)),
            entry("wind_blade_3", Weapon.WIND, "风刃", 3, "风旋",
                    "风刃命中后在地面留下短暂旋流。",
                    "设计文档提案，尚未实装", false, "", temper(3000), completions(3)),
            entry("wind_infuse_1", Weapon.WIND, "元素灌注", 1, "风灌注",
                    "把单把剑或斧灌成风元素武器，能力记在武器上而不是角色上。",
                    "风元素灌注（目前唯一实装的灌注路线）", true, "", temper(200)),
            entry("wind_infuse_2", Weapon.WIND, "元素灌注", 2, "火灌注",
                    "重击积累灼热，达到阈值后释放。",
                    "设计文档提案，尚未实装", false, "", temper(3000)),
            entry("wind_infuse_3", Weapon.WIND, "元素灌注", 3, "土灌注",
                    "重击强化削韧，站稳时更难被击退。",
                    "设计文档提案，尚未实装", false, "", temper(3000), talent(3)),
            // ------------------------------------------------------------------
            //  2026-11-02 扩写：长剑/重剑/战锤/长弓各补两招，长枪（长柄页）开张
            // ------------------------------------------------------------------
            entry("sword_combo_5", Weapon.SWORD, "长剑连招", 5, "退步斩",
                    "顺序 ZQEZ：虚晃、压身、收势时向后撤半步再横斩回身，短时无敌并拉开距离。",
                    "持任意剑类按顺序出招；磨砺 1200 自动点亮，不耗磨砺", true, "", temper(1200)),
            entry("sword_combo_6", Weapon.SWORD, "长剑连招", 6, "燕返",
                    "顺序 EZZQ：右斩起手、两记横斩连压、左斩回身切，四段全中为长剑最高伤害。",
                    "持任意剑类按顺序出招；磨砺 2400 自动点亮，不耗磨砺", true, "", temper(2400)),
            entry("greatsword_combo_5", Weapon.AXE, "重剑连招", 5, "裂地",
                    "顺序 QZZQ：左斩提剑、两记横扫、最后一记砸落；伤害最高、前摇最长。",
                    "持重剑按顺序出招；磨砺 2600 自动点亮，不耗磨砺", true, "", temper(2600)),
            entry("greatsword_combo_6", Weapon.AXE, "重剑连招", 6, "冲阵",
                    "顺序 ZZQQ：重剑唯一带位移的招式，冲进人群再横扫，用来贴身。",
                    "持重剑按顺序出招；磨砺 1400 自动点亮，不耗磨砺", true, "", temper(1400)),
            entry("hammer_combo_5", Weapon.AXE, "锤连招", 5, "崩山",
                    "顺序 ZZZ：三记横砸连着抡下来，原地一圈全被震开，锤的最强一击。",
                    "持战锤按顺序出招；磨砺 2800 自动点亮，不耗磨砺", true, "", temper(2800)),
            entry("hammer_combo_6", Weapon.AXE, "锤连招", 6, "撞锤",
                    "顺序 QEEQ：左斩抢身、双右斩甩锤、左斩顶进，短距离突进砸盾。",
                    "持战锤按顺序出招；磨砺 1500 自动点亮，不耗磨砺", true, "", temper(1500)),
            entry("bow_combo_4", Weapon.BOW, "弓连招", 4, "裂空箭",
                    "顺序 ZQQ：横斩虚引、两次左斩把弓拉满再放，射出贯穿的裂空箭。",
                    "持弓按顺序出招；磨砺 2200 自动点亮，不耗磨砺", true, "", temper(2200)),
            entry("bow_combo_5", Weapon.BOW, "弓连招", 5, "追风射",
                    "顺序 QQQ：三次左斩错步引弓、最后一记放箭，向左滑半步追侧身的敌人。",
                    "持弓按顺序出招；磨砺 1200 自动点亮，不耗磨砺", true, "", temper(1200)),
            entry("bow_combo_6", Weapon.BOW, "弓连招", 6, "连珠箭",
                    "顺序 QEZ：拉弦、扣弦、放箭，三下手速越快箭越密。",
                    "持弓按顺序出招；磨砺 1600 自动点亮，不耗磨砺", true, "", temper(1600)),
            entry("spear_combo_1", Weapon.POLEARM, "长枪连招", 1, "破军刺",
                    "顺序 QQZ：双左斩压枪、横斩送出去，直线突刺；扇形窄但枪长、打得更远。",
                    "持长枪按顺序出招；磨砺 800 自动点亮，不耗磨砺", true, "", temper(800)),
            entry("spear_combo_2", Weapon.POLEARM, "长枪连招", 2, "龙牙突",
                    "顺序 QZQ：压枪起势后送枪，向前突进一大步，枪尖先到。",
                    "持长枪按顺序出招；磨砺 1400 自动点亮，不耗磨砺", true, "", temper(1400)),
            entry("spear_combo_3", Weapon.POLEARM, "长枪连招", 3, "回马枪",
                    "顺序 QQQE：三连压枪诱敌、右斩回身横扫，打断施法/连招并压出僵直。",
                    "持长枪按顺序出招；磨砺 2000 自动点亮，不耗磨砺", true, "", temper(2000)),
            entry("spear_combo_4", Weapon.POLEARM, "长枪连招", 4, "架势·横栏",
                    "顺序 QZE + 长按 Q：把枪横在身前架住攻击，两秒内正面攻击会被弹开。",
                    "持长枪按顺序出招，最后一下按住；磨砺 1600 自动点亮，不耗磨砺", true, "", temper(1600)),
            entry("spear_combo_5", Weapon.POLEARM, "长枪连招", 5, "游龙步",
                    "顺序 EZQ：点枪、借力、收枪向后滑步，短时无敌并拉开距离。",
                    "持长枪按顺序出招；磨砺 1000 自动点亮，不耗磨砺", true, "", temper(1000)));

    private static Entry entry(String id, Weapon weapon, String chain, int tier, String name, String effect,
                               String source, boolean implemented, String role, Clause... gates) {
        if (id.matches("(sword|greatsword|hammer|bow|spear)_combo_[1-6]")) {
            int slot=WarriorUnifiedSlots.slot(id);
            String originalEffect=effect;
            effect="槽位 "+(slot+1)+" · "+WarriorUnifiedSlots.purpose(slot)+" · "+WarriorUnifiedSlots.keys(slot).toUpperCase(java.util.Locale.ROOT)
                +(slot==1?"（末段长按）":"")+"。";
            if(slot==0)effect+="三次短按 Z／左键，原地收势主攻；QZE 仍可使用。长按鼠标右键蓄力，松手攻击，受击会中断。";
            if(slot==1)effect+="架住攻击；有效怪物近战触发横扫击飞。";
            if(slot==2)effect+="向前破开振刀，打断并僵直。";
            if(slot>=3)effect+=originalEffect.replaceAll("[QZE]{2,6}",WarriorUnifiedSlots.keys(slot).toUpperCase(java.util.Locale.ROOT));
            name=WarriorUnifiedSlots.name(id,name);
            tier=slot+1;
        }
        return new Entry(id, weapon, chain, tier, name, effect, source, implemented, List.of(gates), role);
    }

    private static Clause temper(int value) {
        return new Clause(Gate.TEMPER, value);
    }

    private static Clause talent(int value) {
        return new Clause(Gate.TALENT, value);
    }

    private static Clause completions(int value) {
        return new Clause(Gate.COMPLETIONS, value);
    }

    private static Clause bestScore(int value) {
        return new Clause(Gate.BEST_SCORE, value);
    }

    // ------------------------------------------------------------------
    //  查询
    // ------------------------------------------------------------------

    public static List<Entry> all() {
        return ENTRIES;
    }

    public static List<Entry> of(Weapon weapon) {
        List<Entry> out = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            if (entry.weapon() == weapon) {
                out.add(entry);
            }
        }
        return out;
    }

    /** 某条武器线里的链名，按目录里第一次出现的顺序（界面里从左到右）。 */
    public static List<String> chainsOf(Weapon weapon) {
        List<String> out = new ArrayList<>();
        for (Entry entry : of(weapon)) {
            if (!out.contains(entry.chain())) {
                out.add(entry.chain());
            }
        }
        return out;
    }

    /** 某条链下的条目，按 tier 升序（稳定：同 tier 保持目录顺序）。 */
    public static List<Entry> of(Weapon weapon, String chain) {
        List<Entry> out = new ArrayList<>();
        for (Entry entry : of(weapon)) {
            if (entry.chain().equals(chain)) {
                out.add(entry);
            }
        }
        out.sort((a, b) -> Integer.compare(a.tier(), b.tier()));
        return out;
    }

    public static Entry byId(String id) {
        for (Entry entry : ENTRIES) {
            if (entry.id().equals(id)) {
                return entry;
            }
        }
        return null;
    }

    /** 已满足条件的条目数（"点亮了几个"）。 */
    public static int metCount(Progress progress) {
        int count = 0;
        for (Entry entry : ENTRIES) {
            if (entry.state(progress) == State.MET) {
                count++;
            }
        }
        return count;
    }

    /** 已实装条目数（分母：规划中的不算，不然"点亮 5/27"会让人以为是没练够）。 */
    public static int implementedCount() {
        int count = 0;
        for (Entry entry : ENTRIES) {
            if (entry.implemented()) {
                count++;
            }
        }
        return count;
    }

    /** 某条武器线里已满足的条目数 / 已实装条目数。 */
    public static int[] metOfImplemented(Weapon weapon, Progress progress) {
        int met = 0;
        int total = 0;
        for (Entry entry : of(weapon)) {
            if (!entry.implemented()) {
                continue;
            }
            total++;
            if (entry.met(progress)) {
                met++;
            }
        }
        return new int[]{met, total};
    }
}
