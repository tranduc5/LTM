-- Reference schema. ChatDatabase.java initializes the equivalent schema automatically.
CREATE TABLE IF NOT EXISTS users (
    username TEXT PRIMARY KEY COLLATE NOCASE,
    salt TEXT NOT NULL,
    hash TEXT NOT NULL,
    created_at TEXT DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS rooms (
    name TEXT PRIMARY KEY COLLATE NOCASE,
    created_at TEXT DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    sender TEXT NOT NULL,
    room TEXT,
    receiver TEXT,
    body TEXT NOT NULL,
    kind TEXT NOT NULL DEFAULT 'TEXT',
    created_at TEXT DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS attachments (
    id TEXT PRIMARY KEY,
    message_id INTEGER NOT NULL,
    original_name TEXT NOT NULL,
    media_type TEXT NOT NULL,
    stored_name TEXT NOT NULL,
    size INTEGER NOT NULL,
    FOREIGN KEY(message_id) REFERENCES messages(id)
);
CREATE INDEX IF NOT EXISTS idx_room_history ON messages(room,id);
CREATE INDEX IF NOT EXISTS idx_pm_history ON messages(sender,receiver,id);
INSERT OR IGNORE INTO rooms(name) VALUES('lobby');
