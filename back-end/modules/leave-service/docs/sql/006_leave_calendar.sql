-- New requests preserve the working calendar used to calculate their charged units.
CREATE TABLE IF NOT EXISTS leave_calendar_snapshots (
    request_id UUID PRIMARY KEY REFERENCES leave_requests(id) ON DELETE CASCADE,
    calendar_json TEXT NOT NULL
);
