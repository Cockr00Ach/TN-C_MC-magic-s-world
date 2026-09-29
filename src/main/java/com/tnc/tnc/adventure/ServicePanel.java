package com.tnc.tnc.adventure;

/** A clicked worker selects one counter; the handbook only opens personal records. */
public enum ServicePanel {
    PROFILE("profile",0,"冒险者手册","成长、账目与城镇指引"),
    GUILD("guild",0,"冒险者协会 · 艾琳","登记与晋升"),
    WANDS("smith",2,"潮生制杖屋 · 莉娅","七系法杖 · 定制与提货"),
    BANK("broker",3,"归航银行 · 米洛","存取款与房屋购买"),
    ARMOR("armorer",4,"炉石铁匠铺 · 铎恩","基础装备 · 制作与提货"),
    ARCHIVE("archive",1,"酒馆委托 · 旧单归档","新委托请直接使用酒馆委托栏");

    private final String role,title,subtitle;
    private final int tab;
    ServicePanel(String role,int tab,String title,String subtitle){this.role=role;this.tab=tab;this.title=title;this.subtitle=subtitle;}
    public String role(){return role;}
    public int tab(){return tab;}
    public String title(){return title;}
    public String subtitle(){return subtitle;}
    public static ServicePanel fromRole(String role){
        for(var panel:values())if(panel.role.equals(role))return panel;
        return PROFILE;
    }
}
