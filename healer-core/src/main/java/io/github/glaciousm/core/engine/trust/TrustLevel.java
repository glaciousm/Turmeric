package io.github.glaciousm.core.engine.trust;

/**
 * Trust levels for progressive healing autonomy.
 *
 * L0 → L4 represents increasing autonomy, from fully manual to fully autonomous.
 */
public enum TrustLevel {

    /**
     * L0: Shadow mode - suggest heals but never apply.
     * Use case: Initial evaluation of the system.
     */
    L0_SHADOW(0, "Shadow", false, false, false),

    /**
     * L1: Manual approval - require human confirmation for each heal.
     * Use case: Learning phase with human oversight.
     */
    L1_MANUAL(1, "Manual", false, true, false),

    /**
     * L2: Safe healing - auto-apply safe heals (click, type), require approval for others.
     * Use case: Gaining confidence in the system.
     */
    L2_SAFE(2, "Safe", true, true, false),

    /**
     * L3: Auto healing - auto-apply all heals, log and notify.
     * Use case: Trusted system with monitoring.
     */
    L3_AUTO(3, "Auto", true, false, true),

    /**
     * L4: Silent healing - fully autonomous, minimal logging.
     * Use case: Production-hardened, highly trusted system.
     */
    L4_SILENT(4, "Silent", true, false, true);

    private final int level;
    private final String displayName;
    private final boolean canAutoApply;
    private final boolean requiresApproval;
    private final boolean canAutoCommit;

    TrustLevel(int level, String displayName, boolean canAutoApply, boolean requiresApproval, boolean canAutoCommit) {
        this.level = level;
        this.displayName = displayName;
        this.canAutoApply = canAutoApply;
        this.requiresApproval = requiresApproval;
        this.canAutoCommit = canAutoCommit;
    }

    /**
     * Get the numeric level.
     */
    public int getLevel() {
        return level;
    }

    /**
     * Get the display name.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Check if heals can be auto-applied at this level.
     */
    public boolean canAutoApply() {
        return canAutoApply;
    }

    /**
     * Check if heals require manual approval at this level.
     */
    public boolean requiresApproval() {
        return requiresApproval;
    }

    /**
     * Check if heals can be auto-committed to source at this level.
     */
    public boolean canAutoCommit() {
        return canAutoCommit;
    }

    /**
     * Check if a specific action type can be auto-applied at this level.
     */
    public boolean canAutoApplyAction(String actionType) {
        if (!canAutoApply) {
            return false;
        }

        if (this == L2_SAFE) {
            // L2 only auto-applies safe actions
            return isSafeAction(actionType);
        }

        // L3 and L4 auto-apply all actions
        return true;
    }

    /**
     * Check if an action type is considered safe for auto-apply.
     */
    private boolean isSafeAction(String actionType) {
        if (actionType == null) {
            return false;
        }
        String action = actionType.toUpperCase();
        return action.equals("CLICK") ||
               action.equals("SEND_KEYS") ||
               action.equals("TYPE") ||
               action.equals("CLEAR") ||
               action.equals("GET_TEXT") ||
               action.equals("GET_ATTRIBUTE") ||
               action.equals("IS_DISPLAYED") ||
               action.equals("IS_ENABLED");
    }

    /**
     * Get the next higher trust level.
     */
    public TrustLevel promote() {
        return switch (this) {
            case L0_SHADOW -> L1_MANUAL;
            case L1_MANUAL -> L2_SAFE;
            case L2_SAFE -> L3_AUTO;
            case L3_AUTO, L4_SILENT -> L4_SILENT;
        };
    }

    /**
     * Get the next lower trust level.
     */
    public TrustLevel demote() {
        return switch (this) {
            case L0_SHADOW, L1_MANUAL -> L0_SHADOW;
            case L2_SAFE -> L1_MANUAL;
            case L3_AUTO -> L2_SAFE;
            case L4_SILENT -> L3_AUTO;
        };
    }

    /**
     * Get trust level from numeric value.
     */
    public static TrustLevel fromLevel(int level) {
        for (TrustLevel tl : values()) {
            if (tl.level == level) {
                return tl;
            }
        }
        return L0_SHADOW;
    }

    /**
     * Get trust level from string (name or display name).
     */
    public static TrustLevel fromString(String str) {
        if (str == null) {
            return L0_SHADOW;
        }

        String upper = str.toUpperCase().trim();

        // Try enum name first
        for (TrustLevel tl : values()) {
            if (tl.name().equals(upper) || tl.name().replace("_", "").equals(upper)) {
                return tl;
            }
        }

        // Try display name
        for (TrustLevel tl : values()) {
            if (tl.displayName.equalsIgnoreCase(str)) {
                return tl;
            }
        }

        // Try numeric
        try {
            int level = Integer.parseInt(str);
            return fromLevel(level);
        } catch (NumberFormatException e) {
            // Ignore
        }

        return L0_SHADOW;
    }
}
