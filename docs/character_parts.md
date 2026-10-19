# Delivered character parts (doll-maker sheet)

The art delivered on `arena/b487d066-kamand` (`assets/art/character_parts_sheet_green.png`)
is a **complete doll-maker decomposition** of Orchard Girl: the assembled figure
sits on the top left of the sheet and every separated piece is laid out around it,
all drawn on a flat green background (`(60, 121, 50)`).

This folder documents the automatic split of that sheet and the checks that were run
on it. Nothing here is wired into the game yet; the rig in
`assets/game/characters/rig/` is still the one the game ships.

## Pipeline

```bash
python3 tools/character_parts/extract_parts.py \
    --sheet assets/art/character_parts_sheet_green.png \
    --out build/character_parts

python3 tools/character_parts/assemble_parts.py \
    --parts build/character_parts \
    --out build/character_parts_assembly.png
```

`extract_parts.py` keys the background out by colour distance, removes the green
spill from the edges, and splits the sheet with connected components. It finds
**33 parts**, the same count and the same bounding boxes on every run.

`assemble_parts.py` composites the parts back into the reference pose using the
transforms in `tools/character_parts/parts_manifest.json`. The result is
`docs/character_parts_assembly.png`.

## Inventory

`index` is the extraction order (raster order on the sheet).

| # | name | size | role |
|---|------|------|------|
| 00 | assembled_figure | 303×814 | reference |
| 01 | hair_front | 193×186 | hair |
| 02 | hair_back | 240×196 | hair |
| 03 | head | 163×250 | head |
| 04 | brow_left | 53×20 | face |
| 05 | brow_right | 46×19 | face |
| 06 | eye_right | 48×42 | face |
| 07 | eye_left | 54×41 | face |
| 08 | arm_sleeved_a | 140×240 | arm |
| 09 | arm_sleeved_b | 107×267 | arm |
| 10 | arm_sleeved_c | 127×271 | arm |
| 11 | nose | 32×27 | face |
| 12 | mouth | 51×16 | face |
| 13 | arm_bare_a | 85×187 | arm |
| 14 | arm_bare_b | 75×196 | arm |
| 15 | overalls_apron | 194×326 | torso |
| 16 | tshirt | 217×205 | torso |
| 17 | arm_bare_c | 99×207 | arm |
| 18 | arm_bare_d | 78×235 | arm |
| 19 | trousers_pair | 165×265 | legs |
| 20–24 | leg_a … leg_e | 74×178 … 100×208 | legs |
| 25 | basket_empty | 155×219 | prop |
| 26–29 | boot_a … boot_d | 98×162 … 122×167 | foot |
| 30 | basket_berries | 143×138 | prop |
| 31 | strawberry | 58×57 | prop |
| 32 | leaf | 37×41 | prop |

Three sleeved arms, four bare arms, five leg pieces and four boots are drawn as
**interchangeable variants**, not as duplicates — this is a dress-up wardrobe, so
any outfit choice needs its own anchor.

## Verdict

* The parts really are one character. Composited back, the assembly matches the
  shipped idle silhouette with **IoU 0.964** against
  `assets/game/characters/orchard_girl_idle.png` (same design, same pose,
  same colours; the residual difference is hair curls, freckles and shading).
* The set is **complete**: head, both hair masses, all four facial features per eye,
  nose, mouth, torso, apron, both arms, both legs and both boots are present.
* The parts are **not all on the same scale** — the boots and the bare arms are
  drawn larger on the sheet than the head and torso. Each part therefore needs its
  own anchor and scale, which is exactly what `parts_manifest.json` records.
* The assembly sheet in `docs/character_parts_assembly.png` is a **validation
  render, not production art**. Known offsets in it: the nose sits a few pixels high
  and carries a faint fringe, the right arm is a bare arm drawn under the sleeve, and
  the boots are 1–2 % small.

Making the rig out of these parts means re-anchoring 37 rig layers and
recalibrating `test/orchard_girl_render_test.dart`; that work has **not** been done.
