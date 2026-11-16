# ONNX quantization candidates (M1)

Ticket: `0be42506-ebb3-4175-909c-3eb656a3307e`  
Machine JSON: [quantize_candidates.json](quantize_candidates.json)

## Policy

- **Allowed:** weight-only INT8 (`MatMulNBits` bits=8), dynamic INT8, mixed (exclude attention/flow head — deferred).
- **Forbidden as default:** static INT8 (activation calibration). Do not ship GeoTr/IllTr with static INT8 as the default path.

## Tooling

- Dynamic: `onnxruntime.quantization.quantize_dynamic (QInt8, per_channel)`
- Weight-only: `MatMulNBitsQuantizer bits=8 block_size=128 symmetric QOperator`
- Eval: `CPUExecutionProvider smoke; EP latency → later benches/latency.md`

## Measured (CPU ORT smoke, seed=0)

| Model | FP32 MB | dynamic INT8 MB | dyn max_abs | weight-only INT8 MB | WO max_abs |
|---|---:|---:|---:|---:|---:|
| geotr | 86.42 | 23.49 | 0.00374 | 37.45 | 0.0004938 |
| illtr | 66.29 | 42.32 | 0.009683 | 35.13 | 0.006267 |
| seg | 4.39 | 1.39 | 0.9738 | 4.39 | 0 |

## Recommendation

- **GeoTr:** `either` — dynamic ~3.7× smaller (23.5 vs 86.4 MB) with max_abs≈0.004; weight-only tighter error (≈5e-4) at 37.5 MB — pick after latency.md
- **IllTr:** `weight_only_int8` — WO smaller+lower error than dynamic; still blocked by huge export graph
- **Seg:** `keep_fp16_or_fp32` — WO no-op (Conv); dynamic shrinks but sigmoid max_abs≈0.97 — not default
- **static INT8:** `do_not_adopt_as_default`
- **mixed (exclude flow head):** candidate_if_flow_head_degrades — not measured this ticket

## Notes

- IllTr graph: IllTr naive ONNX still ~123k nodes (export_prototype); quantize size OK but load time dominates — simplify IllTr forward before app integration
- Static risk: static INT8 calibrates activations offline; GeoTr flow regression / Transformer sensitivity → ARCHITECTURE forbids as default
- FP16 baseline: weight-only FP16 already measured (sum geo+ill+seg 75.89 MB) — keep as baseline

## Re-run

```bash
cd engine && source .venv/bin/activate
pip install -e '.[doctr,dev]'
# needs FP32 ONNX from scanbooks-export-doctr
scanbooks-quantize-candidates --onnx-dir /tmp/doctr-onnx --out-dir benches/onnx_quant --json-out benches/quantize_candidates.json
pytest tests/test_quantize.py
```

Latency / CoreML / NNAPI EP rows remain for `benches/latency.md` (next M1 slice).
