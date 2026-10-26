package io.github.aruru10313.drivecare.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import io.github.aruru10313.drivecare.domain.DoorPosition
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

enum class BodyStyle(val label: String) { SEDAN("세단"), SUV("SUV") }

/** What the 3D model should show, taken from vehicle state. */
data class CarVisualState(
    val unlockedDoors: Set<DoorPosition> = emptySet(),
    val headlightsOn: Boolean = false,
    val windowsOpen: Boolean = false,
)

private data class V3(val x: Float, val y: Float, val z: Float) {
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    fun cross(o: V3) = V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    fun normalized(): V3 {
        val l = sqrt(dot(this)).coerceAtLeast(1e-6f)
        return V3(x / l, y / l, z / l)
    }
}

/**
 * Side profile of the body in the x (length, front = +x) / y (height) plane, front-bottom first,
 * going over the roof to the rear. The body is this profile extruded across the width.
 * [glass] marks profile segments drawn as glass (windshield, rear window).
 */
private class Profile(
    val points: List<Pair<Float, Float>>,
    val glass: Set<Int>,
    val sideWindow: List<Pair<Float, Float>>,
    val beltLine: Float,
    val pillarX: Float,
    val wheelX: Pair<Float, Float>,
    val halfWidth: Float,
)

private val sedan = Profile(
    points = listOf(
        2.25f to 0.32f, 2.33f to 0.56f, 2.30f to 0.74f, 2.10f to 0.86f, 1.20f to 0.98f, 0.95f to 1.08f,
        0.30f to 1.40f, -0.20f to 1.44f, -0.85f to 1.38f, -1.55f to 1.05f, -1.75f to 0.98f,
        -2.25f to 0.92f, -2.32f to 0.70f, -2.30f to 0.36f,
    ),
    glass = setOf(5, 8),
    sideWindow = listOf(0.92f to 1.06f, 0.28f to 1.36f, -0.82f to 1.34f, -1.45f to 1.04f),
    beltLine = 1.0f,
    pillarX = -0.18f,
    wheelX = 1.42f to -1.45f,
    halfWidth = 0.92f,
)

private val suv = Profile(
    points = listOf(
        2.22f to 0.38f, 2.32f to 0.62f, 2.30f to 0.82f, 2.18f to 1.00f, 1.60f to 1.10f, 1.35f to 1.16f,
        0.70f to 1.64f, 0.40f to 1.70f, -1.70f to 1.70f, -2.05f to 1.62f, -2.25f to 1.38f,
        -2.30f to 1.00f, -2.28f to 0.40f,
    ),
    glass = setOf(5, 9),
    sideWindow = listOf(1.25f to 1.16f, 0.68f to 1.60f, -1.75f to 1.62f, -2.05f to 1.16f),
    beltLine = 1.14f,
    pillarX = -0.38f,
    wheelX = 1.40f to -1.42f,
    halfWidth = 0.96f,
)

private const val WHEEL_RADIUS = 0.39f

/**
 * Rotatable 3D car drawn with Compose Canvas: a perspective projection with flat shading, no
 * external engine or assets. Drag sideways to turn it. Only shown on the parked screens.
 */
@Composable
fun Car3D(
    style: BodyStyle,
    paint: Color,
    visual: CarVisualState,
    modifier: Modifier = Modifier,
    initialYawDegrees: Float = -38f,
) {
    var yaw by remember { mutableFloatStateOf(initialYawDegrees) }
    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectHorizontalDragGestures { change, dragAmount ->
                change.consume()
                yaw += dragAmount * 0.35f
            }
        },
    ) {
        drawCar(if (style == BodyStyle.SUV) suv else sedan, paint, visual, yaw)
    }
}

private class Camera(yawDegrees: Float, private val size: Size) {
    private val yaw = yawDegrees * PI.toFloat() / 180f
    private val pitch = 16f * PI.toFloat() / 180f
    private val cy = cos(yaw)
    private val sy = sin(yaw)
    private val cp = cos(pitch)
    private val sp = sin(pitch)
    private val distance = 9.5f
    private val focal = minOf(size.width / 5.2f, size.height / 2.6f) * distance / 1.0f

    /** World → camera space (camera looks toward -z). Model is centered at height 0.8. */
    fun view(p: V3): V3 {
        val x1 = p.x * cy + p.z * sy
        val z1 = -p.x * sy + p.z * cy
        val y0 = p.y - 0.8f
        val y1 = y0 * cp - z1 * sp
        val z2 = y0 * sp + z1 * cp
        return V3(x1, y1, z2)
    }

    fun project(p: V3): Offset {
        val v = view(p)
        val depth = distance - v.z
        return Offset(size.width / 2f + focal * v.x / depth, size.height * 0.56f - focal * v.y / depth)
    }

    /** True when a face with this world-space normal faces the camera. */
    fun faces(center: V3, normal: V3): Boolean {
        val c = view(center)
        val n = view(V3(center.x + normal.x, center.y + normal.y, center.z + normal.z)) - c
        val toCamera = V3(-c.x, -c.y, distance - c.z)
        return n.dot(toCamera) > 0f
    }

    fun shade(normal: V3): Float {
        val light = V3(-0.35f, 0.85f, 0.45f).normalized()
        return 0.42f + 0.58f * max(0f, normal.normalized().dot(light))
    }
}

private fun DrawScope.drawCar(profile: Profile, paint: Color, visual: CarVisualState, yaw: Float) {
    val cam = Camera(yaw, size)
    val w = profile.halfWidth
    fun poly(points: List<V3>): Path = Path().apply {
        points.forEachIndexed { i, p ->
            val o = cam.project(p)
            if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
        }
        close()
    }
    fun shaded(base: Color, normal: V3) = lerp(Color.Black, base, cam.shade(normal))

    // Soft ground shadow: concentric ovals on the ground plane, darker toward the center.
    for (step in 0 until 6) {
        val grow = 1.35f - step * 0.07f
        val oval = (0 until 28).map { i ->
            val a = i / 28f * 2f * PI.toFloat()
            V3(2.5f * grow * cos(a), 0f, (w + 0.2f) * grow * sin(a))
        }
        drawPath(poly(oval), Color.Black.copy(alpha = 0.09f))
    }

    val pts = profile.points
    val leftVisible = cam.faces(V3(0f, 0.8f, -w), V3(0f, 0f, -1f))
    val nearZ = if (leftVisible) -w else w
    val farZ = -nearZ

    // Far-side wheels peek out under the body, so draw them first.
    drawWheels(cam, profile, farZ, ::poly)

    // Body panels between consecutive profile points, back-face culled.
    val ring = pts + pts.first()
    data class Face(val path: Path, val color: Color, val depth: Float)
    val faces = mutableListOf<Face>()
    for (i in 0 until ring.size - 1) {
        val (x0, y0) = ring[i]
        val (x1, y1) = ring[i + 1]
        val quad = listOf(V3(x0, y0, w), V3(x1, y1, w), V3(x1, y1, -w), V3(x0, y0, -w))
        val normal = (quad[1] - quad[0]).cross(quad[3] - quad[0]).normalized()
        val center = V3((x0 + x1) / 2f, (y0 + y1) / 2f, 0f)
        val outward = if (normal.dot(V3(center.x, center.y - 0.8f, 0f)) < 0f) V3(-normal.x, -normal.y, -normal.z) else normal
        if (!cam.faces(center, outward)) continue
        val glass = i in profile.glass
        val base = if (glass) Color(0xFF1B2733) else paint
        faces += Face(poly(quad), shaded(base, outward), cam.view(center).z)
    }
    faces.sortedBy { it.depth }.forEach { drawPath(it.path, it.color) }

    // Lamps on the front and rear faces.
    drawLamps(cam, profile, visual.headlightsOn, ::poly)

    // Near side: body silhouette, side glass, door seams, unlocked-door highlights, wheels.
    val sideNormal = V3(0f, 0f, if (nearZ < 0) -1f else 1f)
    drawPath(poly(pts.map { (x, y) -> V3(x, y, nearZ) }), shaded(paint, sideNormal))
    val rocker = listOf(
        V3(profile.wheelX.first - 0.48f, 0.30f, nearZ), V3(profile.wheelX.first - 0.48f, 0.47f, nearZ),
        V3(profile.wheelX.second + 0.48f, 0.47f, nearZ), V3(profile.wheelX.second + 0.48f, 0.30f, nearZ),
    ).map { V3(it.x, it.y, it.z * 1.001f) }
    drawPath(poly(rocker), Color(0xFF15191E))
    val glassColor = if (visual.windowsOpen) Color(0xFF0A0F14) else Color(0xFF223140)
    drawPath(poly(profile.sideWindow.map { (x, y) -> V3(x, y, nearZ * 1.002f) }), glassColor)
    drawLine(
        color = paint.copy(alpha = 1f).let { lerp(it, Color.Black, 0.35f) },
        start = cam.project(V3(profile.pillarX, profile.beltLine + 0.42f, nearZ * 1.003f)),
        end = cam.project(V3(profile.pillarX, 0.42f, nearZ * 1.003f)),
        strokeWidth = 3f,
    )

    val front = if (nearZ < 0) DoorPosition.FRONT_LEFT else DoorPosition.FRONT_RIGHT
    val rear = if (nearZ < 0) DoorPosition.REAR_LEFT else DoorPosition.REAR_RIGHT
    val doorTop = profile.beltLine - 0.04f
    val frontDoor = listOf(
        V3(profile.wheelX.first - 0.42f, 0.42f, nearZ), V3(profile.wheelX.first - 0.42f, doorTop, nearZ),
        V3(profile.pillarX, doorTop, nearZ), V3(profile.pillarX, 0.42f, nearZ),
    ).map { V3(it.x, it.y, it.z * 1.004f) }
    val rearDoor = listOf(
        V3(profile.pillarX, 0.42f, nearZ), V3(profile.pillarX, doorTop, nearZ),
        V3(profile.wheelX.second + 0.45f, doorTop, nearZ), V3(profile.wheelX.second + 0.45f, 0.42f, nearZ),
    ).map { V3(it.x, it.y, it.z * 1.004f) }
    for ((door, outline) in listOf(front to frontDoor, rear to rearDoor)) {
        if (door in visual.unlockedDoors) {
            drawPath(poly(outline), Color(0xFFFF6B6B).copy(alpha = 0.55f))
            drawPath(poly(outline), Color(0xFFFF6B6B), style = Stroke(width = 4f))
        } else {
            drawPath(poly(outline), Color.Black.copy(alpha = 0.25f), style = Stroke(width = 2f))
        }
    }
    drawWheels(cam, profile, nearZ, ::poly)
}

private fun DrawScope.drawWheels(cam: Camera, profile: Profile, z: Float, poly: (List<V3>) -> Path) {
    val outer = z + if (z < 0) -0.03f else 0.03f
    for (wx in listOf(profile.wheelX.first, profile.wheelX.second)) {
        fun circle(radius: Float, atZ: Float) = (0 until 20).map { i ->
            val a = i / 20f * 2f * PI.toFloat()
            V3(wx + radius * cos(a), WHEEL_RADIUS + radius * sin(a), atZ)
        }
        drawPath(poly(circle(WHEEL_RADIUS + 0.08f, z * 1.001f)), Color(0xFF07090C))
        drawPath(poly(circle(WHEEL_RADIUS, outer)), Color(0xFF15181C))
        drawPath(poly(circle(WHEEL_RADIUS * 0.62f, outer * 1.01f)), Color(0xFF9AA4AE))
        drawPath(poly(circle(WHEEL_RADIUS * 0.22f, outer * 1.02f)), Color(0xFF3A4048))
    }
}

private fun DrawScope.drawLamps(cam: Camera, profile: Profile, headlightsOn: Boolean, poly: (List<V3>) -> Path) {
    val (fx, fy) = profile.points[1]
    val (rx, ry) = profile.points[profile.points.size - 2]
    val frontVisible = cam.faces(V3(fx, fy, 0f), V3(1f, 0f, 0f))
    val rearVisible = cam.faces(V3(rx, ry, 0f), V3(-1f, 0f, 0f))
    val w = profile.halfWidth
    for (side in listOf(-1f, 1f)) {
        if (frontVisible) {
            val lamp = listOf(
                V3(fx + 0.01f, fy + 0.10f, side * (w - 0.08f)), V3(fx + 0.01f, fy + 0.10f, side * (w - 0.42f)),
                V3(fx + 0.01f, fy - 0.02f, side * (w - 0.42f)), V3(fx + 0.01f, fy - 0.02f, side * (w - 0.08f)),
            )
            if (headlightsOn) {
                val c = cam.project(V3(fx, fy + 0.04f, side * (w - 0.25f)))
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xAAFFF4D0), Color.Transparent), center = c, radius = size.minDimension * 0.16f),
                    radius = size.minDimension * 0.16f,
                    center = c,
                )
            }
            drawPath(poly(lamp), if (headlightsOn) Color(0xFFFFF6DA) else Color(0xFFB8C2CC))
        }
        if (rearVisible) {
            val lamp = listOf(
                V3(rx - 0.01f, ry - 0.12f, side * (w - 0.06f)), V3(rx - 0.01f, ry - 0.12f, side * (w - 0.40f)),
                V3(rx - 0.01f, ry - 0.24f, side * (w - 0.40f)), V3(rx - 0.01f, ry - 0.24f, side * (w - 0.06f)),
            )
            drawPath(poly(lamp), if (headlightsOn) Color(0xFFFF3B3B) else Color(0xFF7A1F25))
        }
    }
}
