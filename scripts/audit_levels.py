#!/usr/bin/env python3
"""Validate and emit a machine-readable static audit of the shipped level catalog.

This checks configuration integrity and documented difficulty bands only. It does
not simulate player strategy or claim that any level is winnable within its move
limit. Runtime seeded-board checks remain in LevelCatalog's Flutter tests.
"""

from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "assets/data/levels.json"
DEFAULT_OUTPUT = ROOT / "docs/level_catalog_audit.json"
EXPECTED_LEVELS = 100
DIFFICULTY_BANDS = ((1, 10, "tutorial"), (11, 20, "easy"), (21, 60, "medium"), (61, 100, "hard"))
VALID_OBSTACLES = {"woodBox", "ice", "vine", "soil"}
VALID_POWERS = {"areaBomb", "lineClear", "colorBomb", "rainbowKite"}


def expected_difficulty(level_id: int) -> str | None:
    for first, last, name in DIFFICULTY_BANDS:
        if first <= level_id <= last:
            return name
    return None


def audit(source: Path) -> dict:
    catalog = json.loads(source.read_text(encoding="utf-8"))
    errors: list[str] = []
    warnings: list[str] = []
    levels = catalog.get("levels")
    if catalog.get("version") != 1:
        errors.append("catalog version must be 1")
    if not isinstance(levels, list):
        levels = []
        errors.append("catalog levels must be an array")
    if len(levels) != EXPECTED_LEVELS:
        errors.append(f"expected {EXPECTED_LEVELS} levels, found {len(levels)}")

    details = []
    for position, level in enumerate(levels, start=1):
        errors_before_level = len(errors)
        level_id = level.get("id")
        prefix = f"level {level_id!r}"
        if level_id != position:
            errors.append(f"{prefix}: expected ordered id {position}")
        if level.get("difficulty") != expected_difficulty(level_id):
            errors.append(f"{prefix}: difficulty is outside its documented id band")

        fruit_goals = level.get("fruitGoals", {})
        obstacles = level.get("obstacles", {})
        specials = level.get("specialPieces", {})
        locks = level.get("lockedCells", {})
        obstacle_goals = level.get("obstacleGoals", {})
        if not fruit_goals or any(not isinstance(count, int) or count < 1 or count > 64 for count in fruit_goals.values()):
            errors.append(f"{prefix}: fruit goals must be non-empty and within 1..64")

        cells: dict[int, str] = {}
        for field, mapping in (("obstacles", obstacles), ("specialPieces", specials), ("lockedCells", locks)):
            if not isinstance(mapping, dict):
                errors.append(f"{prefix}: {field} must be a map")
                continue
            for raw_index, value in mapping.items():
                try:
                    index = int(raw_index)
                except (TypeError, ValueError):
                    errors.append(f"{prefix}: {field} has non-integer cell {raw_index!r}")
                    continue
                if not 0 <= index < 64:
                    errors.append(f"{prefix}: {field} cell {index} is outside 0..63")
                if field == "obstacles":
                    kind = value.get("type") if isinstance(value, dict) else None
                    layers = value.get("layers", 1) if isinstance(value, dict) else None
                    if kind not in VALID_OBSTACLES or not isinstance(layers, int) or not 1 <= layers <= 2:
                        errors.append(f"{prefix}: invalid obstacle at cell {index}")
                    label = f"obstacle:{kind}"
                elif field == "specialPieces":
                    if value not in VALID_POWERS:
                        errors.append(f"{prefix}: invalid special power at cell {index}")
                    label = f"special:{value}"
                else:
                    if not isinstance(value, int) or not 1 <= value <= 2:
                        errors.append(f"{prefix}: invalid legacy lock layers at cell {index}")
                    label = "legacyLock"
                if index in cells:
                    errors.append(f"{prefix}: {label} overlaps {cells[index]} at cell {index}")
                cells[index] = label

        obstacle_counts = Counter(
            item.get("type") for item in obstacles.values() if isinstance(item, dict)
        )
        for kind, count in obstacle_goals.items():
            if kind not in VALID_OBSTACLES or not isinstance(count, int) or not 1 <= count <= 64:
                errors.append(f"{prefix}: invalid obstacle goal {kind!r}={count!r}")
            elif obstacle_counts[kind] < count:
                errors.append(f"{prefix}: {kind} goal {count} exceeds configured count {obstacle_counts[kind]}")
        if bool(obstacles) != bool(obstacle_goals) and level_id > 10:
            errors.append(f"{prefix}: obstacle placement and obstacle goals must both be configured")

        thresholds = level.get("starThresholds", [])
        if (len(thresholds) != 3 or thresholds[0] != level.get("targetScore")
                or any(not isinstance(value, int) or value <= 0 for value in thresholds)
                or not (thresholds[0] < thresholds[1] < thresholds[2])):
            errors.append(f"{prefix}: star thresholds must be three ascending positive scores starting at targetScore")
        if not isinstance(level.get("moveLimit"), int) or not 1 <= level["moveLimit"] <= 60:
            errors.append(f"{prefix}: moveLimit must be within 1..60")

        type_set = set(obstacle_counts)
        if level_id <= 10 and (obstacles or specials):
            errors.append(f"{prefix}: tutorial phase should not introduce obstacles or powers")
        elif 11 <= level_id <= 20 and (type_set != {"woodBox"} or specials):
            errors.append(f"{prefix}: easy band should use wood boxes only and no placed powers")
        elif 21 <= level_id <= 40 and (type_set != {"ice", "vine"} or specials):
            errors.append(f"{prefix}: medium introduction band should use ice and vine without placed powers")
        elif 41 <= level_id <= 60 and (type_set != VALID_OBSTACLES or len(fruit_goals) < 2):
            errors.append(f"{prefix}: mixed-obstacle band should include all four obstacles and multiple fruit goals")
        elif 61 <= level_id <= 80 and (len(specials) < 2 or not obstacles):
            errors.append(f"{prefix}: hard band should include at least two placed powers and obstacles")
        elif 81 <= level_id <= 100 and (len(fruit_goals) != 3 or len(specials) < 2 or len(obstacles) < 4):
            errors.append(f"{prefix}: final band should include three fruit goals, two powers, and four obstacles")

        details.append({
            "id": level_id,
            "chapter": level.get("chapter"),
            "difficulty": level.get("difficulty"),
            "moveLimit": level.get("moveLimit"),
            "targetScore": level.get("targetScore"),
            "starThresholds": thresholds,
            "fruitGoals": fruit_goals,
            "specialPieces": [
                {"cell": int(index), "power": power}
                for index, power in sorted(specials.items(), key=lambda item: int(item[0]))
            ],
            "obstacles": [
                {"cell": int(index), "type": item.get("type"), "layers": item.get("layers", 1)}
                for index, item in sorted(obstacles.items(), key=lambda item: int(item[0]))
            ],
            "obstacleGoals": obstacle_goals,
            "counts": {
                "fruitGoalKinds": len(fruit_goals),
                "specialPieces": len(specials),
                "obstacleCells": len(obstacles),
                "obstacleLayers": sum(item.get("layers", 1) for item in obstacles.values() if isinstance(item, dict)),
                "obstacleTypes": dict(sorted(obstacle_counts.items())),
            },
            "configurationValid": len(errors) == errors_before_level,
        })

    obstacle_totals = Counter(
        obstacle["type"] for level in details for obstacle in level["obstacles"]
    )
    summary = {
        "levelCount": len(levels),
        "idSequenceValid": len(levels) == EXPECTED_LEVELS and [item.get("id") for item in levels] == list(range(1, EXPECTED_LEVELS + 1)),
        "difficultyBands": [
            {"firstLevel": first, "lastLevel": last, "difficulty": name}
            for first, last, name in DIFFICULTY_BANDS
        ],
        "moveLimitRange": [min((x.get("moveLimit", 0) for x in levels), default=0), max((x.get("moveLimit", 0) for x in levels), default=0)],
        "obstacleCellCount": sum(len(x.get("obstacles", {})) for x in levels),
        "obstacleTypeTotals": dict(sorted(obstacle_totals.items())),
        "specialPieceCount": sum(len(x.get("specialPieces", {})) for x in levels),
        "levelsWithObstacles": sum(bool(x.get("obstacles")) for x in levels),
        "levelsWithPowers": sum(bool(x.get("specialPieces")) for x in levels),
    }
    return {
        "reportVersion": 1,
        "source": "assets/data/levels.json",
        "scope": "Static catalog integrity and planned difficulty-band validation; not strategic solvability.",
        "runtimeBoardSmokeTest": "Not executed by this Python report. See LevelCatalog.validateStartingBoards in Flutter tests.",
        "summary": summary,
        "errors": errors,
        "warnings": warnings,
        "levels": details,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=SOURCE)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    report = audit(args.input)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {args.output}")
    print(f"levels={report['summary']['levelCount']} errors={len(report['errors'])} warnings={len(report['warnings'])}")
    for error in report["errors"]:
        print(f"ERROR: {error}")
    return 1 if report["errors"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
