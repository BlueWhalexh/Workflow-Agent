# Stale rejection tabletop

Delta Research / meeting.

Scenario:
- Agent reads note at hash h1.
- User edits the same note before approval.
- User approves old changeset.
- Apply must reject as stale and preserve the user's newer content.

中文结论:
- 不做 force apply.
- 不做 three-way merge in MVP.
- Agent 可以基于新 revision 重新 propose.

This note intentionally has no frontmatter.
