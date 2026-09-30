package com.tnc.tnc.adventure;

import java.math.BigInteger;
import java.util.List;
import net.minecraft.nbt.CompoundTag;

/** Personal bank ledger. Real-time income and online-only debts have separate clocks. */
public final class BankAccount {
    public static final long DAY=86_400_000L, ROUND=48_000L, INTEREST_CAP=10_000L;
    private static final long DENOMINATOR=DAY*100;
    public long savings,lastAt,interestNumerator,nextInterestPay,accruedInterest,pendingInterest,pendingSalary;
    public long nextSalaryPay,onlineTicks;
    public int salaryRate,queuedSalaryRate;
    public Debt loan=new Debt(),mortgage=new Debt();
    public static int salary(int level,List<? extends Integer> rates){return rates.get(level>=70?4:level>=45?3:level>=25?2:level>=10?1:0);}
    public long interestPrincipal(){return Math.min(INTEREST_CAP,Math.max(0,savings-loan.remainingPrincipal()));}
    public boolean arrears(){return loan.overdue(onlineTicks)||mortgage.overdue(onlineTicks);}
    private static long add(long a,long b){return a>Long.MAX_VALUE-b?Long.MAX_VALUE:a+b;}
    public void settle(long now,boolean registered,int wage,int percent){
        now=Math.max(1,now);
        if(lastAt==0){lastAt=now;nextInterestPay=now+DAY;}
        now=Math.max(now,lastAt); // Backward clock changes cannot repeat a settlement.
        long elapsed=now-lastAt;
        var accrual=BigInteger.valueOf(interestPrincipal()).multiply(BigInteger.valueOf(elapsed)).multiply(BigInteger.valueOf(Math.max(0,percent))).add(BigInteger.valueOf(interestNumerator));
        var split=accrual.divideAndRemainder(BigInteger.valueOf(DENOMINATOR));
        accruedInterest=add(accruedInterest,split[0].min(BigInteger.valueOf(Long.MAX_VALUE)).longValue());interestNumerator=split[1].longValue();lastAt=now;
        if(nextInterestPay==0)nextInterestPay=now+DAY;
        if(registered){
            if(nextSalaryPay==0){nextSalaryPay=now+DAY;salaryRate=queuedSalaryRate=wage;}
            if(now>=nextSalaryPay){
                long count=(now-nextSalaryPay)/DAY+1;
                pendingSalary=add(pendingSalary,add(salaryRate,Math.multiplyExact(count-1,(long)queuedSalaryRate)));
                nextSalaryPay+=count*DAY;salaryRate=queuedSalaryRate;
            }
            queuedSalaryRate=wage;
        }
    }
    public void flush(AdventureProfile p,long now){
        // Interest pays on the daily boundary; accrued units stay safe through mid-day withdrawals.
        if(now>=nextInterestPay){pendingInterest=add(pendingInterest,accruedInterest);accruedInterest=0;nextInterestPay+=((now-nextInterestPay)/DAY+1)*DAY;}
        payInterest(p);
        long n=Math.min(pendingSalary,AdventureRules.MAX_COINS-p.coins());
        if(n>0){pendingSalary-=n;p.credit(n,"协会每日工资");}
    }
    private void payInterest(AdventureProfile p){
        long n=Math.min(pendingInterest,AdventureRules.MAX_COINS-p.coins());
        if(n>0){pendingInterest-=n;p.credit(n,"归航银行每日利息");}
    }
    public boolean deposit(AdventureProfile p,long n){
        if(n<=0||n>p.coins()||n>AdventureRules.MAX_COINS-savings)return false;
        if(!p.debit(n,"存入归航银行"))return false;savings+=n;return true;
    }
    public boolean withdraw(AdventureProfile p,long n){
        if(n<=0||n>savings||!p.canCredit(n))return false;
        savings-=n;p.credit(n,"银行取款");return true;
    }
    public boolean borrow(AdventureProfile p,int n){
        int level=n==200?5:n==500?10:n==1000?20:Integer.MAX_VALUE;
        if(!p.registered()||p.level()<level||loan.active()||arrears()||!p.canCredit(n))return false;
        p.coins+=n;p.record("+"+n+"铜 · 银行借款（非收入）");
        loan=Debt.create(n,n*11L/10,n==200?4:n==500?5:10,onlineTicks);return true;
    }
    public boolean canMortgage(AdventureProfile p){return p.registered()&&p.level()>=10&&!arrears()&&!mortgage.active();}
    public void startMortgage(long principal){mortgage=Debt.create(principal,principal*11/10,10,onlineTicks);}
    private boolean deduct(AdventureProfile p,long n){return spend(p,n,"银行自动还款");}
    public boolean spend(AdventureProfile p,long n,String reason){
        if(n<=0||p.coins()+savings<n)return false;
        long cash=Math.min(n,p.coins());if(cash>0)p.debit(cash,reason);savings-=n-cash;
        if(n>cash)p.record("存款 −"+(n-cash)+"铜 · "+reason);return true;
    }
    public boolean payOneDue(AdventureProfile p){
        for(var d:List.of(mortgage,loan))if(d.overdue(onlineTicks)&&deduct(p,d.nextAmount())){d.paid++;return true;}return false;
    }
    public void online(AdventureProfile p,long ticks){
        onlineTicks+=Math.max(0,ticks);
        for(var d:List.of(mortgage,loan)){
            int due=d.dueCount(onlineTicks);
            if(due>d.reviewed){d.reviewed=due;if(d.overdue(onlineTicks)&&deduct(p,d.nextAmount()))d.paid++;}
        }
    }
    public boolean clear(AdventureProfile p,boolean house){
        var d=house?mortgage:loan;if(!d.active())return false;
        long amount=d.clearAmount(onlineTicks);if(!deduct(p,amount))return false;d.paid=d.parts;return true;
    }
    public CompoundTag save(){
        var t=new CompoundTag();t.putLong("Savings",savings);t.putLong("LastAt",lastAt);t.putLong("InterestNumerator",interestNumerator);t.putLong("NextInterestPay",nextInterestPay);t.putLong("PendingInterest",pendingInterest);t.putLong("AccruedInterest",accruedInterest);t.putLong("PendingSalary",pendingSalary);t.putLong("NextSalaryPay",nextSalaryPay);t.putInt("SalaryRate",salaryRate);t.putInt("QueuedSalaryRate",queuedSalaryRate);t.putLong("OnlineTicks",onlineTicks);t.put("Loan",loan.save());t.put("Mortgage",mortgage.save());return t;
    }
    public void load(CompoundTag t){
        savings=Math.max(0,Math.min(AdventureRules.MAX_COINS,t.getLong("Savings")));lastAt=Math.max(0,t.getLong("LastAt"));interestNumerator=Math.floorMod(t.getLong("InterestNumerator"),DENOMINATOR);nextInterestPay=Math.max(0,t.getLong("NextInterestPay"));pendingInterest=Math.max(0,t.getLong("PendingInterest"));accruedInterest=Math.max(0,t.getLong("AccruedInterest"));pendingSalary=Math.max(0,t.getLong("PendingSalary"));nextSalaryPay=Math.max(0,t.getLong("NextSalaryPay"));salaryRate=Math.max(0,t.getInt("SalaryRate"));queuedSalaryRate=Math.max(0,t.getInt("QueuedSalaryRate"));onlineTicks=Math.max(0,t.getLong("OnlineTicks"));loan=Debt.load(t.getCompound("Loan"));mortgage=Debt.load(t.getCompound("Mortgage"));
    }
    public static final class Debt {
        public long principal,total,start;public int parts,paid,reviewed;
        public static Debt create(long p,long total,int parts,long now){var d=new Debt();d.principal=p;d.total=total;d.parts=parts;d.start=now;return d;}
        public boolean active(){return parts>0&&paid<parts;}
        public long remainingPrincipal(){return !active()?0:principal-principal*paid/parts;}
        public long remaining(){return !active()?0:total-total*paid/parts;}
        public long nextAmount(){return !active()?0:total*(paid+1)/parts-total*paid/parts;}
        public int dueCount(long ticks){return !active()?0:(int)Math.min(parts,Math.max(0,ticks-start)/ROUND);}
        public boolean overdue(long ticks){return active()&&dueCount(ticks)>paid;}
        public long ticksUntilNext(long ticks){return !active()?0:Math.max(0,start+(paid+1)*ROUND-ticks);}
        public long clearAmount(long ticks){if(!active())return 0;int due=Math.max(paid,dueCount(ticks));return remainingPrincipal()+(total-principal)*due/parts-(total-principal)*paid/parts;}
        CompoundTag save(){var t=new CompoundTag();t.putLong("Principal",principal);t.putLong("Total",total);t.putLong("Start",start);t.putInt("Parts",parts);t.putInt("Paid",paid);t.putInt("Reviewed",reviewed);return t;}
        static Debt load(CompoundTag t){var d=new Debt();d.principal=Math.max(0,Math.min(AdventureRules.MAX_COINS,t.getLong("Principal")));d.total=Math.max(d.principal,Math.min(AdventureRules.MAX_COINS,t.getLong("Total")));d.start=Math.max(0,t.getLong("Start"));d.parts=Math.max(0,Math.min(10,t.getInt("Parts")));d.paid=Math.max(0,Math.min(d.parts,t.getInt("Paid")));d.reviewed=Math.max(0,Math.min(d.parts,t.getInt("Reviewed")));return d;}
    }
}
