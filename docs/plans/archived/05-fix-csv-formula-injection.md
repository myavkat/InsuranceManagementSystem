# Plan: Fix CSV Formula Injection (data-table.tsx)

**Source:** Sprint 8 code review — finding 7

---

## Objective

Fix a CSV formula injection vulnerability in the DataTable's `escapeCsvField` helper. Cells starting with `=`, `+`, `-`, or `@` are not sanitized, allowing spreadsheet formula execution when the exported CSV is opened in Excel, Google Sheets, or LibreOffice.

---

## Severity

🟠 **Medium** — Security. User-entered data in customer names, addresses, plate numbers, etc. can contain formula payloads that execute when the CSV is opened.

---

## Files to Read First

| File | Why |
|------|-----|
| `frontend-next/src/components/features/data-table/data-table.tsx` | The file being fixed — see `escapeCsvField` helper (bottom of file) and `handleCsvExport` function |

---

## Steps

### Finding — Fix: CSV formula injection (escapeCsvField helper, approx line 355)

**The bug**: The `escapeCsvField` function only quotes fields containing commas, double quotes, or newlines. It does NOT handle cells starting with formula-trigger characters: `=` (formula), `+` (formula), `-` (formula), `@` (formula in some locales). When exported, a cell value like `=HYPERLINK("http://evil.com","Click")` or `+1+1` is interpreted as an executable formula by spreadsheet applications.

**The fix**: Prepend a single quote (`'`) to any cell value that starts with a formula-trigger character. Spreadsheet applications interpret the leading single quote as a "treat this as text" marker.

The standard formula-trigger characters are: `=`, `+`, `-`, `@`, `\t` (tab), `\r` (carriage return).

**Exact change** — Locate the `escapeCsvField` function at the bottom of `data-table.tsx`. It currently looks like:

```typescript
function escapeCsvField(value: string): string {
  if (value.includes(",") || value.includes('"') || value.includes("\n")) {
    return `"${value.replace(/"/g, '""')}"`;
  }
  return value;
}
```

Replace it with:

```typescript
function escapeCsvField(value: string): string {
  // Prevent CSV formula injection: prefix with a tab character
  // when the value starts with a formula-trigger character.
  // Spreadsheet apps treat cells starting with '=', '+', '-', '@',
  // tab, or carriage return as formulas unless quoted or prefixed.
  // The tab prefix (U+0009) makes the cell render as literal text
  // in Excel, Google Sheets, and LibreOffice without visible artifact.
  if (/^[=+\-@\t\r]/.test(value)) {
    value = "\t" + value;
  }

  if (value.includes(",") || value.includes('"') || value.includes("\n")) {
    return `"${value.replace(/"/g, '""')}"`;
  }
  return value;
}
```

### Note on the fix approach

The tab prefix (`\t`, U+0009) is the recommended mitigation because:
1. It prevents formula execution in all major spreadsheet apps
2. It does NOT visibly alter the cell content (unlike a visible single quote `'`)
3. It is compatible with all CSV consumers that handle multi-line/escaped fields
4. It's the approach recommended by OWASP

---

## Acceptance Criteria

- [x] Export CSV from any data table (customers, estimations, insurances, vehicles, real-estate)
- [x] Open CSV in Excel: cells starting with `=`, `+`, `-`, `@` display as literal text, NOT as formulas
- [x] Open CSV in Google Sheets: same — no formula execution
- [x] Normal text cells (no formula prefixes) are unaffected and display correctly
- [x] Cells containing commas, double quotes, and newlines are still properly escaped (no regression)

---

## Dependencies

None — this plan is self-contained.
