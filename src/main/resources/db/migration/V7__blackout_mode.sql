-- P2：黑灯模式（比赛级开关，开启后隐藏榜单/解题动态，仅个人可见自身得分；管理员/裁判不受限）

ALTER TABLE events ADD COLUMN blackout_mode BOOLEAN NOT NULL DEFAULT FALSE;
