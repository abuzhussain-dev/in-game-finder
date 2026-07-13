# Structure Position Algorithm (Cubiomes-derived)

Source: Cubiomes (`generator.c`) — Minecraft's `RandomSpreadStructurePlacement` algorithm.

## Java Random (LCG)

```python
class JavaRandom:
    MULTIPLIER = 25214903917   # 0x5DEECE66D
    INCREMENT  = 11
    MASK       = (1 << 48) - 1  # 281474976710655

    def __init__(self, seed):
        self.seed = (seed ^ self.MULTIPLIER) & self.MASK

    def next(self, bits):
        self.seed = (self.seed * self.MULTIPLIER + self.INCREMENT) & self.MASK
        return self.seed >> (48 - bits)

    def next_int(self, bound):
        if (bound & -bound) == bound:  # power of 2
            return (bound * self.next(31)) >> 31
        while True:
            bits = self.next(31)
            val = bits % bound
            if bits - val + (bound - 1) >= 0:
                return val
```

## Constants

| Constant | Value |
|----------|-------|
| K1 (region X multiplier) | `341873128712` |
| K2 (region Z multiplier) | `132897987541` |

## Floor Division

```python
def floor_div(a, b):
    """True floor division (like Minecraft's Math.floorDiv)"""
    if a >= 0: return a // b
    else: return -((-a - 1) // b + 1)
```

## Structure Sets

| Structure | Salt | Spacing | Separation | Spread |
|-----------|------|---------|------------|--------|
| pillager_outpost | 165745296 | 32 | 8 | linear |
| nether_fossil | 14357921 | 2 | 1 | linear |
| village | 10387312 | 34 | 8 | linear |
| desert_pyramid | 14357617 | 32 | 8 | linear |
| jungle_temple | 14357619 | 32 | 8 | linear |
| igloo | 14357618 | 32 | 8 | linear |
| swamp_hut | 14357620 | 32 | 8 | linear |
| ocean_monument | 10387313 | 32 | 5 | triangular |
| woodland_mansion | 10387319 | 80 | 20 | triangular |
| trial_chambers | 94251327 | 34 | 12 | linear |
| ancient_city | 20083232 | 24 | 8 | linear |
| end_city | 10387313 | 20 | 11 | linear |

## Algorithm (6 Steps)

1. **Region calculation**: `region = floor_div(chunk_coord, spacing)`
2. **Region seed**: `region_seed = world_seed + region_x * K1 + region_z * K2 + salt`
3. **Initialize RNG**: `rng = JavaRandom(region_seed)`
4. **Generate offsets**:
   - Linear: `offset_x = rng.nextInt(spacing - separation)`, same for Z
   - Triangular: `offset = (rng.nextInt(spacing - separation) + rng.nextInt(spacing - separation)) // 2`, twice (once for X, once for Z)
5. **Structure chunk**: `struct_chunk_x = region_x * spacing + offset_x`
6. **Structure block**: `struct_block_x = struct_chunk_x * 16 + 8` (+8 for center of chunk)

## Verified

Test seed `-4195441465221527384`:
- `(-2000, -2704)` → Pillager Outpost at chunk `(-125, -169)` ✓
- `(-832, -2240)` → Pillager Outpost + Nether Fossil at chunk `(-52, -140)` ✓
- `(3232, -1888)` → Pillager Outpost + Nether Fossil at chunk `(202, -118)` ✓
