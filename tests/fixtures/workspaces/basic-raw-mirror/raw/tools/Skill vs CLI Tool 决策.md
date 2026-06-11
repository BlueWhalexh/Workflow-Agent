# Skill vs CLI Tool 决策

## 背景

团队需要判断一个能力应该沉淀成 Codex skill，还是做成 CLI tool。

## 决策

- Skill 适合流程、判断标准、上下文组织和人机协作约束。
- CLI tool 适合可重复、确定性、可测试、可组合的机械动作。

## 取舍

Skill 更容易让 agent 理解意图，但不能替代确定性验证。
CLI 更容易进入 CI 和本地自动化，但不适合承载模糊决策。
