# Delta research reading

Paper skim: "Local-first software and sync conflict handling".

Notes:
- optimistic lock is clearer for human review than automatic merge.
- content hash per file gives a useful stale guard.
- The paper examples are about collaborative text editing, but our changeset pipeline is batch-oriented.

中文问题:
- 如果用户审批前改了 Inbox 中的笔记, apply 必须整批拒绝.
- 不做 partial apply, 避免 audit trail 断裂.

Potential project: Delta Research.
