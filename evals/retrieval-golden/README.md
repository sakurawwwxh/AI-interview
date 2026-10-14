# RAG 检索离线评估集

用来回答「检索到底好不好、改参数有没有用」的回归资产。设计目标有两个：
**CI 里能稳定跑**（纯计算，不连库、不调模型），以及**能被参数实验驱动**
（同一份观测数据可以反复用来比较不同 topK / 阈值 / 分块策略）。

## 目录

| 文件 | 谁维护 | 说明 |
|---|---|---|
| `cases.json` | 人工 | 评估用例（ground truth）：问题 + 期望文档 + 标记串 |
| `observed/observations.json` | 自动生成 | 真实检索录下来的**完整候选列表**（未做阈值过滤、未做 topK 截断） |
| `baseline.json` | 自动生成 | 上一次录制的命中率 / MRR 基线 |

语料是仓库里的 `docs/knowledge-base/*.md`（4 篇面试题文档），导入知识库后即可作为检索目标。

## 用例格式

```json
{
  "name": "mvcc-原理",
  "question": "数据库的 MVCC 是怎么实现的？",
  "expectedDoc": "database.md",
  "expectedMarkers": ["MVCC 原理"]
}
```

- `expectedMarkers` 是一组**可替代**的标记串：召回片段的正文里出现任意一条即算命中。
  标记串取自文档中小节标题的独特文本，因此不需要额外标注 chunk id。
- `question` 的措辞刻意与文档标题不同，避免"字面回声"把检索难度测没了。

## 指标口径

- **命中率** = 期望片段出现在最终 context 里的用例数 / 已观测用例数
- **MRR** = 首个命中片段的排名倒数均值（未命中记 0）

`topK` 与 `minScore` 写在 `cases.json` 里，评估时施加——所以改这两个参数重跑评估，
指标会真实变化，这是做参数实验的基础。

> `RetrievalEvaluator.select()` 复刻了生产侧 `KnowledgeBaseQueryService` 的筛选语义
> （先按 `minScore` 过滤，再取 `topK`）。两者口径必须一致，否则离线结论无法迁移到线上。

## 录制与回归

```bash
# 1) 录制：需要真实环境（pgvector 可用 + 模型 Key 已配置），且目标知识库已导入 docs/knowledge-base
EVAL_KB_IDS=1 mvn -pl app test -Dtest=RetrievalEvalRecordingTest -Deval.record=true

# 2) 回归：CI 里默认跑，无观测数据时自动跳过并提示
mvn -pl app test -Dtest='Retrieval*Test'
```

录制时故意把候选池取到 50、阈值设为 0，把完整候选列表落盘；阈值和 topK 留给离线评估施加。

## 参数实验怎么做

改 `cases.json` 里的 `topK` / `minScore`，然后：

1. 跑回归测试 → 会**明确失败**并提示参数与基线不一致（防止拿不可比的数字下结论）
2. 看打印出的报告里的命中率 / MRR 变化
3. 结论确定后，用 `RetrievalEvalLoader.writeBaseline()` 重新落基线（或删除 `baseline.json` 后重跑录制）

**注意**：候选列表是在**当前分块策略**下召回的。要比较分块参数（例如给 `TokenTextSplitter`
加 50–100 tokens overlap），必须先改分块、重新向量化、再重新录制，否则比的是同一份旧候选。

## 已知限制

- 标记串是**弱标注**（"正文包含该串即命中"），不等于严格的 chunk 级相关性标注。
  对个人项目规模足够定位趋势；若要更严格，可把 `expectedMarkers` 换成 chunk id。
- 观测数据绑定导入时的分词与向量模型；更换 embedding 模型后必须重新录制。
