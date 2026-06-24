# SSE replay test sketch

Beacon Ops test sketch.

Steps:
1. Start a run that emits several tool events.
2. Disconnect after sequence 4.
3. Reconnect with Last-Event-ID: 4.
4. Expect sequence 5..N replayed, then live tail.

Notes in Chinese:
- 终态事件必须出现: run_completed / run_failed / run_cancelled.
- 如果 EventStore GC 了,返回 410,前端改用 snapshot.

This belongs near the runtime lab and ops reliability work.
