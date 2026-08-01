package com.example.mcbridge;

public final class IcePhysicsEngine {

    enum Action { STRAIGHT, LEFT, RIGHT }

    static final double TURN_STEP = 1.0;
    static final double FORWARD_FORCE = 0.04;
    static final double ICE_DRAG = 0.98;
    static final double BLUE_ICE_DRAG = 0.989;

    static final class State {
        double x, z, vx, vz, yaw;
        State(double x, double z, double yaw, double vx, double vz) {
            this.x = x; this.z = z; this.yaw = yaw; this.vx = vx; this.vz = vz;
        }
        State copy() { return new State(x, z, yaw, vx, vz); }
        double speed() { return Math.hypot(vx, vz); }
    }

    static void tick(State s, Action a, boolean blueIce) {
        double drag = blueIce ? BLUE_ICE_DRAG : ICE_DRAG;
        s.vx *= drag;
        s.vz *= drag;
        double yawVel = 0;
        if (a == Action.LEFT) yawVel = -TURN_STEP;
        if (a == Action.RIGHT) yawVel = TURN_STEP;
        s.yaw += yawVel;
        double rad = Math.toRadians(s.yaw);
        s.vx += Math.sin(-rad) * FORWARD_FORCE;
        s.vz += Math.cos(rad) * FORWARD_FORCE;
        s.x += s.vx;
        s.z += s.vz;
    }

    static double normAngle(double angle) {
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        return angle;
    }
}
