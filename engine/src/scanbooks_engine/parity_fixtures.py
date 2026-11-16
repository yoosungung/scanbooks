"""Publish/load M1 parity fixtures under ``engine/tests/fixtures/parity/``.

Contents (per ARCHITECTURE §8 / ROADMAP M1):
- preprocess NCHW tensor (``.npy``)
- ``raw_flow`` @ 288×288 (``.npy``, already scaled) + upsampled flow used for warp
- ``warped`` reference PNG (grid_sample)
- ``raw_mask`` PNG + ``inpainted`` reference PNG (Telea)
"""

from __future__ import annotations

import json
from pathlib import Path

import numpy as np

from scanbooks_engine.contracts import GEO_NET_SIZE
from scanbooks_engine.finger_inpaint import inpaint_telea
from scanbooks_engine.geo_warp import upsample_flow, warp_grid_sample
from scanbooks_engine.preprocess import preprocess_geo

PARITY_CASE = "synth_page"
INPAINT_RADIUS = 3
IMAGE_HW = (96, 128)  # small work crop for cheap unit tests / mobile smoke


def _synthetic_image(h: int, w: int, seed: int = 7) -> np.ndarray:
    """Gradient page + stripe (uint8 RGB) — deterministic, no photo rights."""
    rng = np.random.default_rng(seed)
    yy, xx = np.mgrid[0:h, 0:w]
    r = (xx * 255 // max(w - 1, 1)).astype(np.uint8)
    g = (yy * 255 // max(h - 1, 1)).astype(np.uint8)
    b = np.full((h, w), 180, dtype=np.uint8)
    img = np.stack([r, g, b], axis=-1)
    # Text-like horizontal lines
    for y in range(8, h - 8, 10):
        img[y : y + 2, 8 : w - 8] = (20, 20, 40)
    # Noise speckles for remap sensitivity
    noise = rng.integers(0, 30, size=img.shape, dtype=np.uint8)
    return np.clip(img.astype(np.int16) + noise, 0, 255).astype(np.uint8)


def _synthetic_raw_flow() -> np.ndarray:
    """Mild non-identity flow at geoNetSize, already in grid_sample coords."""
    n = GEO_NET_SIZE
    ys, xs = np.meshgrid(
        np.linspace(-1.0, 1.0, n, dtype=np.float32),
        np.linspace(-1.0, 1.0, n, dtype=np.float32),
        indexing="ij",
    )
    # Barrel-ish nudge — keeps samples mostly in-bounds
    dx = 0.04 * xs * (xs * xs + ys * ys)
    dy = 0.04 * ys * (xs * xs + ys * ys)
    return np.stack([xs + dx, ys + dy], axis=0).astype(np.float32)  # 2×H×W


def _synthetic_mask(h: int, w: int) -> np.ndarray:
    """Thumb-like blob touching left border (outer-CC style)."""
    mask = np.zeros((h, w), dtype=np.uint8)
    cy, cx, ry, rx = h // 2, 0, h // 5, w // 6
    yy, xx = np.ogrid[:h, :w]
    ellipse = ((yy - cy) / max(ry, 1)) ** 2 + ((xx - cx) / max(rx, 1)) ** 2 <= 1.0
    mask[ellipse] = 255
    return mask


def ensure_parity_fixtures(root: Path) -> None:
    """Write parity artifacts if missing (idempotent)."""
    import cv2

    root = Path(root)
    root.mkdir(parents=True, exist_ok=True)
    manifest_path = root / "manifest.json"
    case = PARITY_CASE
    needed = [
        root / f"{case}_image.png",
        root / f"{case}_preprocess.npy",
        root / f"{case}_raw_flow.npy",
        root / f"{case}_flow_hw2.npy",
        root / f"{case}_warped.png",
        root / f"{case}_raw_mask.png",
        root / f"{case}_inpainted.png",
        manifest_path,
    ]
    if all(p.exists() for p in needed):
        return

    h, w = IMAGE_HW
    image_u8 = _synthetic_image(h, w)
    preprocess = preprocess_geo(image_u8)
    raw_flow = _synthetic_raw_flow()  # 2×288×288
    flow_hw2 = upsample_flow(raw_flow.transpose(1, 2, 0), out_hw=(h, w))
    image_f32 = image_u8.astype(np.float32) / 255.0
    warped_f32 = warp_grid_sample(image_f32, flow_hw2)
    warped_u8 = np.clip(np.rint(warped_f32 * 255.0), 0, 255).astype(np.uint8)
    mask = _synthetic_mask(h, w)
    # Inpaint from a copy where masked region is zeroed (typical finger hole)
    to_inpaint = image_u8.copy()
    to_inpaint[mask > 0] = 0
    inpainted = inpaint_telea(to_inpaint, mask, radius=INPAINT_RADIUS)

    cv2.imwrite(str(root / f"{case}_image.png"), cv2.cvtColor(image_u8, cv2.COLOR_RGB2BGR))
    np.save(root / f"{case}_preprocess.npy", preprocess)
    np.save(root / f"{case}_raw_flow.npy", raw_flow)
    np.save(root / f"{case}_flow_hw2.npy", flow_hw2)
    cv2.imwrite(str(root / f"{case}_warped.png"), cv2.cvtColor(warped_u8, cv2.COLOR_RGB2BGR))
    cv2.imwrite(str(root / f"{case}_raw_mask.png"), mask)
    cv2.imwrite(str(root / f"{case}_inpainted.png"), cv2.cvtColor(inpainted, cv2.COLOR_RGB2BGR))

    manifest = {
        "description": "M1 parity: preprocess tensor + raw_flow→warped + raw_mask→inpainted",
        "case": case,
        "geo_net_size": GEO_NET_SIZE,
        "image_hw": [h, w],
        "inpaint_radius": INPAINT_RADIUS,
        "flow_layout": "raw_flow.npy is 2×288×288 (x,y), already grid_sample-scaled",
        "tolerances": {
            "grid_sample_atol": 1e-5,
            "remap_max_abs": 3.0 / 255.0,
            "inpaint_atol": 1.0 / 255.0,
        },
        "files": {
            "image": f"{case}_image.png",
            "preprocess": f"{case}_preprocess.npy",
            "raw_flow": f"{case}_raw_flow.npy",
            "flow_hw2": f"{case}_flow_hw2.npy",
            "warped": f"{case}_warped.png",
            "raw_mask": f"{case}_raw_mask.png",
            "inpainted": f"{case}_inpainted.png",
        },
    }
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")


def load_parity(root: Path, case: str = PARITY_CASE) -> dict:
    """Load one parity case into numpy arrays (RGB float where noted)."""
    import cv2

    root = Path(root)
    ensure_parity_fixtures(root)
    manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
    files = manifest["files"]

    def _rgb(name: str) -> np.ndarray:
        bgr = cv2.imread(str(root / files[name]), cv2.IMREAD_COLOR)
        if bgr is None:
            raise FileNotFoundError(root / files[name])
        return cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)

    image_u8 = _rgb("image")
    warped_u8 = _rgb("warped")
    inpainted_u8 = _rgb("inpainted")
    mask_u8 = cv2.imread(str(root / files["raw_mask"]), cv2.IMREAD_GRAYSCALE)
    if mask_u8 is None:
        raise FileNotFoundError(root / files["raw_mask"])

    return {
        "manifest": manifest,
        "image_u8": image_u8,
        "image_f32": image_u8.astype(np.float32) / 255.0,
        "preprocess_nchw": np.load(root / files["preprocess"]),
        "raw_flow_288": np.load(root / files["raw_flow"]),
        "flow_hw2": np.load(root / files["flow_hw2"]).astype(np.float32),
        "warped_f32": warped_u8.astype(np.float32) / 255.0,
        "mask_u8": mask_u8,
        "inpainted_f32": inpainted_u8.astype(np.float32) / 255.0,
        "inpaint_radius": int(manifest["inpaint_radius"]),
    }
