import test from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

// 页面根元素带有 fluent-spatial.scss 的 .main-content > * 动画，
// animation-fill-mode: both 让 transform 永久非 none，页面根因此成为
// position: fixed 后代的包含块；main.el-main.main-content 还叠加了
// perspective 与 backdrop-filter。两者都会把 el-overlay 的视口定位改成
// 按页面内容盒计算，弹窗底部被裁切。唯一可行的修法是让弹窗 Teleport 到
// body，即显式声明 append-to-body。
const SRC = fileURLToPath(new URL('../src', import.meta.url));

// 有意保留在本地渲染的弹窗：在宿主元素上标注该注释即可豁免。
const OPT_OUT = 'append-to-body-opt-out';

function vueFiles(dir) {
  const out = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) out.push(...vueFiles(full));
    else if (entry.name.endsWith('.vue')) out.push(full);
  }
  return out;
}

// 找到开标签的结束位置，跳过引号内的 '>'。
function tagEnd(source, start) {
  let quote = null;
  for (let i = start; i < source.length; i++) {
    const char = source[i];
    if (quote) {
      if (char === quote) quote = null;
      continue;
    }
    if (char === '"' || char === "'") quote = char;
    else if (char === '>') return i;
  }
  return -1;
}

// 收集所有缺少 append-to-body 的 el-dialog / el-drawer 声明。
function missingAppendToBody(source) {
  const found = [];
  const pattern = /<(el-dialog|el-drawer)\b/g;
  let match;
  while ((match = pattern.exec(source))) {
    const end = tagEnd(source, pattern.lastIndex);
    if (end < 0) break;
    const attrs = source.slice(pattern.lastIndex, end);
    pattern.lastIndex = end;
    if (hasAppendToBodyAttr(attrs)) continue;
    found.push({ tag: match[1], line: source.slice(0, match.index).split('\n').length });
  }
  return found;
}

// 只在属性名位置匹配 append-to-body，避免把 :title="append-to-body" 当成声明。
function hasAppendToBodyAttr(attrs) {
  let quote = null;
  let name = '';
  const names = [];
  for (const char of attrs) {
    if (quote) {
      if (char === quote) quote = null;
      continue;
    }
    if (char === '"' || char === "'") {
      quote = char;
      continue;
    }
    if (/[\s=/>]/.test(char)) {
      if (name) names.push(name);
      name = '';
      continue;
    }
    name += char;
  }
  if (name) names.push(name);
  return names.includes('append-to-body');
}

test('每个页面级弹窗与抽屉都显式 Teleport 到 body', () => {
  const offenders = [];
  for (const file of vueFiles(SRC)) {
    const source = readFileSync(file, 'utf8');
    // 整个文件标注豁免注释时跳过（用于有意保留的本地浮层）。
    if (source.includes(OPT_OUT)) continue;
    for (const item of missingAppendToBody(source)) {
      offenders.push(`${relative(SRC, file)}:${item.line} <${item.tag}>`);
    }
  }
  assert.deepEqual(
    offenders,
    [],
    `以下弹窗缺少 append-to-body，会被页面 transform 裁切：\n${offenders.join('\n')}`,
  );
});

test('扫描器能识别缺失、已声明与豁免三种情形', () => {
  assert.deepEqual(missingAppendToBody('<el-dialog v-model="a" title="x">'), [
    { tag: 'el-dialog', line: 1 },
  ]);
  assert.deepEqual(missingAppendToBody('<el-dialog v-model="a" append-to-body>'), []);
  assert.deepEqual(missingAppendToBody('<el-drawer\n  v-model="a"\n  append-to-body\n>'), []);
  // 属性值里出现同名文本不算声明
  assert.deepEqual(missingAppendToBody('<el-dialog :title="append-to-body">'), [
    { tag: 'el-dialog', line: 1 },
  ]);
  // 引号内的 '>' 不能提前截断开标签
  assert.deepEqual(missingAppendToBody('<el-dialog :title="a > b">'), [
    { tag: 'el-dialog', line: 1 },
  ]);
  assert.deepEqual(missingAppendToBody('<el-dialog :title="a > b" append-to-body>'), []);
});
