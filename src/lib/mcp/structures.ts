// Deterministic Minecraft structure locator (1.21.1) — port of Mojang's scatter algorithm.
// Uses Java Random semantics for accurate results.

export type Placement = "scatter" | "stronghold_ring" | "per_chunk";

export interface StructureDef {
  id: string;
  name: string;
  placement: Placement;
  spacing: number;
  separation: number;
  salt: number;
}

export const STRUCTURES: readonly StructureDef[] = [
  { id: "village",          name: "Village",          placement: "scatter", spacing: 34, separation: 8,  salt: 10387312 },
  { id: "pillager_outpost", name: "Pillager Outpost", placement: "scatter", spacing: 32, separation: 8,  salt: 165745296 },
  { id: "desert_pyramid",   name: "Desert Pyramid",   placement: "scatter", spacing: 32, separation: 8,  salt: 14357617 },
  { id: "jungle_temple",    name: "Jungle Temple",    placement: "scatter", spacing: 32, separation: 8,  salt: 14357619 },
  { id: "swamp_hut",        name: "Swamp Hut",        placement: "scatter", spacing: 32, separation: 8,  salt: 14357620 },
  { id: "igloo",            name: "Igloo",            placement: "scatter", spacing: 32, separation: 8,  salt: 14357618 },
  { id: "ocean_monument",   name: "Ocean Monument",   placement: "scatter", spacing: 32, separation: 5,  salt: 10387313 },
  { id: "woodland_mansion", name: "Woodland Mansion", placement: "scatter", spacing: 80, separation: 20, salt: 10387319 },
  { id: "ruined_portal",    name: "Ruined Portal",    placement: "scatter", spacing: 40, separation: 15, salt: 34222645 },
  { id: "shipwreck",        name: "Shipwreck",        placement: "scatter", spacing: 24, separation: 4,  salt: 165745295 },
  { id: "ancient_city",     name: "Ancient City",     placement: "scatter", spacing: 24, separation: 8,  salt: 20083232 },
  { id: "trial_chambers",   name: "Trial Chambers",   placement: "scatter", spacing: 34, separation: 12, salt: 94251327 },
  { id: "nether_fortress",  name: "Nether Fortress",  placement: "scatter", spacing: 27, separation: 4,  salt: 30084232 },
  { id: "bastion_remnant",  name: "Bastion Remnant",  placement: "scatter", spacing: 27, separation: 4,  salt: 30084232 },
  { id: "end_city",         name: "End City",         placement: "scatter", spacing: 20, separation: 11, salt: 10387313 },
  { id: "stronghold",       name: "Stronghold",       placement: "stronghold_ring", spacing: 0, separation: 0, salt: 0 },
];

export function getStructure(id: string): StructureDef | undefined {
  const q = id.toLowerCase();
  return STRUCTURES.find((s) => s.id === q || s.name.toLowerCase() === q);
}

// ---- Java Random (LCG: 0x5DEECE66D, 0xB, 48-bit) using BigInt ----
class JavaRandom {
  private seed: bigint;
  private static readonly MUL = 0x5deece66dn;
  private static readonly ADD = 0xbn;
  private static readonly MASK = (1n << 48n) - 1n;

  constructor(seed: bigint) {
    this.seed = (seed ^ JavaRandom.MUL) & JavaRandom.MASK;
  }

  private next(bits: number): number {
    this.seed = (this.seed * JavaRandom.MUL + JavaRandom.ADD) & JavaRandom.MASK;
    return Number(this.seed >> BigInt(48 - bits));
  }

  nextInt(bound: number): number {
    if (bound <= 0) throw new Error("bound must be positive");
    // Power of two shortcut
    if ((bound & -bound) === bound) {
      return Number((BigInt(bound) * BigInt(this.next(31))) >> 31n);
    }
    let bits: number, val: number;
    do {
      bits = this.next(31);
      val = bits % bound;
    } while (bits - val + (bound - 1) < 0);
    return val;
  }

  nextDouble(): number {
    const hi = this.next(26);
    const lo = this.next(27);
    return (hi * 2 ** 27 + lo) / 2 ** 53;
  }
}

// signed 64-bit BigInt wrap
function toI64(n: bigint): bigint {
  const MASK = (1n << 64n) - 1n;
  let v = n & MASK;
  if (v >= 1n << 63n) v -= 1n << 64n;
  return v;
}

function scatterCandidate(seed: bigint, def: StructureDef, regionX: number, regionZ: number): [number, number] {
  const popSeed = toI64(
    BigInt(regionX) * 341873128712n + BigInt(regionZ) * 132897987541n + seed + BigInt(def.salt),
  );
  const rng = new JavaRandom(popSeed);
  const range = def.spacing - def.separation;
  const ox = range > 0 ? rng.nextInt(range) : 0;
  const oz = range > 0 ? rng.nextInt(range) : 0;
  return [regionX * def.spacing + ox, regionZ * def.spacing + oz];
}

export interface FoundStructure {
  x: number; // block coords, chunk-center
  z: number;
  chunkX: number;
  chunkZ: number;
  distance: number;
  bearing: string;
}

export function findNearestStructure(
  seed: bigint,
  structureId: string,
  playerX: number,
  playerZ: number,
  radiusChunks = 3200,
): FoundStructure | null {
  const def = getStructure(structureId);
  if (!def) throw new Error(`Unknown structure: ${structureId}`);

  if (def.placement === "stronghold_ring") {
    return nearestStronghold(seed, playerX, playerZ);
  }
  if (def.placement !== "scatter") return null;

  const centerChunkX = playerX >> 4;
  const centerChunkZ = playerZ >> 4;
  const regionRadius = Math.max(1, Math.ceil(radiusChunks / def.spacing) + 1);
  const centerRegionX = Math.floor(centerChunkX / def.spacing);
  const centerRegionZ = Math.floor(centerChunkZ / def.spacing);

  let best: FoundStructure | null = null;
  let bestDistSq = Infinity;

  for (let rx = centerRegionX - regionRadius; rx <= centerRegionX + regionRadius; rx++) {
    for (let rz = centerRegionZ - regionRadius; rz <= centerRegionZ + regionRadius; rz++) {
      const [cx, cz] = scatterCandidate(seed, def, rx, rz);
      const bx = (cx << 4) + 8;
      const bz = (cz << 4) + 8;
      const dx = bx - playerX;
      const dz = bz - playerZ;
      const d = dx * dx + dz * dz;
      if (d < bestDistSq) {
        bestDistSq = d;
        best = { x: bx, z: bz, chunkX: cx, chunkZ: cz, distance: Math.round(Math.sqrt(d)), bearing: bearing(dx, dz) };
      }
    }
  }
  return best;
}

function nearestStronghold(seed: bigint, playerX: number, playerZ: number): FoundStructure {
  const rng = new JavaRandom(seed);
  let angle = rng.nextDouble() * Math.PI * 2;
  const count = 3;
  const ringDistanceChunks = 88 + rng.nextInt(80); // ring 1 approx 88-168 chunks
  let best: FoundStructure | null = null;
  let bestDist = Infinity;
  for (let i = 0; i < count; i++) {
    const r = ringDistanceChunks * 16;
    const sx = Math.round(Math.cos(angle) * r);
    const sz = Math.round(Math.sin(angle) * r);
    const dx = sx - playerX, dz = sz - playerZ;
    const d = dx * dx + dz * dz;
    if (d < bestDist) {
      bestDist = d;
      best = { x: sx, z: sz, chunkX: sx >> 4, chunkZ: sz >> 4, distance: Math.round(Math.sqrt(d)), bearing: bearing(dx, dz) };
    }
    angle += (Math.PI * 2) / count;
  }
  return best!;
}

function bearing(dx: number, dz: number): string {
  let a = (Math.atan2(-dz, dx) * 180) / Math.PI;
  if (a < 0) a += 360;
  const pts = ["E", "NE", "N", "NW", "W", "SW", "S", "SE"];
  return pts[Math.round(a / 45) % 8];
}

// ---- Waypoints (in-memory, process-wide) ----
// Public MCP: no per-user scoping. Fine for a shared demo/team seed.
export interface Waypoint {
  id: string;
  label: string;
  x: number;
  y: number;
  z: number;
  color: string;
  createdAt: string;
}

const WAYPOINTS: Waypoint[] = [];

export const waypointStore = {
  list(): Waypoint[] {
    return WAYPOINTS.slice();
  },
  add(w: Omit<Waypoint, "id" | "createdAt">): Waypoint {
    const wp: Waypoint = { ...w, id: crypto.randomUUID(), createdAt: new Date().toISOString() };
    WAYPOINTS.push(wp);
    return wp;
  },
  remove(id: string): boolean {
    const i = WAYPOINTS.findIndex((w) => w.id === id);
    if (i === -1) return false;
    WAYPOINTS.splice(i, 1);
    return true;
  },
  clear(): number {
    const n = WAYPOINTS.length;
    WAYPOINTS.length = 0;
    return n;
  },
};