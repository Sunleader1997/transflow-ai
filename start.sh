#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "========================================="
echo "  TRANSFLOW 灵流 - 启动脚本"
echo "========================================="

# Check dependencies
if [ -z "$JAVA_HOME" ]; then
    echo "警告: JAVA_HOME 未设置，尝试使用系统 java"
    if ! command -v java &> /dev/null; then
        echo "错误: 未找到 Java，请安装 Java 17 并设置 JAVA_HOME"
        exit 1
    fi
fi

if ! command -v node &> /dev/null; then
    echo "错误: 未找到 Node.js，请安装 Node.js 18+"
    exit 1
fi

# Start backend
echo ""
echo ">>> 启动后端 (Spring Boot)..."
cd "$SCRIPT_DIR/backend"

if [ -n "$MAVEN_HOME" ]; then
    MVN="$MAVEN_HOME/bin/mvn"
elif command -v mvn &> /dev/null; then
    MVN="mvn"
else
    echo "错误: 未找到 Maven，请安装 Maven 3.9+ 或设置 MAVEN_HOME"
    exit 1
fi

if [ -n "$MAVEN_REPOSITORY" ]; then
    export MAVEN_OPTS="-Dmaven.repo.local=$MAVEN_REPOSITORY"
fi

"$MVN" spring-boot:run &
BACKEND_PID=$!

# Start frontend
echo ""
echo ">>> 启动前端 (Vue 3)..."
cd "$SCRIPT_DIR/frontend"

if [ ! -d "node_modules" ]; then
    echo "    安装前端依赖..."
    npm install
fi

npm run dev &
FRONTEND_PID=$!

echo ""
echo "========================================="
echo "  启动完成!"
echo "  后端: http://localhost:8080"
echo "  前端: http://localhost:5173"
echo "========================================="
echo ""
echo "按 Ctrl+C 停止所有服务"

# Trap Ctrl+C
trap "echo ''; echo '正在停止服务...'; kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; exit 0" INT TERM

# Wait for processes
wait
