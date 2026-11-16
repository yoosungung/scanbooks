# Export prototype — DocTr checkpoint measurement

Ticket: `fe6f15d9-862e-4154-9cab-348cc2a42f7f`  
Machine JSON: [export_prototype.json](export_prototype.json)

## Setup

- Upstream: [fh2019ustc/DocTr](https://github.com/fh2019ustc/DocTr) (patched `.cuda()` → `tensor.device` in `GeoTr.py` / `position_encoding.py` for CPU export)
- Weights: Hugging Face Space mirror of official Google Drive (`geotr.pth` / `illtr.pth` / `seg.pth`)
- Tooling: `torch` 2.15 CPU, `onnx` 1.23, `onnxruntime` 1.32, opset 17

Re-run:

```bash
cd engine
source .venv/bin/activate
pip install -e '.[doctr,dev]'
DOCTR_ROOT=/path/to/DocTr scanbooks-export-doctr --out-dir benches/onnx --json-out benches/export_prototype.json
pytest
```

## I/O tensors (confirmed)

| Model | Input NCHW | Output NCHW | dtype |
|---|---|---|---|
| GeoTr (+ flow scale in graph) | `1×3×288×288` | `1×2×288×288` | float32 |
| IllTr (single patch) | `1×3×128×128` | `1×3×128×128` | float32 |
| U2NETP seg | `1×3×288×288` | `1×1×288×288` | float32 |

Flow post-scale in export graph: `(2 * (bm / 286.8) - 1) * 0.99` (matches official `inference.py`).  
`illOverlap` = `int(128 * 0.125) = 16` px (source comment `# 32` is wrong).

## Parameters · FP16 capacity

| Model | params | FP16 weights |
|---|---:|---:|
| GeoTr | 25,747,330 | **49.11 MB** |
| IllTr | 12,907,872 | **24.62 MB** |
| Seg | 1,131,181 | **2.16 MB** |
| **Sum** | | **75.89 MB** |

ARCHITECTURE §5.4 resident budget ≤ ~80 MB: **pass** (geo+ill+seg FP16).  
ONNX file sizes (FP32 initializers) are larger: geotr 86.4 MB, illtr 66.3 MB, seg 4.4 MB — expect FP16 conversion before mobile bundle.

## ORT operators

| Concern | GeoTr ONNX | IllTr ONNX | Seg ONNX |
|---|---|---|---|
| **GridSample** | **absent** | **absent** | absent |
| LayerNormalization | yes | yes | no |
| Reshape | heavy (134) | very heavy (3108) | no |
| Other watch | InstanceNormalization, Cast, Softmax, MatMul | ScatterND×2048, Where/Expand/Slice explosion | Resize |

**GridSample:** official warp uses `F.grid_sample(..., align_corners=True)` **outside** the network. Export keeps warp as OpenCV/`remap` post-process — no in-graph GridSample for MVP.

**IllTr graph size:** naive `torch.onnx.export` produced **~123k nodes** (control-flow / pad-crop unrolled into ScatterND/Where). CPU ORT smoke still ran, but mobile EP + load time are at risk. Follow-up: simplify IllTr forward for export (fixed 128 patch only, no internal padding loops) before app integration.

**LayerNormalization / Reshape:** present on GeoTr+IllTr — profile CoreML/NNAPI for Cast/CPU fallback in the latency ticket.

## ARCHITECTURE §5

Numbers above are reflected in ARCHITECTURE §5 (params / FP16 / operator notes). No change to `geoNetSize=288`, `illPatchSize=128`, `illOverlap=12.5%`, `workLongEdge=1024`.
