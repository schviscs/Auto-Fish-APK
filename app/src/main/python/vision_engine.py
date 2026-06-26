"""Local vision heuristics for Auto Fish NTE.

This module is intentionally deterministic, lightweight and offline-first so it can
run inside Android through Chaquopy. It receives raw RGBA bytes from Kotlin and
returns a plain dictionary that Kotlin maps to BotAction and BotState.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Dict, Iterable, Tuple

try:
    import numpy as np
except Exception:  # pragma: no cover - Chaquopy provides numpy when installed.
    np = None


@dataclass(frozen=True)
class DetectionConfig:
    bite_color_rgb: Tuple[int, int, int] = (80, 180, 255)
    color_tolerance: int = 42
    bite_threshold: float = 0.015
    hook_x_norm: float = 0.50
    hook_y_norm: float = 0.82
    cast_x_norm: float = 0.50
    cast_y_norm: float = 0.82


def _coerce_config(config: Dict[str, Any] | None) -> DetectionConfig:
    config = config or {}
    rgb = config.get("bite_color_rgb") or config.get("biteColorRgb") or (80, 180, 255)
    try:
        rgb_tuple = tuple(int(v) for v in list(rgb)[:3])
    except Exception:
        rgb_tuple = (80, 180, 255)
    if len(rgb_tuple) != 3:
        rgb_tuple = (80, 180, 255)

    return DetectionConfig(
        bite_color_rgb=rgb_tuple,
        color_tolerance=int(config.get("color_tolerance", config.get("colorTolerance", 42))),
        bite_threshold=float(config.get("bite_threshold", config.get("biteThreshold", 0.015))),
        hook_x_norm=float(config.get("hook_x_norm", config.get("hookXNorm", 0.50))),
        hook_y_norm=float(config.get("hook_y_norm", config.get("hookYNorm", 0.82))),
        cast_x_norm=float(config.get("cast_x_norm", config.get("castXNorm", 0.50))),
        cast_y_norm=float(config.get("cast_y_norm", config.get("castYNorm", 0.82))),
    )


def analyze_rgba(frame_bytes: bytes, width: int, height: int, config: Dict[str, Any] | None = None) -> Dict[str, Any]:
    """Analyze an RGBA frame and return a normalized bot decision.

    The first production heuristic detects a bright blue/cyan bite signal. It is
    designed as a stable baseline that can be improved later with template
    matching or a small TensorFlow Lite model, without changing Kotlin APIs.
    """

    cfg = _coerce_config(config)
    if width <= 0 or height <= 0 or not frame_bytes:
        return _wait("empty_frame", 0.0, {"bite_score": "0.0"})

    if np is None:
        return _wait("numpy_unavailable", 0.0, {"bite_score": "0.0"})

    expected = width * height * 4
    if len(frame_bytes) < expected:
        return _wait("short_frame", 0.0, {"bite_score": "0.0", "bytes": str(len(frame_bytes))})

    rgba = np.frombuffer(frame_bytes[:expected], dtype=np.uint8).reshape((height, width, 4))
    rgb = rgba[:, :, :3].astype(np.int16)
    target = np.array(cfg.bite_color_rgb, dtype=np.int16)
    distance = np.abs(rgb - target).sum(axis=2)
    color_mask = distance <= max(1, cfg.color_tolerance) * 3

    # Focus the detector on the middle/lower gameplay area where bite prompts and
    # fishing controls are usually shown, but keep a global fallback.
    y_start = int(height * 0.30)
    y_end = int(height * 0.92)
    x_start = int(width * 0.10)
    x_end = int(width * 0.90)
    roi = color_mask[y_start:y_end, x_start:x_end]
    bite_score = float(roi.mean()) if roi.size else float(color_mask.mean())

    if bite_score >= cfg.bite_threshold:
        centroid = _centroid_norm(roi, x_start, y_start, width, height) or (cfg.hook_x_norm, cfg.hook_y_norm)
        return {
            "state": "BITE_DETECTED",
            "confidence": min(1.0, bite_score / max(cfg.bite_threshold, 0.0001)),
            "action": {
                "type": "tap",
                "x_norm": float(centroid[0]),
                "y_norm": float(centroid[1]),
                "duration_ms": 85,
                "reason": "hook_bite_signal",
            },
            "debug": {
                "bite_score": f"{bite_score:.6f}",
                "roi": f"{x_start},{y_start},{x_end},{y_end}",
            },
        }

    return {
        "state": "WAITING_BITE",
        "confidence": max(0.0, 1.0 - (bite_score / max(cfg.bite_threshold, 0.0001))),
        "action": {
            "type": "wait",
            "duration_ms": 120,
            "reason": "no_bite_signal",
        },
        "debug": {
            "bite_score": f"{bite_score:.6f}",
            "target_rgb": ",".join(str(v) for v in cfg.bite_color_rgb),
        },
    }


def _centroid_norm(mask: Any, x_offset: int, y_offset: int, width: int, height: int) -> Tuple[float, float] | None:
    ys, xs = np.where(mask)
    if xs.size == 0:
        return None
    x_norm = float((xs.mean() + x_offset) / max(width, 1))
    y_norm = float((ys.mean() + y_offset) / max(height, 1))
    return max(0.0, min(1.0, x_norm)), max(0.0, min(1.0, y_norm))


def _wait(reason: str, confidence: float, debug: Dict[str, str]) -> Dict[str, Any]:
    return {
        "state": "WAITING_FRAME",
        "confidence": confidence,
        "action": {
            "type": "wait",
            "duration_ms": 250,
            "reason": reason,
        },
        "debug": debug,
    }
