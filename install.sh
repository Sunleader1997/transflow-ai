#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
INSTALL_DIR="/opt/transflow-ai"
APP_NAME="transflow"
JAR_NAME="transflow-1.0.0.jar"

echo "========================================="
echo "  TRANSFLOW 灵流 - 一键安装"
echo "========================================="
echo ""

# -------- 环境检查 --------
echo "[1/5] 检查环境..."

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
echo "  Maven: $($MVN -version 2>&1 | head -1)"

if [ -n "$MAVEN_REPOSITORY" ]; then
    export MAVEN_OPTS="-Dmaven.repo.local=$MAVEN_REPOSITORY"
fi
echo ""

# -------- 构建前端 --------
echo "[2/5] 构建前端..."
cd "$SCRIPT_DIR/frontend"

if [ ! -d "node_modules" ]; then
    echo "  安装依赖..."
    npm install
fi

npm run build
echo "  前端构建完成 -> frontend/dist/"
echo ""

# -------- 复制前端到后端静态资源 --------
echo "[3/5] 集成前端静态资源..."
STATIC_DIR="$SCRIPT_DIR/backend/src/main/resources/static"
rm -rf "$STATIC_DIR"
cp -r "$SCRIPT_DIR/frontend/dist" "$STATIC_DIR"
echo "  已复制 frontend/dist -> backend/src/main/resources/static/"
echo ""

# -------- 构建后端 JAR --------
echo "[4/5] 构建后端 JAR..."
cd "$SCRIPT_DIR/backend"
$MVN package -DskipTests -q
JAR_PATH="$SCRIPT_DIR/backend/target/$JAR_NAME"
if [ ! -f "$JAR_PATH" ]; then
    # fallback: find any jar in target
    JAR_PATH=$(ls "$SCRIPT_DIR/backend/target/"*.jar 2>/dev/null | head -1)
fi
if [ ! -f "$JAR_PATH" ]; then
    echo "错误: JAR 构建失败"
    exit 1
fi
JAR_SIZE=$(du -h "$JAR_PATH" | cut -f1)
echo "  JAR 构建完成: $JAR_NAME ($JAR_SIZE)"
echo ""

# -------- 安装到目标目录 --------
echo "[5/5] 安装到 $INSTALL_DIR ..."

# 创建目录
sudo mkdir -p "$INSTALL_DIR"/{config,logs,db}

# 复制 JAR
sudo cp "$JAR_PATH" "$INSTALL_DIR/$JAR_NAME"

# 生成默认配置
if [ ! -f "$INSTALL_DIR/config/application.properties" ]; then
    sudo tee "$INSTALL_DIR/config/application.properties" > /dev/null <<'EOF'
server.port=8080
spring.main.web-application-type=reactive
spring.jackson.serialization.write-dates-as-timestamps=false

# 日志
logging.file.name=/opt/transflow-ai/logs/transflow.log
logging.level.root=INFO
logging.level.org.sunyaxing.transflow=INFO

# 数据持久化目录
transflow.db.dir=/opt/transflow-ai/db
EOF
    echo "  已生成默认配置: config/application.properties"
else
    echo "  配置文件已存在，跳过生成"
fi

# 生成启动脚本
sudo tee "$INSTALL_DIR/start.sh" > /dev/null <<'STARTEOF'
#!/bin/bash
set -e

INSTALL_DIR="/opt/transflow-ai"
JAR_NAME="transflow-1.0.0.jar"
PID_FILE="$INSTALL_DIR/transflow.pid"

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
    if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
      echo "TRANSFLOW 已在运行 (PID: $(cat "$PID_FILE"))"
      exit 0
    fi
    echo "启动 TRANSFLOW..."
    nohup $JAVA_CMD $JAVA_OPTS -jar "$INSTALL_DIR/$JAR_NAME" \
      --spring.config.location="$INSTALL_DIR/config/" \
      > "$INSTALL_DIR/logs/startup.log" 2>&1 &
    echo $! > "$PID_FILE"
    sleep 2
    if kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
      echo "TRANSFLOW 启动成功 (PID: $(cat "$PID_FILE"))"
      echo "访问: http://localhost:8080"
    else
      echo "启动失败，请查看日志: $INSTALL_DIR/logs/startup.log"
      exit 1
    fi
    ;;
  stop)
    if [ -f "$PID_FILE" ]; then
      PID=$(cat "$PID_FILE")
      if kill -0 "$PID" 2>/dev/null; then
        echo "停止 TRANSFLOW (PID: $PID)..."
        kill "$PID"
        rm -f "$PID_FILE"
        echo "已停止"
      else
        echo "进程不存在，清理 PID 文件"
        rm -f "$PID_FILE"
      fi
    else
      echo "TRANSFLOW 未在运行"
    fi
    ;;
  restart)
    "$0" stop
    sleep 1
    "$0" start
    ;;
  status)
    if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
      echo "TRANSFLOW 运行中 (PID: $(cat "$PID_FILE"))"
    else
      echo "TRANSFLOW 未运行"
    fi
    ;;
  *)
    echo "用法: $0 {start|stop|restart|status}"
    exit 1
    ;;
esac
STARTEOF
sudo chmod +x "$INSTALL_DIR/start.sh"

# 生成卸载脚本
sudo tee "$INSTALL_DIR/uninstall.sh" > /dev/null <<'UNINSTALLEOF'
#!/bin/bash
INSTALL_DIR="/opt/transflow-ai"
if [ -f "$INSTALL_DIR/transflow.pid" ] && kill -0 "$(cat "$INSTALL_DIR/transflow.pid")" 2>/dev/null; then
    echo "请先停止服务: $INSTALL_DIR/start.sh stop"
    exit 1
fi
echo "卸载 TRANSFLOW..."
sudo rm -rf "$INSTALL_DIR"
echo "已卸载"
UNINSTALLEOF
sudo chmod +x "$INSTALL_DIR/uninstall.sh"

echo ""
echo "========================================="
echo "  安装完成!"
echo "========================================="
echo ""
echo "  安装目录: $INSTALL_DIR"
echo "  配置文件: $INSTALL_DIR/config/application.properties"
echo "  日志目录: $INSTALL_DIR/logs/"
echo "  数据目录: $INSTALL_DIR/db/"
echo ""
echo "  启动: $INSTALL_DIR/start.sh start"
echo "  停止: $INSTALL_DIR/start.sh stop"
echo "  重启: $INSTALL_DIR/start.sh restart"
echo "  状态: $INSTALL_DIR/start.sh status"
echo "  卸载: $INSTALL_DIR/uninstall.sh"
echo ""
