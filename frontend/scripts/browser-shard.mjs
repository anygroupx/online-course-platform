#!/usr/bin/env node
// 把一个分片号映射到 test:browser 的子集，CI 用矩阵并行跑完全部脚本。
//
// 脚本清单的唯一来源是 package.json 的 test:browser；分片总数由调用方给出，
// workflow 里用 ${{ strategy.job-total }} 传入，所以增删分片只需改矩阵本身，
// 不会出现「矩阵改了、模数没改」导致的静默漏跑。
import { readFileSync, existsSync } from 'node:fs'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const frontendRoot = join(dirname(fileURLToPath(import.meta.url)), '..')
const weightsPath = join(frontendRoot, 'scripts/browser-shard-weights.json')

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

// 已知耗时只影响均衡，不影响正确性：权重缺失时退化为按名次取模，
// 每个脚本仍然恰好被分到一个分片。过期的权重最多让分片略不均匀。
function readWeights() {
  if (!existsSync(weightsPath)) return null
  try {
    const raw = JSON.parse(readFileSync(weightsPath, 'utf8'))
    return raw && typeof raw === 'object' ? raw : null
  } catch {
    console.warn(`权重文件解析失败，回退到按名次取模：${weightsPath}`)
    return null
  }
}

// 最长处理时间优先的贪心装箱：按时长降序，每次放进当前负载最小的分片。
function balance(files, total, weights) {
  const bins = Array.from({ length: total }, () => ({ files: [], load: 0 }))
  const ordered = [...files].sort((a, b) => (weights[b] ?? 0) - (weights[a] ?? 0))
  for (const file of ordered) {
    const bin = bins.reduce((min, b) => (b.load < min.load ? b : min), bins[0])
    bin.files.push(file)
    bin.load += weights[file] ?? 0
  }
  return bins
}

function shardFor(files, index, total, weights) {
  if (!weights) return files.filter((_, i) => i % total === index - 1)
  return balance(files, total, weights)[index - 1].files
}

const files = readScripts()
const index = Number(process.argv[2])
const total = Number(process.argv[3])

if (!Number.isInteger(index) || !Number.isInteger(total) || index < 1 || total < 1 || index > total) {
  console.error('用法: node scripts/browser-shard.mjs <分片号 1..N> <总分片数 N> [--list]')
  process.exit(2)
}

const weights = readWeights()
const shard = shardFor(files, index, total, weights)
const listed = process.argv.includes('--list')
if (listed) {
  console.log(shard.join('\n'))
  process.exit(0)
}

const mode = weights ? '按时长均衡' : '按名次取模'
console.log(`浏览器回归分片 ${index}/${total}（${mode}）：${shard.length}/${files.length} 个脚本`)
if (shard.length === 0) {
  // 空分片通常是分片数与脚本数不匹配的信号，直接失败而不是静默通过。
  console.error(`分片 ${index}/${total} 没有分配到任何脚本，请检查分片总数是否超过脚本数`)
  process.exit(1)
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
