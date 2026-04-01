-- Playback order for assembly videos (comma-separated keys: anthem,pledge,prayer,hindi)
ALTER TABLE assembly_config ADD COLUMN assembly_slot_order VARCHAR(200);
