#!/usr/bin/env bash
# 简历提交接口压测:实测「异步化后提交接口延迟」与「分析任务端到端耗时」。
# 用法:
#   ./run-bench.sh <baseUrl> <用户名> <密码> <简历文件或目录> [请求数=20]
#   - 传目录时,目录内每个文件上传一次(内容不同,绕开内容哈希去重,走完整提交路径)
#   - 传单个文件时,重复上传会触发内容去重短路(测的是去重路径,不建议)
# 依赖: curl, awk
set -euo pipefail

BASE_URL="${1:?usage: run-bench.sh <baseUrl> <user> <pass> <file|dir> [N]}"
USERNAME="${2:?}"
PASSWORD="${3:?}"
RESUME_PATH="${4:?}"
N="${5:-20}"

if [ -d "$RESUME_PATH" ]; then
  mapfile -t FILES < <(ls "$RESUME_PATH"/*.txt "$RESUME_PATH"/*.pdf 2>/dev/null | head -n "$N")
  N=${#FILES[@]}
  [ "$N" -gt 0 ] || { echo "目录中没有可上传文件"; exit 1; }
  echo "目录模式: $N 个不同文件"
else
  FILES=("$RESUME_PATH")
fi

echo "== 0. 登录 + 环境基线(鉴权 GET /api/auth/me 往返 5 次)=="
TOKEN=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}" \
  | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
[ -n "$TOKEN" ] || { echo "登录失败"; exit 1; }
: > /tmp/bench_baseline.txt
for i in 1 2 3 4 5; do
  curl -s -o /dev/null -w "%{time_total}\n" \
    -H "Authorization: Bearer $TOKEN" "$BASE_URL/api/auth/me" >> /tmp/bench_baseline.txt
done
sort -n /tmp/bench_baseline.txt | awk '{a[NR]=$1; s+=$1}
  END { printf "基线 P50: %.0f ms  均值: %.0f ms(单次 REST 往返,含 JWT+1 次 DB 查询)\n",
        a[int(NR*0.5+0.999)]*1000, s/NR*1000 }'

echo "== 1. 上传 $N 次(每个文件不同内容,间隔 300ms,限流 5次/秒)=="
: > /tmp/bench_latencies.txt
: > /tmp/bench_ids.txt
for i in $(seq 1 "$N"); do
  F="${FILES[$(( (i-1) % ${#FILES[@]} ))]}"
  RESP=$(curl -s -w "\n%{http_code} %{time_total}" -X POST "$BASE_URL/api/resumes/upload" \
    -H "Authorization: Bearer $TOKEN" \
    -F "file=@$F;type=application/pdf")
  BODY=$(echo "$RESP" | sed '$d'); META=$(echo "$RESP" | tail -1)
  HTTP_CODE=$(echo "$META" | awk '{print $1}'); SECS=$(echo "$META" | awk '{print $2}')
  if [ "$HTTP_CODE" != "200" ]; then echo "  [$i] HTTP $HTTP_CODE: $(echo "$BODY" | head -c 120)"; continue; fi
  RID=$(echo "$BODY" | sed -n 's/.*"resumeId":\([0-9]*\).*/\1/p')
  echo "$SECS" >> /tmp/bench_latencies.txt
  [ -n "$RID" ] && echo "$RID" >> /tmp/bench_ids.txt
  echo "  [$i] ${SECS}s resumeId=$RID"
  sleep 0.3
done

echo "== 2. 提交延迟统计 =="
sort -n /tmp/bench_latencies.txt | awk '{a[NR]=$1; s+=$1}
  END { if (NR==0) { print "无成功样本"; exit 1 }
        printf "样本数: %d\nP50: %.0f ms\nP95: %.0f ms\n最大: %.0f ms\n均值: %.0f ms\n",
        NR, a[int(NR*0.5+0.999)]*1000, a[int(NR*0.95+0.999)]*1000, a[NR]*1000, s/NR*1000 }'

echo "== 3. 抽样 3 条:分析任务端到端耗时(同步等 AI 完成,即旧同步接口的等效耗时)=="
mapfile -t IDS < /tmp/bench_ids.txt
DONE=0
for RID in "${IDS[@]}"; do
  [ "$DONE" -ge 3 ] && break
  START=$(date +%s%3N)
  STATUS=""
  for t in $(seq 1 150); do
    STATUS=$(curl -s "$BASE_URL/api/resumes/$RID/detail" -H "Authorization: Bearer $TOKEN" \
      | sed -n 's/.*"analyzeStatus":"\([A-Z_]*\)".*/\1/p' | head -1)
    [ "$STATUS" = "COMPLETED" ] || [ "$STATUS" = "FAILED" ] && break
    sleep 2
  done
  END=$(date +%s%3N)
  echo "resumeId=$RID status=${STATUS:-TIMEOUT} 端到端: $((END-START)) ms"
  DONE=$((DONE+1))
done

echo "== 完成:提交延迟 P50/P95 为异步化后口径,端到端耗时为旧同步口径的量级 =="
