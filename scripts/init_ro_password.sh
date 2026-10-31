#!/usr/bin/env bash
# 空数据卷首次初始化时，把 huiyi_ro 的口令改成 RO_DB_PASSWORD。
# 口令为空则保持 init_mysql.sql 中的 huiyi_ro123，并以 0 退出。
set -euo pipefail

ro_alter_sql() {
  local password="${RO_DB_PASSWORD-}"
  if [[ -z "$password" ]]; then
    return 0
  fi
  if [[ "$password" == *$'\n'* || "$password" == *$'\r'* ]]; then
    echo "RO_DB_PASSWORD contains a line break; refusing to build ALTER USER" >&2
    return 1
  fi
  # MySQL treats \ as an escape inside a quoted literal. Double it first,
  # then turn a single quote into '', so both survive the server parser.
  local escaped="${password//\\/\\\\}"
  escaped="${escaped//"'"/"''"}"
  printf "ALTER USER 'huiyi_ro'@'%%' IDENTIFIED BY '%s';\n" "$escaped"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  sql="$(ro_alter_sql)"
  if [[ -z "$sql" ]]; then
    exit 0
  fi
  mysql --user=root --password="$MYSQL_ROOT_PASSWORD" --execute="$sql"
fi
