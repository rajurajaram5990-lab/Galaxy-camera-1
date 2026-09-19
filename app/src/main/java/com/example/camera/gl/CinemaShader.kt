package com.example.camera.gl

import android.opengl.GLES20
import android.opengl.GLES11Ext
import android.util.Log

object CinemaShader {

    const val VERTEX_SHADER = """
        uniform mat4 uMVPMatrix;
        uniform mat4 uSTMatrix;
        attribute vec4 aPosition;
        attribute vec4 aTextureCoord;
        varying vec2 vTextureCoord;
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            vTextureCoord = (uSTMatrix * aTextureCoord).xy;
        }
    """

    const val FRAGMENT_SHADER = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;

        varying vec2 vTextureCoord;
        uniform samplerExternalOES sTexture;

        // Profile uniforms
        uniform int uProfile; // 0=Standard, 1=Cinematic, 2=Flat, 3=LogStyle
        uniform float uGammaCurve;
        uniform float uBlackLevelLift;
        uniform float uHighlightCompression;

        // LUT uniforms
        uniform float uLutIntensity; // 0.0 to 1.0
        uniform vec3 uShadowTint;
        uniform vec3 uHighlightTint;
        uniform float uContrast;
        uniform float uSaturation;
        uniform float uColorSeparation;
        uniform float uSkinProtection;
        uniform float uHighlightRollOff;

        // Assist Tools uniforms
        uniform int uShowFocusPeaking; // 0 or 1
        uniform int uShowZebra;         // 0 or 1
        uniform float uZebraThreshold; // 0.90
        uniform float uTime;
        uniform vec2 uTexelSize;

        // Convert RGB to YCbCr for skin tone detection
        vec3 rgbToYCbCr(vec3 rgb) {
            float y = 0.299 * rgb.r + 0.587 * rgb.g + 0.114 * rgb.b;
            float cb = -0.1687 * rgb.r - 0.3313 * rgb.g + 0.5 * rgb.b;
            float cr = 0.5 * rgb.r - 0.4187 * rgb.g - 0.0813 * rgb.b;
            return vec3(y, cb, cr);
        }

        // Skin tone protection factor
        float getSkinToneWeight(vec3 rgb) {
            vec3 ycbcr = rgbToYCbCr(rgb);
            // Human skin tone cluster center in YCbCr: Cb ~ -0.11, Cr ~ 0.15
            vec2 skinCenter = vec2(-0.11, 0.15);
            float dist = distance(vec2(ycbcr.y, ycbcr.z), skinCenter);
            float weight = 1.0 - smoothstep(0.04, 0.16, dist);
            return clamp(weight * uSkinProtection, 0.0, 1.0);
        }

        // Highlight roll-off shoulder curve
        vec3 applyHighlightRollOff(vec3 col, float rollOff) {
            if (rollOff <= 0.01) return col;
            // Film shoulder compression: compresses highlights smoothly toward 1.0
            vec3 knee = vec3(0.75);
            vec3 overKnee = max(vec3(0.0), col - knee);
            vec3 compressed = knee + overKnee / (vec3(1.0) + overKnee * (1.0 + rollOff * 2.0));
            return mix(col, compressed, step(knee, col));
        }

        // Cinema Profile Tone Mapping
        vec3 applyProfile(vec3 rgb) {
            if (uProfile == 0) {
                // Standard Rec.709
                return rgb;
            } else if (uProfile == 1) {
                // Cinematic: filmic S-curve + slight lifted toe
                vec3 lifted = rgb + vec3(uBlackLevelLift) * (1.0 - rgb);
                vec3 scurve = pow(lifted, vec3(uGammaCurve));
                return scurve;
            } else if (uProfile == 2) {
                // Flat: wide latitude, lowered contrast, lifted blacks
                vec3 flatCol = rgb * 0.85 + vec3(uBlackLevelLift);
                return pow(flatCol, vec3(uGammaCurve));
            } else if (uProfile == 3) {
                // LOG-Style: Cineon/Logarithmic curve simulation
                // y = a * ln(b * x + 1.0) + c
                vec3 logCol = 0.28 * log(rgb * 12.0 + 1.0) + vec3(uBlackLevelLift);
                return clamp(logCol, 0.0, 1.0);
            }
            return rgb;
        }

        void main() {
            vec4 rawColor = texture2D(sTexture, vTextureCoord);
            vec3 baseRgb = rawColor.rgb;

            // 1. Apply Profile Tone Mapping
            vec3 profileRgb = applyProfile(baseRgb);

            // 2. Grade with Hollywood-Inspired LUT parameters
            float luma = dot(profileRgb, vec3(0.2126, 0.7152, 0.0722));

            // Shadow and Highlight color tinting
            float shadowWeight = clamp((0.5 - luma) * 2.0, 0.0, 1.0);
            float highlightWeight = clamp((luma - 0.5) * 2.0, 0.0, 1.0);
            vec3 graded = profileRgb + uShadowTint * shadowWeight + uHighlightTint * highlightWeight;

            // Contrast around 18% cinema gray pivot (0.18)
            graded = (graded - 0.18) * uContrast + 0.18;

            // Saturation & color separation
            float gradedLuma = dot(graded, vec3(0.2126, 0.7152, 0.0722));
            vec3 satRgb = mix(vec3(gradedLuma), graded, uSaturation);

            // Color separation (boosts contrast between warm highlights and cool shadows)
            if (uColorSeparation > 0.01) {
                float warmCool = satRgb.r - satRgb.b;
                satRgb.r += warmCool * uColorSeparation * 0.2;
                satRgb.b -= warmCool * uColorSeparation * 0.2;
            }

            // Highlight roll-off
            satRgb = applyHighlightRollOff(satRgb, uHighlightRollOff);

            // Skin Tone Protection: blend original natural skin chromaticity back
            float skinWeight = getSkinToneWeight(profileRgb);
            vec3 skinSafe = mix(satRgb, profileRgb, skinWeight * 0.65);

            // Overall LUT Intensity blend
            vec3 finalRgb = mix(profileRgb, skinSafe, uLutIntensity);
            finalRgb = clamp(finalRgb, 0.0, 1.0);

            // 3. Assist Tool Overlays (Visual Only)
            // Zebra pattern for overexposed highlights (> 90% IRE)
            if (uShowZebra == 1) {
                float checkLuma = dot(finalRgb, vec3(0.299, 0.587, 0.114));
                if (checkLuma >= uZebraThreshold) {
                    // Diagonal stripes pattern based on pixel screen coordinates
                    float stripe = sin((gl_FragCoord.x + gl_FragCoord.y + uTime * 20.0) * 0.4);
                    if (stripe > 0.0) {
                        finalRgb = vec3(0.0, 0.0, 0.0); // Zebra black stripe
                    }
                }
            }

            // Focus Peaking (Sobel / Laplacian Edge Detection)
            if (uShowFocusPeaking == 1) {
                vec2 dx = vec2(uTexelSize.x, 0.0);
                vec2 dy = vec2(0.0, uTexelSize.y);
                float c = dot(texture2D(sTexture, vTextureCoord).rgb, vec3(0.299, 0.587, 0.114));
                float l = dot(texture2D(sTexture, vTextureCoord - dx).rgb, vec3(0.299, 0.587, 0.114));
                float r = dot(texture2D(sTexture, vTextureCoord + dx).rgb, vec3(0.299, 0.587, 0.114));
                float t = dot(texture2D(sTexture, vTextureCoord - dy).rgb, vec3(0.299, 0.587, 0.114));
                float b = dot(texture2D(sTexture, vTextureCoord + dy).rgb, vec3(0.299, 0.587, 0.114));

                float edge = abs(l + r + t + b - 4.0 * c);
                if (edge > 0.12) {
                    // Highlight in neon green peaking indicator
                    finalRgb = mix(finalRgb, vec3(0.0, 1.0, 0.3), 0.85);
                }
            }

            gl_FragColor = vec4(finalRgb, 1.0);
        }
    """

    fun compileShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            Log.e("CinemaShader", "Shader compile failed: $log")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    fun createProgram(vertexCode: String, fragmentCode: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexCode)
        if (vertexShader == 0) return 0

        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentCode)
        if (fragmentShader == 0) {
            GLES20.glDeleteShader(vertexShader)
            return 0
        }

        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            Log.e("CinemaShader", "Program link failed: $log")
            GLES20.glDeleteProgram(program)
            return 0
        }
        return program
    }

    fun checkGlError(op: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            Log.e("CinemaShader", "$op: glError 0x${Integer.toHexString(error)}")
        }
    }
}
