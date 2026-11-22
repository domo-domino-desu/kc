import { expect, test } from "bun:test";
import {
  accountIdentities,
  assertNoPrivateValues,
  assertNoAccountIdentities,
  cookieFrom,
  sanitize,
  sanitizeJson,
  shellWords,
} from "./fixture-tools";

test("Cookie accepts raw value and copied curl without executing shell syntax", () => {
  expect(cookieFrom("a=example-cookie; b=second-cookie")).toBe(
    "a=example-cookie; b=second-cookie",
  );
  const copiedCurl = [
    "curl https://example.invalid/ " + String.fromCharCode(92),
    "-H 'Cookie: a=example-cookie; b=second-cookie' --compressed",
  ].join("\n");
  expect(cookieFrom(copiedCurl)).toBe("a=example-cookie; b=second-cookie");
  expect(
    cookieFrom('curl https://example.invalid -b "a=$(touch /never-run)"'),
  ).toBe("a=$(touch /never-run)");
  expect(cookieFrom("curl x --header='cookie: a=example-cookie'")).toBe(
    "a=example-cookie",
  );
  expect(cookieFrom("curl x --cookie=a=example-cookie")).toBe(
    "a=example-cookie",
  );
  expect(() => cookieFrom("curl x -H 'Accept: text/html'")).toThrow();
  expect(() => cookieFrom("a=x\nHeader: injected")).toThrow();
  expect(() => shellWords("curl 'unfinished")).toThrow();
});

test("Redacts dynamically discovered accounts without changing HTML tags or SVG", async () => {
  for (const username of [
    crypto.randomUUID(),
    "user." + crypto.randomUUID(),
    "画师-" + crypto.randomUUID(),
    "body",
    "account",
    "fixture",
  ]) {
    const credential = crypto.randomUUID();
    const html = `<html><body data-user-blocklist="private-user" data-tag-blocklist="private-tag" data-tag-blocklist-nonce="private-nonce"><img class="loggedin_user_avatar" alt="${username}" src="/${username}.gif"><a href="/user/${username}/">${username}</a><input name="key" value="secret-form"><a href="/x?token=secret-url">x</a><a class="notification-container" title="25 Notifications">25S</a><svg><path d="M0 .192.3.4.5"/></svg><p>${credential}</p></body></html>`;
    const identities = await accountIdentities(html);
    expect(identities).toContain(username);
    const result = await sanitize(html, "a=" + credential);
    expect(await accountIdentities(result)).toEqual(["fixture-account"]);
    await assertNoPrivateValues(result, [credential]);
    for (const secret of [
      "private-user",
      "private-tag",
      "private-nonce",
      "secret-form",
      "secret-url",
      credential,
      "25S",
    ])
      expect(result).not.toContain(secret);
    expect(result).toContain('d="M0 .192.3.4.5"');
    expect(result).toContain("<body ");
    expect(result).toContain("</body>");
    expect(result).toContain("</html>");
    expect((result.match(/<a /g) ?? []).length).toBe(3);
    if (username.length > 8) expect(result).not.toContain(username);
  }
});

test("Privacy validation rejects discovered secrets without including them in errors", async () => {
  const secret = crypto.randomUUID();
  await expect(
    assertNoPrivateValues("<p>" + secret + "</p>", [secret]),
  ).rejects.toThrow("Privacy check failed; not saved");
  await expect(
    assertNoPrivateValues("<p>" + encodeURIComponent(secret + "@") + "</p>", [
      secret + "@",
    ]),
  ).rejects.toThrow("Privacy check failed; not saved");
});

test("Saved live pages are complete and contain no account secrets", async () => {
  const root = `${import.meta.dir}/../shared/src/commonTest/resources/fixture/live`;
  let count = 0;
  for (const file of new Bun.Glob("*.html").scanSync(root)) {
    const html = await Bun.file(`${root}/${file}`).text();
    expect(await Bun.file(`${root}/${file}.json`).exists()).toBe(false);
    expect(html).toMatch(/<\/html\s*>/i);
    expect(html).not.toMatch(/cf_clearance=/i);
    await new HTMLRewriter()
      .on("img.loggedin_user_avatar", {
        element(el) {
          expect(el.getAttribute("alt")).toBe("fixture-account");
        },
      })
      .on("body", {
        element(el) {
          for (const [name, value] of el.attributes)
            if (/^data-(?:user|tag)-blocklist/.test(name))
              expect(["", "REDACTED"]).toContain(value);
        },
      })
      .on('input[name="key"]', {
        element(el) {
          expect(el.getAttribute("value")).toBe("REDACTED");
        },
      })
      .transform(new Response(html))
      .text();
    count++;
  }
  expect(count).toBeGreaterThan(0);
});

test("Email masking preserves FA thumbnail URLs containing @", async () => {
  const thumbnail = "//t.furaffinity.net/12345678@300-1661390616.jpg";
  const html =
    '<html><body><img src="' +
    thumbnail +
    '"><p>person@example.com</p><p>IP: 198.51.100.9</p></body></html>';
  const result = await sanitize(html);
  expect(result).toContain(thumbnail);
  expect(result).not.toContain("person@example.com");
  expect(result).toContain("IP: 192.0.2.1");
});

test("Masking keeps entity spelling, noscript markup and inline JavaScript intact", async () => {
  const html =
    '<html><body><p>A &amp; B &lt; C</p><noscript><span class="fallback"></span></noscript><script>if (1 < 2) { window.ok = true; }</script></body></html>';
  expect(await sanitize(html)).toBe(html);
});

test("Account checks use extracted identities across text, attributes and comments", async () => {
  const username = crypto.randomUUID();
  const raw =
    '<html><body><img class="loggedin_user_avatar" alt="' +
    username +
    '"><p data-owner="' +
    username +
    '" class="u-' +
    username +
    '">' +
    username +
    "</p><!-- " +
    username +
    " --></body></html>";
  const identities = await accountIdentities(raw);
  await expect(assertNoAccountIdentities(raw, identities)).rejects.toThrow();
  const result = await sanitize(raw);
  await assertNoAccountIdentities(result, identities);
  expect(result).not.toContain(username);
  expect(result).toContain("<!-- fixture-account -->");
});

test("Complete API fixtures retain fields and redact temporary URL signatures", async () => {
  for (const name of [
    "post-temporary-images",
    "post-temporary-video",
    "post-temporary-archive",
    "post-unavailable-original",
  ]) {
    const root =
      import.meta.dir + "/../shared/src/commonTest/resources/fixture/live/";
    const body = await Bun.file(root + name + ".json").text();
    expect(await Bun.file(root + name + ".json.json").exists()).toBe(false);
    const post = JSON.parse(body);
    expect(post.id).toBeDefined();
    expect(post.attachments).toBeArray();
    for (const file of [post.file, ...post.attachments].filter(Boolean)) {
      for (const url of [file.temp_url, file.temp_download_url].filter(
        Boolean,
      )) {
        expect(new URL(url).searchParams.get("s")).toBe("REDACTED");
        expect(Number(new URL(url).searchParams.get("e"))).toBeGreaterThan(0);
      }
    }
  }
  const signature = crypto.randomUUID();
  const raw = JSON.stringify({
    content: 'A "quoted" line',
    file: {
      temp_url: "https://example.invalid/f/name?e=123&s=" + signature,
      temp_expires: "2026-12-23T00:00:00Z",
    },
    attachments: [],
  });
  const safe = sanitizeJson(raw);
  expect(safe).not.toContain(signature);
  expect(JSON.parse(safe).content).toBe(JSON.parse(raw).content);
  expect(JSON.parse(safe).file.temp_expires).toBe(
    JSON.parse(raw).file.temp_expires,
  );
});
