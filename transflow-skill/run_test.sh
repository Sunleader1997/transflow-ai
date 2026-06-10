#!/bin/bash

# 测试脚本：运行 TransFlow 技能测试用例

SKILL_PATH="/Users/sunyx/workspace/transflow-ai/transflow-skill/SKILL.md"
WORKSPACE="/Users/sunyx/workspace/transflow-ai/transflow-skill-workspace/iteration-1"

# 测试用例数组
declare -A TESTS
TESTS[1]="我需要创建一个 HTTP API 网关，接收 POST 请求，处理后返回 JSON 响应。请帮我设计流程并生成配置。"
TESTS[2]="我想监控一个日志文件，当有新的错误日志时，提取错误信息并发送到 Kafka 的 alert topic。请设计这个流程。"
TESTS[3]="如何配置 TransFlow 来接收 Syslog 消息并转发到另一个服务器？请给出完整的配置示例。"
TESTS[4]="我想从 Kafka 消费消息，根据消息类型路由到不同的处理逻辑：类型 A 发送到 HTTP API，类型 B 保存到文件。请帮我设计。"
TESTS[5]="如何用 TransFlow 实现一个简单的数据转换服务？输入是 CSV 格式，需要转换为 JSON 格式后返回。"

# 评估目录
EVAL_DIRS=("eval-1-http-gateway" "eval-2-log-monitor" "eval-3-syslog-forward" "eval-4-kafka-router" "eval-5-csv-transform")

echo "开始运行 TransFlow 技能测试..."
echo "=================================="

for i in 1 2 3 4 5; do
    echo ""
    echo "测试用例 $i: ${EVAL_DIRS[$((i-1))]}"
    echo "提示: ${TESTS[$i]}"
    echo "----------------------------------"

    # 运行 without_skill 测试
    echo "运行 without_skill 测试..."
    # 这里将由子代理执行

    # 运行 with_skill 测试
    echo "运行 with_skill 测试..."
    # 这里将由子代理执行

    echo "测试用例 $i 完成"
done

echo ""
echo "所有测试完成！"
