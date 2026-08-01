import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

async function render() {
  const workerUrl = new URL("../dist/server/index.js", import.meta.url);
  workerUrl.searchParams.set("test", `${process.pid}-${Date.now()}`);
  const { default: worker } = await import(workerUrl.href);

  return worker.fetch(
    new Request("http://localhost/", {
      headers: { accept: "text/html" },
    }),
    {
      ASSETS: {
        fetch: async () => new Response("Not found", { status: 404 }),
      },
    },
    {
      waitUntil() {},
      passThroughOnException() {},
    },
  );
}

test("server-renders the bilingual privacy policy", async () => {
  const response = await render();
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type") ?? "", /^text\/html\b/i);
  assert.match(
    response.headers.get("content-security-policy") ?? "",
    /frame-ancestors 'none'/,
  );
  assert.equal(response.headers.get("x-content-type-options"), "nosniff");
  assert.equal(response.headers.get("referrer-policy"), "no-referrer");

  const html = await response.text();
  assert.match(html, /<title>Privacy — 站桩 · Zhan Zhuang<\/title>/i);
  assert.match(html, /Privacy Policy/);
  assert.match(html, /隐私政策/);
  assert.match(html, /app\.zhanzhuang\.timer/);
  assert.match(html, /Health Connect/);
  assert.match(html, /does not request location/);
  assert.doesNotMatch(html, /codex-preview|react-loading-skeleton/i);
});

test("uses the approved Urticad-inspired palette", async () => {
  const css = await readFile(new URL("../app/globals.css", import.meta.url), "utf8");

  for (const color of [
    "#15110b",
    "#241c12",
    "#fbf8f1",
    "#e7d5a6",
    "#c6a867",
    "#a9863f",
    "#d8c18c",
    "#fff4d6",
  ]) {
    assert.match(css, new RegExp(color, "i"));
  }
});
