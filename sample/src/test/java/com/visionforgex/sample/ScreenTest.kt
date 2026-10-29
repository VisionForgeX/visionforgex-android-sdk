package com.visionforgex.sample

import android.graphics.Bitmap
import android.view.View
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The demo's screen draws (a picture of it when VFX_SHOTS names a folder). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class ScreenTest {
    @Test fun theScreenDraws() {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = a.window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(1233, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2673, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1233, 2673)
        val b = Bitmap.createBitmap(1233, 2673, Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(b))
        System.getProperty("vfx.shots")?.let { d -> File(d).mkdirs(); File(d, "demo-screen.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        assertTrue(root.width > 0)
    }
}
