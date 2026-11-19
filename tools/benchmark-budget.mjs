#!/usr/bin/env node
// Design §10 budget gate for Macrobenchmark results (B-19). Reads one or more benchmarkData.json files written by
// `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest` and fails (exit 1) when a measure is over budget.
// Usage: node tools/benchmark-budget.mjs [--unthrottled] <benchmarkData.json>...
// Budgets hold "with the CPU throttled 4x". --unthrottled is for a phone whose clocks cannot be lowered (the
// unrooted Redmi 10): every budget is divided by 4 as a stand-in (docs/Decisions.md, B-19 ⚠).
import { readFileSync } from 'node:fs';

// Change budgets in Design §10 first. `metric` is the Macrobenchmark metric, `stat` the statistic checked.
export const BUDGETS = [
  { test: /^coldStart/, metric: 'timeToFullDisplayMs', stat: 'median', budgetMs: 2000, what: 'cold start to usable Hari ini' },
  { test: /^tabSwitch/, metric: 'frameDurationCpuMs', stat: 'P99', budgetMs: 100, what: 'tab switch (slowest frames)' },
];

export const THROTTLE = 4;

/** Returns { lines, failures } for one parsed benchmarkData.json. */
export function check(data, { unthrottled = false } = {}) {
  const lines = [];
  const failures = [];
  const device = data.context?.build ? `${data.context.build.model} API ${data.context.build.version?.sdk}` : 'unknown device';
  for (const b of data.benchmarks ?? []) {
    for (const rule of BUDGETS.filter((r) => r.test.test(b.name))) {
      const m = b.metrics?.[rule.metric] ?? b.sampledMetrics?.[rule.metric];
      const value = m?.[rule.stat];
      if (typeof value !== 'number') {
        failures.push(`${b.name}: ${rule.metric}.${rule.stat} missing (did Hari ini report fully drawn?)`);
        continue;
      }
      const budget = unthrottled ? rule.budgetMs / THROTTLE : rule.budgetMs;
      const line = `${b.name} on ${device}: ${rule.what} ${rule.metric} ${rule.stat} = ${value.toFixed(1)} ms, budget ${budget} ms${unthrottled ? ' (unthrottled stand-in, budget / 4)' : ''}`;
      lines.push(line);
      if (value > budget) failures.push(`OVER BUDGET ${line}`);
    }
  }
  if (lines.length === 0 && failures.length === 0) failures.push('no benchmark in the file matches a budget');
  return { lines, failures };
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const args = process.argv.slice(2);
  const unthrottled = args.includes('--unthrottled');
  const files = args.filter((a) => a !== '--unthrottled');
  if (files.length === 0) {
    console.error('usage: node tools/benchmark-budget.mjs [--unthrottled] <benchmarkData.json>...');
    process.exit(2);
  }
  let failed = false;
  for (const f of files) {
    const { lines, failures } = check(JSON.parse(readFileSync(f, 'utf8')), { unthrottled });
    lines.forEach((l) => console.log(l));
    failures.forEach((l) => console.error(l));
    failed ||= failures.length > 0;
  }
  process.exit(failed ? 1 : 0);
}
