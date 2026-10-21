-- Who is going, kept here rather than looked up.
--
-- The name arrives on the event that says an hour was held, and this service has no way to
-- ask the scheduling service for it later: that would be a synchronous call between two
-- services that are deliberately not coupled. So the fact travels with the event and is
-- stored where it is displayed.
ALTER TABLE incident ADD COLUMN technician_name text;
