CREATE TYPE extraction_status AS ENUM (
    'INIT',
    'PENDING',
    'DONE',
    'FAILED',
    'NO_IMPLEMENTATION',
    'RE_EVALUATE'
);

ALTER TABLE dl_extraction_run
    ALTER COLUMN status DROP DEFAULT;

ALTER TABLE dl_extraction_run
    ALTER COLUMN status TYPE extraction_status
    USING status::extraction_status;

ALTER TABLE dl_extraction_run
    ALTER COLUMN status SET DEFAULT 'INIT'::extraction_status;
