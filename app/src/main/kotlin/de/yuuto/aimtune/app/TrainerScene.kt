package de.yuuto.aimtune.app

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
import com.badlogic.gdx.utils.Disposable
import de.yuuto.aimtune.core.math.ViewAngles
import de.yuuto.aimtune.core.scenario.Target

/**
 * Plain 3D room with one sphere per target. The camera sits at the origin and uses the vertical
 * FOV derived from the game profile's FOV, independent of the window aspect ratio.
 */
class TrainerScene(
    verticalFovDegrees: Float,
    /** The picture is letterboxed to this width / height, whatever the window's shape. */
    private val aspectRatio: Double = 16.0 / 9.0,
    private val modelRadius: Double = DEFAULT_MODEL_RADIUS,
) : Disposable {
    private var windowWidth = 16
    private var windowHeight = 9

    private val camera = PerspectiveCamera(verticalFovDegrees, 16f, 9f).apply {
        near = NEAR_PLANE
        far = FAR_PLANE
    }
    private val batch = ModelBatch()
    private val environment = Environment().apply {
        set(ColorAttribute(ColorAttribute.AmbientLight, 0.55f, 0.55f, 0.6f, 1f))
        add(DirectionalLight().set(0.6f, 0.6f, 0.6f, -0.4f, -1f, -0.3f))
    }

    private val roomModel: Model = ModelBuilder().createBox(
        ROOM_WIDTH, ROOM_HEIGHT, ROOM_DEPTH,
        // Front faces are culled so the inside of the box is what we see.
        Material(ColorAttribute.createDiffuse(Theme.roomWall), IntAttribute.createCullFace(GL20.GL_FRONT)),
        (Usage.Position or Usage.Normal).toLong(),
    )
    private val room = ModelInstance(roomModel).apply { transform.setToTranslation(0f, 0f, ROOM_CENTER_Z) }

    private val targetModel: Model = ModelBuilder().createSphere(
        modelRadius.toFloat() * 2, modelRadius.toFloat() * 2, modelRadius.toFloat() * 2, SPHERE_DIVISIONS, SPHERE_DIVISIONS,
        Material(ColorAttribute.createDiffuse(Theme.accentSoft), ColorAttribute.createEmissive(Theme.accentSoft)),
        (Usage.Position or Usage.Normal).toLong(),
    )
    private val targetInstance = ModelInstance(targetModel)

    fun resize(width: Int, height: Int) {
        windowWidth = width
        windowHeight = height
        val box = letterbox(width, height, aspectRatio)
        camera.viewportWidth = box.width.toFloat()
        camera.viewportHeight = box.height.toFloat()
    }

    fun render(view: ViewAngles, targets: List<Target>) {
        val direction = view.direction()
        camera.position.set(0f, 0f, 0f)
        camera.direction.set(direction.x.toFloat(), direction.y.toFloat(), direction.z.toFloat())
        camera.up.set(0f, 1f, 0f)
        camera.update()

        val box = letterbox(windowWidth, windowHeight, aspectRatio)
        HdpiUtils.glViewport(box.x, box.y, box.width, box.height)
        batch.begin(camera)
        batch.render(room, environment)
        for (target in targets) {
            val scale = (target.radius / modelRadius).toFloat()
            targetInstance.transform.setToTranslation(target.center.x.toFloat(), target.center.y.toFloat(), target.center.z.toFloat()).scale(scale, scale, scale)
            batch.render(targetInstance, environment)
        }
        batch.end()
        HdpiUtils.glViewport(0, 0, windowWidth, windowHeight)
    }

    override fun dispose() {
        batch.dispose()
        roomModel.dispose()
        targetModel.dispose()
    }

    private companion object {
        /** The sphere model is built once at this radius and scaled per target. */
        const val DEFAULT_MODEL_RADIUS = 1.0
        const val NEAR_PLANE = 0.05f
        const val FAR_PLANE = 200f
        const val ROOM_WIDTH = 80f
        const val ROOM_HEIGHT = 50f
        const val ROOM_DEPTH = 70f
        const val ROOM_CENTER_Z = -10f
        const val SPHERE_DIVISIONS = 24
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
