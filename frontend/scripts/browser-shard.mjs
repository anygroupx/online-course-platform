#!/usr/bin/env node
// 把一个分片号映射到 test:browser 的子集，CI 用矩阵并行跑完全部脚本。
//
// 脚本清单唯一来源仍是 package.json 的 test:browser；分片只按名次取模分配，
// 所以脚本增删不需要同步改这里或 workflow。合计恰为 total 个分片各跑一次。
import { readFileSync } from 'node:fs'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const frontendRoot = join(dirname(fileURLToPath(import.meta.url)), '..')

function readScripts() {
  const pkg = JSON.parse(readFileSync(join(frontendRoot, 'package.json'), 'utf8'))
  const command = pkg.scripts?.['test:browser']
  if (typeof command !== 'string' || command.trim() === '') {
    console.error('package.json 缺少 test:browser 脚本')
    process.exit(2)
  }
  const files = [...command.matchAll(/(?:^|&&)\s*node\s+(\S+)/g)].map((m) => m[1])
  if (files.length === 0) {
    console.error('test:browser 中未解析到任何 node 脚本')
    process.exit(2)
  }
  return files
}

const files = readScripts()
const index = Number(process.argv[2])
const total = Number(process.argv[3])

if (!Number.isInteger(index) || !Number.isInteger(total) || index < 1 || total < 1 || index > total) {
  console.error('用法: node scripts/browser-shard.mjs <分片号 1..N> <总分片数 N> [--list]')
  process.exit(2)
}

const shard = files.filter((_, i) => i % total === index - 1)
const listed = process.argv.includes('--list')
if (listed) {
  console.log(shard.join('\n'))
  process.exit(0)
}

console.log(`浏览器回归分片 ${index}/${total}：${shard.length}/${files.length} 个脚本`)
if (shard.length === 0) {
  console.log('该分片没有脚本，视为通过')
  process.exit(0)
}

// 串行执行本分片，任一失败立即终止，保持与 npm run test:browser 相同的阻断语义。
for (const file of shard) {
  console.log(`\n=== ${file} ===`)
  const result = spawnSync('node', [file], { cwd: frontendRoot, stdio: 'inherit' })
  if (result.error) {
    console.error(`执行 ${file} 失败: ${result.error.message}`)
    process.exit(1)
  }
  if (result.status !== 0) {
    console.error(`\n分片 ${index}/${total} 在 ${file} 失败，退出码 ${result.status}`)
    process.exit(result.status ?? 1)
  }
}
console.log(`\n分片 ${index}/${total} 全部通过`)
