# Known Issues

## Context and maintenance
- The repository needs updates in the .ai files when behavior or priorities change.
- New feature work should be appended to the feature register in .ai/project-brief.md and reflected here when it creates follow-up work.

## Technical risk
- Configuration defaults should not rely on hardcoded sensitive values.

## Future improvements
- Add a deeper test matrix for the Redis vote path and queue worker.
- Consider stronger durability for vote events if the queue becomes a bottleneck.
- Add a stricter review rule for agent handoffs so feature deltas cannot be skipped.