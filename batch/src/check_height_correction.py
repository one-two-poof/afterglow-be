"""HEIGHT_RAW가 정상 범위였는데도 HEIGHT(보정후)가 달라진 건물을 찾는 QA 스크립트.

buildings._correct_height() 의 needs_fallback 로직이 의도대로 정상 범위 값은
건드리지 않는지 확인한다. 결과를 batch/debug/height_correction_check.csv 로 저장.
"""
from __future__ import annotations

import os

from src.buildings import DEBUG_DIR, HEIGHT_MAX_M, HEIGHT_MIN_M, load_seoul_buildings


def main() -> None:
    gdf, _ = load_seoul_buildings()

    was_normal_range = (
        gdf["HEIGHT_RAW"].notna()
        & (gdf["HEIGHT_RAW"] >= HEIGHT_MIN_M)
        & (gdf["HEIGHT_RAW"] <= HEIGHT_MAX_M)
    )
    changed = gdf["HEIGHT"] != gdf["HEIGHT_RAW"]

    mismatched = gdf.loc[was_normal_range & changed].copy()
    mismatched["차이값"] = mismatched["HEIGHT"] - mismatched["HEIGHT_RAW"]

    out = mismatched[["HEIGHT_RAW", "GRND_FLR", "HEIGHT", "차이값"]]
    out_path = os.path.join(DEBUG_DIR, "height_correction_check.csv")
    os.makedirs(DEBUG_DIR, exist_ok=True)
    out.to_csv(out_path, index=False, encoding="utf-8-sig")

    print(f"정상범위(2~300)였는데 보정 후 값이 달라진 건물 수: {len(out)}")
    print(f"저장: {out_path}")


if __name__ == "__main__":
    main()
