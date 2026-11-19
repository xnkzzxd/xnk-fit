// Builds content/figures/rig-local.json from rig-local.src.json (task C-08b).
// Usage: node content/figures/build-rig-local.mjs   (writes rig-local.json)
// tools/check-figures.mjs imports build() and fails when rig-local.json differs from a fresh build.
//
// Output uses the XNK rig format (xnk-source/rig.min.json: segments "s" = [x, y, angle], joint angles "j",
// points "p", cables "c"; 13 frames, cosine easing) and XNK's own len / w / muscle marks, so one renderer
// draws both. Two kinds of entries:
//  - a new pattern ("joints" in the source): forward kinematics from a root point and the joint angles of
//    the source table; only the joints listed under "move" change between frames.
//  - a variant ("rig" + "variant"): the frames of its parent (XNK or local) unchanged, with other equipment;
//    optionally the arms are re-posed ("arms"). The checker fails if any joint the parent animates differs.
import { readFileSync, writeFileSync } from "node:fs";

const N = 13;
const r1 = (v) => Math.round(v * 10) / 10;
const rad = (a) => (a * Math.PI) / 180;
const pt = (o, len, a) => [o[0] + len * Math.cos(rad(a)), o[1] + len * Math.sin(rad(a))];
const ease = (i) => (1 - Math.cos((Math.PI * i) / (N - 1))) / 2;
const norm180 = (a) => { let x = ((a % 360) + 360) % 360; return x > 180 ? x - 360 : x; };

// XNK joint conventions (Gerak.md, checked against rig.min.json frames):
// knee = shin - thigh; hip = 180 - |thigh - torso|; elbow = |fa - ua|; shoulder = (torso + 180) - ua;
// ankle = 90 - (shin - foot). 0 = straight; arm 0 = at the side, 180 = overhead.
function jointsOf(s) {
  return {
    knee: Math.round(Math.abs(norm180(s.shin[2] - s.thigh[2]))),
    hip: Math.round(180 - Math.abs(norm180(s.thigh[2] - s.torso[2]))),
    elbow: Math.round(Math.abs(norm180(s.fa[2] - s.ua[2]))),
    shoulder: Math.round(norm180(s.torso[2] + 180 - s.ua[2])),
    ankle: Math.round(norm180(90 - (s.shin[2] - s.foot[2]))),
  };
}

function arm(shoulder, torsoA, spec) {
  // spec.abs = [ua, fa] absolute segment angles, or spec.rel = [shoulder, elbow] joint angles.
  const ua = spec.abs ? spec.abs[0] : torsoA + 180 - spec.rel[0];
  const fa = spec.abs ? spec.abs[1] : ua - spec.rel[1];
  const elbow = pt(shoulder, 0, 0), e = pt(shoulder, L.ua, ua), w = pt(e, L.fa, fa);
  return { ua: [r1(elbow[0]), r1(elbow[1]), r1(ua)], fa: [r1(e[0]), r1(e[1]), r1(fa)], elbow: [r1(e[0]), r1(e[1])], wrist: [r1(w[0]), r1(w[1])] };
}

let L;
function sideFrame(def, t) {
  const at = (name) => { const v = def.joints[name]; return Array.isArray(v) ? v[0] + (v[1] - v[0]) * t : v; };
  const T = at("torso"), Th = at("thigh"), knee = at("knee"), ankle = at("ankle");
  const sh = at("shoulder"), el = at("elbow");
  const shinA = Th + knee, footA = shinA - 90 + ankle, uaA = T + 180 - sh, faA = uaA - el;
  const hip = def.root, shoulder = pt(hip, L.torso, T), headP = pt(shoulder, L.neck, T);
  const knee_ = pt(hip, L.thigh, Th), ankle_ = pt(knee_, L.shin, shinA);
  const elbow = pt(shoulder, L.ua, uaA), wrist = pt(elbow, L.fa, faA);
  const P = (p) => [r1(p[0]), r1(p[1])];
  const s = {
    torso: [...P(hip), r1(T)], thigh: [...P(hip), r1(Th)], shin: [...P(knee_), r1(shinA)], foot: [...P(ankle_), r1(footA)],
    ua: [...P(shoulder), r1(uaA)], fa: [...P(elbow), r1(faA)], head: [...P(headP), r1(T)],
  };
  return { s, j: jointsOf(s), p: { hip: P(hip), shoulder: P(shoulder), knee: P(knee_), ankle: P(ankle_), wrist: P(wrist), elbow: P(elbow) } };
}

function cables(eq, frame) {
  const c = eq.filter((e) => e.cable).map((e) => {
    const a = e.cable, b = frame.p[e.f];
    return [a[0], a[1], r1((Math.atan2(b[1] - a[1], b[0] - a[0]) * 180) / Math.PI), r1(Math.hypot(b[0] - a[0], b[1] - a[1]))];
  });
  if (c.length) frame.c = c; else delete frame.c;
  return frame;
}

export function build(src, xnk) {
  L = xnk.len;
  const out = {};
  const parentOf = (name) => xnk.pats[name] ?? out[name];
  for (const [name, def] of Object.entries(src.patterns)) {
    const base = { name: def.name, muscles: def.muscles, eq: def.eq };
    if (def.joints) {
      const frames = Array.from({ length: N }, (_, i) => cables(def.eq, sideFrame(def, ease(i))));
      out[name] = { ...base, frames };
    } else {
      const par = parentOf(def.rig);
      if (!par) throw new Error(`${name}: parent ${def.rig} not built yet or unknown`);
      const frames = par.frames.map((f0) => {
        const f = JSON.parse(JSON.stringify(f0));
        if (def.arms) {
          const a = arm(f.p.shoulder, f.s.torso[2], def.arms);
          f.s.ua = a.ua; f.s.fa = a.fa; f.p.elbow = a.elbow; f.p.wrist = a.wrist;
          f.j = { ...f.j, elbow: jointsOf(f.s).elbow, shoulder: jointsOf(f.s).shoulder };
        }
        return cables(def.eq, f);
      });
      out[name] = { ...base, ...(par.view ? { view: par.view, order: par.order, fm: par.fm } : {}), rig: def.rig, variant: def.variant, frames };
    }
  }
  return {
    schema: 1,
    about: "Built by content/figures/build-rig-local.mjs from rig-local.src.json (C-08b); do not edit by hand. Same format as xnk-source/rig.min.json and drawn with its len, w, wf and muscle marks. Entries with rig + variant are an XNK or local pattern with other equipment: same frames, same animated joint angles (checked). Joint angles and their sources: rig-local.src.json and Content §5.3.",
    frames: N,
    pats: out,
  };
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const dir = new URL("./", import.meta.url);
  const src = JSON.parse(readFileSync(new URL("rig-local.src.json", dir), "utf8"));
  const xnk = JSON.parse(readFileSync(new URL("xnk-source/rig.min.json", dir), "utf8"));
  writeFileSync(new URL("rig-local.json", dir), JSON.stringify(build(src, xnk)) + "\n");
  console.log("rig-local.json written:", Object.keys(src.patterns).length, "entries");
}
