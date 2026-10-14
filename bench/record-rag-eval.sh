#!/usr/bin/env bash
# 录制 RAG 检索评估的观测数据:真实跑一遍检索,把完整候选列表写入
# evals/retrieval-golden/observed/observations.json,并刷新 baseline.json。
#
# 用法:
#   ./record-rag-eval.sh                  # 不指定知识库 ID 时对全部知识库检索
#   EVAL_KB_IDS=4,5 ./record-rag-eval.sh  # 只对指定知识库检索
#   ./record-rag-eval.sh -Dspring.datasource.url='jdbc:postgresql://...'   # 透传额外 mvn 参数
#
# 依赖:
#   - 应用中间件可达(PostgreSQL + pgvector、Redis)
#   - .env 中 AI_BAILIAN_API_KEY 已填真实值(录制要调 embedding,会把问题向量化)
#   - 目标知识库中已导入 evals/retrieval-golden/cases.json 里 corpus 列出的文档
#
# 说明:Spring Boot 不会自动读取 .env(只有 docker compose 会),所以这里显式 source。
# 录制完成后,常规 `mvn test` 中的「RAG 检索指标回归」会拿观测数据重算指标并与基线比较。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

ENV_FILE="$ROOT/.env"
if [[ ! -f "$ENV_FILE" ]]; then
  echo "错误: 找不到 $ENV_FILE" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

if [[ -z "${AI_BAILIAN_API_KEY:-}" || "${AI_BAILIAN_API_KEY}" == "your_api_key_here" ]]; then
  echo "错误: AI_BAILIAN_API_KEY 仍是占位符,请在 $ENV_FILE 中填入真实的百炼 API Key" >&2
  exit 1
fi

echo "开始录制检索观测数据(知识库: ${EVAL_KB_IDS:-全部})..."
exec mvn -pl app test \
  -Dtest=RetrievalEvalRecordingTest \
  -Deval.record=true \
  "$@"
