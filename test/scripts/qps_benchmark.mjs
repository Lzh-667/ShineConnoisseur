import http from 'node:http'
import https from 'node:https'
import { performance } from 'node:perf_hooks'

const url = new URL(process.argv[2])
const concurrency = Number(process.argv[3] || 20)
const durationMs = Number(process.argv[4] || 10) * 1000
const warmupMs = Number(process.argv[5] || 2) * 1000
const transport = url.protocol === 'https:' ? https : http
const agent = new transport.Agent({ keepAlive: true, maxSockets: concurrency, maxFreeSockets: concurrency })

function requestOnce(record, results) {
  return new Promise((resolve) => {
    const started = performance.now()
    const request = transport.get(url, { agent, timeout: 10000 }, (response) => {
      let bytes = 0
      response.on('data', chunk => { bytes += chunk.length })
      response.on('end', () => {
        if (record) {
          results.latencies.push(performance.now() - started)
          results.statuses[response.statusCode] = (results.statuses[response.statusCode] || 0) + 1
          results.bytes += bytes
        }
        resolve()
      })
    })
    request.on('timeout', () => request.destroy(new Error('timeout')))
    request.on('error', (error) => {
      if (record) {
        results.latencies.push(performance.now() - started)
        const key = `error:${error.code || error.name}`
        results.statuses[key] = (results.statuses[key] || 0) + 1
      }
      resolve()
    })
  })
}

async function phase(milliseconds, record, results) {
  const deadline = performance.now() + milliseconds
  async function worker() {
    while (performance.now() < deadline) await requestOnce(record, results)
  }
  await Promise.all(Array.from({ length: concurrency }, worker))
}

function percentile(sorted, ratio) {
  if (!sorted.length) return 0
  return sorted[Math.max(0, Math.ceil(sorted.length * ratio) - 1)]
}

const results = { latencies: [], statuses: {}, bytes: 0 }
await phase(warmupMs, false, results)
const started = performance.now()
await phase(durationMs, true, results)
const elapsed = (performance.now() - started) / 1000
agent.destroy()

const sorted = results.latencies.sort((a, b) => a - b)
const total = sorted.length
const success = Object.entries(results.statuses)
  .filter(([status]) => status.startsWith('2'))
  .reduce((sum, [, count]) => sum + count, 0)
const round = value => Math.round(value * 100) / 100

console.log(JSON.stringify({
  url: url.toString(),
  concurrency,
  durationSeconds: round(elapsed),
  requests: total,
  success,
  errorRatePercent: total ? round((total - success) * 100 / total) : 100,
  qps: round(total / elapsed),
  latencyMs: {
    avg: total ? round(sorted.reduce((sum, value) => sum + value, 0) / total) : 0,
    p50: round(percentile(sorted, 0.50)),
    p95: round(percentile(sorted, 0.95)),
    p99: round(percentile(sorted, 0.99)),
    max: round(sorted.at(-1) || 0),
  },
  statusCounts: results.statuses,
  responseMiB: round(results.bytes / 1024 / 1024),
}))
