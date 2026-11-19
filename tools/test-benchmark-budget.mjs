// Tests for benchmark-budget.mjs (B-19). Run: node --test tools/test-benchmark-budget.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { check } from './benchmark-budget.mjs';

const ctx = { build: { model: 'Redmi 10', version: { sdk: 33 } } };
const start = (ms) => ({ name: 'coldStartBaselineProfile', metrics: { timeToFullDisplayMs: { median: ms } } });
const tabs = (p99) => ({ name: 'tabSwitchBaselineProfile', sampledMetrics: { frameDurationCpuMs: { P50: 8, P99: p99 } } });

test('within budget passes', () => {
  const r = check({ context: ctx, benchmarks: [start(1999), tabs(100)] });
  assert.deepEqual(r.failures, []);
  assert.equal(r.lines.length, 2);
});

test('over budget fails with the measure named', () => {
  const r = check({ context: ctx, benchmarks: [start(2001), tabs(101)] });
  assert.equal(r.failures.length, 2);
  assert.match(r.failures[0], /OVER BUDGET coldStart/);
  assert.match(r.failures[1], /OVER BUDGET tabSwitch/);
});

test('missing fully drawn metric fails instead of passing quietly', () => {
  const r = check({ context: ctx, benchmarks: [{ name: 'coldStartNoCompilation', metrics: { timeToInitialDisplayMs: { median: 500 } } }] });
  assert.match(r.failures[0], /missing/);
});

test('a file with no matching benchmark fails', () => {
  assert.equal(check({ benchmarks: [] }).failures.length, 1);
});

test('unthrottled run is held to a quarter of each budget', () => {
  const r = check({ context: ctx, benchmarks: [start(501), tabs(25)] }, { unthrottled: true });
  assert.equal(r.failures.length, 1);
  assert.match(r.failures[0], /coldStart.*budget 500 ms/);
});
