package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Cinema Camera", appName)
  }

  @Test
  fun `verify camera mode values`() {
    assertEquals(3, com.example.camera.model.CameraMode.values().size)
  }

  @Test
  fun `verify hollywood luts available`() {
    val luts = com.example.camera.model.CinemaLut.values()
    org.junit.Assert.assertTrue(luts.any { it.title == "Oppenheimer" })
    org.junit.Assert.assertTrue(luts.any { it.title == "Dune" })
    org.junit.Assert.assertTrue(luts.any { it.title == "Blade Runner 2049" })
  }
}
