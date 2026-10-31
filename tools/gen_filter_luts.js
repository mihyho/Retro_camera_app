// 목업의 CSS 필터 필름(Cine/Faded/Night)을 같은 수식으로 .cube(33^3)로 굽는다.
// 실행: node tools/gen_filter_luts.js   → RetroCamera/app/src/main/assets/luts/{cine500,faded100,night1600}.cube
// 필터 행렬은 W3C filter-effects 명세, 틴트는 soft-light 합성. 실제 필름 사진 비교로 맞춘 LUT가 아니라 수식 근사다.
const fs = require("fs"), path = require("path");
const N = 33;
const OUT = path.join(__dirname, "..", "RetroCamera", "app", "src", "main", "assets", "luts");

const clamp = v => Math.min(1, Math.max(0, v));
const mat = (m, [r, g, b]) => [m[0] * r + m[1] * g + m[2] * b, m[3] * r + m[4] * g + m[5] * b, m[6] * r + m[7] * g + m[8] * b];
const saturate = s => c => mat([.213 + .787 * s, .715 - .715 * s, .072 - .072 * s, .213 - .213 * s, .715 + .285 * s, .072 - .072 * s, .213 - .213 * s, .715 - .715 * s, .072 + .928 * s], c);
const sepia = a => c => { const k = 1 - a; return mat([.393 + .607 * k, .769 - .769 * k, .189 - .189 * k, .349 - .349 * k, .686 + .314 * k, .168 - .168 * k, .272 - .272 * k, .534 - .534 * k, .131 + .869 * k], c); };
const hue = deg => c => { const a = deg * Math.PI / 180, s = Math.sin(a), k = Math.cos(a);
  return mat([.213 + k * .787 - s * .213, .715 - k * .715 - s * .715, .072 - k * .072 + s * .928,
    .213 - k * .213 + s * .143, .715 + k * .285 + s * .140, .072 - k * .072 - s * .283,
    .213 - k * .213 - s * .787, .715 - k * .715 + s * .715, .072 + k * .928 + s * .072], c); };
const contrast = a => c => c.map(v => clamp(a * v + (-.5 * a + .5)));
const brightness = a => c => c.map(v => clamp(a * v));
const hex = h => [h >> 16 & 255, h >> 8 & 255, h & 255].map(v => v / 255);

// soft-light(W3C compositing) 후 불투명도 o로 섞는다
const softLight = (cb, cs) => { const d = cb <= .25 ? ((16 * cb - 12) * cb + 4) * cb : Math.sqrt(cb);
  return cs <= .5 ? cb - (1 - 2 * cs) * cb * (1 - cb) : cb + (2 * cs - 1) * (d - cb); };
const tint = (h, o) => { const t = hex(h); return c => c.map((v, i) => v * (1 - o) + clamp(softLight(clamp(v), t[i])) * o); };

const FILMS = {
  cine500: [hue(12), saturate(1.3), contrast(1.12), sepia(.1), tint(0x2f8f99, .55)],
  faded100: [saturate(.7), contrast(.88), brightness(1.1), sepia(.22), tint(0xc9b8a0, .55)],
  night1600: [saturate(1.4), contrast(1.2), hue(-16), brightness(.95), tint(0xc0392b, .55)],
};

for (const [key, steps] of Object.entries(FILMS)) {
  const lines = [`TITLE "${key}"`, `LUT_3D_SIZE ${N}`, "DOMAIN_MIN 0 0 0", "DOMAIN_MAX 1 1 1"];
  let count = 0;
  for (let b = 0; b < N; b++) for (let g = 0; g < N; g++) for (let r = 0; r < N; r++) { // R이 가장 빨리 변한다
    const o = steps.reduce((c, f) => f(c).map(clamp), [r, g, b].map(v => v / (N - 1)));
    if (o.some(v => !(v >= 0 && v <= 1))) throw new Error("범위 밖 값");
    lines.push(o.map(v => v.toFixed(6)).join(" ")); count++;
  }
  if (count !== N * N * N) throw new Error("개수 불일치");
  fs.writeFileSync(path.join(OUT, key + ".cube"), lines.join("\n") + "\n");
  console.log(key, count, "entries, black→", lines[4], "white→", lines[lines.length - 1]);
}
