# Security Policy

## Supported version

当前只维护默认分支的最新版本。

## Reporting

请不要在公开 Issue 中提交密钥、个人数据或可直接利用的漏洞细节。使用 GitHub Security Advisory 私下报告，并提供影响范围、复现步骤和建议修复方式。

## Security boundaries

- Agent 只允许调用服务端注册的类型化工具。
- 任意 Shell、SQL、动态代码和任意 URL 调用不属于允许能力。
- 写工具必须经过角色校验和人工确认。
- `.env`、Token、云密钥和生产密码不得提交到仓库。
- 演示账号与 `.env.example` 中的值只能用于隔离的本地环境。
- 农技建议属于辅助信息；精确用药和高风险场景必须由专业人员复核。
