// 앱 아이콘 생성: node tools/make_icon.js
// 애플의 앱 아이콘 방식을 따른다(Human Interface Guidelines).
//  - 하나의 초점(렌즈) 도형만 크게. 글자·잔장식 없음
//  - 배경은 캔버스 전체를 채우는 하나의 부드러운 그라데이션(모서리 마스크는 시스템이 씌운다)
//  - 위에서 빛이 오는 일관된 광원: 위쪽은 밝고 아래쪽은 어둡게, 맺힌 하이라이트와 아주 옅은 그림자로 깊이를 만든다
//  - 색은 적게: 필름 옐로우 + 흑연(그래파이트) + 유리의 푸른 반사
//  - 어댑티브 아이콘 안전 영역(중앙 66dp) 안에 렌즈가 들어간다(반지름 28)
// 만드는 파일(RetroCamera/app/src/main/res): drawable/ic_launcher_{background,foreground,monochrome}.xml, mipmap-anydpi/ic_launcher{,_round}.xml
const fs = require("fs");
const path = require("path");
const RES = path.join(__dirname, "..", "RetroCamera", "app", "src", "main", "res");

const C = 54;         // 중심 (108 x 108 캔버스)
const R_BARREL = 28;  // 렌즈 경통 바깥 반지름
const NS = 'xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt"';

function circle(cx, cy, r) {
  return `M${cx - r},${cy}a${r},${r} 0 1,0 ${2 * r},0a${r},${r} 0 1,0 ${-2 * r},0z`;
}

function stops(list) {
  return list.map(([o, c]) => `                <item android:offset="${o}" android:color="${c}" />`).join("\n");
}

function linear(x0, y0, x1, y1, list) {
  return `        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="${x0}" android:startY="${y0}" android:endX="${x1}" android:endY="${y1}">
${stops(list)}
            </gradient>
        </aapt:attr>`;
}

function radial(cx, cy, r, list) {
  return `        <aapt:attr name="android:fillColor">
            <gradient android:type="radial" android:centerX="${cx}" android:centerY="${cy}" android:gradientRadius="${r}">
${stops(list)}
            </gradient>
        </aapt:attr>`;
}

function pathEl(data, fill, extra = "") {
  const fillAttr = fill.startsWith("#") ? `\n        android:fillColor="${fill}"` : "";
  const body = fill.startsWith("#") ? "" : `\n${fill}\n    `;
  return `    <path${fillAttr}${extra}\n        android:pathData="${data}"${body ? ">" : " />"}${body ? body + "</path>" : ""}`;
}

// ── 배경: 필름 옐로우의 세로 그라데이션 + 왼쪽 위에서 번지는 부드러운 빛 ──
const background = `<?xml version="1.0" encoding="utf-8"?>
<!-- 필름 옐로우 배경 (tools/make_icon.js 로 생성) -->
<vector ${NS}
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
${pathEl("M0,0h108v108h-108z", linear(54, 0, 54, 108, [[0, "#FFD968"], [0.55, "#F2B83E"], [1, "#D98F22"]]))}
${pathEl("M0,0h108v108h-108z", radial(30, 22, 78, [[0, "#66FFFFFF"], [0.6, "#14FFFFFF"], [1, "#00FFFFFF"]]))}
</vector>
`;

// ── 전경: 하나의 렌즈. 위에서 빛이 온다 ──
const sh = []; // 아주 옅은 그림자(3겹으로 번지게)
[[R_BARREL + 3.2, "#0D000000"], [R_BARREL + 1.8, "#14000000"], [R_BARREL + 0.6, "#1F000000"]].forEach(([r, col]) => sh.push(pathEl(circle(C, C + 2.6, r), col)));

const foreground = `<?xml version="1.0" encoding="utf-8"?>
<!-- 렌즈 (tools/make_icon.js 로 생성). 어댑티브 아이콘 안전 영역(중앙 66dp) 안에 그렸다 -->
<vector ${NS}
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
${sh.join("\n")}
${/* 경통: 위가 밝은 흑연 */ pathEl(circle(C, C, R_BARREL), linear(C, C - R_BARREL, C, C + R_BARREL, [[0, "#5A5A60"], [0.5, "#2C2C30"], [1, "#121214"]]))}
${/* 경통 윗면 가장자리의 가는 빛 */ pathEl(circle(C, C, R_BARREL - 0.7), linear(C, C - R_BARREL, C, C, [[0, "#40FFFFFF"], [1, "#00FFFFFF"]]), "")}
${/* 안쪽 링: 반대로 위가 어둡고 아래가 밝은 오목한 면 */ pathEl(circle(C, C, 22.5), linear(C, C - 22.5, C, C + 22.5, [[0, "#0C0C0E"], [1, "#46464C"]]))}
${/* 유리: 왼쪽 위에서 비치는 깊은 남색 */ pathEl(circle(C, C, 19), radial(C - 5, C - 6, 28, [[0, "#3A5486"], [0.45, "#16213A"], [1, "#05070D"]]))}
${/* 유리 아래쪽의 따뜻한 반사(필름 색) */ pathEl(circle(C + 6, C + 9, 9), radial(C + 6, C + 9, 9, [[0, "#66F2B83E"], [1, "#00F2B83E"]]))}
${/* 조리개의 가는 테두리 */ pathEl(circle(C, C, 11.5), "#14FFFFFF")}
${pathEl(circle(C, C, 10.6), radial(C - 2, C - 3, 14, [[0, "#1B2640"], [1, "#05070D"]]))}
${/* 맺힌 하이라이트: 왼쪽 위의 초승달 모양 */ pathEl(`M${C - 15},${C - 5}a16,16 0 0,1 17,-12a13,12 0 0,0 -13,14z`, linear(C - 15, C - 17, C - 2, C - 3, [[0, "#A6FFFFFF"], [1, "#00FFFFFF"]]))}
${/* 작은 점 반사 */ pathEl(circle(C + 7, C + 7, 1.6), "#59FFFFFF")}
</vector>
`;

// ── 단색 아이콘(Android 13 테마 아이콘): 모양만, 색은 시스템이 입힌다 ──
const ring = `${circle(C, C, R_BARREL)}${circle(C, C, 23)}`;       // 바깥 링(가운데 비움)
const lensDisc = circle(C, C, 17);
const monochrome = `<?xml version="1.0" encoding="utf-8"?>
<!-- 테마 아이콘용 단색 렌즈 (tools/make_icon.js 로 생성) -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFFFFF"
        android:fillType="evenOdd"
        android:pathData="${ring}" />
    <path
        android:fillColor="#FFFFFF"
        android:pathData="${lensDisc}" />
</vector>
`;

const adaptive = `<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
`;

function write(rel, text) {
  const p = path.join(RES, rel);
  fs.mkdirSync(path.dirname(p), { recursive: true });
  fs.writeFileSync(p, text);
  console.log("wrote", rel);
}
write("drawable/ic_launcher_background.xml", background);
write("drawable/ic_launcher_foreground.xml", foreground);
write("drawable/ic_launcher_monochrome.xml", monochrome);
write("mipmap-anydpi/ic_launcher.xml", adaptive);
write("mipmap-anydpi/ic_launcher_round.xml", adaptive);
// 예전 단색 배경 색은 더 쓰지 않는다
const old = path.join(RES, "values", "ic_launcher_background.xml");
if (fs.existsSync(old)) { fs.unlinkSync(old); console.log("removed values/ic_launcher_background.xml"); }
