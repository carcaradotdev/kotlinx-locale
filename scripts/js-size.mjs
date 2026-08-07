#!/usr/bin/env node
/*
 * Copyright 2026 Carcara.dev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Measures what this library costs a JavaScript/TypeScript consumer.
//
// For each scenario it builds the probe in tools/js-size, which re-exports the
// full public API of the selected modules through @JsExport, links it with
// Kotlin/JS DCE and bundles it with webpack in production mode (terser
// minification). Nothing reachable from JS can be tree-shaken away, so the
// result is the upper bound: what ships when a consumer uses everything.
//
// Scenarios are `none` (the empty baseline), any comma-separated combination of
// `locale`, `country`, `currency`, `datetime` and `kotlinx-datetime`, or `all`.
// Picking a module also picks the modules it exposes through `api`
// dependencies, since their types are part of its public surface.
//
// Usage:
//   node scripts/js-size.mjs                       # the standard scenarios
//   node scripts/js-size.mjs currency              # one module
//   node scripts/js-size.mjs country,datetime all  # any combination
//   node scripts/js-size.mjs --json
//   node scripts/js-size.mjs --markdown
//   node scripts/js-size.mjs --verbose             # stream the Gradle output

import { execFileSync } from 'node:child_process'
import { brotliCompressSync, constants, gzipSync } from 'node:zlib'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const REPO = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const PROBE = join(REPO, 'tools', 'js-size')
const BUNDLE = join(PROBE, 'build', 'kotlin-webpack', 'js', 'productionExecutable', 'probe.js')
const TYPINGS = join(PROBE, 'build', 'compileSync', 'js', 'main', 'productionExecutable', 'kotlin', 'js-size.d.ts')
const OUT = join(REPO, 'build', 'js-size')

// The scenarios reported when no arguments are given. `none` is the empty
// bundle: the Kotlin/JS runtime floor every scenario pays, whichever modules
// are selected.
const DEFAULT_SCENARIOS = ['none', 'locale', 'country', 'currency', 'kotlinx-datetime', 'datetime', 'all']

const LABELS = {
  'none': 'baseline (Kotlin/JS runtime only)',
  'kotlinx-datetime': 'kotlinx-datetime (third-party)',
  'all': 'all modules',
}

function parseArgs(argv) {
  const scenarios = []
  let format = 'table'
  let verbose = false
  for (const arg of argv) {
    switch (arg) {
      case '--json':
        format = 'json'
        break
      case '--markdown':
      case '--md':
        format = 'markdown'
        break
      case '--verbose':
      case '-v':
        verbose = true
        break
      case '-h':
      case '--help':
        format = 'help'
        break
      default:
        if (arg.startsWith('-')) {
          throw new Error(`Unknown option: ${arg}`)
        }
        scenarios.push(arg === 'baseline' ? 'none' : arg)
    }
  }
  return { scenarios: scenarios.length > 0 ? scenarios : DEFAULT_SCENARIOS, format, verbose }
}

function build(scenario, verbose) {
  const gradlew = process.platform === 'win32' ? 'gradlew.bat' : './gradlew'
  const args = ['-p', PROBE, 'jsBrowserProductionWebpack', `-Pmodules=${scenario}`, '--console=plain', '--quiet']
  try {
    execFileSync(gradlew, args, { cwd: REPO, stdio: verbose ? 'inherit' : ['ignore', 'pipe', 'pipe'] })
  } catch (error) {
    if (error.stdout) process.stderr.write(error.stdout)
    if (error.stderr) process.stderr.write(error.stderr)
    throw new Error(`Failed to build scenario '${scenario}'`)
  }
}

// The declarations a TypeScript consumer sees, as a check that the probe really
// exported the surface it was supposed to. Only the ones nested inside a
// namespace count; the top-level lines are Kotlin's own boilerplate.
function countExports() {
  if (!existsSync(TYPINGS)) return 0
  return readFileSync(TYPINGS, 'utf8')
    .split('\n')
    .filter((line) => /^\s+(function|class|abstract class|const)\s/.test(line))
    .length
}

function measure(scenario, verbose) {
  build(scenario, verbose)
  const bytes = readFileSync(BUNDLE)
  const exports = countExports()
  if (scenario !== 'none' && exports === 0) {
    throw new Error(`Scenario '${scenario}' exported nothing: the probe facade did not compile into the bundle`)
  }
  mkdirSync(OUT, { recursive: true })
  writeFileSync(join(OUT, `${scenario.replace(/,/g, '+')}.js`), bytes)
  return {
    scenario,
    label: LABELS[scenario] ?? scenario,
    minified: bytes.length,
    gzip: gzipSync(bytes, { level: 9 }).length,
    // Quality 11 is what static asset pipelines use for pre-compressed files.
    brotli: brotliCompressSync(bytes, {
      params: { [constants.BROTLI_PARAM_QUALITY]: 11, [constants.BROTLI_PARAM_SIZE_HINT]: bytes.length },
    }).length,
    exports,
  }
}

function human(bytes) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

function withDeltas(results) {
  const baseline = results.find((it) => it.scenario === 'none')
  return results.map((it) => ({
    ...it,
    minifiedDelta: baseline ? it.minified - baseline.minified : null,
    gzipDelta: baseline ? it.gzip - baseline.gzip : null,
    brotliDelta: baseline ? it.brotli - baseline.brotli : null,
  }))
}

function cells(result) {
  const delta = result.scenario === 'none' || result.gzipDelta === null ? 'n/a' : `+${human(result.gzipDelta)}`
  return [result.label, human(result.minified), human(result.gzip), human(result.brotli), delta]
}

const HEADER = ['scenario', 'minified', 'gzip', 'brotli', 'gzip over baseline']

function renderTable(results) {
  const rows = results.map(cells)
  const widths = HEADER.map((_, i) => Math.max(...[HEADER, ...rows].map((row) => row[i].length)))
  const line = (row, pad) =>
    row.map((cell, i) => (i === 0 ? cell.padEnd(widths[i], pad) : cell.padStart(widths[i], pad))).join('  ')

  console.log()
  console.log(line(HEADER, ' '))
  console.log(line(widths.map((w) => '-'.repeat(w)), '-'))
  for (const row of rows) console.log(line(row, ' '))
  console.log()
  console.log('Kotlin/JS production link (DCE) + webpack production bundle (terser),')
  console.log('with the whole public API @JsExport-ed so nothing can be tree-shaken.')
  console.log('gzip -9, brotli quality 11. Bundles kept in build/js-size/ for inspection.')
}

function renderMarkdown(results) {
  console.log(`| ${HEADER.map((it) => it[0].toUpperCase() + it.slice(1)).join(' | ')} |`)
  console.log(`| --- |${' ---: |'.repeat(HEADER.length - 1)}`)
  for (const result of results) console.log(`| ${cells(result).join(' | ')} |`)
}

function help() {
  console.log(
    readFileSync(fileURLToPath(import.meta.url), 'utf8')
      .split('\n')
      .filter((line) => line.startsWith('//'))
      .map((line) => line.replace(/^\/\/ ?/, ''))
      .join('\n'),
  )
}

const { scenarios, format, verbose } = parseArgs(process.argv.slice(2))
if (format === 'help') {
  help()
  process.exit(0)
}

// The baseline is what makes the other numbers meaningful, so measure it even
// when it was not asked for.
const plan = scenarios.includes('none') ? scenarios : ['none', ...scenarios]
const results = []
for (const scenario of plan) {
  process.stderr.write(`building ${scenario} ...\n`)
  results.push(measure(scenario, verbose))
}

const reported = withDeltas(results).filter((it) => scenarios.includes(it.scenario))
if (format === 'json') {
  console.log(JSON.stringify(reported, null, 2))
} else if (format === 'markdown') {
  renderMarkdown(reported)
} else {
  renderTable(reported)
}
