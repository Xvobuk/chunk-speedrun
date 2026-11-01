const REPOSITORY = "Xvobuk/chunk-speedrun";
const RULES = "26.3-hard-air-bedrock-v1";
const MAX_TIME = 10 * 365 * 24 * 3600 * 1000;
const windows = new Map();
const inFlight = new Map();

class HttpError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}

function reply(status, body, headers = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store", ...headers },
  });
}

export function validate(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new HttpError(400, "Invalid result");
  const { nickname, activeMillis, totalMillis, rules } = value;
  if (typeof value.attemptId !== "string" || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value.attemptId)) throw new HttpError(400, "Invalid attempt ID");
  if (typeof nickname !== "string" || !/^[A-Za-z0-9_]{3,16}$/.test(nickname)) throw new HttpError(400, "Invalid nickname");
  if (!Number.isSafeInteger(activeMillis) || !Number.isSafeInteger(totalMillis) || activeMillis <= 0 || activeMillis > totalMillis || totalMillis > MAX_TIME || rules !== RULES) throw new HttpError(400, "Invalid times or rules");
  // Client-supplied status, labels, repository, URLs and other fields are discarded.
  return { attemptId: value.attemptId.toLowerCase(), nickname, activeMillis, totalMillis, rules };
}

function sameResult(a, b) {
  return ["attemptId", "nickname", "activeMillis", "totalMillis", "rules"].every(key => a[key] === b[key]);
}

async function readResult(request) {
  if (Number(request.headers.get("Content-Length")) > 4096) throw new HttpError(413, "Body too large");
  if (!request.body) throw new HttpError(400, "Missing result");
  const reader = request.body.getReader();
  const chunks = []; let total = 0;
  while (true) {
    const { value, done } = await reader.read(); if (done) break;
    total += value.byteLength;
    if (total > 4096) { await reader.cancel(); throw new HttpError(413, "Body too large"); }
    chunks.push(value);
  }
  const bytes = new Uint8Array(total); let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
  try { return validate(JSON.parse(new TextDecoder("utf-8", { fatal: true, ignoreBOM: false }).decode(bytes))); }
  catch (error) { if (error instanceof HttpError) throw error; throw new HttpError(400, "Invalid JSON"); }
}

function limit(request) {
  const now = Date.now(); const hour = Math.floor(now / 3600000);
  // Best-effort per-isolate limits. No IP addresses are written to storage or logs.
  // Cloudflare supplies this header; the public caller cannot select the GitHub target.
  const peer = request.headers.get("CF-Connecting-IP") || "unknown";
  if (windows.size > 10000) for (const [key, value] of windows) if (value.hour !== hour) windows.delete(key);
  for (const [key, max] of [[`ip:${peer}`, 30], ["global", 240]]) {
    const value = windows.get(key);
    if (value?.hour === hour && value.count >= max) throw new HttpError(429, "Try again later");
  }
  for (const key of [`ip:${peer}`, "global"]) {
    const value = windows.get(key);
    windows.set(key, { hour, count: value?.hour === hour ? value.count + 1 : 1 });
  }
}

async function github(path, env, method = "GET", body) {
  let response;
  try {
    response = await fetch(`https://api.github.com/repos/${REPOSITORY}/${path}`, {
      method, redirect: "manual", signal: AbortSignal.timeout(15000),
      headers: {
        "Authorization": `Bearer ${env.GITHUB_TOKEN}`,
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2026-03-10",
        "User-Agent": "ChunkSpeedrun-Cloudflare-Receiver",
        ...(body ? { "Content-Type": "application/json" } : {}),
      },
      ...(body ? { body: JSON.stringify(body) } : {}),
    });
  } catch { throw new HttpError(503, "GitHub unavailable; retry later"); }
  if (!response.ok) throw new HttpError(503, `GitHub unavailable (HTTP ${response.status}); retry later`);
  try { return await response.json(); } catch { throw new HttpError(503, "Invalid GitHub response"); }
}

function existingResult(issue, row) {
  try {
    const text = (issue.body || "").replaceAll("\r\n", "\n").trim();
    if (!text.startsWith("```json\n") || !text.endsWith("\n```")) throw new Error();
    const prior = validate(JSON.parse(text.slice(8, -4)));
    if (!sameResult(prior, row)) throw new Error();
    return { code: 200, issue: issue.html_url, status: "unverified" };
  } catch { throw new HttpError(409, "Attempt already exists with different data"); }
}

async function submit(row, env) {
  // Consult the immutable repository ledger first, including old attempts.
  try {
    const response = await fetch(`https://raw.githubusercontent.com/${REPOSITORY}/main/records.json`, { redirect: "manual", signal: AbortSignal.timeout(10000) });
    if (response.ok) {
      const ledger = await response.json(); const prior = ledger[row.attemptId];
      if (prior) {
        if (!sameResult(prior, row)) throw new HttpError(409, "Attempt already exists with different data");
        return { code: 200, issue: `https://github.com/${REPOSITORY}/issues/${prior.issue}`, status: "unverified" };
      }
    }
  } catch (error) { if (error instanceof HttpError) throw error; }
  const title = `ChunkSpeedrun result ${row.attemptId}`;
  // Listing recent issues avoids the indexing delay of GitHub issue search.
  const recent = await github("issues?state=all&per_page=100&sort=created&direction=desc", env);
  const existing = recent.find(issue => !issue.pull_request && issue.title === title);
  if (existing) return existingResult(existing, row);
  const issue = await github("issues", env, "POST", {
    title, body: "```json\n" + JSON.stringify(row, null, 2) + "\n```\n",
  });
  return { code: 201, issue: issue.html_url, status: "unverified" };
}

export default {
  async fetch(request, env) {
    const path = new URL(request.url).pathname;
    if (request.method === "GET" && path === "/health") return reply(200, { status: env.GITHUB_TOKEN ? "ok" : "needs-configuration", repository: REPOSITORY });
    if (path !== "/results") return reply(404, { error: "Not found" });
    if (request.method !== "POST") return reply(405, { error: "POST required" }, { Allow: "POST" });
    try {
      const row = await readResult(request);
      if (!env.GITHUB_TOKEN) throw new HttpError(503, "Receiver is not configured; keep local result and retry later");
      limit(request);
      const current = inFlight.get(row.attemptId);
      let result;
      if (current) {
        if (!sameResult(current.row, row)) throw new HttpError(409, "Attempt already exists with different data");
        result = await current.promise;
      } else {
        const promise = submit(row, env); inFlight.set(row.attemptId, { row, promise });
        try { result = await promise; } finally { inFlight.delete(row.attemptId); }
      }
      return reply(result.code, { issue: result.issue, status: result.status });
    } catch (error) {
      const status = error instanceof HttpError ? error.status : 503;
      return reply(status, { error: error instanceof HttpError ? error.message : "Receiver temporarily unavailable; retry later" }, status === 429 ? { "Retry-After": "3600" } : {});
    }
  },
};
