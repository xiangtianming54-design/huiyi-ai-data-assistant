ALTER TABLE assistant_run_steps
  ADD COLUMN tool_name VARCHAR(128) NULL COMMENT '工具名；固定步骤为空' AFTER step_type;
