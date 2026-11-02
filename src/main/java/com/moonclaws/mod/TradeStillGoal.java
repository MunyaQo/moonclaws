package com.moonclaws.mod;

import java.util.EnumSet;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;

/** While a player trades with the cat, it stays where it is and looks at the player. */
public class TradeStillGoal extends Goal {

    private final MobEntity cat;
    private final CatMerchant merchant;

    public TradeStillGoal(MobEntity cat, CatMerchant merchant) {
        this.cat = cat;
        this.merchant = merchant;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return merchant.isTrading();
    }

    @Override
    public void start() {
        cat.getNavigation().stop();
    }

    @Override
    public void tick() {
        cat.getNavigation().stop();
        PlayerEntity p = merchant.getTradingPlayer();
        if (p != null) {
            cat.getLookControl().setLookAt(p, 30.0F, 30.0F);
        }
    }
}
