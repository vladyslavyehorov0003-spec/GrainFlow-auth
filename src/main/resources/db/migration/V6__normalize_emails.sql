-- Backfill: lowercase + trim all stored emails to match new DTO normalization.
-- Before this, register/login stored emails as typed, so DB may contain mixed-case
-- rows that the new normalized lookups can't find.

-- Guard against case-only duplicates (e.g. "Foo@bar.com" + "foo@bar.com" both exist).
-- The UNIQUE constraint on users.email would reject the UPDATE silently otherwise.
-- Resolve such conflicts manually before running this migration.
DO $$
DECLARE
    duplicate_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO duplicate_count
    FROM (
        SELECT LOWER(TRIM(email))
        FROM users
        GROUP BY LOWER(TRIM(email))
        HAVING COUNT(*) > 1
    ) AS dups;

    IF duplicate_count > 0 THEN
        RAISE EXCEPTION
            'Cannot normalize emails: % case-only duplicate group(s) in users table. Resolve manually first.',
            duplicate_count;
    END IF;
END $$;

UPDATE users
SET email = LOWER(TRIM(email))
WHERE email <> LOWER(TRIM(email));

UPDATE email_change_codes
SET new_email = LOWER(TRIM(new_email))
WHERE new_email <> LOWER(TRIM(new_email));
