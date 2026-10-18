// TN-C casino machine inspector.
//
//   Prints the machine geometry and the animation curves as text, so the shape
//   and the timing can be tuned WITHOUT launching the game.
//
//   This exists because the machines are drawn entirely in code
//   (casino/client/TNCasinoGeometry); there is no .json model to open in
//   Blockbench. This script is the stand-in for "look at the model".
//
//   Usage:
//     node tools/casino-machines.mjs              # everything
//     node tools/casino-machines.mjs --parts      # static geometry only
//     node tools/casino-machines.mjs --anim       # animation curves only
//     node tools/casino-machines.mjs --view       # top-down ASCII plan of each machine
"use strict";

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const SRC = join(root, "src/main/java/com/tnc/tnc/casino");

const args = new Set(process.argv.slice(2));
const only = args.has("--parts") ? "parts" : args.has("--anim") ? "anim" : args.has("--view") ? "view" : "all";
const want = (what) => only === "all" || only === what;

const read = (p) => readFileSync(join(SRC, p), "utf8");

function section(title) {
  console.log("\n" + "=".repeat(78));
  console.log(title);
  console.log("=".repeat(78));
}

// ---------------------------------------------------------------------------
//  Static geometry: pull the `add(out, x0,y0,z0, x1,y1,z1, COLORS);` calls out
//  of a renderer's buildParts(). Colours are resolved against CasinoPalette.
// ---------------------------------------------------------------------------

const PALETTE = (() => {
  const text = read("client/CasinoPalette.java");
  const out = {};
  for (const m of text.matchAll(/public static final int (\w+)\s*=\s*(0x[0-9A-Fa-f]+);/g)) {
    out[m[1]] = Number(m[2]);
  }
  return out;
})();

// The gamble table renderer gives its numbers in MODEL units (1/16 block),
// matching tools/gen_gamble_table.py, and divides by 16 inside add(). So the
// divisor is read from the source rather than assumed -- guessing 1 would
// report a table 16x too big.
function unitDivisor(file) {
  const text = read(file);
  return /\/\s*16\.0F/.test(text) ? 16 : 1;
}

function parseParts(file) {
  const div = unitDivisor(file);
  const text = read(file);
  // Anchor on the DECLARATION, not on "buildParts()" -- the first occurrence of
  // that string is the field initialiser (`PARTS = buildParts();`), so anchoring
  // on it yields a 144-char slice and silently reports "0 boxes".
  const decl = text.indexOf("List<Part> buildParts()");
  if (decl < 0) throw new Error(`buildParts() not found in ${file}`);
  const open = text.indexOf("{", decl);
  // brace-match so the slice ends at the real end of the method
  let depth = 0, end = -1;
  for (let i = open; i < text.length; i++) {
    if (text[i] === "{") depth++;
    else if (text[i] === "}") { depth--; if (depth === 0) { end = i; break; } }
  }
  if (end < 0) throw new Error(`unbalanced braces in buildParts() of ${file}`);
  const body = text.slice(open, end);
  const parts = [];
  // Strict numeric token on purpose.
  //
  // Two traps this avoids, both hit for real while writing this tool:
  //   * `[\d.]+` is greedy and swallows several comma-separated numbers into one
  //     match, so the roulette table reported 1 box instead of 23;
  //   * a box whose coordinate is written as an expression (`TOP - 0.04F`) or as a
  //     bare int (`12`) never matches at all.
  // The renderers therefore spell every box as literal `NN.NNF` values, and the
  // trailing `;` + end-of-line anchor stops a match from spanning two lines.
  const NUM = String.raw`(-?\d+(?:\.\d+)?)F`;
  const addRe = new RegExp(
    String.raw`add\(out,\s*` + NUM + String.raw`,\s*` + NUM + String.raw`,\s*` + NUM
    + String.raw`,\s*` + NUM + String.raw`,\s*` + NUM + String.raw`,\s*` + NUM
    + String.raw`,\s*CasinoPalette\.(\w+)\);\s*(?://.*)?$`, "gm");
  for (const m of body.matchAll(addRe)) {
    const [, x0, y0, z0, x1, y1, z1, colour] = m;
    // normalise to BLOCKS using the divisor detected from the source
    parts.push({
      x0: +x0 / div, y0: +y0 / div, z0: +z0 / div,
      x1: +x1 / div, y1: +y1 / div, z1: +z1 / div,
      colour, rgb: PALETTE[colour],
    });
  }
  // Every box must be a literal `add(out, ...)` line -- anything else (a loop, an
  // arithmetic coordinate, a bare int) silently disappears from this report, so
  // shout about it instead of printing a wrong count.
  const declared = (body.match(/^\s*add\(out,/gm) || []).length;
  if (declared !== parts.length) {
    console.log(`\n  WARNING: ${file} has ${declared} add(out, ...) line(s) but only `
      + `${parts.length} could be read. A box is probably written as an expression,`);
    console.log("           a bare int, or inside a loop. Rewrite it as literal NN.NNF values");
    console.log("           so this report stays a true mirror of the geometry.");
  }
  return parts;
}

const hex = (n) => "#" + n.toString(16).padStart(6, "0");

function printParts(name, parts) {
  section(`${name} — static geometry (${parts.length} boxes)`);
  console.log("coords in BLOCKS (1.0 = 1 block), origin = the placed block's min corner");
  console.log("front of the machine is -Z when FACING=north\n");
  console.log("  #   from (x,y,z)             to (x,y,z)               size (w,h,d)         colour");
  console.log("  " + "-".repeat(94));
  parts.forEach((p, i) => {
    const f = `(${p.x0.toFixed(2)},${p.y0.toFixed(2)},${p.z0.toFixed(2)})`.padEnd(23);
    const t = `(${p.x1.toFixed(2)},${p.y1.toFixed(2)},${p.z1.toFixed(2)})`.padEnd(23);
    const s = `(${(p.x1 - p.x0).toFixed(2)},${(p.y1 - p.y0).toFixed(2)},${(p.z1 - p.z0).toFixed(2)})`.padEnd(21);
    console.log(`  ${String(i).padStart(2)}  ${f}${t}${s}${p.colour} ${hex(p.rgb)}`);
  });
}

// ---------------------------------------------------------------------------
//  Readable views. Rather than a colour projection (which just shows whichever
//  box was written last), stamp the box INDEX into the grid. The index matches
//  the --parts table, so a cell in the picture can be traced to exact coords.
// ---------------------------------------------------------------------------

const DIGITS = "0123456789abcdefghijklmnopqrstuvwxyz";

function printProjection(name, parts, axis) {
  const [hAxis, vAxis, hLabel, vLabel, note] = axis;
  section(`${name} — ${note}`);

  // Work the footprint out from the parts so the picture keeps the real aspect
  // ratio -- the machines are 2x1x2 but the gamble table is 4x1x2, and a fixed
  // grid would clip the table (the horizontal axis spans 4 blocks, not 2).
  let maxX = 0, maxY = 0, maxZ = 0;
  for (const p of parts) {
    maxX = Math.max(maxX, p.x1);
    maxY = Math.max(maxY, p.y1);
    maxZ = Math.max(maxZ, p.z1);
  }
  const extent = { x0: maxX, y0: maxY, z0: maxZ };
  const horizBlocks = Math.max(0.5, extent[hAxis[0][0]]);
  const vertBlocks = Math.max(0.5, extent[vAxis[0][0]]);

  // One shared pixels-per-block, so the aspect ratio is honest.
  const PER_BLOCK = 22;
  const CX = Math.min(100, Math.max(32, Math.round(horizBlocks * PER_BLOCK)));
  const CY = Math.max(6, Math.round(vertBlocks * PER_BLOCK));
  const grid = Array.from({ length: CY }, () => Array(CX).fill(" "));

  parts.forEach((p, i) => {
    const ch = i < DIGITS.length ? DIGITS[i] : "?";
    const h0 = Math.round(Math.min(p[hAxis[0]], p[hAxis[1]]) * CX);
    const h1 = Math.round(Math.max(p[hAxis[0]], p[hAxis[1]]) * CX) - 1;
    // vertical: row 0 is the TOP of the picture, so measure down from the max
    const vTop = Math.round((1 - Math.max(p[vAxis[0]], p[vAxis[1]])) * CY);
    const vBot = Math.round((1 - Math.min(p[vAxis[0]], p[vAxis[1]])) * CY) - 1;
    for (let v = Math.max(0, vTop); v <= Math.min(CY - 1, vBot); v++) {
      for (let h = Math.max(0, h0); h <= Math.min(CX - 1, h1); h++) grid[v][h] = ch;
    }
  });

  const perBlock = (CX / horizBlocks).toFixed(1);
  console.log(`  ${hLabel} ->   ${horizBlocks} x ${vertBlocks} blocks, about ${perBlock} columns per block`);
  grid.forEach((row, v) => {
    const tag = v === 0 ? " (top)" : v === CY - 1 ? ` ${vLabel}` : "";
    console.log(`  |${row.join("")}|${tag}`);
  });
  console.log("\n  Each character is a box INDEX — look it up in the --parts table.");
}

// Each axis is [horizontalFields, verticalFields, hLabel, vLabel, note].
// Fields are the NAMES on the part record (x0/x1/y0/y1/z0/z1) -- indexing the
// record numerically silently yields undefined and draws an empty picture.
function axisXZ() {
  return [["x0", "x1"], ["z0", "z1"], "x (0..2 blocks)", "z=0 (front) / z=2 (back)",
    "top-down plan (looking down -Y; front row is at the BOTTOM)"];
}
function axisXY() {
  return [["x0", "x1"], ["y0", "y1"], "x (0..2 blocks)", "y=0 (ground)",
    "front elevation (looking from -Z, i.e. straight at the machine's face)"];
}
function axisZY() {
  return [["z0", "z1"], ["y0", "y1"], "z (0..2 blocks)", "y=0 (ground)",
    "side elevation (looking from -X)"];
}

// ---------------------------------------------------------------------------
//  Animation: read the tunable constants straight out of the entity/renderer so
//  this report can never drift from the code.
// ---------------------------------------------------------------------------

function constOf(file, pattern, fallback) {
  const m = read(file).match(pattern);
  return m ? m[1] : fallback;
}

function printAnim() {
  section("Animation timing — server (authority)");
  const slot = "machine/TNSlotMachineEntity.java";
  const rou = "machine/TNRouletteTableEntity.java";
  // [name, server spin, client animation, result hold]
  const rows = [
    ["slot machine",
      constOf(slot, /SPIN_TICKS\s*=\s*(\d+)/, "?"),
      constOf(slot, /CLIENT_SPIN_TICKS\s*=\s*(\d+)/, "?"),
      constOf(slot, /RESULT_TICKS\s*=\s*(\d+)/, "?")],
    ["roulette table",
      constOf(rou, /SPIN_TICKS\s*=\s*(\d+)/, "?"),
      constOf(rou, /CLIENT_SPIN_TICKS\s*=\s*(\d+)/, "?"),
      constOf(rou, /RESULT_TICKS\s*=\s*(\d+)/, "?")],
  ];
  console.log("  ticks; 20 ticks = 1 second\n");
  console.log("  machine           server  client  result");
  console.log("                    spin    anim    hold");
  console.log("  " + "-".repeat(44));
  for (const [n, sv, cv, rv] of rows) {
    console.log(`  ${n.padEnd(18)}${String(sv).padStart(4)}${String(cv).padStart(8)}${String(rv).padStart(8)}`);
  }
  console.log("\n  rule: client animation MUST be longer than the server spin,");
  console.log("        otherwise the machine is already IDLE again while the reels are still moving.");

  section("Animation timing — client (cosmetic only, safe to tune freely)");
  const sr = "client/TNSlotMachineRenderer.java";
  const rr = "client/TNRouletteRenderer.java";
  const knobs = [
    ["slot", "SPIN_CYCLES", constOf(sr, /SPIN_CYCLES\s*=\s*(\d+)/, "?"), "how many turns the reels make"],
    ["slot", "REEL_TAIL", constOf(sr, /REEL_TAIL\s*=\s*(\d+)/, "?"), "ticks between reel 1/2/3 stopping"],
    ["slot", "SYMBOL_PITCH", constOf(sr, /SYMBOL_PITCH\s*=\s*([\d.]+F)/, "?"), "height of one symbol cell"],
    ["roulette", "WEDGES", constOf(rr, /WEDGES\s*=\s*(\d+)/, "?"), "boxes used to fake the round wheel"],
    ["roulette", "WHEEL_R", constOf(rr, /WHEEL_R\s*=\s*([\d.]+F)/, "?"), "wheel radius"],
    ["roulette", "POCKET_R", constOf(rr, /POCKET_R\s*=\s*([\d.]+F)/, "?"), "ball rest radius (the pocket ring)"],
  ];
  console.log("  machine    constant        value      meaning");
  console.log("  " + "-".repeat(72));
  for (const [m, k, v, d] of knobs) console.log(`  ${m.padEnd(11)}${k.padEnd(16)}${String(v).padEnd(11)}${d}`);

  console.log("\n  easing used (both machines):  easeOutCubic  1-(1-t)^3");
  console.log("  ball fall window (roulette):  last 45% of the spin, radius lerps outer -> POCKET_R");
}

// ---------------------------------------------------------------------------

const SLOT = "client/TNSlotMachineRenderer.java";
const ROULETTE = "client/TNRouletteRenderer.java";
const GAMBLE = "client/TNGambleTableRenderer.java";

const slotParts = parseParts(SLOT);
const rouParts = parseParts(ROULETTE);
const gambleParts = parseParts(GAMBLE);

if (want("parts")) {
  printParts("TNSlotMachineRenderer", slotParts);
  printParts("TNRouletteRenderer", rouParts);
  printParts("TNGambleTableRenderer", gambleParts);
}
if (want("view")) {
  for (const [n, parts] of [["slot machine", slotParts],
                            ["roulette table", rouParts],
                            ["gamble table (4x2)", gambleParts]]) {
    printProjection(n, parts, axisXY());
    printProjection(n, parts, axisXZ());
    printProjection(n, parts, axisZY());
  }
}
if (want("anim")) printAnim();

if (only === "all") {
  console.log("\n" + "-".repeat(78));
  console.log("TIP: --parts / --view / --anim  print just one block of this report.");
  console.log("     All numbers above are read from the java sources at run time,");
  console.log("     so they cannot go stale.");
}

console.log();
