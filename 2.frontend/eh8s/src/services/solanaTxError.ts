/**
 * Turns Solana wallet / simulation failures into a short banner string.
 * Prefer Anchor / program log lines over the opaque "Internal error" wallets often throw.
 */

type LogCarrier = {
  message?: string;
  logs?: string[];
  getLogs?: () => string[] | Promise<string[]>;
};

function pickAnchorLine(logs: string[]): string | null {
  for (let i = logs.length - 1; i >= 0; i -= 1) {
    const line = logs[i];
    const anchor = line.match(/Error Message:\s*(.+)$/i);
    if (anchor?.[1]) return anchor[1].trim();
    const custom = line.match(/Error Code:\s*(\w+).*Error Number:\s*(\d+)/i);
    if (custom) return `${custom[1]} (${custom[2]})`;
    if (/AccountNotEnoughKeys|custom program error|failed: /i.test(line)) {
      return line.replace(/^Program log:\s*/i, "").trim();
    }
  }
  return null;
}

function collectLogs(err: unknown): string[] {
  if (!err || typeof err !== "object") return [];
  const e = err as LogCarrier & { cause?: unknown };
  const fromField = Array.isArray(e.logs) ? e.logs.filter((x) => typeof x === "string") : [];
  if (fromField.length) return fromField;
  if (typeof e.getLogs === "function") {
    try {
      const out = e.getLogs();
      if (Array.isArray(out)) return out.filter((x) => typeof x === "string");
    } catch {
      /* sync getLogs may throw when logs were never attached */
    }
  }
  if (e.cause) return collectLogs(e.cause);
  return [];
}

/**
 * @param err wallet or web3.js error
 * @param fallback when nothing useful is present
 */
export function formatSolanaTxError(err: unknown, fallback = "Wallet transaction failed"): string {
  const logs = collectLogs(err);
  const fromLogs = logs.length ? pickAnchorLine(logs) : null;
  const raw =
    err instanceof Error
      ? err.message
      : typeof err === "string"
        ? err
        : fallback;
  const opaque = !raw || /^internal error$/i.test(raw.trim()) || /failed to send transaction/i.test(raw);
  if (fromLogs) {
    return opaque ? fromLogs : `${raw} — ${fromLogs}`;
  }
  if (logs.length) {
    const last = logs[logs.length - 1]?.replace(/^Program log:\s*/i, "").trim();
    if (last && opaque) return last;
  }
  return raw || fallback;
}
