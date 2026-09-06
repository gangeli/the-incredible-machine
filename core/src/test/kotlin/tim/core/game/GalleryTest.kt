package tim.core.game

import org.junit.jupiter.api.Test
import tim.desktop.Snap

class GalleryTest {
    @Test
    fun `render parts gallery`() {
        val f = Snap.save(Snap.partsGallery(), "parts-gallery")
        println("gallery: ${f.absolutePath}")
    }
}
