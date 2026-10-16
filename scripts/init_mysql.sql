-- =====================================================================
-- HuiYi AI Data Assistant 数据库初始化脚本
-- 挂载到 MySQL 容器 /docker-entrypoint-initdb.d/，仅首次启动空数据卷时执行
-- 数据模型与《开发文档》第 6 节一致；示例业务数据由 P5 阶段 init_sample_data.sql 提供
-- =====================================================================

USE huiyi_demo;

-- Text-to-SQL 执行专用只读账号（四道 SQL 防线之一：数据库层只读约束）
CREATE USER IF NOT EXISTS 'huiyi_ro'@'%' IDENTIFIED BY 'huiyi_ro123';
GRANT SELECT ON huiyi_demo.* TO 'huiyi_ro'@'%';
FLUSH PRIVILEGES;

-- 6.1 商品表
CREATE TABLE IF NOT EXISTS products (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_code VARCHAR(64) NOT NULL UNIQUE,
  product_name VARCHAR(128) NOT NULL,
  category VARCHAR(64),
  price DECIMAL(12,2),
  status VARCHAR(32) DEFAULT 'ACTIVE',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品表';

-- 6.2 地区表
CREATE TABLE IF NOT EXISTS regions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  region_code VARCHAR(32) NOT NULL UNIQUE,
  region_name VARCHAR(64) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '地区表';

-- 6.3 渠道表
CREATE TABLE IF NOT EXISTS channels (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  channel_code VARCHAR(32) NOT NULL UNIQUE,
  channel_name VARCHAR(64) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '渠道表';

-- 6.4 销售订单表（状态：CREATED/PAID/SHIPPED/COMPLETED/REFUNDED/CANCELLED）
CREATE TABLE IF NOT EXISTS sales_orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(64) NOT NULL UNIQUE,
  order_date DATE NOT NULL,
  product_id BIGINT NOT NULL,
  region_id BIGINT NOT NULL,
  channel_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  unit_price DECIMAL(12,2) NOT NULL,
  total_amount DECIMAL(14,2) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_order_date (order_date),
  INDEX idx_product (product_id),
  INDEX idx_region (region_id),
  INDEX idx_channel (channel_id),
  INDEX idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '销售订单表';

-- 6.5 知识库文档元数据表（状态：UPLOADED/PARSED/READY/FAILED）
CREATE TABLE IF NOT EXISTS knowledge_docs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  doc_name VARCHAR(256) NOT NULL,
  file_type VARCHAR(32),
  file_size BIGINT,
  chunk_count INT DEFAULT 0,
  status VARCHAR(32) DEFAULT 'UPLOADED',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '知识库文档表';

-- 6.6 会话消息表
CREATE TABLE IF NOT EXISTS chat_messages (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id VARCHAR(64) NOT NULL,
  role VARCHAR(32) NOT NULL,
  content TEXT,
  intent VARCHAR(32),
  sql_text TEXT,
  metadata_json TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_session (session_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '会话消息表';

-- 6.7 会话摘要表（超过 10 轮后压缩更早对话）
CREATE TABLE IF NOT EXISTS chat_session_summaries (
  session_id VARCHAR(64) PRIMARY KEY,
  summary TEXT NOT NULL,
  covered_until_id BIGINT NOT NULL,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '会话历史摘要';

