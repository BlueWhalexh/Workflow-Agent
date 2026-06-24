# Beacon Ops incident review

会议记录 / meeting notes.

Participants: Rui, Maya, Chen.

We reviewed the sidecar restart incident from last week. The root cause was not a model failure; the process kept old event cursors after a local restart.

中文摘要:
- SSE reconnect 需要按 sequence replay, 不能让前端自己猜状态.
- heartbeat 应该作为正常事件进入 timeline.
- Apply result panel 要把 stale changeset 与 validator failure 分开显示.

Action items:
- Add resilience scenario for disconnect + reconnect.
- Link this note to Beacon Ops runbook once frontmatter is added.
