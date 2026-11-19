// Tests for tools/content-manifest.mjs with a throwaway key made in memory (no key is stored anywhere).
// Run: node --test tools/test-content-manifest.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { createHash, generateKeyPairSync, verify } from "node:crypto";
import { mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { CONTENT_FILES, buildManifest, publicKeyBase64, signManifest } from "./content-manifest.mjs";

function fixtureDir() {
  const dir = mkdtempSync(join(tmpdir(), "xnk-content-"));
  writeFileSync(join(dir, "content-version.json"), JSON.stringify({ contentVersion: 7, minAppVersion: 2, schema: 1 }));
  for (const f of CONTENT_FILES) writeFileSync(join(dir, f), JSON.stringify({ file: f }));
  return dir;
}

function testKey() {
  return generateKeyPairSync("ec", { namedCurve: "prime256v1" })
    .privateKey.export({ type: "pkcs8", format: "pem" });
}

test("manifest lists every file with sha256 and size, in a fixed order", () => {
  const dir = fixtureDir();
  const m = JSON.parse(buildManifest(dir).toString());
  assert.deepEqual([m.schema, m.contentVersion, m.minAppVersion], [1, 7, 2]);
  assert.deepEqual(m.files.map((f) => f.path), CONTENT_FILES);
  const f = m.files[0];
  const bytes = readFileSync(join(dir, f.path));
  assert.equal(f.sha256, createHash("sha256").update(bytes).digest("hex"));
  assert.equal(f.size, bytes.length);
  assert.deepEqual(buildManifest(dir), buildManifest(dir), "same input, same bytes");
});

test("signature verifies with the public key, and fails on a changed byte or another key", () => {
  const pem = testKey();
  const manifest = buildManifest(fixtureDir());
  const sig = signManifest(manifest, pem);
  const pub = { key: Buffer.from(publicKeyBase64(pem), "base64"), format: "der", type: "spki" };
  const der = Buffer.from(sig.toString().trim(), "base64");
  assert.ok(verify("sha256", manifest, { ...pub, dsaEncoding: "der" }, der));
  const tampered = Buffer.from(manifest);
  tampered[tampered.length - 3] ^= 1;
  assert.ok(!verify("sha256", tampered, { ...pub, dsaEncoding: "der" }, der));
  const otherPub = { key: Buffer.from(publicKeyBase64(testKey()), "base64"), format: "der", type: "spki" };
  assert.ok(!verify("sha256", manifest, { ...otherPub, dsaEncoding: "der" }, der));
});

test("a key that is not P-256 is refused", () => {
  const pem = generateKeyPairSync("ec", { namedCurve: "secp384r1" }).privateKey.export({ type: "pkcs8", format: "pem" });
  assert.throws(() => signManifest(Buffer.from("x"), pem), /P-256/);
});

test("the real content/ folder builds a manifest of the shipped files", () => {
  const m = JSON.parse(buildManifest("content").toString());
  assert.equal(m.files.length, CONTENT_FILES.length);
  assert.ok(m.files.every((f) => f.size > 0 && /^[0-9a-f]{64}$/.test(f.sha256)));
});
