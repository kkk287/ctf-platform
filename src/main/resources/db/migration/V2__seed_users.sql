-- 演示账号种子数据（密码均为 BCrypt 哈希，明文只写在 README 中）
-- admin   / Admin@123   管理员
-- alice   / Alice@123   普通用户（0x00sec 队长）
-- bob     / Bob@123     普通用户（0x00sec 成员）
-- carol   / Carol@123   普通用户（Byte Raiders 队长）

INSERT INTO app_user (id, username, password, display_name, role, banned, created_at) VALUES
('u-admin', 'admin', '$2b$10$FGRlc7HROtNZ5O/lvY2hOe2WS7rA5swvbO4kGBcpzoEZx5FHZqTxy', '平台管理员', 'ADMIN', FALSE, CURRENT_TIMESTAMP),
('u-alice', 'alice', '$2b$10$QIFrvjcEhi3dNq/FhdhkNuOqHTgdyiLD3GFrCC5BKs/.QyfYlcI72', 'Alice', 'USER', FALSE, CURRENT_TIMESTAMP),
('u-bob', 'bob', '$2b$10$CFpjMnKi3F/GfM4Oqdp9Ju4YgUeL.b5n2a5wd3mXMQ5yHXdLV98Ba', 'Bob', 'USER', FALSE, CURRENT_TIMESTAMP),
('u-carol', 'carol', '$2b$10$kVLKoCmwV/H/n/vzlH.PuO5/9iQrDYNLDVwKHBjcNficBzhcHUjMS', 'Carol', 'USER', FALSE, CURRENT_TIMESTAMP);
