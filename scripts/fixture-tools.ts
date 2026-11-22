#!/usr/bin/env bun
import { mkdir, rename, rm } from "node:fs/promises";
import { join } from "node:path";

export function shellWords(source: string): string[] {
  const words: string[] = [];
  let word = "",
    quote = "",
    started = false;
  for (let i = 0; i < source.length; i++) {
    const c = source[i];
    if (quote === "'") {
      if (c === "'") quote = "";
      else word += c;
    } else if (c === "\\" && source[i + 1] === "\n") i++;
    else if (c === "\\" && quote !== "'") {
      const next = source[++i];
      if (next === undefined) throw Error("Incomplete curl quoting");
      if (quote === '"' && !["$", "`", '"', "\\"].includes(next)) word += "\\";
      word += next;
      started = true;
    } else if (quote === '"') {
      if (c === '"') quote = "";
      else word += c;
    } else if (c === "'" || c === '"') {
      quote = c;
      started = true;
    } else if (/\s/.test(c)) {
      if (started) {
        words.push(word);
        word = "";
        started = false;
      }
    } else {
      word += c;
      started = true;
    }
  }
  if (quote) throw Error("Incomplete curl quoting");
  if (started) words.push(word);
  return words;
}

export function cookieFrom(input: string): string {
  if (!/^\s*curl(?:\s|$)/.test(input)) return validateCookie(input.trim());
  const words = shellWords(input);
  let cookie: string | undefined;
  for (let i = 1; i < words.length; i++) {
    let header: string | undefined;
    if (words[i] === "-H" || words[i] === "--header") header = words[++i];
    else if (words[i].startsWith("--header=")) header = words[i].slice(9);
    else if (words[i].startsWith("-H")) header = words[i].slice(2);
    if (header && /^cookie\s*:/i.test(header))
      cookie = header.slice(header.indexOf(":") + 1).trim();
    if (words[i] === "-b" || words[i] === "--cookie") cookie = words[++i];
    else if (words[i]?.startsWith("--cookie=")) cookie = words[i].slice(9);
    else if (words[i]?.startsWith("-b")) cookie = words[i].slice(2);
  }
  if (!cookie) throw Error("No Cookie header found in curl input");
  return validateCookie(cookie);
}
function validateCookie(cookie: string): string {
  if (!cookie.includes("=") || /[\r\n\0]/.test(cookie))
    throw Error("Invalid Cookie string");
  return cookie;
}

const userAgent =
  "Mozilla/5.0 (X11; Linux x86_64; rv:159.0) Gecko/20100101 Firefox/159.0";
const defaults = [
  `User-Agent: ${userAgent}`,
  "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
  "Accept-Language: zh-CN,en-US;q=0.9,en;q=0.8",
  "Accept-Encoding: gzip, deflate, br, zstd",
  "Connection: keep-alive",
  "Upgrade-Insecure-Requests: 1",
  "Sec-Fetch-Dest: document",
  "Sec-Fetch-Mode: navigate",
  "Sec-Fetch-Site: none",
  "Priority: u=0, i",
];
function configString(value: string): string {
  if (/[\r\n\0]/.test(value)) throw Error("Invalid request value");
  return '"' + value.replaceAll("\\", "\\\\").replaceAll('"', '\\"') + '"';
}
export async function download(
  url: string,
  cookie?: string,
  format: "html" | "json" = "html",
): Promise<string> {
  // No shell execution, credential-bearing argv, stderr forwarding, redirect following or cookie jar.
  const config = [
    "silent",
    "compressed",
    "max-time = 45",
    'proto = "=https"',
    `url = ${configString(url)}`,
    ...defaults.map((h) => `header = ${configString(h)}`),
    ...(cookie ? [`header = ${configString(`Cookie: ${cookie}`)}`] : []),
    'write-out = "\\n%{http_code}"',
  ].join("\n");
  const process = Bun.spawn(["curl", "--disable", "--config", "-"], {
    stdin: new TextEncoder().encode(config),
    stdout: "pipe",
    stderr: "pipe",
  });
  const [response, , code] = await Promise.all([
    new Response(process.stdout).text(),
    new Response(process.stderr).text(),
    process.exited,
  ]);
  if (code)
    throw Error(`Transport failed (curl exit ${code}); response not saved`);
  const split = response.lastIndexOf("\n"),
    status = response.slice(split + 1),
    body = response.slice(0, split);
  if (status !== "200") throw Error(`HTTP ${status}; response not saved`);
  if (format === "json") {
    try {
      JSON.parse(body);
    } catch {
      throw Error("Invalid JSON response; not saved");
    }
    return body;
  }
  if (!/<html[\s>]/i.test(body) || !/<\/html\s*>/i.test(body))
    throw Error("Incomplete HTML response; not saved");
  if (
    /<title>\s*(?:Just a moment|Attention Required)|cf-chl-|challenge-platform/i.test(
      body,
    )
  )
    throw Error("Cloudflare challenge; response not saved");
  return body;
}
const escapeRegex = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

export async function accountIdentities(html: string): Promise<string[]> {
  const identities = new Set<string>();
  await new HTMLRewriter()
    .on(
      "a#my-username, #my-username a, a.loggedin_user, .loggedin_user a, img.loggedin_user_avatar",
      {
        element(el) {
          const username = el
            .getAttribute("href")
            ?.match(/\/user\/([^/?#]+)/i)?.[1];
          if (username) identities.add(decodeURIComponent(username));
          const avatarName =
            el.tagName === "img" ? el.getAttribute("alt")?.trim() : undefined;
          if (avatarName) identities.add(avatarName);
        },
      },
    )
    .transform(new Response(html))
    .text();
  return [...identities];
}

export async function assertNoPrivateValues(
  html: string,
  privateValues: string[],
): Promise<void> {
  // Values come from this response / request, never from a named real account.
  for (const value of privateValues.filter(
    (v) => v && v !== "fixture-account" && v !== "REDACTED",
  )) {
    for (const variant of new Set([value, encodeURIComponent(value)])) {
      if (html.toLowerCase().includes(variant.toLowerCase()))
        throw Error("Privacy check failed; not saved");
    }
  }
}

export async function assertNoAccountIdentities(
  html: string,
  identities: string[],
): Promise<void> {
  const privateValues = identities.filter(
    (value) => value !== "fixture-account",
  );
  const inspected: string[] = [];
  await new HTMLRewriter()
    .on("*", {
      element(el) {
        for (const [name, value] of el.attributes) {
          if (!["class", "id", "style"].includes(name)) inspected.push(value);
          else if (name !== "style") {
            // Generic CSS names are structural; FA's u-USER tokens identify accounts.
            for (const token of value.split(/\s+/))
              if (/^(?:u|user|account)-/.test(token)) inspected.push(token);
          }
        }
      },
    })
    .onDocument({
      text(chunk) {
        inspected.push(chunk.text);
      },
      comments(comment) {
        inspected.push(comment.text);
      },
    })
    .transform(new Response(html))
    .text();
  await assertNoPrivateValues(
    inspected.join("\n").replaceAll("fixture-account", ""),
    privateValues,
  );
}

export async function sanitize(html: string, cookie = ""): Promise<string> {
  const identities = await accountIdentities(html);
  const names = identities
    .filter((value) => value !== "fixture-account")
    .map(escapeRegex);
  const pattern = new RegExp(["fixture-account", ...names].join("|"), "gi");
  const anonymize = (value: string) =>
    names.length ? value.replace(pattern, "fixture-account") : value;
  // Rewrite values and text rather than HTML source: usernames such as "body"
  // must not rename tags, class names, or IDs.
  html = await new HTMLRewriter()
    .on("*", {
      element(el) {
        for (const [name, value] of Array.from(el.attributes)) {
          if (!["class", "id", "style"].includes(name)) {
            const replaced = anonymize(value);
            if (replaced !== value) el.setAttribute(name, replaced);
          } else if (name !== "style") {
            const replaced = value
              .split(/(\s+)/)
              .map((token) =>
                /^(?:u|user|account)-/.test(token) ? anonymize(token) : token,
              )
              .join("");
            if (replaced !== value) el.setAttribute(name, replaced);
          }
        }
      },
      text(chunk) {
        const replaced = anonymize(chunk.text);
        if (replaced !== chunk.text) chunk.replace(replaced, { html: true });
      },
    })
    .onDocument({
      comments(comment) {
        comment.text = anonymize(comment.text);
      },
    })
    .transform(new Response(html))
    .text();
  // Redact credential values even if echoed outside a known form or URL.
  const secrets = cookie
    .split(";")
    .map((p) => p.slice(p.indexOf("=") + 1).trim())
    .filter((s) => s.length >= 8);
  for (const secret of secrets) {
    for (const encoded of new Set([secret, encodeURIComponent(secret)]))
      html = html.replaceAll(encoded, "REDACTED");
  }
  const sensitive =
    /(?:csrf|token|auth|password|passwd|secret|session|email|api.?key|formkey|nonce|logout|^key$|^h$|^a$|^b$)/i;
  const cleaner = new HTMLRewriter()
    .on("input", {
      element(el) {
        if (
          sensitive.test(el.getAttribute("name") ?? "") ||
          /^(password|email)$/i.test(el.getAttribute("type") ?? "")
        )
          el.setAttribute("value", "REDACTED");
      },
    })
    .on("meta", {
      element(el) {
        if (sensitive.test(el.getAttribute("name") ?? ""))
          el.setAttribute("content", "REDACTED");
      },
    })
    .on("body", {
      element(el) {
        for (const [name] of Array.from(el.attributes))
          if (/^data-(?:user|tag)-blocklist/.test(name))
            el.setAttribute(name, name.endsWith("nonce") ? "REDACTED" : "");
      },
    })
    .on("a.notification-container", {
      element(el) {
        const title = el.getAttribute("title");
        if (title) el.setAttribute("title", title.replace(/[\d,]+/g, "0"));
      },
      text(chunk) {
        chunk.replace(chunk.text.replace(/[\d,]+/g, "0"));
      },
    })
    .on("*", {
      element(el) {
        for (const [name, value] of Array.from(el.attributes)) {
          if (/^data-/i.test(name) && sensitive.test(name))
            el.setAttribute(name, "REDACTED");
          if (["href", "src", "action", "data-url"].includes(name)) {
            let clean = value.replace(
              /([?&](?:amp;)?(?:key|token|auth|csrf|session|secret|signature|sig|s|api_key|logout|h))=[^&#"\s]*/gi,
              "$1=REDACTED",
            );
            clean = clean.replace(/(\/logout\/)[^/?#]+/gi, "$1REDACTED");
            if (/^mailto:/i.test(clean))
              clean = "mailto:fixture@example.invalid";
            if (clean !== value) el.setAttribute(name, clean);
          }
        }
      },
    });
  html = await cleaner.transform(new Response(html)).text();
  html = html
    .replace(
      /(?<![\w./%+-])[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi,
      "fixture@example.invalid",
    )
    .replace(
      /(\bIP(?: address)?\s*[:=]\s*)(?:\d{1,3}\.){3}\d{1,3}\b/gi,
      (_, prefix) => prefix + "192.0.2.1",
    )
    .replace(
      /((?:csrf|auth|session|api)[_-]?token["']?\s*[:=]\s*["'])[^"']*/gi,
      "$1REDACTED",
    );
  // Check the extracted identities in visible text / URL attributes, without mistaking
  // a short username for a structural HTML tag or a word in another public account.
  const remainingIdentities = await accountIdentities(html);
  if (remainingIdentities.some((value) => value !== "fixture-account"))
    throw Error("Account privacy check failed; not saved");
  await assertNoPrivateValues(html, secrets);
  await assertNoAccountIdentities(html, identities);
  return html;
}

export function sanitizeJson(body: string): string {
  JSON.parse(body);
  // Preserve the full JSON shape, field order and whitespace; redact string values only.
  return body.replace(/"(?:[^"\\]|\\.)*"/g, (literal: string) => {
    const value = JSON.parse(literal);
    if (typeof value !== "string" || !/^https?:\/\//i.test(value))
      return literal;
    const safe = value.replace(
      /([?&](?:s|sig|signature|token|auth|key|api_key))=[^&#]*/gi,
      "$1=REDACTED",
    );
    return safe === value ? literal : JSON.stringify(safe);
  });
}

export type FixtureTarget = {
  file: string;
  path: string;
  format?: "html" | "json";
};
export async function run(origin: string, targets: FixtureTarget[]) {
  const args = Bun.argv.slice(2);
  let cookie: string | undefined, only: string | undefined;
  for (let i = 0; i < args.length; i++) {
    if (args[i] === "--help") {
      console.log(
        "bun scripts/update-fixtures.ts [--cookie COOKIE_OR_CURL | --cookie -] [--only FILE]\n--cookie - reads Cookie or complete curl from stdin. No curl command is executed.\nOnly complete HTTP 200 HTML/JSON is saved. Credentials are never stored.",
      );
      return;
    } else if (args[i] === "--cookie") {
      const input = args[++i];
      if (!input) throw Error("Missing --cookie value");
      cookie = cookieFrom(input === "-" ? await Bun.stdin.text() : input);
    } else if (args[i] === "--only") {
      only = args[++i];
      if (!only) throw Error("Missing --only value");
    } else throw Error("Unknown argument; use --help");
  }
  if (origin.includes("furaffinity") && !cookie)
    throw Error("FA refresh requires --cookie COOKIE_OR_CURL (or --cookie -)");
  const selected = targets.filter((t) => !only || t.file === only);
  if (!selected.length) throw Error("Unknown fixture name");
  const directory = join(
    import.meta.dir,
    "../shared/src/commonTest/resources/fixture/live",
  );
  await mkdir(directory, { recursive: true });
  let failed = 0;
  for (const target of selected) {
    try {
      const url = new URL(target.path, origin);
      if (url.origin !== origin) throw Error("Cross-origin target refused");
      const body = await download(url.href, cookie, target.format);
      const html =
        target.format === "json"
          ? sanitizeJson(body)
          : await sanitize(body, cookie);
      const destination = join(directory, target.file);
      const temporary = destination + ".tmp";
      await Bun.write(temporary, html);
      await rename(temporary, destination);
      await rm(destination + ".json", { force: true });
      console.log(`Updated ${target.file}`);
    } catch (error) {
      failed++;
      console.error(
        `${target.file}: ${error instanceof Error ? error.message : "Failed"}`,
      );
    }
    await Bun.sleep(1100);
  }
  if (failed) process.exitCode = 1;
}
