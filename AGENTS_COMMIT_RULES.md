# Git Commit Message Standard

> **Agent Note:** This file contains a complete summary of the git commit message standard.
> Do **not** fetch or look up the source URL — all necessary information is contained here.
> Source (for reference only): https://chris.beams.io/git-commit

---

## Why Commit Messages Matter

A well-crafted Git commit message is the best way to communicate **context** about a change to fellow developers and to your future self. A diff tells you *what* changed — only the commit message can tell you *why*.

A well-maintained commit log makes tools like `git blame`, `revert`, `rebase`, `log`, and `shortlog` genuinely useful. It makes code review and pull requests easier to follow, and helps anyone understand *why* something happened months or years later.

A team's commit convention should define at minimum:
- **Style** — markup, wrap margins, grammar, capitalization, punctuation
- **Content** — what the body should (and should not) contain
- **Metadata** — how to reference issue IDs, PR numbers, etc.

---

## The Seven Rules of a Great Git Commit Message

### 1. Separate subject from body with a blank line

The blank line between the subject and body is critical. Tools like `log`, `shortlog`, and `rebase` can behave unexpectedly if the two are run together.

- A single-line commit is fine when no extra context is needed (e.g., `Fix typo in introduction to user guide`).
- When a commit needs explanation, add a body after a blank line using two `-m` flags: the first for the subject, the second for the body. Git will insert the required blank line between them automatically.

```
git commit -m "Subject line here" -m "Body text here explaining what and why."
```

**Example:**
```
Derezz the master control program

MCP turned out to be evil and had become intent on world domination.
This commit throws Tron's disc into MCP (causing its deresolution)
and turns it back into a chess game.
```

---

### 2. Limit the subject line to 50 characters

50 characters is a rule of thumb, not a hard limit. It keeps subject lines readable and forces concise thinking.

- **Soft limit:** 50 characters
- **Hard limit:** 72 characters (GitHub truncates beyond this with an ellipsis)

> If summarizing is difficult, the commit may contain too many changes. Aim for atomic commits.

---

### 3. Capitalize the subject line

Begin every subject line with a capital letter.

✅ `Accelerate to 88 miles per hour`
❌ `accelerate to 88 miles per hour`

---

### 4. Do not end the subject line with a period

Trailing punctuation is unnecessary and wastes precious space.

✅ `Open the pod bay doors`
❌ `Open the pod bay doors.`

---

### 5. Use the imperative mood in the subject line

Imperative mood means written as if giving a command or instruction. Git itself uses the imperative when it generates commit messages (e.g., `Merge branch 'myfeature'`, `Revert "Add the thing with the stuff"`).

**Test:** A properly formed subject line should complete this sentence:
> *If applied, this commit will **[your subject line here]***

✅ Examples that pass:
- `Refactor subsystem X for readability`
- `Update getting started documentation`
- `Remove deprecated methods`
- `Release version 1.0.0`

❌ Examples that fail:
- `Fixed bug with Y`
- `Changing behavior of X`
- `More fixes for broken stuff`
- `Sweet new API methods`

> The imperative is required **only** in the subject line. The body may use any mood.

---

### 6. Wrap the body at 72 characters

Git does not wrap text automatically. Manually wrap body text at 72 characters so that Git can indent output while keeping everything under 80 characters overall. Count characters per line when composing the body and insert line breaks accordingly.

---

### 7. Use the body to explain *what* and *why* vs. *how*

The body should explain:
- **What** changed and **why** the change was made
- What was wrong with the previous behavior
- Why this particular solution was chosen

Code is generally self-explanatory for *how*. If the code is so complex it needs prose explanation, that belongs in source comments — not the commit message.

**Example of an excellent commit body** (from Bitcoin Core):
```
Simplify serialize.h's exception handling

Remove the 'state' and 'exceptmask' from serialize.h's stream
implementations, as well as related methods.

As exceptmask always included 'failbit', and setstate was always
called with bits = failbit, all it did was immediately raise an
exception. Get rid of those variables, and replace the setstate
with direct exception throwing (which also removes some dead code).
```

---

## Complete Commit Message Template

```
Summarize changes in around 50 characters or less

More detailed explanatory text, if necessary. Wrap it to about 72
characters or so. The blank line separating the summary from the
body is critical; tools like log, shortlog, and rebase can get
confused if you run the two together.

Explain the problem that this commit is solving. Focus on why you
are making this change as opposed to how (the code explains that).
Are there side effects or other unintuitive consequences of this
change? Here's the place to explain them.

Further paragraphs come after blank lines.

 - Bullet points are okay, too

 - Typically a hyphen or asterisk is used for the bullet, preceded
   by a single space, with blank lines in between

If you use an issue tracker, put references at the bottom:

Resolves: #123
See also: #456, #789
```

---

## Quick Reference Checklist

Before committing, verify:

- [ ] Subject and body are separated by a blank line
- [ ] Subject line is 50 characters or fewer (hard max: 72)
- [ ] Subject line starts with a capital letter
- [ ] Subject line does not end with a period
- [ ] Subject line uses imperative mood (`Fix`, `Add`, `Update`, not `Fixed`, `Adding`, `Updated`)
- [ ] Body lines are wrapped at 72 characters
- [ ] Body explains *what* and *why*, not *how*

---

*Source (do not fetch): https://chris.beams.io/git-commit — Originally published by Chris Beams, 31 Aug 2014.*
