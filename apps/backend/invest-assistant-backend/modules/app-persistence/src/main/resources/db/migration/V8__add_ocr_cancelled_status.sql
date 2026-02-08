DO $$
DECLARE
  constraint_name text;
BEGIN
  SELECT conname INTO constraint_name
  FROM pg_constraint
  WHERE conrelid = 'app.ocr_jobs'::regclass
    AND contype = 'c'
    AND pg_get_constraintdef(oid) LIKE '%status%IN%';
  IF constraint_name IS NOT NULL THEN
    EXECUTE format('ALTER TABLE app.ocr_jobs DROP CONSTRAINT %I', constraint_name);
  END IF;
END $$;

ALTER TABLE app.ocr_jobs
  ADD CONSTRAINT ocr_jobs_status_check
  CHECK (status IN ('QUEUED','RUNNING','FAILED','DONE','CANCELLED'));

-- Job status: QUEUED, RUNNING, FAILED, DONE, CANCELLED
COMMENT ON COLUMN app.ocr_jobs.status IS 'Job status (QUEUED/RUNNING/FAILED/DONE/CANCELLED)';