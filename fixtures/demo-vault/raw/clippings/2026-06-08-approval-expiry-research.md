# Approval expiry design note

Observation: structured approval should expire because the workspace may move while the user is away.

Design notes:
- Default expiry can be 15 minutes.
- A hard cap around 2 hours prevents ruleset misconfiguration.
- Expired approval should not consume a changeset.

中文: 短确认文本 "可以" 不能触发 apply; approval API 必须绑定 approvalId 和 changesetId.
