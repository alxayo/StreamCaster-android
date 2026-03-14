---
name: harden-android-implplan
description: Rewrite the Android implementation plan into a hardened, execution-ready plan by incorporating adversarial review findings — fixing task decomposition, dependency graph, agent prompts, interface contracts, test coverage, and timeline.
argument-hint: Select or paste the adversarial review findings, then optionally add constraints such as team size, delivery deadline, or known blockers.
---

Act as a principal Android architect and delivery lead rewriting a flawed implementation plan into an execution-ready engineering plan.

Your job is not to critique the plan again. Your job is to absorb the original implementation plan, incorporate the adversarial review findings, resolve every identified flaw, and produce a hardened plan that a team of agents (human or AI) can execute with correct dependency ordering, unambiguous handoff instructions, consistent interface contracts, and realistic timelines.

Primary artifact to rewrite:
- [IMPLEMENTATION_PLAN.md](../../IMPLEMENTATION_PLAN.md)

Source specification (authoritative requirements):
- [SPECIFICATION.md](../../SPECIFICATION.md)

Adversarial review findings:
- ${selection}
- ${input:reviewFindings:Optional if nothing is selected: paste the adversarial review findings or key risks}

Additional constraints:
- ${input:constraints:Optional: team size, delivery deadline, known blockers, scope cuts, or other execution constraints}

Incorporate the additional constraints when provided. If they conflict with the review findings, resolve the conflict explicitly.

Operating rules:
- Treat the adversarial review findings as mandatory input. Every Critical and High severity finding must be resolved in the rewritten plan or explicitly deferred with justification.
- Medium severity findings should be resolved where the fix is low-effort; otherwise defer with rationale.
- Preserve the original plan's architecture and scope where possible, but prefer executability, integration safety, and schedule realism over plan elegance.
- Do not silently ignore contradictions between the plan, the review, and the specification. Resolve them explicitly.
- Do not leave ambiguous handoff instructions such as "integrate with service" or "handle errors." Replace them with concrete contracts, method signatures, error handling behavior, and file ownership.
- If critical information is still missing after considering all inputs, add an explicit `Open Questions` section with resolution deadlines instead of inventing unsupported decisions.
- Every task must have a single owner for every deliverable file. No two tasks may list the same file as a primary deliverable without explicit sequencing and integration instructions.

Rewrite goals:

1. **Fix task decomposition.** Split over-scoped tasks that bundle unknowns with critical-path work. Ensure every task has a concrete, verifiable deliverable that can be completed independently.
2. **Fix the dependency graph.** Correct all missing, wrong, and circular dependencies. Ensure stage assignments are consistent with declared dependencies. Recalculate the critical path.
3. **Fix agent handoff prompts.** Provide agent prompts for every task on the critical path and every High-Risk task. Each prompt must specify: exact input interfaces (with method signatures), exact output interfaces, file ownership, error handling contract, coordination points with adjacent tasks, and a verification command that fails if the deliverable is broken.
4. **Fix interface contracts.** Reconcile all mismatches between agent prompts, interface definitions, and data contracts. Ensure every shared type is defined in exactly one place. Ensure method signatures are consistent across all references.
5. **Fix test coverage.** Add tests for every specification requirement that the review identified as uncovered. Replace "Manual" verification with automated tests where feasible. Define concrete automation methods for failure injection and device-specific tests.
6. **Fix the timeline.** Recalculate milestone windows using corrected dependency chains. Add integration buffers between milestones. Apply the plan's own 30% critical-path slip factor and verify the schedule still holds. Restructure the starter plan to avoid idle agents.
7. **Fix specification coverage.** Add tasks or expand existing task scope for every specification requirement the review identified as missing from the WBS.

Required output structure:

# Hardened Implementation Plan

## 1. Delivery Assumptions
Restate team assumptions, scope, and API/device constraints. Update any assumptions invalidated by the review findings.

## 2. Architecture Baseline
Restate the architecture with any corrections required by the review (e.g., new interfaces, ownership changes, contract reconciliation). Include the corrected data contracts and package layout.

## 3. Work Breakdown Structure (WBS)
Provide the complete WBS table with corrected task scopes, dependencies, effort estimates, and stage assignments. For any task that was split, added, or re-scoped, mark it clearly. Every task must have:
- Unambiguous scope (what is in, what is out)
- Correct dependencies
- Single-owner file deliverables (no overlapping file ownership)
- A verification command that fails on broken output

## 4. Dependency Graph and Critical Path
Provide the corrected DAG with stage assignments consistent with dependencies. State the recalculated critical path with day-by-day estimates. Identify secondary critical paths.

## 5. Agent Handoff Prompts
Provide complete handoff prompts for all critical-path and high-risk tasks. Each prompt must include:
- Objective and deliverables
- Input interfaces with exact method signatures and types
- Output interfaces with exact method signatures and types
- File ownership (which files this agent creates or modifies)
- Error handling contract (what errors to catch, what to propagate, what state to emit)
- Coordination points (which adjacent tasks produce inputs, which consume outputs)
- Verification command and expected result
- Anti-patterns to avoid (common mistakes the review identified)

## 6. Interface Contracts
Provide all reconciled interface definitions. Every shared interface must appear exactly once with canonical method signatures. Flag any contract change from the original plan with a brief rationale.

## 7. Test Plan
Provide the corrected test matrix including:
- New tests added to cover review findings
- Concrete automation methods for failure injection tests
- Device matrix requirements per test
- Mapping from specification requirements to test IDs

## 8. Milestone Plan
Provide corrected milestone windows with:
- Realistic day ranges using corrected critical path
- Integration buffers between milestones
- Entry/exit criteria including integration verification
- Parallel execution lane assignments

## 9. Starter Plan (First 72–96 Hours)
Provide a corrected day-by-day plan that avoids agent idle time, respects dependency ordering, and does not assume a 4-day task completes in 1 day.

## 10. Open Questions
List unresolved questions with:
- Resolution deadline (which day/milestone they block)
- Proposed owner
- Fallback decision if unresolved by deadline

Rewriting requirements:
- Write a clean standalone plan, not commentary on the original.
- Remove or correct all findings from the adversarial review.
- Preserve task IDs where tasks are unchanged; use suffixed IDs (e.g., T-007a, T-007b) for splits and T-039+ for new tasks.
- Every interface contract, method signature, and type reference must be internally consistent across all sections.
- Do not mention that you are following a prompt.

Before producing the final plan, perform this internal verification:
- Every Critical and High review finding is either resolved in the plan or listed in Open Questions with justification.
- No two tasks list the same file as a primary deliverable without explicit sequencing.
- All stage assignments are consistent with declared dependencies (no task appears in a stage before its dependencies' stages).
- The critical path day count is arithmetically correct given task effort estimates.
- Every agent prompt references only types and interfaces that are defined in §6.
- Every specification requirement cited in the review as missing has a corresponding task or is explicitly deferred in Open Questions.
