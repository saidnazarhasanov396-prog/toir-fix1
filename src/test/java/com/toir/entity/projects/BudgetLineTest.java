package com.toir.entity.projects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BudgetLineTest {

    @Test
    void getRemainingAmount_shouldReturnPositive_whenCommittedLessThanPlanned() {
        BudgetLine line = new BudgetLine();
        line.setPlannedAmount(100000.0);
        line.setCommittedAmount(30000.0);
        
        double remaining = line.getRemainingAmount();
        
        assertEquals(70000.0, remaining, 0.001);
    }

    @Test
    void getRemainingAmount_shouldReturnZero_whenCommittedEqualsPlanned() {
        BudgetLine line = new BudgetLine();
        line.setPlannedAmount(100000.0);
        line.setCommittedAmount(100000.0);
        
        double remaining = line.getRemainingAmount();
        
        assertEquals(0.0, remaining, 0.001);
    }

    @Test
    void getRemainingAmount_shouldReturnZero_whenCommittedExceedsPlanned() {
        BudgetLine line = new BudgetLine();
        line.setPlannedAmount(100000.0);
        line.setCommittedAmount(300000.0);
        
        double remaining = line.getRemainingAmount();
        
        assertEquals(0.0, remaining, 0.001);
        assertTrue(remaining >= 0, "Remaining should never be negative");
    }

    @Test
    void getAvailableForActual_shouldReturnPositive_whenActualLessThanPlannedMinusCommitted() {
        BudgetLine line = new BudgetLine();
        line.setPlannedAmount(100000.0);
        line.setCommittedAmount(30000.0);
        line.setActualAmount(20000.0);
        
        double available = line.getAvailableForActual();
        
        assertEquals(50000.0, available, 0.001);
    }

    @Test
    void getAvailableForActual_shouldReturnZero_whenActualEqualsPlannedMinusCommitted() {
        BudgetLine line = new BudgetLine();
        line.setPlannedAmount(100000.0);
        line.setCommittedAmount(30000.0);
        line.setActualAmount(70000.0);
        
        double available = line.getAvailableForActual();
        
        assertEquals(0.0, available, 0.001);
    }

    @Test
    void getAvailableForActual_shouldReturnZero_whenActualExceedsPlannedMinusCommitted() {
        BudgetLine line = new BudgetLine();
        line.setPlannedAmount(100000.0);
        line.setCommittedAmount(30000.0);
        line.setActualAmount(80000.0);
        
        double available = line.getAvailableForActual();
        
        assertEquals(0.0, available, 0.001);
        assertTrue(available >= 0, "Available for actual should never be negative");
    }

    @Test
    void getAvailableForCommitment_shouldReturnPositive_whenCommittedLessThanPlanned() {
        BudgetLine line = new BudgetLine();
        line.setPlannedAmount(100000.0);
        line.setCommittedAmount(30000.0);
        
        double available = line.getAvailableForCommitment();
        
        assertEquals(70000.0, available, 0.001);
    }

    @Test
    void getAvailableForCommitment_shouldReturnZero_whenCommittedEqualsPlanned() {
        BudgetLine line = new BudgetLine();
        line.setPlannedAmount(100000.0);
        line.setCommittedAmount(100000.0);
        
        double available = line.getAvailableForCommitment();
        
        assertEquals(0.0, available, 0.001);
    }

    @Test
    void getAvailableForCommitment_shouldReturnZero_whenCommittedExceedsPlanned() {
        BudgetLine line = new BudgetLine();
        line.setPlannedAmount(100000.0);
        line.setCommittedAmount(300000.0);
        
        double available = line.getAvailableForCommitment();
        
        assertEquals(0.0, available, 0.001);
        assertTrue(available >= 0, "Available for commitment should never be negative");
    }

    @Test
    void getRemainingAmount_shouldHandleZeroValues() {
        BudgetLine line = new BudgetLine();
        line.setPlannedAmount(0.0);
        line.setCommittedAmount(0.0);
        
        double remaining = line.getRemainingAmount();
        
        assertEquals(0.0, remaining, 0.001);
    }

    @Test
    void getAvailableForActual_shouldHandleZeroValues() {
        BudgetLine line = new BudgetLine();
        line.setPlannedAmount(0.0);
        line.setCommittedAmount(0.0);
        line.setActualAmount(0.0);
        
        double available = line.getAvailableForActual();
        
        assertEquals(0.0, available, 0.001);
    }

    @Test
    void getAvailableForCommitment_shouldHandleZeroValues() {
        BudgetLine line = new BudgetLine();
        line.setPlannedAmount(0.0);
        line.setCommittedAmount(0.0);
        
        double available = line.getAvailableForCommitment();
        
        assertEquals(0.0, available, 0.001);
    }
}
