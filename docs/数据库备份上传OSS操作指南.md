# 数据库备份定时上传阿里云 OSS —— 服务器操作指南

> 适用：Linux 服务器（root 权限）。目的：把服务器上的 MySQL 备份（每月全量 .sql + 每日增量 binlog）每天自动同步到阿里云 OSS，实现异地容灾。

---

## 一、背景与原理

| 项目 | 说明 |
|------|------|
| 服务器上的全量备份 | `/data/backup/`，每月 1 号 3:00 由服务器定时任务生成的 `.sql` 文件 |
| 服务器上的增量备份 | `/data/db_backup/mysql_binlog/`，MySQL binlog 实时日志，自动保留 90 天 |
| 上传目标 | OSS Bucket `on-site-tpmdata`（深圳节点 `oss-cn-shenzhen.aliyuncs.com`） |
| 上传工具 | 阿里云官方命令行工具 **ossutil**（单二进制，无需 Python/Java 环境） |
| 定时触发 | Linux `crontab`（写进 `/etc/crontab`，每天自动执行） |
| 增量逻辑 | `ossutil sync --update` 只上传"本地比远端新"的文件，重复执行不会重复传已传过的文件 |

**最终效果**：每天凌晨自动把当天新增/变更的备份文件传到 OSS，服务器磁盘坏了也能从 OSS 恢复备份。

```
服务器 /data/backup（全量 .sql）     ──每天 3:05──►  OSS: db-backup/full/
服务器 /data/db_backup/mysql_binlog ──每天 3:10──►  OSS: db-backup/binlog/
```

---

## 二、需要准备的东西

1. **阿里云 AccessKey 一对**（已在用或新创建均可）：
   - AccessKey ID（形如 `LTAI5t6HM5...`）
   - AccessKey Secret
   - 要求：该密钥对 `on-site-tpmdata` 这个 bucket 有上传权限（`oss:PutObject`）。已实测当前密钥可用。
2. **服务器能访问外网**（安装 ossutil 需要下载，上传 OSS 需要网络）。

---

## 三、详细操作步骤（每步说明作用）

### 第 0 步：把脚本放到服务器

把仓库里的 `scripts/oss-backup-setup.sh` 传到服务器**项目根目录**（例如 `/root/Table-Management-System/oss-backup-setup.sh`，即与项目代码同目录），并确认两个备份目录存在：

```bash
ls -d /data/backup /data/db_backup/mysql_binlog
```

**作用**：确认脚本能找到备份目录。脚本第 4 步会再次检查，但提前确认可以避免定时任务注册后才发现目录路径不对。脚本内部全部使用绝对路径，放在项目根目录或任意位置均可运行。

### 第 1 步：设置密钥环境变量

```bash
export OSS_AK_ID="你的AccessKeyId"
export OSS_AK_SECRET="你的AccessKeySecret"
```

**作用**：脚本从环境变量读取密钥（而不是把密钥明文写死在脚本里，防止脚本文件泄露密钥）。**注意**：这两条命令只在当前终端会话有效，关闭终端后失效——脚本只在运行那一刻需要它们。

### 第 2 步：运行安装配置脚本

```bash
sudo -E bash oss-backup-setup.sh
```

> 如果 `sudo -E` 提示环境变量仍丢失，改用：
> `sudo env OSS_AK_ID="你的AccessKeyId" OSS_AK_SECRET="你的AccessKeySecret" bash oss-backup-setup.sh`
>
> 注：命令在**项目根目录**（脚本所在目录）执行；脚本放任意位置都可运行（内部全绝对路径），不在当前目录时把 `oss-backup-setup.sh` 换成实际路径即可。

**作用**：脚本自动完成以下 4 件事（每一步都会打印 `[1/4]`~`[4/4]` 进度）：
1. **安装 ossutil**：下载阿里云官方安装包到 `/usr/bin/ossutil`（若已安装则跳过）。
2. **写入 ossutil 配置**：生成 `~/.ossutilconfig` 文件，写入 endpoint、bucket、密钥，并把文件权限设为 `600`（只有 root 能读，保护密钥）。
3. **注册两条定时任务**：追加到 `/etc/crontab`：
   - `5 3 1 * *`（每月 1 号 3:05）→ 同步全量目录 `/data/backup` 到 `oss://on-site-tpmdata/db-backup/full/`
   - `10 3 * * *`（每天 3:10）→ 同步增量目录 `/data/db_backup/mysql_binlog` 到 `oss://on-site-tpmdata/db-backup/binlog/`
   - 两条任务的时间故意错开 5 分钟，避免同时上传互相抢带宽。
4. **检查备份目录**：目录不存在只警告、不中断（任务已注册，目录创建后自动生效）。

### 第 3 步：手动验证同步（重要，首次必须做）

> 注：ossutil 1.x **没有 dry-run 选项**（v1.7.19 帮助文本无 `--dry-run`，传了会打印用法并退出）。`--update` 本身只上传"本地比远端新"的文件，重复执行不会重复传已传过的文件，直接执行即安全。

真正执行首次同步（首次是全量传输，之后每天只传新增的；`-c` 显式指定配置文件，避免 sudo/root 环境读不到默认路径）：

```bash
ossutil sync /data/backup oss://on-site-tpmdata/db-backup/full/ --update --loglevel=info -c /home/hyjm/.ossutilconfig
ossutil sync /data/db_backup/mysql_binlog oss://on-site-tpmdata/db-backup/binlog/ --update --loglevel=info -c /home/hyjm/.ossutilconfig
```

`--loglevel=info` 会在终端显示上传进度；去掉它则静默执行（crontab 定时任务里的版本不带该参数，输出进日志文件）。若 binlog 文件属主是 mysql/dnsmasq 且权限 640，普通用户读不了——定时任务以 root 运行不受影响，手动同步用 `sudo`（记得带 `-c`）。

### 第 4 步：核对上传结果

```bash
ossutil ls oss://on-site-tpmdata/db-backup/
```

**作用**：列出 OSS 上 `db-backup/` 前缀下的对象，应能看到 `full/` 和 `binlog/` 两个目录。也可以在阿里云 OSS 控制台 → `on-site-tpmdata` → `db-backup/` 下查看文件，核对文件名和大小与服务器一致。

查看同步日志：

```bash
tail -f /var/log/oss-backup.log
```

**作用**：每次定时同步的输出都会追加到这个日志文件。确认没有 ERROR 行。

### 第 5 步：确认定时任务已注册

```bash
grep -n "ossutil sync" /etc/crontab
```

**作用**：应看到两条以 `5 3 1 * *` 和 `10 3 * * *` 开头的任务行。以后想改时间/目录，直接编辑 `/etc/crontab` 对应行即可。

---

## 四、日常维护

| 场景 | 操作 |
|------|------|
| 看同步是否正常 | `tail -f /var/log/oss-backup.log`（每天凌晨 3:05/3:10 后应有新记录） |
| 手动立即同步一次 | 重新执行第 3 步的两条 `ossutil sync` 命令 |
| 修改同步时间 | 编辑 `/etc/crontab` 中对应行的前 5 个字段（分 时 日 月 周） |
| 换密钥 | 重新编辑 `~/.ossutilconfig`（chmod 600 保持） |
| 验证备份可恢复 | 用 `ossutil cp oss://on-site-tpmdata/db-backup/full/xxx.sql ./` 下载一个文件测试 |
| 想清理 OSS 上的旧 binlog | 在 OSS 控制台给 `db-backup/binlog/` 前缀配置生命周期规则（如 90 天自动删除） |

---

## 五、常见问题

**Q1：脚本报"请先设置环境变量 OSS_AK_ID 和 OSS_AK_SECRET"**
A：`sudo` 默认会清空环境变量。用 `sudo -E bash ...` 或 `sudo env OSS_AK_ID=... OSS_AK_SECRET=... bash ...` 方式运行。

**Q2：ossutil sync 报 AccessDenied**
A：密钥对 bucket 没有写权限。到阿里云 RAM 控制台给该密钥所属用户添加 `oss:PutObject`（针对 `acs:oss:*:*:on-site-tpmdata/*`）。

**Q3：服务器没有 unzip 命令**
A：脚本会自动尝试安装（`apt install unzip` 或 `yum install unzip`）。若包管理器也不可用，手动装 unzip 后重跑脚本（脚本幂等，重跑安全）。

**Q4：脚本重跑会不会产生重复定时任务？**
A：不会。脚本用 grep 检查 `/etc/crontab` 里是否已有相同命令，已存在就跳过（幂等）。

**Q5：OSS 上的 binlog 会一直累积吗？**
A：是的，OSS 侧默认不自动清理（服务器侧 90 天保留只影响服务器本地）。如需 OSS 侧也按 90 天清理，在 OSS 控制台给 `db-backup/binlog/` 配生命周期规则。
