-- Fix budget lines where committed_amount > planned_amount (overshoot)
-- This migration safely corrects existing data to prevent negative remaining amounts

-- Step 1: Log overshoot cases before fixing
DO $$
DECLARE
    overshot_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO overshot_count
    FROM budget_lines
    WHERE is_deleted = false
      AND committed_amount > planned_amount;
    
    IF overshot_count > 0 THEN
        RAISE NOTICE 'Found % budget lines with committed_amount > planned_amount. Fixing...', overshot_count;
    END IF;
END $$;

-- Step 2: Fix overshoot by setting committed_amount = planned_amount
UPDATE budget_lines
SET committed_amount = planned_amount,
    updated_at = NOW()
WHERE is_deleted = false
  AND committed_amount > planned_amount;

-- Step 3: Ensure NULL committed_amount values are set to 0.0
UPDATE budget_lines
SET committed_amount = 0.0,
    updated_at = NOW()
WHERE is_deleted = false
  AND committed_amount IS NULL;

-- Step 4: Verification - ensure no overshoot remains
DO $$
DECLARE
    remaining_overshot INTEGER;
BEGIN
    SELECT COUNT(*) INTO remaining_overshot
    FROM budget_lines
    WHERE is_deleted = false
      AND committed_amount > planned_amount;
    
    IF remaining_overshot > 0 THEN
        RAISE EXCEPTION 'Migration failed: Still have % budget lines with overshoot', remaining_overshot;
    END IF;
    
    RAISE NOTICE 'Migration successful: No budget lines with overshoot remaining';
END $$;
