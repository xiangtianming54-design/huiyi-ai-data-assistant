-- =====================================================================
-- HuiYi AI Data Assistant 示例业务数据
-- 20 商品 / 8 地区 / 4 渠道 / 600 订单（2026-07-15 ~ 2026-10-14，跨 3 个月）
-- 订单状态含 PAID/SHIPPED/COMPLETED/CREATED/REFUNDED/CANCELLED
-- 造数规则确定性（取模分布），可重复执行（先清后插）
-- =====================================================================

USE huiyi_demo;

DELETE FROM sales_orders;
DELETE FROM products;
DELETE FROM regions;
DELETE FROM channels;

-- ===== 4 渠道 =====
INSERT INTO channels (id, channel_code, channel_name) VALUES
(1, 'ONLINE', '线上商城'),
(2, 'LIVE', '直播带货'),
(3, 'OFFLINE', '线下门店'),
(4, 'AGENT', '分销代理');

-- ===== 8 地区 =====
INSERT INTO regions (id, region_code, region_name) VALUES
(1, 'HD', '华东'),
(2, 'HB', '华北'),
(3, 'HN', '华南'),
(4, 'HZ', '华中'),
(5, 'XN', '西南'),
(6, 'XB', '西北'),
(7, 'DB', '东北'),
(8, 'GAT', '港澳台');

-- ===== 20 商品 =====
INSERT INTO products (id, product_code, product_name, category, price, status) VALUES
(1,  'P0001', '无线蓝牙耳机 Pro',     '电子产品', 299.00, 'ACTIVE'),
(2,  'P0002', '智能手表 S2',          '电子产品', 899.00, 'ACTIVE'),
(3,  'P0003', '机械键盘 87 键',       '电子产品', 399.00, 'ACTIVE'),
(4,  'P0004', '4K 便携显示器',        '电子产品', 1299.00, 'ACTIVE'),
(5,  'P0005', '升降办公桌 1.4m',      '办公用品', 1499.00, 'ACTIVE'),
(6,  'P0006', '人体工学椅',           '办公用品', 1099.00, 'ACTIVE'),
(7,  'P0007', '护眼台灯',             '办公用品', 169.00, 'ACTIVE'),
(8,  'P0008', '文件收纳柜',           '办公用品', 329.00, 'ACTIVE'),
(9,  'P0009', '乳胶床垫 1.8m',        '家居生活', 2999.00, 'ACTIVE'),
(10, 'P0010', '记忆棉枕头',           '家居生活', 199.00, 'ACTIVE'),
(11, 'P0011', '智能扫地机器人',       '家居生活', 1899.00, 'ACTIVE'),
(12, 'P0012', '香薰加湿器',           '家居生活', 139.00, 'ACTIVE'),
(13, 'P0013', '冷萃咖啡液 12 条',     '食品饮料', 69.00, 'ACTIVE'),
(14, 'P0014', '坚果每日装 30 包',     '食品饮料', 129.00, 'ACTIVE'),
(15, 'P0015', '低糖燕麦奶 6 瓶',      '食品饮料', 59.00, 'ACTIVE'),
(16, 'P0016', '气泡水 24 罐',         '食品饮料', 89.00, 'ACTIVE'),
(17, 'P0017', '轻薄羽绒服',           '服装配饰', 599.00, 'ACTIVE'),
(18, 'P0018', '商务双肩包',           '服装配饰', 349.00, 'ACTIVE'),
(19, 'P0019', '真丝围巾',             '服装配饰', 269.00, 'ACTIVE'),
(20, 'P0020', '防紫外线太阳镜',       '服装配饰', 219.00, 'ACTIVE');

-- ===== 600 订单（确定性取模分布，维度间已去相关） =====
-- order_date: 2026-07-15 起，MOD(n*37, 92) 天偏移 → 覆盖 2026-07-15 ~ 2026-10-14
-- region 用 FLOOR(n/4) 分段、channel 用 MOD(n,4) 循环，避免取模序列相关导致维度塌缩
-- 状态: 75% PAID，少量 SHIPPED/COMPLETED/CREATED/REFUNDED/CANCELLED
INSERT INTO sales_orders
  (order_no, order_date, product_id, region_id, channel_id, quantity, unit_price, total_amount, status)
WITH RECURSIVE seq AS (
  SELECT 1 AS n
  UNION ALL
  SELECT n + 1 FROM seq WHERE n < 600
)
SELECT
  CONCAT('SO', LPAD(n, 6, '0'))                              AS order_no,
  DATE_ADD('2026-07-15', INTERVAL MOD(n * 37, 92) DAY)       AS order_date,
  p.id                                                       AS product_id,
  MOD(FLOOR((n - 1) / 4), 8) + 1                             AS region_id,
  MOD(n - 1, 4) + 1                                          AS channel_id,
  MOD((n - 1) * 5, 10) + 1                                   AS quantity,
  p.price                                                    AS unit_price,
  ROUND(p.price * (MOD((n - 1) * 5, 10) + 1), 2)             AS total_amount,
  CASE MOD(n, 20)
    WHEN 0 THEN 'REFUNDED'
    WHEN 1 THEN 'CANCELLED'
    WHEN 2 THEN 'SHIPPED'
    WHEN 3 THEN 'CREATED'
    WHEN 4 THEN 'COMPLETED'
    ELSE 'PAID'
  END                                                        AS status
FROM seq
JOIN products p ON p.id = MOD(n * 13, 20) + 1;
