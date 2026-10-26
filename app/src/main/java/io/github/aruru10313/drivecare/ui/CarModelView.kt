package io.github.aruru10313.drivecare.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelLoader

/*
 * 3D car models: "Generic passenger car pack" by Comrade1280
 * (https://sketchfab.com/3d-models/generic-passenger-car-pack-20f9af9b8a404d5cb022ac6fe87f21f5),
 * CC-BY-4.0. Split per car, scaled to real size and re-tinted per paint option; the pack is made
 * to resemble no real vehicle.
 */
private val carTarget = Position(0f, 0.75f, 0f)

/** Asset for a body style and paint index, e.g. models/suv_white.glb. */
internal fun carAsset(style: BodyStyle, paintIndex: Int): String {
    val body = if (style == BodyStyle.SUV) "suv" else "sedan"
    val paint = listOf("white", "gray", "blue")[paintIndex.coerceIn(0, 2)]
    return "models/${body}_$paint.glb"
}

/** Real glTF car rendered with Filament; drag to orbit. */
@Composable
internal fun CarModelView(assetPath: String, modifier: Modifier = Modifier) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    // Front three-quarter view from the driver's side, slightly above, like the system car view.
    val cameraNode = rememberCameraNode(engine) {
        position = Position(x = 4.5f, y = 2.1f, z = 5.3f)
        lookAt(carTarget)
    }
    val modelNode = remember(assetPath) {
        ModelNode(
            modelInstance = modelLoader.createModelInstance(assetFileLocation = assetPath),
            scaleToUnits = null,
        )
    }
    DisposableEffect(modelNode) { onDispose { modelNode.destroy() } }

    Scene(
        modifier = modifier,
        engine = engine,
        modelLoader = modelLoader,
        environmentLoader = environmentLoader,
        isOpaque = false,
        cameraNode = cameraNode,
        cameraManipulator = rememberCameraManipulator(
            orbitHomePosition = cameraNode.worldPosition,
            targetPosition = carTarget,
        ),
        environment = rememberEnvironment(environmentLoader, isOpaque = false),
        childNodes = listOf(modelNode),
    )
}

