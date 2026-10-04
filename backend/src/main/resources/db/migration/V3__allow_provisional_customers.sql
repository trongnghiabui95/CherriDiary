-- Permit provisional customers captured from a comment before contact details are known.
-- Remove only the original contact-presence check, retaining all other checks/data.
DO $$
DECLARE contact_check RECORD;
BEGIN
    FOR contact_check IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'customers'::regclass AND contype = 'c'
        AND upper(regexp_replace(pg_get_constraintdef(oid), '[()[:space:]]', '', 'g')) =
            'CHECKPHONE_NUMBERISNOTNULLORTIKTOK_IDISNOTNULLORFACEBOOK_IDISNOTNULL'
    LOOP
        EXECUTE format('ALTER TABLE customers DROP CONSTRAINT %I', contact_check.conname);
    END LOOP;
END $$;

-- Drafts with no items have no stock reservation. Preserve reservation checks
-- for confirmed/shipping/completed orders and release checks for cancellation.
DO $$
DECLARE reservation_check RECORD;
BEGIN
    FOR reservation_check IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'orders'::regclass AND contype = 'c'
        AND pg_get_constraintdef(oid) LIKE '%stock_reserved%'
        AND pg_get_constraintdef(oid) LIKE '%CANCELLED%'
    LOOP
        EXECUTE format('ALTER TABLE orders DROP CONSTRAINT %I', reservation_check.conname);
    END LOOP;
END $$;
ALTER TABLE orders ADD CONSTRAINT orders_stock_reservation_check CHECK (
    (status = 'CANCELLED' AND NOT stock_reserved)
    OR status = 'DRAFT'
    OR (status NOT IN ('CANCELLED', 'DRAFT') AND stock_reserved)
);
