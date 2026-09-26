-- Run once on databases that still have evaluation PDF storage from an older build.
-- Safe to run after the app no longer references pdf_report_path (see etasmi_schema.sql).
ALTER TABLE evaluation DROP COLUMN pdf_report_path;
