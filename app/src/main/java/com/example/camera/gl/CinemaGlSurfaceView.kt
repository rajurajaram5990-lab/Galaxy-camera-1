package com.example.camera.gl

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Surface
import com.example.camera.model.CinemaLut
import com.example.camera.model.CinemaProfile
import com.example.camera.model.CinemaSettings
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CinemaGlSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs), GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    interface SurfaceListener {
        fun onSurfaceReady(surface: Surface, width: Int, height: Int)
        fun onHistogramData(bins: FloatArray)
    }

    var listener: SurfaceListener? = null

    private var program = 0
    private var textureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var cameraSurface: Surface? = null

    private val mvpMatrix = FloatArray(16)
    private val stMatrix = FloatArray(16)

    private var uMVPMatrixLoc = -1
    private var uSTMatrixLoc = -1
    private var aPositionLoc = -1
    private var aTextureCoordLoc = -1

    private var uProfileLoc = -1
    private var uGammaCurveLoc = -1
    private var uBlackLevelLiftLoc = -1
    private var uHighlightCompressionLoc = -1

    private var uLutIntensityLoc = -1
    private var uShadowTintLoc = -1
    private var uHighlightTintLoc = -1
    private var uContrastLoc = -1
    private var uSaturationLoc = -1
    private var uColorSeparationLoc = -1
    private var uSkinProtectionLoc = -1
    private var uHighlightRollOffLoc = -1

    private var uShowFocusPeakingLoc = -1
    private var uShowZebraLoc = -1
    private var uZebraThresholdLoc = -1
    private var uTimeLoc = -1
    private var uTexelSizeLoc = -1

    private val vertexBuffer: FloatBuffer
    private val textureBuffer: FloatBuffer

    @Volatile
    private var updateSurface = false

    @Volatile
    var currentSettings = CinemaSettings()

    private var startTime = SystemClock.uptimeMillis()
    private var frameCount = 0
    private val histogramBuffer = ByteBuffer.allocateDirect(64 * 64 * 4).order(ByteOrder.nativeOrder())
    private val histogramBins = FloatArray(32)

    init {
        setEGLContextClientVersion(2)
        setRenderer(this)
        renderMode = RENDERMODE_CONTINUOUSLY

        // Full screen quad coordinates
        val vertexCoords = floatArrayOf(
            -1.0f, -1.0f, 0.0f,
             1.0f, -1.0f, 0.0f,
            -1.0f,  1.0f, 0.0f,
             1.0f,  1.0f, 0.0f
        )
        val texCoords = floatArrayOf(
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
        )

        vertexBuffer = ByteBuffer.allocateDirect(vertexCoords.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(vertexCoords)
                position(0)
            }

        textureBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(texCoords)
                position(0)
            }

        Matrix.setIdentityM(mvpMatrix, 0)
        Matrix.setIdentityM(stMatrix, 0)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = CinemaShader.createProgram(CinemaShader.VERTEX_SHADER, CinemaShader.FRAGMENT_SHADER)
        if (program == 0) return

        aPositionLoc = GLES20.glGetAttribLocation(program, "aPosition")
        aTextureCoordLoc = GLES20.glGetAttribLocation(program, "aTextureCoord")
        uMVPMatrixLoc = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        uSTMatrixLoc = GLES20.glGetUniformLocation(program, "uSTMatrix")

        uProfileLoc = GLES20.glGetUniformLocation(program, "uProfile")
        uGammaCurveLoc = GLES20.glGetUniformLocation(program, "uGammaCurve")
        uBlackLevelLiftLoc = GLES20.glGetUniformLocation(program, "uBlackLevelLift")
        uHighlightCompressionLoc = GLES20.glGetUniformLocation(program, "uHighlightCompression")

        uLutIntensityLoc = GLES20.glGetUniformLocation(program, "uLutIntensity")
        uShadowTintLoc = GLES20.glGetUniformLocation(program, "uShadowTint")
        uHighlightTintLoc = GLES20.glGetUniformLocation(program, "uHighlightTint")
        uContrastLoc = GLES20.glGetUniformLocation(program, "uContrast")
        uSaturationLoc = GLES20.glGetUniformLocation(program, "uSaturation")
        uColorSeparationLoc = GLES20.glGetUniformLocation(program, "uColorSeparation")
        uSkinProtectionLoc = GLES20.glGetUniformLocation(program, "uSkinProtection")
        uHighlightRollOffLoc = GLES20.glGetUniformLocation(program, "uHighlightRollOff")

        uShowFocusPeakingLoc = GLES20.glGetUniformLocation(program, "uShowFocusPeaking")
        uShowZebraLoc = GLES20.glGetUniformLocation(program, "uShowZebra")
        uZebraThresholdLoc = GLES20.glGetUniformLocation(program, "uZebraThreshold")
        uTimeLoc = GLES20.glGetUniformLocation(program, "uTime")
        uTexelSizeLoc = GLES20.glGetUniformLocation(program, "uTexelSize")

        // Create OES texture for Camera2
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val st = SurfaceTexture(textureId)
        st.setOnFrameAvailableListener(this)
        surfaceTexture = st
        cameraSurface = Surface(st)

        GLES20.glClearColor(0.05f, 0.05f, 0.06f, 1.0f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        cameraSurface?.let { surf ->
            listener?.onSurfaceReady(surf, width, height)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        synchronized(this) {
            if (updateSurface) {
                surfaceTexture?.updateTexImage()
                surfaceTexture?.getTransformMatrix(stMatrix)
                updateSurface = false
            }
        }

        if (program == 0) return

        GLES20.glUseProgram(program)

        // Texture matrix and MVP matrix
        GLES20.glUniformMatrix4fv(uMVPMatrixLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uSTMatrixLoc, 1, false, stMatrix, 0)

        // Upload Profile uniforms
        val settings = currentSettings
        val profile = settings.profile
        val profileCode = when (profile) {
            CinemaProfile.STANDARD -> 0
            CinemaProfile.CINEMATIC -> 1
            CinemaProfile.FLAT -> 2
            CinemaProfile.LOG_STYLE -> 3
        }
        GLES20.glUniform1i(uProfileLoc, profileCode)
        GLES20.glUniform1f(uGammaCurveLoc, profile.gammaCurve)
        GLES20.glUniform1f(uBlackLevelLiftLoc, profile.blackLevelLift)
        GLES20.glUniform1f(uHighlightCompressionLoc, profile.highlightCompression)

        // Upload LUT uniforms
        val lut = settings.lut
        val intensity = (settings.lutIntensity / 100f).coerceIn(0f, 1f)
        GLES20.glUniform1f(uLutIntensityLoc, intensity)
        GLES20.glUniform3f(uShadowTintLoc, lut.shadowTintR, lut.shadowTintG, lut.shadowTintB)
        GLES20.glUniform3f(uHighlightTintLoc, lut.highlightTintR, lut.highlightTintG, lut.highlightTintB)
        GLES20.glUniform1f(uContrastLoc, lut.contrast)
        GLES20.glUniform1f(uSaturationLoc, lut.saturation)
        GLES20.glUniform1f(uColorSeparationLoc, lut.colorSeparation)
        GLES20.glUniform1f(uSkinProtectionLoc, lut.skinProtection)
        GLES20.glUniform1f(uHighlightRollOffLoc, lut.highlightRollOff)

        // Assist tools uniforms
        GLES20.glUniform1i(uShowFocusPeakingLoc, if (settings.showFocusPeaking) 1 else 0)
        GLES20.glUniform1i(uShowZebraLoc, if (settings.showZebra) 1 else 0)
        GLES20.glUniform1f(uZebraThresholdLoc, settings.zebraThreshold)

        val elapsed = (SystemClock.uptimeMillis() - startTime) / 1000f
        GLES20.glUniform1f(uTimeLoc, elapsed)
        GLES20.glUniform2f(uTexelSizeLoc, 1.0f / 1080f, 1.0f / 1920f)

        // Bind OES texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        // Draw quad
        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glVertexAttribPointer(aPositionLoc, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer)

        GLES20.glEnableVertexAttribArray(aTextureCoordLoc)
        GLES20.glVertexAttribPointer(aTextureCoordLoc, 2, GLES20.GL_FLOAT, false, 8, textureBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aTextureCoordLoc)

        // Histogram analysis: sample small area periodically (every 6 frames ~ 5fps)
        frameCount++
        if (settings.showHistogram && frameCount % 6 == 0) {
            computeHistogram()
        }
    }

    private fun computeHistogram() {
        try {
            histogramBuffer.position(0)
            GLES20.glReadPixels(0, 0, 64, 64, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, histogramBuffer)
            histogramBuffer.position(0)

            for (i in histogramBins.indices) histogramBins[i] = 0f

            val totalPixels = 64 * 64
            var maxCount = 0f

            for (i in 0 until totalPixels) {
                val r = (histogramBuffer.get().toInt() and 0xFF) / 255f
                val g = (histogramBuffer.get().toInt() and 0xFF) / 255f
                val b = (histogramBuffer.get().toInt() and 0xFF) / 255f
                histogramBuffer.get() // Skip alpha

                val luma = 0.299f * r + 0.587f * g + 0.114f * b
                val bin = ((luma * 31f).toInt()).coerceIn(0, 31)
                histogramBins[bin] += 1f
                if (histogramBins[bin] > maxCount) maxCount = histogramBins[bin]
            }

            if (maxCount > 0f) {
                for (i in histogramBins.indices) {
                    histogramBins[i] /= maxCount
                }
            }

            listener?.onHistogramData(histogramBins.clone())
        } catch (e: Exception) {
            // Readpixels safety
        }
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        synchronized(this) {
            updateSurface = true
        }
        requestRender()
    }

    fun release() {
        cameraSurface?.release()
        cameraSurface = null
        surfaceTexture?.release()
        surfaceTexture = null
    }
}
