-- Two customers on purpose: one with a tight contract, one with a generous one, so the
-- same incident can be approved for one and rejected for the other. That is the demo.
INSERT INTO contract (customer_id, priority, response_minutes) VALUES
    ('acme',     'CRITICAL', 30),
    ('acme',     'HIGH',     240),
    ('acme',     'NORMAL',   1440),
    ('globex',   'CRITICAL', 15),
    ('globex',   'HIGH',     60),
    ('globex',   'NORMAL',   480);
