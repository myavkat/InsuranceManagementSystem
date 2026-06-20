# Agent Instructions: Workspace Context & Workflow

You are operating within a structured, multi-tier engineering framework. Your state, instructions, and tasks are managed through localized Markdown files prefixed with number (e.g. 01_FILE.md) rather than memory or chat history. This ensures context retention across model handovers.

---

## Directory Schema

You must use and reference the directories under `docs/` according to these strict operational roles:

### 1. `docs/outlines/` (The Technical Blueprint)
*   **What it is:** Permanent architectural decisions, system boundaries, technology stacks, and data models.
*   **How to use it:** Treat this as law. You may never violate an architectural boundary defined here. If a task conflicts with an outline file, halt and ask for clarification.

### 2. `docs/stories/` (The Functional Scope)
*   **What it is:** Short, human-written user stories describing required features and behaviors from an end-user perspective.
*   **How to use it:** Use these to understand *why* a feature is being built and what edge cases must be handled.

### 3. `docs/tasks/` (The Execution Prompts)
*   **What it is:** Directed instructions linking specific outlines and stories together to initiate a slice of work. 
*   **How to use it:** When a task file points you to a specific outline and story, your first objective is *always* to parse them and generate an execution plan. Do not write code directly from a task file.

### 4. `docs/plans/` (The Active State & Source of Truth)
*   **What it is:** Step-by-step, checkbox-based (`- [ ]`) engineering execution checklists.
*   **How to use it:** This is your handoff engine. 
    *   Before writing code, generate or read the active plan.
    *   Execute tasks incrementally, focusing only on the next unchecked item.
    *   **Crucial:** Update the plan file directly (`- [ ]` to `- [x]`) as you successfully complete and test each step.

---

## Git Branching Rule

Before any code or doc change:
1. **Branch off `main`** — never commit directly on `main`.
2. **Branch name** — use the plan or task name in lowercase with hyphens (e.g., `phase0-preparation`, `sprint1-customer-service`). For unplanned work, pick a concise descriptive name.
3. **Commit periodically** — commit as you complete each logical step or checkbox item to track progress.
4. **Never push to remote** unless the user explicitly asks. Work remains local.

---

## Operational Protocol for Model Handovers

If you have just been initialized or swapped into this project:
1.  **Scan Active State:** Locate the latest modified plan file in `docs/plans/`.
2.  **Locate the Cutoff:** Find the first incomplete checkbox (`- [ ]`). This is your entry point.
3.  **Synchronize:** Cross-reference the current codebase with the last completed checklist item to verify context before generating the next code block.
