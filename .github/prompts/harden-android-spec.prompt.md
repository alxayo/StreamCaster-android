---
name: harden-android-spec
description: Rewrite the Android streaming specification into a hardened, implementation-ready spec using adversarial review findings.
argument-hint: Select or paste the adversarial review findings, then optionally add constraints such as target devices, API floor, or delivery deadlines.
---

Act as a principal Android architect rewriting a fragile product spec into an implementation-ready engineering specification.

Your job is not to critique the spec again. Your job is to absorb the original specification, incorporate the adversarial review findings, resolve weak or contradictory requirements, and produce a hardened specification that an Android team can implement with fewer hidden failure modes.

Primary artifact to rewrite:
- [SPECIFICATION.md](../../SPECIFICATION.md)

Adversarial review findings:
- ${selection}
- ${input:reviewFindings:Optional if nothing is selected: paste the adversarial review findings or key risks}

Additional constraints:
- ${input:constraints:Optional: add business constraints, launch scope limits, target devices, compliance requirements, or schedule pressure}

Operating rules:
- Treat the adversarial review findings as required input unless they clearly conflict with the product intent.
- Preserve the original product goals where possible, but prefer implementability, safety, and operational stability over feature ambition.
- Do not silently ignore contradictions. Resolve them explicitly in the rewritten spec.
- Do not leave vague statements such as "handle errors gracefully" or "optimize performance." Replace them with concrete engineering requirements.
- If critical information is still missing after considering the original spec and review findings, add an explicit `Open Questions` section at the end instead of inventing unsupported decisions.
- Keep the rewrite model-agnostic and tool-agnostic where possible, but make Android platform constraints explicit.

Rewrite goals:

1. Convert ambiguous goals into testable requirements.
2. Add missing lifecycle, failure-handling, security, and observability requirements.
3. Resolve contradictions between UX goals, background execution limits, streaming behavior, and device constraints.
4. Reduce risk from process death, permission revocation, connectivity loss, thermal throttling, and OEM-specific behavior.
5. Make the document directly usable for implementation planning, task breakdown, and test design.

Required output structure:

# Hardened Specification

## 1. Product Scope and Non-Goals
Rewrite the scope so it is precise and bounded.

## 2. Supported Platforms and Operating Assumptions
Define Android API assumptions, device class expectations, network assumptions, and OEM-risk posture.

## 3. Functional Requirements
Rewrite feature behavior into explicit, testable requirements.

## 4. Lifecycle and State Management Requirements
Define app lifecycle, foreground service behavior, camera/audio ownership, process death handling, notification behavior, and recovery rules.

## 5. Media Pipeline Requirements
Define camera, audio, encoding, bitrate/resolution fallback behavior, latency expectations, frame-drop policy, and thermal protection behavior.

## 6. Security and Privacy Requirements
Define credential handling, transport security expectations, permission flows, capture indicators, local data handling, and logging restrictions.

## 7. Reliability and Failure Handling
Define behavior for network loss, background restrictions, revoked permissions, encoder failures, device sleep, and user interruption.

## 8. Observability and Diagnostics
Define logs, metrics, health signals, and debug surfaces required to support development and production triage without leaking secrets.

## 9. Acceptance Criteria
Provide concrete acceptance criteria that can be turned into QA scenarios or automated tests.

## 10. Open Questions
Only include unresolved issues that cannot be responsibly decided from the original spec plus review findings.

Rewriting requirements:
- Write the result as a clean standalone specification, not as commentary on the original.
- Remove redundant or contradictory requirements.
- Preserve only requirements that survive architectural scrutiny.
- Where the adversarial review identified a flaw, reflect the fix directly in the rewritten requirement.
- Use normative language such as `must`, `must not`, `should`, and `may` appropriately.
- Prefer concise, implementation-ready prose and bullet lists over narrative explanation.
- Do not mention that you are following a prompt.

Before producing the final rewritten specification, perform this internal check:
- verify that every major risk raised by the adversarial review is either resolved in the rewritten spec or listed in `Open Questions`
- verify that no requirement depends on impossible or restricted Android background behavior without stating the constraint
- verify that credentials, permissions, and foreground service behavior are covered explicitly