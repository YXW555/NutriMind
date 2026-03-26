CREATE TABLE IF NOT EXISTS user_account (
  id BIGINT NOT NULL PRIMARY KEY,
  username VARCHAR(64) NOT NULL,
  password VARCHAR(255) NOT NULL,
  nickname VARCHAR(64),
  email VARCHAR(128),
  phone VARCHAR(32),
  role VARCHAR(32) DEFAULT 'USER',
  status TINYINT DEFAULT 1,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  last_login_at TIMESTAMP,
  CONSTRAINT uk_user_username UNIQUE (username),
  CONSTRAINT uk_user_email UNIQUE (email),
  CONSTRAINT uk_user_phone UNIQUE (phone)
);
