# Contributing

## Local checks

提交前至少运行：

```powershell
cd backend
mvn -B test

cd ../frontend
npm test
npm run build

cd ..
python scripts/build-agent-eval-dataset.py
python benchmarks/evaluate_agent.py --validate-only
```

## Change requirements

- Agent 新工具必须声明输入、输出、角色权限、副作用和超时策略。
- 写工具必须进入人工确认流程，并提供拒绝路径测试。
- 指标变化必须提交测试环境、原始结果和失败样本。
- 不得提交凭据、生产数据、第三方论文原文、模型权重、运行日志或本机绝对路径。
- 架构决策需要在 `202607worklog/01-adr/` 新增或更新 ADR。
