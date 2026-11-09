package com.huiyi.assistant.quota;

/**
 * 用户等级的每日 Token 上限，以及一次通过提额增加的数量。
 *
 * @author huiyi
 */
public final class UserTierLimits {

    /** 当天有一条已通过的提额时，加到等级上限上的 Token 数。 */
    public static final int BOOST_TOKENS = 500_000;

    private UserTierLimits() {
    }

    /**
     * 等级原值。1 为 50 万，2 为 150 万，3 为 500 万。
     *
     * @param tier 用户等级
     * @return 每日 Token 上限
     */
    public static int dailyLimit(int tier) {
        return switch (tier) {
            case 1 -> 500_000;
            case 2 -> 1_500_000;
            case 3 -> 5_000_000;
            default -> throw new IllegalArgumentException("未知等级: " + tier);
        };
    }
}
