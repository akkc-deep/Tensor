const assert = require('node:assert/strict');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { chromium } = require('../control-plane/node_modules/playwright');

const preview = pathToFileURL(path.resolve(__dirname,
  '../docs/issues/proposals/ISSUE-004-ui-visual-concepts.html')).href;

async function appearance(page) {
  return page.evaluate(() => {
    const regions = ['html', '.app-nav', '.setup-panel', '.result-panel', '#download-submit'];
    return Object.fromEntries(regions.map((selector) => {
      const element = document.querySelector(selector);
      const { x, y, width, height } = element.getBoundingClientRect();
      return [selector, { color: getComputedStyle(element).backgroundColor, x, y, width, height }];
    }));
  });
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1080 } });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(preview);
    const navigate = view => page.locator(`[data-view="${view}"]`).click();
    assert.equal(await page.locator('#theme-form').isVisible(), false, 'Theme controls belong in settings');
    await navigate('settings');
    assert.equal(await page.locator('#settings-view').isVisible(), true);
    assert.equal(await page.locator('.state-preview').isVisible(), false);
    await page.locator('#theme-reset').click();
    await navigate('download');
    const initial = await appearance(page);

    await navigate('settings');
    await page.locator('#theme-hex').fill('#b52c63');
    await page.locator('#theme-form button[type="submit"]').click();
    await navigate('download');
    const themed = await appearance(page);
    for (const [selector, before] of Object.entries(initial)) {
      assert.notEqual(themed[selector].color, before.color, `${selector} must follow the theme`);
      const { color: oldColor, ...oldLayout } = before;
      const { color: newColor, ...newLayout } = themed[selector];
      assert.deepEqual(newLayout, oldLayout, `${selector} layout must stay unchanged`);
    }

    await navigate('settings');
    await page.reload();
    assert.equal(await page.locator('#settings-view').isVisible(), true, 'Reload must retain the settings page');
    assert.equal(await page.locator('#theme-hex').inputValue(), '#B52C63');
    await navigate('download');
    assert.deepEqual(await appearance(page), themed, 'The whole theme must survive reload');
    await navigate('settings');
    await page.locator('#theme-color').fill('#11765b');
    await navigate('download');
    const picked = await appearance(page);
    for (const selector of Object.keys(initial)) {
      assert.notEqual(picked[selector].color, themed[selector].color, `${selector} must follow the picker`);
    }
    await navigate('dataset');
    const sourcePanelColor = await page.locator('.query-panel').evaluate(element => getComputedStyle(element).backgroundColor);
    assert.equal(sourcePanelColor, picked['.setup-panel'].color, 'Both pages must share the surface theme');
    await navigate('settings');
    await page.locator('#theme-reset').click();
    await navigate('download');
    assert.deepEqual(await appearance(page), initial, 'Reset must restore all theme colors');
    assert.deepEqual(errors, []);
    console.log('PASS: settings navigation, whole-page theme, stable layout, picker, persistence, page switch and reset.');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
