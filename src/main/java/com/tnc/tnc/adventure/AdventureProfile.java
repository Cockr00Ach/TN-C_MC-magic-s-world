package com.tnc.tnc.adventure;

import java.util.*;

/** Server-owned permanent account. Serialization is isolated in AdventureSavedData. */
public final class AdventureProfile {
    long xp, coins, completedEpoch = -1, smithReady = -1;
    int reputation, rank, learningBase = 4;
    boolean registered, crafted, smithFree;
    boolean armorCrafted;
    public long divineReadyAt;
    public final BankAccount bank=new BankAccount();
    String smithDesign="";
    int nativeBounties;
    int adventureKills,bossKills;
    final Set<String> milestones = new LinkedHashSet<>();
    final Set<String> paidOffers = new LinkedHashSet<>();
    final Map<String, ContractProgress> contracts = new LinkedHashMap<>();
    final Deque<String> ledger = new ArrayDeque<>();

    public int level() { return AdventureRules.level(xp); }
    public long xp() { return xp; }
    public long coins() { return coins; }
    public int reputation() { return reputation; }
    public boolean registered() { return registered; }
    public boolean hasMilestone(String id) { return milestones.contains(id); }
    public int learningPoints() { return learningBase + level() / 10 * 5; }
    public void addXp(long amount) {
        if (amount > 0) xp = Math.min(AdventureRules.xpAtLevel(100), xp + Math.min(amount, AdventureRules.xpAtLevel(100)));
    }
    public boolean debit(long amount, String reason) {
        if (amount <= 0 || amount > coins) return false;
        coins -= amount; record("−" + amount + "铜 · " + reason); return true;
    }
    public boolean canCredit(long amount) { return amount >= 0 && amount <= AdventureRules.MAX_COINS - coins; }
    public boolean credit(long amount, String reason) {
        if (!canCredit(amount)) return false;
        coins += amount; record("+" + amount + "铜 · " + reason);if(amount>0&&!reason.equals("银行取款"))bank.payOneDue(this);return true;
    }
    void record(String line) { ledger.addFirst(line); while (ledger.size() > 5) ledger.removeLast(); }
    void epoch(long epoch) {
        if (completedEpoch != epoch) { completedEpoch = epoch; paidOffers.clear(); }
    }
    public boolean accept(ContractCatalog.Contract c, long epoch) {
        epoch(epoch);
        int higher = (int) contracts.values().stream().filter(p -> ContractCatalog.find(p.id).grade() > rank).count();
        if (!registered || contracts.containsKey(c.id()) || paidOffers.contains(c.id())
                || (c.teaching() && hasMilestone("teaching_paid"))
                || !AdventureRules.canAccept(rank, c.grade(), contracts.size(), higher)) return false;
        contracts.put(c.id(), new ContractProgress(c.id(), epoch, 0));
        milestones.add("accepted"); return true;
    }
    public static final class ContractProgress {
        final String id;
        final long epoch;
        int kills;
        ContractProgress(String id, long epoch, int kills) { this.id = id; this.epoch = epoch; this.kills = kills; }
    }
}
