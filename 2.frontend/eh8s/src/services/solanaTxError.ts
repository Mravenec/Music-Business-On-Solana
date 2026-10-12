/**
 * Turns Solana wallet / simulation failures into a short banner string.
 * Prefer Anchor / program log lines over the opaque "Internal error" wallets often throw.
 *
 * Wallet-adapter wraps the original failure in `.error` (not `.cause`).
 */

type LogCarrier = {
  message?: string;
  logs?: string[];
  getLogs?: () => string[] | Promise<string[]>;
  cause?: unknown;
  /** @solana/wallet-adapter-base WalletError stores the inner error here */
  error?: unknown;
  transactionError?: { message?: string; logs?: string[] };
};

/** Known Solana / Anchor custom codes we have hit in EH8S flows. */
const CUSTOM_ERROR_HINTS: Record<string, string> = {
  "0xbbd": "Not enough account keys (AccountNotEnoughKeys / 3005)",
  "3005": "Not enough account keys (AccountNotEnoughKeys)",
};

function isOpaque(msg: string): boolean {
  const t = msg.trim();
  if (!t) return true;
  if (/^internal error$/i.test(t)) return true;
  if (/^simulation failed\.?$/i.test(t)) return true;
  if (/failed to send transaction/i.test(t)) return true;
  if (/^wallet(send|sign)?transactionerror$/i.test(t)) return true;
  return false;
}

/** Walk wallet-adapter `.error` and standard `.cause` without looping. */
function unwrapChain(err: unknown): unknown[] {
  const out: unknown[] = [];
  const seen = new Set<unknown>();
  let cur: unknown = err;
  while (cur && typeof cur === "object" && !seen.has(cur)) {
    seen.add(cur);
    out.push(cur);
    const e = cur as LogCarrier;
    cur = e.error ?? e.cause ?? null;
  }
  return out;
}

function stripProgramPrefix(line: string): string {
  return line.replace(/^Program log:\s*/i, "").trim();
}

function hintFromCustomCode(text: string): string | null {
  const hex = text.match(/custom program error:\s*(0x[0-9a-f]+)/i);
  if (hex?.[1]) {
    const key = hex[1].toLowerCase();
    if (CUSTOM_ERROR_HINTS[key]) return CUSTOM_ERROR_HINTS[key];
  }
  const dec = text.match(/(?:custom program error|error(?:\s+number)?):\s*(\d{3,5})\b/i);
  if (dec?.[1] && CUSTOM_ERROR_HINTS[dec[1]]) return CUSTOM_ERROR_HINTS[dec[1]];
  return null;
}

/**
 * Prefer human Anchor / runtime lines; never stop on bare `failed: custom program error: 0x…`
 * when a clearer line exists earlier in the log list.
 */
function pickAnchorLine(logs: string[]): string | null {
  for (let i = logs.length - 1; i >= 0; i -= 1) {
    const line = stripProgramPrefix(logs[i] ?? "");
    const anchor = line.match(/Error Message:\s*(.+)$/i);
    if (anchor?.[1]) return anchor[1].trim();
  }
  for (let i = logs.length - 1; i >= 0; i -= 1) {
    const line = stripProgramPrefix(logs[i] ?? "");
    const custom = line.match(/Error Code:\s*(\w+).*Error Number:\s*(\d+)/i);
    if (custom) return `${custom[1]} (${custom[2]})`;
  }
  for (let i = logs.length - 1; i >= 0; i -= 1) {
    const line = stripProgramPrefix(logs[i] ?? "");
    // Human phrases only — bare "failed: custom program error: 0x…" is handled below.
    if (/AccountNotEnoughKeys|Not enough account keys|insufficient funds/i.test(line)) {
      return line;
    }
  }
  for (let i = logs.length - 1; i >= 0; i -= 1) {
    const hint = hintFromCustomCode(logs[i] ?? "");
    if (hint) return hint;
  }
  return null;
}

function collectLogs(err: unknown): string[] {
  for (const node of unwrapChain(err)) {
    const e = node as LogCarrier;
    const fromField = Array.isArray(e.logs)
      ? e.logs.filter((x): x is string => typeof x === "string")
      : [];
    if (fromField.length) return fromField;
    const fromTx = Array.isArray(e.transactionError?.logs)
      ? e.transactionError!.logs!.filter((x): x is string => typeof x === "string")
      : [];
    if (fromTx.length) return fromTx;
    if (typeof e.getLogs === "function") {
      try {
        const out = e.getLogs();
        if (Array.isArray(out)) return out.filter((x): x is string => typeof x === "string");
      } catch {
        /* sync getLogs may throw when logs were never attached / needs Connection */
      }
    }
    if (typeof e.message === "string") {
      const fromMsg = logsFromMessage(e.message);
      if (fromMsg.length) return fromMsg;
    }
  }
  return [];
}

/**
 * web3.js often appends `Logs: \n[...].` inside Error.message when `.logs` is missing.
 * Pretty-print uses `JSON.stringify(logs, undefined, 2)`, so the closing `]` is alone on
 * its line. A lazy `\[[\s\S]*?\]` stops at `invoke [1]` and breaks JSON.parse.
 */
function logsFromMessage(message: string): string[] {
  const m =
    message.match(/Logs:\s*(\[[\s\S]*?\n\])/) ?? message.match(/Logs:\s*(\[[\s\S]*\])/);
  if (!m?.[1]) return [];
  try {
    const parsed = JSON.parse(m[1]) as unknown;
    if (!Array.isArray(parsed)) return [];
    return parsed.filter((x): x is string => typeof x === "string");
  } catch {
    return [];
  }
}

function bestRawMessage(err: unknown, fallback: string): string {
  let opaqueHit: string | null = null;
  for (const node of unwrapChain(err)) {
    const e = node as LogCarrier;
    const candidates = [
      typeof e.transactionError?.message === "string" ? e.transactionError.message : null,
      node instanceof Error ? node.message : null,
      typeof e.message === "string" ? e.message : null,
    ].filter((x): x is string => !!x && x.trim().length > 0);
    for (const raw of candidates) {
      const msgLine = raw.match(/Message:\s*(.+?)(?:\.\s*$|\.\s*\n|\n)/i);
      if (msgLine?.[1] && !isOpaque(msgLine[1])) {
        return msgLine[1].trim();
      }
      const first = raw.split("\n")[0]?.trim() ?? raw.trim();
      if (!isOpaque(first) && first.length < 280) return first;
      if (isOpaque(first)) opaqueHit = first;
    }
  }
  if (typeof err === "string" && err.trim()) return err.trim();
  return opaqueHit || fallback;
}

/**
 * @param err wallet or web3.js error
 * @param fallback when nothing useful is present
 */
export function formatSolanaTxError(err: unknown, fallback = "Wallet transaction failed"): string {
  const logs = collectLogs(err);
  const fromLogs = logs.length ? pickAnchorLine(logs) : null;
  const raw = bestRawMessage(err, fallback);
  const opaque = isOpaque(raw);

  if (fromLogs) {
    return opaque ? fromLogs : `${raw} — ${fromLogs}`;
  }

  const fromRawCode = hintFromCustomCode(raw);
  if (fromRawCode && opaque) return fromRawCode;

  if (logs.length) {
    const last = stripProgramPrefix(logs[logs.length - 1] ?? "");
    const lastHint = hintFromCustomCode(last);
    if (lastHint) return lastHint;
    if (last && opaque && !/^failed:\s*custom program error/i.test(last)) return last;
  }

  if (opaque) {
    return "Wallet could not complete the transaction. Check the wallet dialog or try again.";
  }
  return raw || fallback;
}
