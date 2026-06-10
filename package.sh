#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_NAME="transflow-ai"
JAR_NAME="transflow-1.0.0.jar"
VERSION="1.0.0"
OUTPUT_NAME="${APP_NAME}-${VERSION}.zip"

echo "========================================="
echo "  TRANSFLOW 灵流 - 打包脚本"
echo "========================================="
echo ""

# -------- 环境检查 --------
echo "[1/4] 检查环境..."

if [ -z "$JAVA_HOME" ]; then
    if command -v java &> /dev/null; then
        JAVA_CMD="java"
    else
        echo "错误: 未找到 Java，请安装 Java 17 并设置 JAVA_HOME"
        exit 1
    fi
else
    JAVA_CMD="$JAVA_HOME/bin/java"
fi

JAVA_VER=$($JAVA_CMD -version 2>&1 | head -1 | sed 's/.*"\([0-9]*\).*/\1/')
if [ "$JAVA_VER" -lt 17 ]; then
    echo "错误: 需要 Java 17+，当前版本: $JAVA_VER"
    exit 1
fi
echo "  Java: $($JAVA_CMD -version 2>&1 | head -1)"

if ! command -v node &> /dev/null; then
    echo "错误: 未找到 Node.js，请安装 Node.js 18+"
    exit 1
fi
echo "  Node.js: $(node -v)"

if [ -n "$MAVEN_HOME" ]; then
    MVN="$MAVEN_HOME/bin/mvn"
elif command -v mvn &> /dev/null; then
    MVN="mvn"
else
    echo "错误: 未找到 Maven，请安装 Maven 3.9+ 或设置 MAVEN_HOME"
    exit 1
fi
echo "  Maven: $("$MVN" -version 2>&1 | head -1)"

if [ -n "$MAVEN_REPOSITORY" ]; then
    export MAVEN_OPTS="-Dmaven.repo.local=$MAVEN_REPOSITORY"
fi
echo ""

# -------- 构建前端 --------
echo "[2/4] 构建前端..."
cd "$SCRIPT_DIR/frontend"

if [ ! -d "node_modules" ]; then
    echo "  安装依赖..."
    npm install
fi

npm run build
echo "  前端构建完成 -> frontend/dist/"
echo ""

# -------- 集成前端到后端 --------
echo "[3/4] 集成前端静态资源..."
STATIC_DIR="$SCRIPT_DIR/backend/src/main/resources/static"
rm -rf "$STATIC_DIR"
cp -r "$SCRIPT_DIR/frontend/dist" "$STATIC_DIR"
echo "  已复制 frontend/dist -> backend/src/main/resources/static/"
echo ""

# -------- 构建后端 JAR --------
echo "[4/4] 构建并打包..."
cd "$SCRIPT_DIR/backend"
"$MVN" package -DskipTests -q

JAR_PATH="$SCRIPT_DIR/backend/target/$JAR_NAME"
if [ ! -f "$JAR_PATH" ]; then
    JAR_PATH=$(ls "$SCRIPT_DIR/backend/target/"*.jar 2>/dev/null | grep -v "sources\|javadoc" | head -1)
fi
if [ ! -f "$JAR_PATH" ]; then
    echo "错误: JAR 构建失败"
    exit 1
fi

# 创建临时打包目录
PKG_DIR="$SCRIPT_DIR/target/${APP_NAME}"
rm -rf "$PKG_DIR"
mkdir -p "$PKG_DIR"/{config,logs,db}

# 复制 JAR
cp "$JAR_PATH" "$PKG_DIR/$JAR_NAME"

# 复制配置文件
if [ -f "$SCRIPT_DIR/backend/src/main/resources/application.yml" ]; then
    cp "$SCRIPT_DIR/backend/src/main/resources/application.yml" "$PKG_DIR/config/"
    echo "  已复制配置文件: application.yml"
fi

# 生成启动脚本（前台运行，适配 systemd）
cat > "$PKG_DIR/start.sh" <<'STARTEOF'
#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_NAME="transflow-1.0.0.jar"

# 查找 Java
if [ -n "$JAVA_HOME" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
elif command -v java &> /dev/null; then
    JAVA_CMD="java"
else
    echo "错误: 未找到 Java"
    exit 1
fi

# JVM 参数
JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"

case "${1:-start}" in
  start)
    echo "启动 TRANSFLOW..."
    exec "$JAVA_CMD" $JAVA_OPTS -jar "$SCRIPT_DIR/$JAR_NAME" \
      --spring.config.location="$SCRIPT_DIR/config/"
    ;;
  stop)
    if [ -f /etc/systemd/system/transflow-ai.service ]; then
      sudo systemctl stop transflow-ai
    else
      pkill -f "$JAR_NAME" || true
    fi
    ;;
  restart)
    if [ -f /etc/systemd/system/transflow-ai.service ]; then
      sudo systemctl restart transflow-ai
    else
      "$0" stop
      sleep 1
      "$0" start
    fi
    ;;
  status)
    if [ -f /etc/systemd/system/transflow-ai.service ]; then
      systemctl status transflow-ai
    else
      if pgrep -f "$JAR_NAME" > /dev/null; then
        echo "TRANSFLOW 运行中 (PID: $(pgrep -f "$JAR_NAME"))"
      else
        echo "TRANSFLOW 未运行"
      fi
    fi
    ;;
  *)
    echo "用法: $0 {start|stop|restart|status}"
    exit 1
    ;;
esac
STARTEOF
chmod +x "$PKG_DIR/start.sh"

# 生成 systemd service 文件
cat > "$PKG_DIR/transflow-ai.service" <<'SERVICEEOF'
[Unit]
Description=Transflow AI Service
After=network.target

[Service]
Type=simple
User=root
Group=root
WorkingDirectory=/opt/transflow-ai
ExecStart=/opt/transflow-ai/start.sh start
ExecStop=/opt/transflow-ai/start.sh stop
Restart=on-failure
RestartSec=5
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
SERVICEEOF

# 生成安装脚本
cat > "$PKG_DIR/install.sh" <<'INSTALLEOF'
#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
INSTALL_DIR="/opt/transflow-ai"
SERVICE_NAME="transflow-ai"

# 检查 root 权限
if [ "$(id -u)" -ne 0 ]; then
    echo "错误: 安装需要 root 权限，请使用 sudo 运行"
    exit 1
fi

echo "========================================="
echo "  TRANSFLOW 灵流 - 安装脚本"
echo "========================================="
echo ""

# 创建系统用户
if ! id transflow &>/dev/null; then
    echo "[1/5] 创建系统用户 transflow..."
    useradd -r -s /bin/false -d "$INSTALL_DIR" transflow
else
    echo "[1/5] 用户 transflow 已存在，跳过"
fi

# 创建安装目录
echo "[2/5] 安装文件到 $INSTALL_DIR..."
mkdir -p "$INSTALL_DIR"
cp -a "$SCRIPT_DIR"/. "$INSTALL_DIR"/

# 设置目录权限
echo "[3/5] 设置目录权限..."
chown -R transflow:transflow "$INSTALL_DIR"
chmod +x "$INSTALL_DIR/start.sh"

# 安装 systemd service
echo "[4/5] 安装 systemd 服务..."
cp "$INSTALL_DIR/transflow-ai.service" /etc/systemd/system/
systemctl daemon-reload

# 启用并启动服务
echo "[5/5] 启用并启动服务..."
systemctl enable "$SERVICE_NAME"
systemctl start "$SERVICE_NAME"

echo ""
echo "========================================="
echo "  安装完成!"
echo "========================================="
echo ""
echo "  服务管理命令:"
echo "    sudo systemctl start transflow-ai    启动"
echo "    sudo systemctl stop transflow-ai     停止"
echo "    sudo systemctl restart transflow-ai  重启"
echo "    sudo systemctl status transflow-ai   查看状态"
echo "    sudo journalctl -u transflow-ai -f   查看日志"
echo ""
echo "  访问地址: http://localhost:8080"
echo ""
INSTALLEOF
chmod +x "$PKG_DIR/install.sh"

# 生成卸载脚本
cat > "$PKG_DIR/uninstall.sh" <<'UNINSTALLEOF'
#!/bin/bash
set -e

INSTALL_DIR="/opt/transflow-ai"
SERVICE_NAME="transflow-ai"

if [ "$(id -u)" -ne 0 ]; then
    echo "错误: 卸载需要 root 权限，请使用 sudo 运行"
    exit 1
fi

echo "卸载 TRANSFLOW..."

# 停止并禁用服务
systemctl stop "$SERVICE_NAME" 2>/dev/null || true
systemctl disable "$SERVICE_NAME" 2>/dev/null || true
rm -f /etc/systemd/system/"$SERVICE_NAME".service
systemctl daemon-reload

# 删除安装目录
rm -rf "$INSTALL_DIR"

# 删除系统用户
if id transflow &>/dev/null; then
    userdel transflow 2>/dev/null || true
fi

echo "已卸载 TRANSFLOW"
UNINSTALLEOF
chmod +x "$PKG_DIR/uninstall.sh"

# 生成 README
cat > "$PKG_DIR/README.txt" <<'READMEEOF'
TRANSFLOW 灵流 v1.0.0
=========================

目录结构
--------
  transflow-1.0.0.jar      主程序 JAR
  config/                  配置文件目录
    application.yml
  logs/                    日志目录
  db/                      数据持久化目录
  start.sh                 启动脚本（前台运行，适配 systemd）
  install.sh               安装脚本（安装到 /opt/transflow-ai）
  uninstall.sh             卸载脚本
  transflow-ai.service     systemd 服务文件

快速开始
--------
  1. 解压 zip 包到任意目录
  2. 进入解压后的目录
  3. 执行 sudo ./install.sh 安装服务
  4. 浏览器访问 http://localhost:8080

手动运行（不使用 systemd）
------------------------
  ./start.sh start    前台启动（Ctrl+C 停止）

systemd 服务管理
----------------
  sudo systemctl start transflow-ai     启动
  sudo systemctl stop transflow-ai      停止
  sudo systemctl restart transflow-ai   重启
  sudo systemctl status transflow-ai    查看状态
  sudo journalctl -u transflow-ai -f    查看日志

卸载
----
  sudo ./uninstall.sh

环境要求
--------
  - Java 17+
  - 内存: 最低 512MB
READMEEOF

# 打包成 zip
cd "$SCRIPT_DIR/target"
rm -f "$OUTPUT_NAME"
zip -r "$OUTPUT_NAME" "${APP_NAME}"

JAR_SIZE=$(du -h "$JAR_PATH" | cut -f1)
ZIP_SIZE=$(du -h "$SCRIPT_DIR/target/$OUTPUT_NAME" | cut -f1)

echo ""
echo "========================================="
echo "  打包完成!"
echo "========================================="
echo ""
echo "  输出文件: target/$OUTPUT_NAME"
echo "  ZIP 大小: $ZIP_SIZE"
echo "  JAR 大小: $JAR_SIZE"
echo ""
echo "  部署方式:"
echo "    1. 解压: unzip target/$OUTPUT_NAME"
echo "    2. 安装: cd $APP_NAME && sudo ./install.sh"
echo ""
