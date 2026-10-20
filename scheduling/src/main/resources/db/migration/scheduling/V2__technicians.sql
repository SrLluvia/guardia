-- The field force. Slots are not seeded here: timestamps written into a migration go stale
-- the day after, so the rolling agenda is kept by SlotHorizon at startup instead.
INSERT INTO technician (id, name, skill) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Ane Mendizabal',  'NETWORK'),
    ('22222222-2222-2222-2222-222222222222', 'Bruno Carvalho',  'NETWORK'),
    ('33333333-3333-3333-3333-333333333333', 'Carla Otxoa',     'DATABASE'),
    ('44444444-4444-4444-4444-444444444444', 'Diego Ferreiro',  'HARDWARE');
