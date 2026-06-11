# Agent Loop 失败复盘

## 现象

一次全库整理中，agent 读了 schema，也生成了大量 wikilinks，但没有创建有效页面。

## 根因

单个长 loop 同时承担扫描、规划、写正文、维护索引和质量收敛。

## 改进

把任务拆成 work item，并用 PatchBundle、MergeGuard、Validator 控制写入。
