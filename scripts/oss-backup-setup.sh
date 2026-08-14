#!/bin/bash
# ============================================================
# 数据库备份定时上传阿里云 OSS —— 一次性安装配置脚本（幂等）
#
# 功能：
#   1. 安装 ossutil（阿里云官方命令行工具，单二进制）
#   2. 写入 ossutil 配置文件（~/.ossutilconfig，权限 600）
#   3. 注册两条 crontab 定时任务（幂等，重复执行不产生重复行）：
#      - 每月 1 号 3:05 同步全量备份目录  /data/backup              -> db-backup/full/
#      - 每天 3:10 同步增量 binlog 目录   /data/db_backup/mysql_binlog -> db-backup/binlog/
#   4. 首次手动同步验证（可选，--dry-run 先行）
#
# 用法（在 Linux 服务器项目根目录、以 root 执行——脚本可放任意目录，内部全部使用绝对路径）：
#   export OSS_AK_ID="你的AccessKeyId"
#   export OSS_AK_SECRET="你的AccessKeySecret"
#   sudo -E bash oss-backup-setup.sh
#
# 说明：
#   - 密钥只从环境变量读取，脚本内不留明文；config 文件 chmod 600
#   - sync 用 --update：仅上传本地比远端新的文件（增量语义）
#   - 日志追加到 /var/log/oss-backup.log
#   - 幂等：重复执行不会重复安装、不会重复写 crontab 行
# ============================================================
set -euo pipefail

# ---------- 配置区（可按需修改） ----------
BUCKET="on-site-tpmdata"
ENDPOINT="oss-cn-shenzhen.aliyuncs.com"
FULL_DIR="/data/backup"
BINLOG_DIR="/data/db_backup/mysql_binlog"
OSS_PREFIX="db-backup"          # OSS 侧根前缀
LOG_FILE="/var/log/oss-backup.log"
CRON_FILE="/etc/crontab"
# 全量备份每月 1 号 3:00 生成，上传定 3:05 错开；binlog 每天 3:10
FULL_CRON="5 3 1 * *"
BINLOG_CRON="10 3 * * *"
# -------------------------------------------

if [[ -z "${OSS_AK_ID:-}" || -z "${OSS_AK_SECRET:-}" ]]; then
    echo "错误：请先设置环境变量 OSS_AK_ID 和 OSS_AK_SECRET" >&2
    exit 1
fi

# ---------- 1. 安装 ossutil（幂等） ----------
if ! command -v ossutil >/dev/null 2>&1; then
    echo "[1/4] 安装 ossutil ..."
    # 官方安装脚本需要 unzip/7z 解压工具
    if ! command -v unzip >/dev/null 2>&1 && ! command -v 7z >/dev/null 2>&1; then
        echo "   检测到缺少 unzip，尝试安装 ..."
        if command -v apt-get >/dev/null 2>&1; then
            apt-get update -qq && apt-get install -y -qq unzip
        elif command -v yum >/dev/null 2>&1; then
            yum install -y -q unzip
        else
            echo "错误：未找到包管理器，请手动安装 unzip 后重试" >&2
            exit 1
        fi
    fi
    curl -s https://gosspublic.alicdn.com/ossutil/install.sh | bash
    command -v ossutil >/dev/null 2>&1 || { echo "错误：ossutil 安装失败" >&2; exit 1; }
else
    echo "[1/4] ossutil 已安装，跳过"
fi

# ---------- 2. 写入 ossutil 配置（权限 600） ----------
CONFIG_FILE="$HOME/.ossutilconfig"
if [[ -f "$CONFIG_FILE" ]] && grep -q "accessKeyID" "$CONFIG_FILE"; then
    echo "[2/4] ossutil 配置已存在，跳过（如需更新请手动编辑 $CONFIG_FILE）"
else
    echo "[2/4] 写入 ossutil 配置 $CONFIG_FILE ..."
    # ossutil 1.x 配置文件格式：Language/Endpoint/AccessKeyID/AccessKeySecret
    cat > "$CONFIG_FILE" <<EOF
[Credentials]
language=CH
endpoint=$ENDPOINT
accessKeyID=$OSS_AK_ID
accessKeySecret=$OSS_AK_SECRET
EOF
    chmod 600 "$CONFIG_FILE"
    echo "   配置已写入（权限 600）"
fi

# ---------- 3. 注册 crontab 定时任务（幂等：grep 防重复） ----------
echo "[3/4] 检查/注册 crontab 定时任务 ..."
BACKUP_MARKER="# oss-backup-to-oss"
if grep -qF "ossutil sync $FULL_DIR" "$CRON_FILE" 2>/dev/null; then
    echo "   全量同步任务已存在，跳过"
else
    echo "$BACKUP_MARKER full-backup" >> "$CRON_FILE"
    echo "$FULL_CRON root ossutil sync $FULL_DIR oss://$BUCKET/$OSS_PREFIX/full/ --update >> $LOG_FILE 2>&1" >> "$CRON_FILE"
    echo "   已添加全量同步任务（$FULL_CRON）"
fi
if grep -qF "ossutil sync $BINLOG_DIR" "$CRON_FILE" 2>/dev/null; then
    echo "   binlog 同步任务已存在，跳过"
else
    echo "$BACKUP_MARKER binlog-backup" >> "$CRON_FILE"
    echo "$BINLOG_CRON root ossutil sync $BINLOG_DIR oss://$BUCKET/$OSS_PREFIX/binlog/ --update >> $LOG_FILE 2>&1" >> "$CRON_FILE"
    echo "   已添加 binlog 同步任务（$BINLOG_CRON）"
fi

# ---------- 4. 目录存在性检查 + 首次同步验证 ----------
echo "[4/4] 目录检查与首次同步 ..."
for d in "$FULL_DIR" "$BINLOG_DIR"; do
    if [[ ! -d "$d" ]]; then
        echo "警告：目录 $d 不存在（crontab 任务已注册，目录创建后会自动生效）" >&2
    fi
done

echo ""
echo "安装配置完成。建议先跑 dry-run 验证："
echo "  ossutil sync $FULL_DIR oss://$BUCKET/$OSS_PREFIX/full/ --update --dry-run"
echo "确认无误后手动执行首次同步："
echo "  ossutil sync $FULL_DIR oss://$BUCKET/$OSS_PREFIX/full/ --update"
echo "  ossutil sync $BINLOG_DIR oss://$BUCKET/$OSS_PREFIX/binlog/ --update"
echo "查看结果：ossutil ls oss://$BUCKET/$OSS_PREFIX/"
echo "日志：tail -f $LOG_FILE"
