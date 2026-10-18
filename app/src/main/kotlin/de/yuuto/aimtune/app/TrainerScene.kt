package de.yuuto.aimtune.app

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.glutils.HdpiUtils
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import de.yuuto.aimtune.core.math.ViewAngles
import de.yuuto.aimtune.core.scenario.ArenaFeatureKind
import de.yuuto.aimtune.core.scenario.ArenaLayout
import de.yuuto.aimtune.core.scenario.FlashState
import de.yuuto.aimtune.core.scenario.Target
import de.yuuto.aimtune.core.scenario.TrainerGeometry
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Plain 3D room with one sphere per target. The camera sits at the origin and uses the vertical
 * FOV derived from the game profile's FOV, independent of the window aspect ratio.
 */
class TrainerScene(
    verticalFovDegrees: Float,
    /** The picture is letterboxed to this width / height, whatever the window's shape. */
    private val aspectRatio: Double = 16.0 / 9.0,
    private val modelRadius: Double = DEFAULT_MODEL_RADIUS,
    /** With the geometry of a game the scene has a floor at eye height, a wall, corners and doorways; without it only the plain room. */
    geometry: TrainerGeometry? = null,
) : Disposable {
    private var windowWidth = 16
    private var windowHeight = 9

    private val camera = PerspectiveCamera(verticalFovDegrees, 16f, 9f).apply {
        near = NEAR_PLANE
        far = FAR_PLANE
    }
    private val batch = ModelBatch()
    private val environment = Environment().apply {
        set(ColorAttribute(ColorAttribute.AmbientLight, 0.42f, 0.42f, 0.48f, 1f))
        add(DirectionalLight().set(0.85f, 0.85f, 0.85f, -0.55f, -1f, -0.35f))
        // Distant things fade into the dark: the main depth cue besides perspective (the camera's far plane is the fog distance).
        set(ColorAttribute(ColorAttribute.Fog, Theme.roomFog.r, Theme.roomFog.g, Theme.roomFog.b, 1f))
    }
    private val fogOnly = Environment().apply {
        set(ColorAttribute(ColorAttribute.Fog, Theme.roomFog.r, Theme.roomFog.g, Theme.roomFog.b, 1f))
    }

    private val floorY: Float? = geometry?.let { -it.eyeHeightMeters.toFloat() }

    private val roomModel: Model = ModelBuilder().createBox(
        ROOM_WIDTH, ROOM_HEIGHT, ROOM_DEPTH,
        // Front faces are culled so the inside of the box is what we see.
        Material(ColorAttribute.createDiffuse(Theme.roomWall), IntAttribute.createCullFace(GL20.GL_FRONT)),
        (Usage.Position or Usage.Normal).toLong(),
    )
    // The plain room is centered on the camera; with a floor, the room's bottom sits below it so the floor covers it.
    private val room = ModelInstance(roomModel).apply {
        transform.setToTranslation(0f, floorY?.let { it - ROOM_BELOW_FLOOR + ROOM_HEIGHT / 2 } ?: 0f, if (floorY == null) ROOM_CENTER_Z else 0f)
    }

    private val unitBoxes = listOf(Theme.roomFloor, Theme.roomStructure, Theme.roomBody, Theme.roomPillar, Theme.roomPanel, Theme.roomCrate).map { color ->
        ModelBuilder().createBox(1f, 1f, 1f, Material(ColorAttribute.createDiffuse(color)), (Usage.Position or Usage.Normal).toLong())
    }
    private val floorBox = unitBoxes[0]
    private val structureBox = unitBoxes[1]
    private val bodyBox = unitBoxes[2]
    private val pillarBox = unitBoxes[3]
    private val panelBox = unitBoxes[4]
    private val crateBox = unitBoxes[5]

    /** Thin lines on the floor every 2 m: the strongest depth cue on an otherwise plain floor. */
    private val gridModel: Model? = floorY?.let { floor ->
        val builder = ModelBuilder()
        builder.begin()
        val lines = builder.part("grid", GL20.GL_LINES, Usage.Position.toLong(), Material(ColorAttribute.createDiffuse(Theme.roomGrid)))
        val y = floor + GRID_LIFT
        var at = -GRID_EXTENT
        while (at <= GRID_EXTENT) {
            lines.line(at, y, -GRID_EXTENT, at, y, GRID_EXTENT)
            lines.line(-GRID_EXTENT, y, at, GRID_EXTENT, y, at)
            at += GRID_SPACING
        }
        builder.end()
    }
    private val gridInstance: ModelInstance? = gridModel?.let { ModelInstance(it) }

    /** Floor, wall ring, corner pillars and door frames; empty without a geometry. */
    private val arena: List<ModelInstance> = if (geometry == null || floorY == null) emptyList() else buildArena(geometry, floorY)
    private val bodyInstance = ModelInstance(bodyBox)

    private val targetModel: Model = ModelBuilder().createSphere(
        modelRadius.toFloat() * 2, modelRadius.toFloat() * 2, modelRadius.toFloat() * 2, SPHERE_DIVISIONS, SPHERE_DIVISIONS,
        Material(ColorAttribute.createDiffuse(Theme.accentSoft), ColorAttribute.createEmissive(Theme.accentSoft)),
        (Usage.Position or Usage.Normal).toLong(),
    )
    private val targetInstance = ModelInstance(targetModel)

    /** The flash orb: a bright sphere that is not shaded, so it cannot be missed. */
    private val flashModel: Model = ModelBuilder().createSphere(
        modelRadius.toFloat() * 2, modelRadius.toFloat() * 2, modelRadius.toFloat() * 2, SPHERE_DIVISIONS, SPHERE_DIVISIONS,
        Material(ColorAttribute.createDiffuse(Color.WHITE), ColorAttribute.createEmissive(Color.WHITE)),
        (Usage.Position or Usage.Normal).toLong(),
    )
    private val flashInstance = ModelInstance(flashModel)

    fun resize(width: Int, height: Int) {
        windowWidth = width
        windowHeight = height
        val box = letterbox(width, height, aspectRatio)
        camera.viewportWidth = box.width.toFloat()
        camera.viewportHeight = box.height.toFloat()
    }

    /**
     * Draws the room, the [targets] and the [flash] orb while it has not popped. A popped flash that blinded the
     * player ([FlashState.popped]) replaces the picture with white.
     */
    fun render(view: ViewAngles, targets: List<Target>, flash: FlashState? = null) {
        val direction = view.direction()
        camera.position.set(0f, 0f, 0f)
        camera.direction.set(direction.x.toFloat(), direction.y.toFloat(), direction.z.toFloat())
        camera.up.set(0f, 1f, 0f)
        camera.update()

        val box = letterbox(windowWidth, windowHeight, aspectRatio)
        HdpiUtils.glViewport(box.x, box.y, box.width, box.height)
        if (flash?.popped == true) {
            whiteOut(box)
            HdpiUtils.glViewport(0, 0, windowWidth, windowHeight)
            return
        }
        batch.begin(camera)
        batch.render(room, environment)
        arena.forEach { batch.render(it, environment) }
        gridInstance?.let { batch.render(it, fogOnly) }
        batch.end()
        // Targets are drawn over the map: whatever the map hides, the player must still be able to see them.
        Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT)
        batch.begin(camera)
        if (flash != null) {
            val orb = ViewAngles.directionOf(flash.yawDegrees, flash.pitchDegrees) * flash.distanceMeters
            val scale = (flash.distanceMeters * sin(Math.toRadians(FLASH_ORB_DEGREES / 2.0)) / modelRadius).toFloat()
            flashInstance.transform.setToTranslation(orb.x.toFloat(), orb.y.toFloat(), orb.z.toFloat()).scale(scale, scale, scale)
            batch.render(flashInstance, environment)
        }
        for (target in targets) {
            val scale = (target.radius / modelRadius).toFloat()
            targetInstance.transform.setToTranslation(target.center.x.toFloat(), target.center.y.toFloat(), target.center.z.toFloat()).scale(scale, scale, scale)
            batch.render(targetInstance, environment)
            drawBody(target)
        }
        batch.end()
        HdpiUtils.glViewport(0, 0, windowWidth, windowHeight)
    }

    /** A column from the floor to the bottom of a head-sized target, so the head stands on something. */
    private fun drawBody(target: Target) {
        val floor = floorY ?: return
        if (target.radius >= MAX_HEAD_RADIUS) return
        val top = (target.center.y - target.radius).toFloat()
        val height = top - floor
        if (height < MIN_BODY_HEIGHT) return
        val yaw = Math.toDegrees(atan2(target.center.x, -target.center.z))
        bodyInstance.transform.setToTranslation(target.center.x.toFloat(), floor + height / 2, target.center.z.toFloat())
            .rotateRad(Vector3.Y, -Math.toRadians(yaw).toFloat()).scale(BODY_WIDTH, height, BODY_DEPTH)
        batch.render(bodyInstance, environment)
    }

    /** A box [sx] wide (sideways), [sy] high and [sz] deep (along the line of sight) centered at ([x], [y], [z]), turned to face yaw [yawDegrees]. */
    private fun part(model: Model, x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, yawDegrees: Double): ModelInstance =
        ModelInstance(model).apply { transform.setToTranslation(x, y, z).rotateRad(Vector3.Y, -Math.toRadians(yawDegrees).toFloat()).scale(sx, sy, sz) }

    private fun buildArena(geometry: TrainerGeometry, floor: Float): List<ModelInstance> {
        val parts = mutableListOf<ModelInstance>()
        parts += part(floorBox, 0f, floor - FLOOR_THICKNESS / 2, 0f, FLOOR_SIZE, FLOOR_THICKNESS, FLOOR_SIZE, 0.0)
        val layout = geometry.arena
        val wall = layout.wallDistanceMeters
        val segmentWidth = (2.0 * wall * tan(Math.PI / WALL_SEGMENTS) * 1.02).toFloat()
        for (i in 0 until WALL_SEGMENTS) {
            val yaw = i * 360.0 / WALL_SEGMENTS
            val c = ViewAngles.directionOf(yaw, 0.0) * wall
            parts += part(if (i % 2 == 0) structureBox else panelBox, c.x.toFloat(), floor + WALL_HEIGHT / 2, c.z.toFloat(), segmentWidth, WALL_HEIGHT, WALL_THICKNESS, yaw)
        }
        // Crates just in front of the wall, farther away than any target: parallax and a sense of size.
        CRATES.forEach { (yaw, distance, size) ->
            val c = ViewAngles.directionOf(yaw, 0.0) * distance
            parts += part(crateBox, c.x.toFloat(), floor + size / 2, c.z.toFloat(), size, size, size, yaw + 20.0)
        }
        layout.features.forEachIndexed { index, feature ->
            val yaw = feature.yawDegrees
            val at = ViewAngles.directionOf(yaw, 0.0) * feature.distanceMeters
            // Sideways on the ground, to the right of the line of sight.
            val sideX = cos(Math.toRadians(yaw)).toFloat()
            val sideZ = sin(Math.toRadians(yaw)).toFloat()
            fun beside(offset: Float, depth: Float, height: Float, width: Float) =
                part(pillarBox, at.x.toFloat() + sideX * offset, floor + height / 2, at.z.toFloat() + sideZ * offset, width, height, depth, yaw)
            when (feature.kind) {
                ArenaFeatureKind.CORNER -> {
                    val side = if (index % 2 == 0) 1f else -1f
                    parts += beside(side * (CORNER_WIDTH / 2 + ENEMY_CLEARANCE), CORNER_WIDTH, CORNER_HEIGHT, CORNER_WIDTH)
                }
                ArenaFeatureKind.DOORWAY -> {
                    parts += beside(-DOOR_HALF_WIDTH, JAMB, DOOR_HEIGHT, JAMB)
                    parts += beside(DOOR_HALF_WIDTH, JAMB, DOOR_HEIGHT, JAMB)
                    parts += part(
                        pillarBox, at.x.toFloat(), floor + DOOR_HEIGHT + LINTEL / 2, at.z.toFloat(),
                        2 * DOOR_HALF_WIDTH + JAMB, LINTEL, JAMB, yaw,
                    )
                }
            }
        }
        return parts
    }

    /** How bright the white-out is, 0..1 of full white (the player's setting); full white hurt the eyes. */
    var whiteOutLevel: Float = 1f

    /** Fills the picture with white at [whiteOutLevel], the way a flash does. */
    private fun whiteOut(box: PixelBox) {
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST)
        HdpiUtils.glScissor(box.x, box.y, box.width, box.height)
        val level = whiteOutLevel.coerceIn(0f, 1f)
        Gdx.gl.glClearColor(level, level, level, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST)
    }

    override fun dispose() {
        flashModel.dispose()
        batch.dispose()
        roomModel.dispose()
        targetModel.dispose()
        unitBoxes.forEach { it.dispose() }
        gridModel?.dispose()
    }

    private companion object {
        /** The sphere model is built once at this radius and scaled per target. */
        const val DEFAULT_MODEL_RADIUS = 1.0
        const val NEAR_PLANE = 0.05f
        const val FAR_PLANE = 60f
        const val ROOM_WIDTH = 150f
        const val ROOM_HEIGHT = 50f
        const val ROOM_DEPTH = 150f
        const val ROOM_CENTER_Z = -10f
        const val ROOM_BELOW_FLOOR = 5f
        const val FLOOR_SIZE = 140f
        const val FLOOR_THICKNESS = 0.5f
        const val WALL_SEGMENTS = 36
        const val WALL_HEIGHT = 6f
        const val WALL_THICKNESS = 0.6f
        val CORNER_WIDTH = ArenaLayout.CORNER_WIDTH.toFloat()
        const val CORNER_HEIGHT = 4.2f
        val ENEMY_CLEARANCE = ArenaLayout.ENEMY_CLEARANCE.toFloat()
        val DOOR_HALF_WIDTH = ArenaLayout.DOOR_HALF_WIDTH.toFloat()
        const val DOOR_HEIGHT = 4.2f
        val JAMB = ArenaLayout.JAMB.toFloat()
        const val LINTEL = 0.4f
        const val BODY_WIDTH = 0.45f
        const val BODY_DEPTH = 0.3f
        const val MIN_BODY_HEIGHT = 0.2f
        const val MAX_HEAD_RADIUS = 0.3
        const val GRID_SPACING = 2f
        const val GRID_EXTENT = 60f
        const val GRID_LIFT = 0.01f

        /** Yaw, distance (beyond the farthest target) and edge length of the crates. */
        val CRATES = listOf(
            Triple(-150.0, 33.0, 2.4f), Triple(-118.0, 34.0, 3.2f), Triple(-96.0, 32.5, 1.8f), Triple(-63.0, 34.0, 2.8f),
            Triple(-24.0, 33.0, 2.0f), Triple(8.0, 34.0, 3.4f), Triple(41.0, 32.5, 2.2f), Triple(70.0, 34.0, 3.0f),
            Triple(104.0, 33.0, 2.6f), Triple(131.0, 34.0, 1.9f), Triple(165.0, 32.5, 3.2f),
        )
        const val SPHERE_DIVISIONS = 24

        /** Apparent size of the flash orb, about a grenade seen from a few meters. */
        const val FLASH_ORB_DEGREES = 4.0
    }
}

/** A rectangle in window pixels. */
data class PixelBox(val x: Int, val y: Int, val width: Int, val height: Int)

/** The largest centered box of shape [aspectRatio] (width / height) inside a window of [width] x [height]. */
fun letterbox(width: Int, height: Int, aspectRatio: Double): PixelBox {
    require(width > 0 && height > 0 && aspectRatio > 0.0) { "window and aspect ratio must be positive" }
    val boxWidth = minOf(width.toDouble(), height * aspectRatio).toInt().coerceAtLeast(1)
    val boxHeight = minOf(height.toDouble(), width / aspectRatio).toInt().coerceAtLeast(1)
    return PixelBox((width - boxWidth) / 2, (height - boxHeight) / 2, boxWidth, boxHeight)
}
