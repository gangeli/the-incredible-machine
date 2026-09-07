// Plays the first puzzle in headless Chromium against a local server and saves screenshots.
// Usage: NODE_PATH=$(npm root -g) node web/e2e/smoke.cjs [baseUrl] [outDir]
const { chromium } = require('playwright');
const fs = require('node:fs');

const base = process.argv[2] || 'http://127.0.0.1:8123/';
const out = process.argv[3] || 'web/build/e2e';
fs.mkdirSync(out, { recursive: true });

(async () => {

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1280, height: 800 }, deviceScaleFactor: 1, hasTouch: true });
const errors = [];
page.on('pageerror', (e) => errors.push(String(e)));
page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });

await page.goto(base, { waitUntil: 'load' });
await page.waitForFunction(() => window.timDebug && window.timDebug.screen() === 'TitleScreen', null, { timeout: 20000 });
await page.waitForTimeout(500);
await page.screenshot({ path: `${out}/web-title.png` });
// Chrome's install prompt: faking the event must make the title screen offer Install
await page.evaluate(() => { const e = new Event('beforeinstallprompt'); e.prompt = () => {}; window.dispatchEvent(e); });
await page.waitForFunction(() => window.timDebug.canInstall());
await page.waitForTimeout(100);
await page.screenshot({ path: `${out}/web-title-install.png` });
const msPerStep = await page.evaluate(() => window.timDebug.bench(3000));
console.log(`physics: ${msPerStep.toFixed(3)} ms per 60 Hz step of a busy level (budget 16.7 ms)`);

const hook = (expr) => page.evaluate(expr);
const tap = async (x, y) => { await page.mouse.move(x, y); await page.mouse.down(); await page.waitForTimeout(40); await page.mouse.up(); await page.waitForTimeout(120); };

// title -> level list -> level 1 (same coordinates the Android smoke test uses at 1280x800)
await tap(640, 800 * 0.56 + 55);
await page.waitForFunction(() => window.timDebug.screen() === 'LevelSelectScreen');
await page.screenshot({ path: `${out}/web-levels.png` });
await tap(200, 190);
await page.waitForFunction(() => window.timDebug.screen() === 'PlayScreen');
await page.waitForTimeout(300);

// drag every solution part out of the tray to its spot
const solution = await hook(() => window.timDebug.solution());
for (const part of solution) {
  const tile = await hook(() => window.timDebug.trayTile(0));
  if (!tile) throw new Error('no tray tile');
  const lift = 48; // the game lifts a dragged part above the finger by 48 units at u=1
  const target = await page.evaluate(([x, y]) => window.timDebug.toScreen(x, y), [part.x + part.w / 2, part.y + part.h / 2]);
  await page.mouse.move(tile.cx, tile.cy); await page.mouse.down();
  await page.mouse.move(tile.cx - 40, tile.cy, { steps: 4 });
  await page.mouse.move(tile.x - 80, tile.cy, { steps: 4 });
  await page.mouse.move(target.x, target.y + lift, { steps: 8 });
  await page.waitForTimeout(50);
  await page.mouse.up();
  await page.waitForTimeout(150);
}
const placed = await hook(() => window.timDebug.placed());
if (placed !== solution.length) throw new Error(`placed ${placed} parts, expected ${solution.length}`);
await page.screenshot({ path: `${out}/web-play.png` });

// press Play and wait for the celebration
const play = await hook(() => window.timDebug.playButton());
await tap(play.cx, play.cy);
await page.waitForFunction(() => window.timDebug.running());
await page.waitForFunction(() => window.timDebug.won(), null, { timeout: 30000 });
await page.waitForTimeout(600);
await page.screenshot({ path: `${out}/web-won.png` });

// progress survives a reload
await page.reload({ waitUntil: 'load' });
await page.waitForFunction(() => window.timDebug && window.timDebug.screen() === 'TitleScreen', null, { timeout: 20000 });
const solved = await page.evaluate(() => localStorage.getItem('tim:stars:l01'));
if (!solved) throw new Error('progress was not saved');

await browser.close();
if (errors.length) { console.error('page errors:', errors); process.exit(1); }
console.log(`web smoke test passed: ${solution.length} part(s) placed, puzzle solved, progress saved; screenshots in ${out}`);
})().catch((e) => { console.error(e); process.exit(1); });
