package com.intenthealer.core.engine.trust;

import com.intenthealer.core.model.ActionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TrustLevelManager")
class TrustLevelManagerTest {

    private TrustLevelManager manager;

    @BeforeEach
    void setUp() {
        manager = TrustLevelManager.builder()
                .initialLevel(TrustLevel.L1_MANUAL)
                .successesToPromote(3)
                .failuresToDemote(2)
                .failureWindow(Duration.ofHours(1))
                .autoPromote(true)
                .autoDemote(true)
                .build();
    }

    @Test
    @DisplayName("should start at initial trust level")
    void startsAtInitialLevel() {
        assertEquals(TrustLevel.L1_MANUAL, manager.getCurrentLevel());
    }

    @Test
    @DisplayName("should promote after consecutive successes")
    void promotesAfterSuccesses() {
        manager.recordSuccess();
        manager.recordSuccess();
        assertEquals(TrustLevel.L1_MANUAL, manager.getCurrentLevel());

        manager.recordSuccess(); // Third success triggers promotion
        assertEquals(TrustLevel.L2_SAFE, manager.getCurrentLevel());
    }

    @Test
    @DisplayName("should demote after failures in window")
    void demotesAfterFailures() {
        manager.recordFailure();
        assertEquals(TrustLevel.L1_MANUAL, manager.getCurrentLevel());

        manager.recordFailure(); // Second failure triggers demotion
        assertEquals(TrustLevel.L0_SHADOW, manager.getCurrentLevel());
    }

    @Test
    @DisplayName("should reset consecutive successes on failure")
    void resetsSuccessesOnFailure() {
        manager.recordSuccess();
        manager.recordSuccess();

        manager.recordFailure();

        manager.recordSuccess(); // Only 1 success, not promoted
        assertEquals(TrustLevel.L1_MANUAL, manager.getCurrentLevel());
    }

    @Test
    @DisplayName("should allow manual level setting")
    void allowsManualLevelSetting() {
        manager.setLevel(TrustLevel.L3_AUTO);
        assertEquals(TrustLevel.L3_AUTO, manager.getCurrentLevel());
    }

    @Test
    @DisplayName("should respect max level constraint")
    void respectsMaxLevel() {
        TrustLevelManager constrainedManager = TrustLevelManager.builder()
                .initialLevel(TrustLevel.L2_SAFE)
                .maxLevel(TrustLevel.L2_SAFE)
                .successesToPromote(1)
                .build();

        constrainedManager.recordSuccess();
        assertEquals(TrustLevel.L2_SAFE, constrainedManager.getCurrentLevel());
    }

    @Test
    @DisplayName("should respect min level constraint")
    void respectsMinLevel() {
        TrustLevelManager constrainedManager = TrustLevelManager.builder()
                .initialLevel(TrustLevel.L2_SAFE)
                .minLevel(TrustLevel.L1_MANUAL)
                .failuresToDemote(1)
                .build();

        constrainedManager.recordFailure();
        constrainedManager.recordFailure();
        assertEquals(TrustLevel.L1_MANUAL, constrainedManager.getCurrentLevel());
    }

    @Test
    @DisplayName("refusals should not affect trust level")
    void refusalsDoNotAffectTrust() {
        manager.recordSuccess();
        manager.recordSuccess();

        manager.recordRefusal();
        manager.recordRefusal();

        manager.recordSuccess(); // Should still count as third consecutive
        assertEquals(TrustLevel.L2_SAFE, manager.getCurrentLevel());
    }

    @Test
    @DisplayName("should check auto-apply permissions correctly")
    void checksAutoApplyPermissions() {
        // L1_MANUAL should not auto-apply
        assertFalse(manager.canAutoApply(ActionType.CLICK));

        manager.setLevel(TrustLevel.L2_SAFE);
        // L2_SAFE should auto-apply safe actions
        assertTrue(manager.canAutoApply(ActionType.CLICK));
        assertTrue(manager.canAutoApply(ActionType.TYPE));
        assertFalse(manager.canAutoApply(ActionType.SUBMIT));

        manager.setLevel(TrustLevel.L3_AUTO);
        // L3_AUTO should auto-apply all
        assertTrue(manager.canAutoApply(ActionType.CLICK));
        assertTrue(manager.canAutoApply(ActionType.SUBMIT));
    }

    @Test
    @DisplayName("should provide statistics")
    void providesStatistics() {
        manager.recordSuccess();
        manager.recordSuccess();

        TrustLevelManager.TrustStats stats = manager.getStats();

        assertEquals(TrustLevel.L1_MANUAL, stats.currentLevel());
        assertEquals(2, stats.consecutiveSuccesses());
        assertEquals(1, stats.successesUntilPromotion());
    }
}

@DisplayName("TrustLevel")
class TrustLevelTest {

    @Test
    @DisplayName("should have correct promotion chain")
    void hasCorrectPromotionChain() {
        assertEquals(TrustLevel.L1_MANUAL, TrustLevel.L0_SHADOW.promote());
        assertEquals(TrustLevel.L2_SAFE, TrustLevel.L1_MANUAL.promote());
        assertEquals(TrustLevel.L3_AUTO, TrustLevel.L2_SAFE.promote());
        assertEquals(TrustLevel.L4_SILENT, TrustLevel.L3_AUTO.promote());
        assertEquals(TrustLevel.L4_SILENT, TrustLevel.L4_SILENT.promote());
    }

    @Test
    @DisplayName("should have correct demotion chain")
    void hasCorrectDemotionChain() {
        assertEquals(TrustLevel.L3_AUTO, TrustLevel.L4_SILENT.demote());
        assertEquals(TrustLevel.L2_SAFE, TrustLevel.L3_AUTO.demote());
        assertEquals(TrustLevel.L1_MANUAL, TrustLevel.L2_SAFE.demote());
        assertEquals(TrustLevel.L0_SHADOW, TrustLevel.L1_MANUAL.demote());
        assertEquals(TrustLevel.L0_SHADOW, TrustLevel.L0_SHADOW.demote());
    }

    @Test
    @DisplayName("should parse from string correctly")
    void parsesFromString() {
        assertEquals(TrustLevel.L0_SHADOW, TrustLevel.fromString("L0_SHADOW"));
        assertEquals(TrustLevel.L1_MANUAL, TrustLevel.fromString("Manual"));
        assertEquals(TrustLevel.L2_SAFE, TrustLevel.fromString("2"));
        assertEquals(TrustLevel.L0_SHADOW, TrustLevel.fromString("invalid"));
        assertEquals(TrustLevel.L0_SHADOW, TrustLevel.fromString(null));
    }

    @Test
    @DisplayName("should have correct auto-apply permissions")
    void hasCorrectAutoApplyPermissions() {
        assertFalse(TrustLevel.L0_SHADOW.canAutoApply());
        assertFalse(TrustLevel.L1_MANUAL.canAutoApply());
        assertTrue(TrustLevel.L2_SAFE.canAutoApply());
        assertTrue(TrustLevel.L3_AUTO.canAutoApply());
        assertTrue(TrustLevel.L4_SILENT.canAutoApply());
    }

    @Test
    @DisplayName("should have correct approval requirements")
    void hasCorrectApprovalRequirements() {
        assertFalse(TrustLevel.L0_SHADOW.requiresApproval());
        assertTrue(TrustLevel.L1_MANUAL.requiresApproval());
        assertTrue(TrustLevel.L2_SAFE.requiresApproval());
        assertFalse(TrustLevel.L3_AUTO.requiresApproval());
        assertFalse(TrustLevel.L4_SILENT.requiresApproval());
    }
}
