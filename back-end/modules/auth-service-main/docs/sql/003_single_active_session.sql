-- Apply to an existing auth_db before deploying single-session authentication.
-- Existing tokens have no sid and will require a fresh login after deployment.
BEGIN;
ALTER TABLE users ADD COLUMN IF NOT EXISTS active_session_id UUID;
COMMIT;
