/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.event.EventTarget
import net.ccbluex.liquidbounce.event.Render2DEvent
import net.ccbluex.liquidbounce.event.Render3DEvent
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.ClientUtils.LOGGER
import net.ccbluex.liquidbounce.utils.EntityUtils.isLookingOnEntities
import net.ccbluex.liquidbounce.utils.RotationUtils
import net.ccbluex.liquidbounce.utils.render.ColorUtils.rainbow
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawBlockBox
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawEntityBox
import net.ccbluex.liquidbounce.utils.render.shader.shaders.GlowShader
import net.ccbluex.liquidbounce.value.IntegerValue
import net.ccbluex.liquidbounce.value.boolean
import net.ccbluex.liquidbounce.value.choices
import net.ccbluex.liquidbounce.value.float
import net.ccbluex.liquidbounce.value.int
import net.minecraft.entity.item.EntityFallingBlock
import net.minecraft.util.BlockPos
import net.minecraft.util.Vec3
import java.awt.Color
import kotlin.math.pow

object ProphuntESP : Module("ProphuntESP", Category.RENDER, gameDetecting = false) {
    val blocks = mutableMapOf<BlockPos, Long>()

    private val mode by choices("Mode", arrayOf("Box", "OtherBox", "Glow"), "OtherBox")
    private val glowRenderScale by float("Glow-Renderscale", 1f, 0.5f..2f) { mode == "Glow" }
    private val glowRadius by int("Glow-Radius", 4, 1..5) { mode == "Glow" }
    private val glowFade by int("Glow-Fade", 10, 0..30) { mode == "Glow" }
    private val glowTargetAlpha by float("Glow-Target-Alpha", 0f, 0f..1f) { mode == "Glow" }

    private val colorRainbow by boolean("Rainbow", false)
    private val colorRed by int("R", 0, 0..255) { !colorRainbow }
    private val colorGreen by int("G", 90, 0..255) { !colorRainbow }
    private val colorBlue by int("B", 255, 0..255) { !colorRainbow }

    private val maxRenderDistance by object : IntegerValue("MaxRenderDistance", 50, 1..200) {
        override fun onUpdate(value: Int) {
            maxRenderDistanceSq = value.toDouble().pow(2.0)
        }
    }

    private var maxRenderDistanceSq = 0.0
        set(value) {
            field = if (value <= 0.0) maxRenderDistance.toDouble().pow(2.0) else value
        }

    private val onLook by boolean("OnLook", false)
    private val maxAngleDifference by float("MaxAngleDifference", 90f, 5.0f..90f) { onLook }

    private val thruBlocks by boolean("ThruBlocks", true)

    private val color
        get() = if (colorRainbow) rainbow() else Color(colorRed, colorGreen, colorBlue)

    override fun onDisable() {
        synchronized(blocks) { blocks.clear() }
    }

    @EventTarget
    fun onRender3D(event: Render3DEvent) {
        for (entity in mc.theWorld.loadedEntityList) {
            if (mode != "Box" && mode != "OtherBox") break
            if (entity !is EntityFallingBlock) continue
            if (onLook && !isLookingOnEntities(entity, maxAngleDifference.toDouble())) continue
            if (!thruBlocks && !RotationUtils.isVisible(Vec3(entity.posX, entity.posY, entity.posZ))) continue

            val distanceSquared = mc.thePlayer.getDistanceSqToEntity(entity)

            if (distanceSquared <= maxRenderDistanceSq) {
                drawEntityBox(entity, color, mode == "Box")
            }
        }

        synchronized(blocks) {
            val iterator = blocks.entries.iterator()

            while (iterator.hasNext()) {
                val (pos, time) = iterator.next()

                if (System.currentTimeMillis() - time > 2000L) {
                    iterator.remove()
                    continue
                }

                drawBlockBox(pos, color, mode == "Box")
            }
        }
    }

    @EventTarget
    fun onRender2D(event: Render2DEvent) {
        if (mc.theWorld == null || mode != "Glow")
            return


        GlowShader.startDraw(event.partialTicks, glowRenderScale)

        for (entities in mc.theWorld.loadedEntityList) {
            val distanceSquared = mc.thePlayer.getDistanceSqToEntity(entities)

            if (distanceSquared <= maxRenderDistanceSq) {
                if (entities !is EntityFallingBlock) continue
                if (onLook && !isLookingOnEntities(entities, maxAngleDifference.toDouble())) continue
                if (!thruBlocks && !RotationUtils.isVisible(Vec3(entities.posX, entities.posY, entities.posZ))) continue

                try {
                    mc.theWorld.loadedEntityList.forEach { entity ->
                        if (entity is EntityFallingBlock) {
                            mc.renderManager.renderEntityStatic(entity, mc.timer.renderPartialTicks, true)
                        }
                    }
                } catch (ex: Exception) {
                    LOGGER.error("An error occurred while rendering all entities for shader esp", ex)
                }
            }
        }

        GlowShader.stopDraw(color, glowRadius, glowFade, glowTargetAlpha)
    }
}
