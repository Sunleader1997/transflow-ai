#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_NAME="transflow"
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
PKG_DIR="$SCRIPT_DIR/target/package-${APP_NAME}"
rm -rf "$PKG_DIR"
mkdir -p "$PKG_DIR"/{config,logs,db}

# 复制 JAR
cp "$JAR_PATH" "$PKG_DIR/$JAR_NAME"

# 生成默认配置
cat > "$PKG_DIR/config/application.properties" <<'EOF'
server.port=8080
spring.main.web-application-type=reactive
spring.jackson.serialization.write-dates-as-timestamps=false

# 日志
logging.file.name=logs/transflow.log
logging.level.root=INFO
logging.level.org.sunyaxing.transflow=INFO

# 数据持久化目录
transflow.db.dir=db
EOF

# 生成启动脚本
cat > "$PKG_DIR/start.sh" <<'STARTEOF'
#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_NAME="transflow-1.0.0.jar"
PID_FILE="$SCRIPT_DIR/transflow.pid"

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
    nohup "$JAVA_CMD" $JAVA_OPTS -jar "$SCRIPT_DIR/$JAR_NAME" \
      --spring.config.location="$SCRIPT_DIR/config/" \
      > "$SCRIPT_DIR/logs/startup.log" 2>&1 &
    echo $! > "$PID_FILE"
    sleep 2
    if kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
      echo "TRANSFLOW 启动成功 (PID: $(cat "$PID_FILE"))"
      echo "访问: http://localhost:8080"
    else
      echo "启动失败，请查看日志: $SCRIPT_DIR/logs/startup.log"
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
chmod +x "$PKG_DIR/start.sh"

# 生成卸载脚本
cat > "$PKG_DIR/uninstall.sh" <<'UNINSTALLEOF'
#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -f "$SCRIPT_DIR/transflow.pid" ] && kill -0 "$(cat "$SCRIPT_DIR/transflow.pid")" 2>/dev/null; then
    echo "请先停止服务: $SCRIPT_DIR/start.sh stop"
    exit 1
fi
echo "卸载 TRANSFLOW..."
cd "$SCRIPT_DIR/.."
rm -rf "$SCRIPT_DIR"
echo "已卸载"
UNINSTALLEOF
chmod +x "$PKG_DIR/uninstall.sh"

# 生成 README
cat > "$PKG_DIR/README.txt" <<'READMEEOF'
TRANSFLOW 灵流 v1.0.0
=========================

目录结构
--------
  transflow-1.0.0.jar    主程序 JAR
  config/                配置文件目录
    application.properties
  logs/                  日志目录
  db/                    数据持久化目录
  start.sh               启动/停止/重启脚本
  uninstall.sh           卸载脚本

快速开始
--------
  1. 解压 zip 包到任意目录
  2. 进入解压后的目录
  3. 执行 ./start.sh start 启动服务
  4. 浏览器访问 http://localhost:8080

常用命令
--------
  ./start.sh start    启动服务
  ./start.sh stop     停止服务
  ./start.sh restart  重启服务
  ./start.sh status   查看运行状态

环境要求
--------
  - Java 17+
  - 内存: 最低 512MB
READMEEOF

# 打包成 zip
cd "$SCRIPT_DIR/target"
rm -f "$OUTPUT_NAME"
zip -r "$OUTPUT_NAME" "package-${APP_NAME}"

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
echo "  解压后即可运行:"
echo "    unzip target/$OUTPUT_NAME"
echo "    cd package-transflow"
echo "    ./start.sh start"
echo ""
