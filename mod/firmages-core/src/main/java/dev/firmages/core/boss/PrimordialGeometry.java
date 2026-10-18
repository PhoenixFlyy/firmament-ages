package dev.firmages.core.boss;

import net.minecraft.world.phys.Vec3;

/**
 * Where The Primordial's nine nodes are, the same on the server (ranged hits on a node) and the client (beam origin,
 * flares). It mirrors the model of dev/gen_primordial.py through GeckoLib's transform chain (javap, 4.9.3): bedrock x,
 * x and y rotations are negated at bake time, bones rotate Z * Y * X, the renderer turns the model by 180 - body yaw.
 * The halo tilt and spin are not keyframed: PrimordialModel sets them from {@link #tilt} and {@link #spin}, so both
 * sides agree. {@code python dev/gen_primordial.py --check} compares this closed form with the full chain.
 */
public final class PrimordialGeometry {
    private PrimordialGeometry() {}

    /**
     * The entity hovers this far above its floor (the altar's top, y 65); the hitbox (6 x 7) starts there, the keel tip
     * touches its bottom. Round G4: 7 (was 2.5), so it floats at y 72 over the grand arena's domes (to y 70) and the
     * altar pedestals (y 70); the slam still brings the keel down to the floor.
     */
    public static final double HOVER = 7.0;
    /** The core's centre above the entity origin (body pivot, 56 px). */
    public static final double BODY_Y = 56 / 16.0;
    /** Node 0's centre in Java model pixels, relative to the body pivot (bedrock (44, 55.5, 17), x mirrored). */
    private static final double NODE_X = -44, NODE_Y = 55.5 - 56, NODE_Z = 17;
    /** Ten shards, one per Age 0..9 (round G4: the Singularity segment joined the ring). */
    public static final int SHARDS = 10;
    private static final double SHARD_STEP = Math.toRadians(360.0 / SHARDS);
    /** The halo turns 27 degrees per tick in its own plane: 1.5 turns a second (round G4; was 0.6 degrees, a turn in 30 s). */
    public static final double SPIN_DEGREES_PER_TICK = 27;
    private static final double SPIN_PER_TICK = Math.toRadians(SPIN_DEGREES_PER_TICK);
    /** Horizontal distance of the beam's floor point from the entity (between the d 4 and d 10 rings). */
    public static final double BEAM_RADIUS = 9.0;
    /** A node counts as hit when a projectile's path passes this close to its centre. */
    public static final double NODE_HIT_RADIUS = 1.25;
    /**
     * Node colours: nodes 0..8 in the ceremony beam colour of Age 0..8 (BEAM_FALLBACK in dev/gen_textures.py, the same
     * the texture is painted with), 9 the white-violet of Age 9 (the tenth shard, the spire tip, the lit eye).
     */
    public static final int[] NODE_COLORS = ShotAge.COLORS;

    /** A node cube's centre inside its shard bone, in Java model space (blocks): the same for every shard. */
    public static org.joml.Vector3f nodeCentreModel() {
        return new org.joml.Vector3f((float) (NODE_X / 16), (float) ((NODE_Y + 56) / 16), (float) (NODE_Z / 16));
    }

    /** Halo tilt (Java radians) per phase: closed (18), open (35), shattered (10). */
    public static float tilt(int phase) {
        return (float) Math.toRadians(switch (phase) {
            case 2 -> 35;
            case 3 -> 10;
            default -> 18;
        });
    }

    /** Halo spin (Java radians) at a game time in ticks (with the partial tick on the client). */
    public static float spin(double gameTime) {
        return (float) ((gameTime * SPIN_PER_TICK) % (Math.PI * 2));
    }

    /** The node that stands highest on the tilted, spinning ring at game time {@code gameTime} (the segment "on top"). */
    public static int topNode(int phase, double gameTime) {
        float tilt = tilt(phase), spin = spin(gameTime);
        int best = 0;
        double by = -Double.MAX_VALUE;
        for (int i = 0; i < SHARDS; i++) {
            double y = nodeModel(i, tilt, spin).y;
            if (y > by) {
                by = y;
                best = i;
            }
        }
        return best;
    }

    /** Node {@code i}'s centre in Java model space (blocks, before the entity yaw). */
    public static Vec3 nodeModel(int i, float tilt, float spin) {
        double x = NODE_X, y = NODE_Y, z = NODE_Z;
        double a = -SHARD_STEP * i;
        double x1 = x * Math.cos(a) + z * Math.sin(a), z1 = -x * Math.sin(a) + z * Math.cos(a);
        double x2 = x1 * Math.cos(spin) + z1 * Math.sin(spin), z2 = -x1 * Math.sin(spin) + z1 * Math.cos(spin);
        double y3 = y * Math.cos(tilt) - z2 * Math.sin(tilt), z3 = y * Math.sin(tilt) + z2 * Math.cos(tilt);
        return new Vec3(x2 / 16, y3 / 16 + BODY_Y, z3 / 16);
    }

    /** Node {@code i}'s centre relative to the entity origin in world axes (blocks); {@code yaw} is the body yaw. */
    public static Vec3 nodeOffset(int i, float yawDeg, float tilt, float spin) {
        Vec3 m = nodeModel(i, tilt, spin);
        double t = Math.toRadians(180.0 - yawDeg);
        return new Vec3(m.x * Math.cos(t) + m.z * Math.sin(t), m.y, -m.x * Math.sin(t) + m.z * Math.cos(t));
    }

    /** The beam's floor point relative to the entity origin, for a beam yaw in degrees (0 = +x, 90 = +z). */
    public static Vec3 beamTarget(float beamYawDeg) {
        double a = Math.toRadians(beamYawDeg);
        return new Vec3(Math.cos(a) * BEAM_RADIUS, -HOVER, Math.sin(a) * BEAM_RADIUS);
    }

    /**
     * True when a projectile at {@code pos} moving by {@code motion} per tick passes the node centre: an arrow calls
     * hurt from where it started its tick, so the test takes the line of its flight, from a few blocks behind the
     * start to two ticks ahead, and its closest approach to the node.
     */
    public static boolean pathHits(Vec3 node, Vec3 pos, Vec3 motion) {
        if (pos.distanceTo(node) <= NODE_HIT_RADIUS) return true;
        double len = motion.length();
        if (len < 1.0E-4) return false;
        Vec3 dir = motion.scale(1 / len);
        double along = node.subtract(pos).dot(dir);
        if (along < -1.0 || along > 2 * len + 1.0) return false;
        return pos.add(dir.scale(along)).distanceTo(node) <= NODE_HIT_RADIUS;
    }
}
