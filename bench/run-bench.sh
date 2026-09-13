#!/usr/bin/env bash
# 简历提交接口压测:实测"异步化后提交接口延迟"与"分析任务端到端耗时"两组数据。
# 用法: ./run-bench.sh <baseUrl> <用户名> <密码> <简历PDF路径> [并发请求数=20]
# 依赖: curl, awk, jq(可选,缺失时用 grep 兜底)
set -euo pipefail

BASE_URL="${1:?usage: run-bench.sh <baseUrl> <user> <pass> <resume.pdf> [N]}"
USERNAME="${2:?}"
PASSWORD="${3:?}"
RESUME_FILE="${4:?}"
N="${5:-20}"

echo "== 1. 登录获取 accessToken =="
LOGIN_RESP=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")
TOKEN=$(echo "$LOGIN_RESP" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
[ -n "$TOKEN" ] || { echo "登录失败: $LOGIN_RESP"; exit 1; }
echo "token: ${TOKEN:0:20}..."

echo "== 2. 上传 $N 次(限流 GLOBAL+IP 5次/秒,间隔 300ms)=="
: > /tmp/bench_latencies.txt
RESUME_IDS=()
for i in $(seq 1 "$N"); do
  # curl -w time_total 记录总耗时(秒)
  RESP=$(curl -s -w "\n%{http_code} %{time_total}" -X POST "$BASE_URL/api/resumes/upload" \
    -H "Authorization: Bearer $TOKEN" \
    -F "file=@$RESUME_FILE;type=application/pdf")
  BODY=$(echo "$RESP" | sed '$d'); META=$(echo "$RESP" | tail -1)
  HTTP_CODE=$(echo "$META" | awk '{print $1}'); SECS=$(echo "$META" | awk '{print $2}')
  if [ "$HTTP_CODE" != "200" ]; then echo "  [$i] HTTP $HTTP_CODE (响应:$BODY)"; continue; fi
  RID=$(echo "$BODY" | sed -n 's/.*"resumeId":\([0-9]*\).*/\1/p')
  echo "$SECS" >> /tmp/bench_latencies.txt
  [ -n "$RID" ] && RESUME_IDS+=("$RID")
  echo "  [$i] ${SECS}s resumeId=$RID"
  sleep 0.3
done

echo "== 3. 延迟统计 =="
sort -n /tmp/bench_latencies.txt | awk '
  {a[NR]=$1}
  END {
    if (NR==0) { print "无成功样本"; exit 1 }
    printf "样本数: %d\nP50: %.0f ms\nP95: %.0f ms\n最大: %.0f ms\n均值: %.0f ms\n",
      NR, a[int(NR*0.5+0.999)]*1000, a[int(NR*0.95+0.999)]*1000, a[NR]*1000,
      (function(){s=0; for(i=1;i<=NR;i++) s+=a[i]; return s/NR})()*1000
  }' 2>/dev/null || sort -n /tmp/bench_latencies.txt | awk '{a[NR]=$1; s+=$1}
  END { printf "样本数: %d\nP50: %.0f ms\nP95: %.0f ms\n最大: %.0f ms\n均值: %.0f ms\n",
        NR, a[int(NR*0.5+0.999)]*1000, a[int(NR*0.95+0.999)]*1000, a[NR]*1000, s/NR*1000 }'

echo "== 4. 抽样 3 条,轮询分析任务端到端耗时(同步等待 AI 完成,即旧同步接口的等效耗时)=="
COUNT=0
for RID in "${RESUME_IDS[@]:0:3}"; do
  START=$(date +%s%3N)
  for t in $(seq 1 120); do
    STATUS=$(curl -s "$BASE_URL/api/resumes/$RID/detail" -H "Authorization: Bearer $TOKEN" \
      | sed -n 's/.*"status":"\([A-Z_]*\)".*/\1/p' | head -1)
    [ "$STATUS" = "COMPLETED" ] || [ "$STATUS" = "FAILED" ] && break
    sleep 2
  done
  END=$(date +%s%3N)
  echo "resumeId=$RID status=${STATUS:-TIMEOUT} 端到端: $((END-START)) ms"
  COUNT=$((COUNT+1))
done
echo "完成。分析耗时即'同步等待 AI'的量级,提交延迟 P95 即'异步化后'的量级。"
