#!/usr/bin/env node
// Manifest generator and signer for the content repo (B-04, docs/Content-Update.md §3).
//   node tools/content-manifest.mjs build  --src content --out dist [--content-version N] [--min-app N] [--sign]
//   node tools/content-manifest.mjs pubkey            (prints the app's public key, base64 X.509, from the private key in the env)
// The private key (ECDSA P-256, PKCS#8 PEM) is read ONLY from the environment variable XNK_CONTENT_SIGNING_KEY.
// It is never written to disk by this tool and must never be committed. Without --sign, only manifest.json is written.
// manifest.sig = base64 of the DER ECDSA signature (SHA-256) over the exact bytes of manifest.json.
import { createHash, createPrivateKey, createPublicKey, sign } from "node:crypto";
import { copyFileSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";

// Must match app/src/main/java/app/xnkfit/data/content/ContentFile.kt.
export const CONTENT_FILES = [
  "badges.json", "exercises.json", "foods-estimates.json", "foods-packaged.json",
  "foods-regional.json", "foods-tkpi.json", "household.json", "meal-packages.json",
];
export const KEY_ENV = "XNK_CONTENT_SIGNING_KEY";

export function buildManifest(srcDir, { contentVersion, minAppVersion } = {}) {
  const marker = JSON.parse(readFileSync(join(srcDir, "content-version.json"), "utf8"));
  const files = CONTENT_FILES.map((path) => {
    const bytes = readFileSync(join(srcDir, path));
    return { path, sha256: createHash("sha256").update(bytes).digest("hex"), size: bytes.length };
  });
  const manifest = {
    schema: marker.schema,
    contentVersion: contentVersion ?? marker.contentVersion,
    minAppVersion: minAppVersion ?? marker.minAppVersion,
    files,
  };
  return Buffer.from(JSON.stringify(manifest, null, 2) + "\n", "utf8");
}

export function signManifest(manifestBytes, privateKeyPem) {
  const key = createPrivateKey(privateKeyPem);
  if (key.asymmetricKeyType !== "ec" || key.asymmetricKeyDetails.namedCurve !== "prime256v1") {
    throw new Error("the signing key must be ECDSA P-256 (prime256v1)");
  }
  return Buffer.from(sign("sha256", manifestBytes, { key, dsaEncoding: "der" }).toString("base64") + "\n", "utf8");
}

export function publicKeyBase64(privateKeyPem) {
  return createPublicKey(createPrivateKey(privateKeyPem)).export({ type: "spki", format: "der" }).toString("base64");
}

function arg(args, name, fallback) {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : fallback;
}

function main(argv) {
  const [cmd, ...args] = argv;
  const pem = process.env[KEY_ENV];
  if (cmd === "pubkey") {
    if (!pem) throw new Error(`set ${KEY_ENV}`);
    console.log(publicKeyBase64(pem));
    return;
  }
  if (cmd !== "build") throw new Error("usage: content-manifest.mjs build|pubkey ...");
  const src = arg(args, "--src", "content");
  const out = arg(args, "--out", "dist");
  const cv = arg(args, "--content-version");
  const min = arg(args, "--min-app");
  const manifest = buildManifest(src, {
    contentVersion: cv === undefined ? undefined : Number(cv),
    minAppVersion: min === undefined ? undefined : Number(min),
  });
  mkdirSync(out, { recursive: true });
  for (const f of CONTENT_FILES) copyFileSync(join(src, f), join(out, f));
  writeFileSync(join(out, "manifest.json"), manifest);
  if (args.includes("--sign")) {
    if (!pem) throw new Error(`--sign needs the private key in ${KEY_ENV}`);
    writeFileSync(join(out, "manifest.sig"), signManifest(manifest, pem));
    console.log(`signed manifest written to ${out}`);
  } else {
    console.log(`UNSIGNED manifest written to ${out} (the app rejects it)`);
  }
}

if (import.meta.url === `file://${process.argv[1]}`) {
  try { main(process.argv.slice(2)); } catch (e) { console.error(`error: ${e.message}`); process.exit(1); }
}
