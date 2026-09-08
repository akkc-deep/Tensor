const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { chromium } = require('../control-plane/node_modules/playwright');
const { expect } = require('../control-plane/node_modules/@playwright/test');

const demo = path.resolve(__dirname, '../docs/issues/proposals/ISSUE-017-demo/index.html');
const url = process.argv[2] || pathToFileURL(demo).href;
const shots = path.join(os.tmpdir(), 'tensor-issue017-review');
fs.mkdirSync(shots, { recursive: true });

(async () => {
  const browser = await chromium.launch({ headless: true });
  const errors = [], unexpected = [];
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  page.on('pageerror', error => errors.push(error.message));
  page.on('request', request => {
    if (!request.url().startsWith('file:') && !request.url().startsWith('http://127.0.0.1:4177/') && !request.url().startsWith('data:')) unexpected.push(request.url());
  });
  const navigate = async (view) => {
    await page.locator(`a[href="#${view}"]`).filter({ hasNot: page.locator('.brand-mark') }).click();
    await expect(page.locator(`.${view === 'datasets' ? 'dataset' : view === 'downloads' ? 'download' : 'settings'}-page`)).toBeVisible();
  };
  const picker = async (id, label) => {
    await page.locator('#' + id).click();
    await page.locator('#' + id + '-options').getByRole('option').filter({ hasText: label }).click();
  };
  try {
    await page.goto(url);
    await expect(page.locator('h1')).toHaveText('数据查看');
    await page.evaluate(() => document.fonts.ready);
    assert.equal(await page.evaluate(() => document.fonts.check('400 14px "Tensor Sans"', '证券数据')), true);
    assert.equal(await page.locator('tbody tr').count(), 50);
    await expect(page.locator('.record-count')).toHaveText('120 条');
    const firstTable = await page.locator('thead').boundingBox();
    await page.screenshot({ path: path.join(shots, 'datasets-desktop.png'), fullPage: true, animations: 'disabled' });
    assert.ok(firstTable.y <= 320, `table header starts at ${firstTable.y}px`);

    await page.locator('#query-code').fill('000001.SZ');
    await page.getByRole('button', { name: '查询', exact: true }).click();
    await expect(page.locator('.data-surface')).toHaveAttribute('aria-busy', 'false');
    await expect(page.locator('tbody tr')).toHaveCount(1);
    await expect(page.locator('tbody tr td').first()).toHaveText('000001.SZ');
    await page.getByRole('button', { name: '重置', exact: true }).click();
    await page.getByRole('button', { name: '下一页' }).click();
    await expect(page.locator('.page-number')).toContainText('2');
    await expect(page.locator('.pagination-summary')).toHaveText('51–100 / 120 条');
    await page.locator('#page-size').selectOption('20');
    await expect(page.locator('tbody tr')).toHaveCount(20);
    await page.locator('#query-start').fill('2026-09-09');
    await page.locator('#query-end').fill('2026-08-07');
    await page.getByRole('button', { name: '查询', exact: true }).click();
    await expect(page.getByRole('alert')).toHaveText('开始日期不能晚于结束日期。');
    await page.getByRole('button', { name: '重置', exact: true }).click();

    await picker('query-dataset', '高精度与长文本样例');
    const copiedNumber = await page.locator('tbody tr').first().locator('td').nth(3).evaluate(td => { const range = document.createRange(); range.selectNodeContents(td); const selection = getSelection(); selection.removeAllRanges(); selection.addRange(range); const value = selection.toString(); selection.removeAllRanges(); return value; });
    assert.equal(copiedNumber, '12345678901234567890.123456789012345678', 'Selecting a numeric cell preserves exact copy text');
    const precision = await page.locator('tbody tr').first().innerText();
    assert.ok(precision.includes('9223372036854775807'));
    assert.ok(precision.replace(/\s/g, '').includes('12345678901234567890.123456789012345678'));
    await page.screenshot({ path: path.join(shots, 'precision-desktop.png'), fullPage: true, animations: 'disabled' });
    const heights = await page.locator('tbody tr').evaluateAll(rows => rows.map(row => row.getBoundingClientRect().height));
    assert.equal(new Set(heights).size, 1, 'All ordinary rows have the same height');
    await picker('query-dataset', '资产负债表');
    await expect(page.locator('th')).toHaveCount(155);
    assert.equal(await page.locator('tbody tr').count(), 20);
    await page.locator('.table-scroll').evaluate(el => { el.scrollLeft = 800; });
    const fixed = await page.locator('tbody tr').first().locator('td').first().boundingBox();
    const fixedHead = await page.locator('th').first().boundingBox();
    assert.ok(Math.abs(fixed.x - fixedHead.x) <= 1, 'Fixed header/body align while scrolled');
    await picker('query-dataset', '上市公司基本信息');
    await page.locator('tbody .text-cell').first().click();
    await expect(page.locator('dialog')).toBeVisible();
    assert.ok((await page.locator('dialog p').textContent()).length > 30);
    await page.keyboard.press('Escape');
    await expect(page.locator('dialog')).not.toBeVisible();

    await navigate('downloads');
    await page.getByRole('button', { name: '开始下载', exact: true }).click();
    await expect(page.locator('.receipt.success')).toBeVisible();
    await page.screenshot({ path: path.join(shots, 'downloads-desktop.png'), fullPage: true, animations: 'disabled' });
    await page.locator('.download-page .scenario-control select').selectOption('error');
    await page.getByRole('button', { name: '开始下载', exact: true }).click();
    await expect(page.locator('.receipt.error')).toBeVisible();
    await page.locator('#download-date').fill('2026-09-09');
    await page.getByRole('button', { name: '使用原参数重试', exact: true }).click();
    await expect(page.locator('.receipt.success')).toBeVisible();
    await expect(page.locator('.receipt.success')).toContainText('2026-08-07');
    await page.locator('.download-page .scenario-control select').selectOption('empty');
    await page.getByRole('button', { name: '开始下载', exact: true }).click();
    await expect(page.locator('.receipt.empty')).toBeVisible();
    await page.locator('.download-page .scenario-control select').selectOption('success');

    await navigate('settings');
    const before = await page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--nav'));
    await page.getByRole('button', { name: '青绿', exact: true }).click();
    assert.notEqual(await page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--nav')), before);
    await page.locator('#theme-hex').fill('bad');
    await page.getByRole('button', { name: '应用', exact: true }).click();
    await expect(page.getByRole('alert')).toContainText('#RRGGBB');
    await page.getByRole('button', { name: '减少动画', exact: true }).click();
    await expect(page.locator('#app')).toHaveClass(/reduce-motion/);
    await page.reload();
    await expect(page.locator('#app')).toHaveClass(/reduce-motion/);
    await expect(page.locator('#theme-hex')).toHaveValue('#20766B');
    await page.getByRole('button', { name: '恢复默认外观', exact: true }).click();
    await page.screenshot({ path: path.join(shots, 'settings-desktop.png'), fullPage: true, animations: 'disabled' });
    for (const viewport of [{ width: 1440, height: 900 }, { width: 1024, height: 768 }, { width: 768, height: 1024 }, { width: 390, height: 844 }, { width: 360, height: 800 }]) {
      await page.setViewportSize(viewport);
      for (const view of ['datasets', 'downloads', 'settings']) {
        await navigate(view);
        const overflow = await page.evaluate(() => document.documentElement.scrollWidth - innerWidth);
        assert.ok(overflow <= 1, `${view} overflows at ${viewport.width}px by ${overflow}px`);
        if (viewport.width === 390) await page.screenshot({ path: path.join(shots, `${view}-mobile.png`), fullPage: true, animations: 'disabled' });
      }
    }
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await navigate('downloads');
    await page.getByRole('button', { name: '开始下载', exact: true }).click();
    await expect(page.locator('.receipt.success')).toBeVisible();
    assert.deepEqual(errors, [], 'No browser errors');
    assert.deepEqual(unexpected, [], 'No external or backend requests');
    console.log('PASS: local font, query/filter/reset/pagination, exact numbers, 155-column table, fixed-column alignment, full text, download success/error/empty/retry, theme/persistence, reduced motion, 5 viewports × 3 pages.');
    console.log(`Screenshots: ${shots}`);
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
