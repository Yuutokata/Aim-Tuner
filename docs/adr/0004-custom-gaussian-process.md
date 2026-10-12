# 0004: Custom Gaussian process

## Context
The optimizer needs a 1-D GP with a noise model and an acquisition function over a handful of observations.

## Decision
Implement the GP in `core` with EJML for Cholesky, not a general ML framework.

## Consequences
- Small dependency footprint, full control over kernel, noise and session offsets.
- We own the numerics (jitter, conditioning) and must test them, including against the simulated player.
