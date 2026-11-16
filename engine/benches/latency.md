# EP latency bench (M1)

Ticket: `3ad0ee61-1d56-4846-9b4b-411aa1e5a301`  
Machine JSON: [latency.json](latency.json)

Host: `Darwin arm64`  
ORT providers: `['CoreMLExecutionProvider', 'AzureExecutionProvider', 'CPUExecutionProvider']`

## Gates (ARCHITECTURE §5.4)

- GeoTr e2e ≤ **2.5 s** / page @ net 288 + workLongEdge 1024
- IllTr e2e ≤ **3.5 s** / page @ 128 patches + stitch

## Required EP rows (net inference ms, median of timed repeats)

| Model | CPU FP32 ms | CPU weight-INT8 ms | CPU dynamic-INT8 ms | CoreML FP16 ms | NNAPI FP16 |
|---|---:|---:|---:|---:|---|
| geotr | 187.96 | 199.02 | 187.74 | 121.61 | deferred_android_only |
| illtr | 136.1 | 134.55 | 135.08 | skipped_illtr_compile_risk | deferred_android_only |
| seg | 53.91 | 52.1 | 51.08 | 27.59 | deferred_android_only |

## Post-process (not in ONNX)

- flow upsample + blur + `cv2.remap` @ 1024: **4.3 ms**
- IllTr patch grid @ 1024×1024: **81** patches (stride 112)

## End-to-end estimates

| EP | GeoTr e2e s (net+remap) | gate ≤2.5 | IllTr e2e s (seq patches) | gate ≤3.5 |
|---|---:|:---:|---:|:---:|
| cpu_fp32 | 0.192 | pass | 11.024 | fail |
| cpu_weight_int8 | 0.203 | pass | 10.899 | fail |
| cpu_dynamic_int8 | 0.192 | pass | 10.941 | fail |
| coreml_fp16 | 0.126 | pass | — | n/a |

## Recommendation

- **Default EP (this host):** `CoreML_MLProgram_NeuralEngine+CPU_fallback`
- **GeoTr quant:** `dynamic_int8` — CPU dyn 187.74 ms / WO 199.02 ms; errors from quantize_candidates (dyn max_abs≈0.0037, WO≈5e-4)
- **IllTr:** blocked_on_export_simplify — latency dominated by ~123k-node graph; WO still preferred size
- **Seg:** keep_fp16_or_fp32 — not on critical path for EP pick

## Fallback / Cast notes

- LayerNormalization: present — CoreML/NNAPI may Cast/decompose; profile fallback
- Reshape: present (n=134) — prefer static shapes; watch dynamic reshape on mobile EP
- GridSample: absent from network ONNX — warp is OpenCV remap post-process (measured separately)
- CoreML partition: providers_used=['CoreMLExecutionProvider', 'CPUExecutionProvider']; fallback_to_cpu=True
- NNAPI: deferred_android_only — measure on device with ORT Mobile NNAPI EP
- CPU FP16 policy: CPU×FP16 not a required ship path; ARCHITECTURE: if CPU FP16 slower than CPU FP32, do not adopt that combo as default.
- CoreML vs CPU: CoreML NE geotr 121.61 ms < CPU FP32 187.96 ms — prefer CoreML MLProgram on Apple hosts

## Re-run

```bash
cd engine && source .venv/bin/activate
pip install -e '.[doctr,dev]'
scanbooks-latency-bench --onnx-dir /tmp/doctr-onnx --quant-dir /tmp/doctr-onnx/quant
pytest tests/test_latency.py -q
```
