package com.tnc.tnc.magic.thunderseal;
public final class ThunderSealRules {
 public static int tier(int t){return Math.max(1,Math.min(5,t));}
 public static int count(int t){return new int[]{0,1,2,3,6,9}[tier(t)];}
 public static double radius(int t){return new double[]{0,2.5,4,6,9,13}[tier(t)];}
 public static int charge(int t){return 22+tier(t)*3;}
 public static int pulses(int t){return tier(t);}
 public static int finish(int t){return charge(t)+12*pulses(t);}
 public static int lifetime(int t){return finish(t)+24;}
 public static double height(int t){return 2.8+tier(t)*.9;}

 /**
  * 第 {@code slot} 个雷楔的<b>天雷落地 tick</b> ✓ —— 一楔一道雷，楔与楔之间错开 2 tick。
  *
  * <p>★ 2026-11-07 作者："那个雷锲就不要从地底冒出来吧，这样，一个天雷砸地，
  * 然后汇聚出一个雷锲如何" / "六个就是六道" ⇒ 雷数**等于**楔数
  * （t4 六个楔 = 六道雷、t5 九个楔 = 九道雷），不是写死"五道"✗。
  */
 public static double strikeTick(int slot){return 2+Math.max(0,slot)*2;}

 /**
  * 天雷落地后"余波"的窗口（tick）：落点白闪 ＋ 雷环向外炸。
  *
  * <p>★ 2026-11-08 作者："雷锲的出现直接闪出就行，你不要雷劈了之后还从地里长出来"
  * ⇒ 楔子本体<b>落地即满尺寸</b>（不缩放，见 {@code ThunderSealRenderer} §5 ⑤），
  * 这 6 tick 只有"炸开"的余波、没有"收拢/长大"✗。
  * 时序上仍要求 {@code strikeTick(最后一道) + birthTicks() ≤ charge}，
  * 保证所有楔子在第一下伤害脉冲之前就已经出现在那儿了 ✓。
  */
 public static double birthTicks(){return 6;}
 private ThunderSealRules(){}
}
