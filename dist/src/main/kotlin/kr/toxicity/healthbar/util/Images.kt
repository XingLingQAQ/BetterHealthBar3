package kr.toxicity.healthbar.util
import kr.toxicity.healthbar.api.image.NamedProcessedImage
import kr.toxicity.healthbar.api.image.ProcessedImage
import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.image.BufferedImage
import java.awt.image.RenderedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.imageio.ImageIO
import kotlin.math.roundToInt

fun RenderedImage.save(file: File) {
    ImageIO.write(this, "png", file)
}
fun RenderedImage.save(outputStream: OutputStream) {
    ImageIO.write(this, "png", outputStream)
}

fun RenderedImage.toByteArray(): ByteArray {
    return ByteArrayOutputStream(2048).use {
        ImageIO.write(this, "png", it)
        it
    }.toByteArray()
}

fun File.toImage(): BufferedImage = ImageIO.read(this)
fun InputStream.toImage(): BufferedImage = ImageIO.read(this)

fun ProcessedImage.toNamed(name: String) = NamedProcessedImage(name, this)

fun BufferedImage.removeEmptyWidth(x: Int = 0, y: Int = 0): ProcessedImage? {

    var widthA = 0
    var widthB = width

    for (i1 in 0..<width) {
        for (i2 in 0..<height) {
            if ((getRGB(i1, i2) and -0x1000000) ushr 24 > 0) {
                if (widthA < i1) widthA = i1
                if (widthB > i1) widthB = i1
            }
        }
    }
    val finalWidth = widthA - widthB + 1

    if (finalWidth <= 0) return null

    return ProcessedImage(
        getSubimage(widthB, 0, finalWidth, height),
        widthB + x,
        y
    )
}

private val FRC = FontRenderContext(null, true, true)

fun BufferedImage.processFont(char: Char, font: Font): BufferedImage? {
    createGraphics().run {
        fill(font.createGlyphVector(FRC, char.toString()).getOutline(0F, font.size.toFloat()))
        dispose()
    }

    return removeEmptyWidth()?.image
}

fun BufferedImage.fontSubImage(sampling: Int = 96): BufferedImage? {
    var widthA = 0
    var widthB = width

    createGraphics().run {
        for (i1 in 0..<width) {
            for (i2 in 0..<height) {
                val rgb = getRGB(i1, i2)
                val alpha = (rgb and -0x1000000) ushr 24
                if (alpha > sampling) {
                    setRGB(i1, i2, (255 shl 24) + 0xFFFFFF)
                } else {
                    setRGB(i1, i2, 0)
                    continue
                }
                if (widthA < i1) widthA = i1
                if (widthB > i1) widthB = i1
            }
        }
        dispose()
    }

    val finalWidth = widthA - widthB + 1

    if (finalWidth <= 0) return null

    return getSubimage(widthB, 0, finalWidth, height)
}

fun BufferedImage.removeEmptySide(): ProcessedImage? {
    var heightA = 0
    var heightB = height

    var widthA = 0
    var widthB = width

    for (i1 in 0..<width) {
        for (i2 in 0..<height) {
            if ((getRGB(i1, i2) and -0x1000000) ushr 24 > 0) {
                if (widthA < i1) widthA = i1
                if (widthB > i1) widthB = i1
                if (heightA < i2) heightA = i2
                if (heightB > i2) heightB = i2
            }
        }
    }
    val finalWidth = widthA - widthB + 1
    val finalHeight = heightA - heightB + 1

    if (finalWidth <= 0 || finalHeight <= 0) return null

    return ProcessedImage(
        getSubimage(widthB, heightB, finalWidth, finalHeight),
        widthB,
        heightB
    )
}

fun BufferedImage.withOpacity(opacity: Int): BufferedImage {
    return BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also {
        for (i1 in 0..<width) {
            for (i2 in 0..<height) {
                val rgba = getRGB(i1, i2)
                it.setRGB(i1, i2, (opacity shl 24) or (rgba and 0xFFFFFF))
            }
        }
    }
}