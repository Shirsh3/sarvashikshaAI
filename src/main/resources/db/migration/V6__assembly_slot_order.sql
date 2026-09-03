-- Playback order for assembly videos (comma-separated keys: anthem,pledge,prayer,hindi)
ALTER TABLE assembly_config ADD COLUMN IF NOT EXISTS assembly_slot_order VARCHAR(200);
