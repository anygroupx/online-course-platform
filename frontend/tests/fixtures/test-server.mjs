import { createServer } from 'vite'
import { createServer as createNetServer } from 'node:net'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

// Vite 5 treats port:0 as an unset port and falls back to 5173. Reserve a real
// loopback port and isolate optimized dependencies when two test runs overlap.
async function unusedLoopbackPort() {
  const probe = createNetServer()
  await new Promise((resolve, reject) => {
    probe.once('error', reject)
    probe.listen(0, '127.0.0.1', resolve)
  })
  const port = probe.address().port
  await new Promise((resolve, reject) => probe.close((error) => error ? reject(error) : resolve()))
  return port
}

export async function createTestServer(options) {
  const cacheDir = await mkdtemp(join(tmpdir(), 'course-browser-vite-'))
  try {
    const server = await createServer({
      ...options,
      cacheDir,
      server: { ...options.server, host: '127.0.0.1', port: await unusedLoopbackPort(), strictPort: false },
    })
    const close = server.close.bind(server)
    server.close = async () => {
      try { await close() } finally { await rm(cacheDir, { recursive: true, force: true }) }
    }
    return server
  } catch (error) {
    await rm(cacheDir, { recursive: true, force: true })
    throw error
  }
}
