ALTER TABLE users ADD COLUMN username TEXT;
UPDATE users SET username = display_name WHERE username IS NULL;
CREATE UNIQUE INDEX idx_users_username ON users (username);
