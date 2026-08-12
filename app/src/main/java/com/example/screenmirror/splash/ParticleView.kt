package com.example.screenmirror.splash

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Custom View for rendering particle effects on splash screen
 * Features:
 * - Floating ambient particles
 * - Touch interaction burst effects
 * - Trail effects on touch move
 * - Celebration burst animation
 * - Optimized rendering with hardware acceleration
 */
class ParticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var isAnimating = false
    private var intensity = 1f
    
    // Particle configuration
    private val maxParticles = 80
    private val particleColors = intArrayOf(
        0xFF00B4FF.toInt(),  // Accent blue
        0xFF0066FF.toInt(),  // Gradient start
        0xFF00D4FF.toInt(),  // Gradient end
        0xFF00E676.toInt(),  // Success green
        0xFFFFFFFF.toInt()   // White
    )

    init {
        // Enable hardware acceleration
        setLayerType(LAYER_TYPE_HARDWARE, null)
        
        // Configure paints
        paint.style = Paint.Style.FILL
        blurPaint.style = Paint.Style.FILL
        blurPaint.maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
    }

    fun startParticles() {
        isAnimating = true
        initializeParticles()
        invalidate()
    }

    fun stopParticles() {
        isAnimating = false
    }

    fun cleanup() {
        isAnimating = false
        synchronized(particles) {
            particles.clear()
        }
    }

    fun setIntensity(value: Float) {
        intensity = value.coerceIn(0f, 1f)
    }

    fun createTouchBurst(x: Float, y: Float) {
        // Create burst of particles at touch point
        val newParticles = mutableListOf<Particle>()
        repeat(15) {
            val angle = Random.nextFloat() * 2 * Math.PI.toFloat()
            val speed = Random.nextFloat() * 3f + 2f
            val vx = cos(angle) * speed
            val vy = sin(angle) * speed
            
            newParticles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = vx,
                    vy = vy,
                    size = Random.nextFloat() * 4f + 3f,
                    color = particleColors.random(),
                    life = 1f,
                    fadeRate = Random.nextFloat() * 0.02f + 0.015f,
                    type = ParticleType.BURST
                )
            )
        }
        synchronized(particles) {
            particles.addAll(newParticles)
        }
        maintainParticleLimit()
    }

    fun createTouchTrail(x: Float, y: Float) {
        // Create subtle trail particles
        if (Random.nextFloat() < 0.3f) {
            synchronized(particles) {
                particles.add(
                    Particle(
                        x = x + Random.nextFloat() * 20 - 10,
                        y = y + Random.nextFloat() * 20 - 10,
                        vx = Random.nextFloat() * 0.5f - 0.25f,
                        vy = Random.nextFloat() * 0.5f - 0.25f,
                        size = Random.nextFloat() * 3f + 2f,
                        color = particleColors.random(),
                        life = 1f,
                        fadeRate = 0.03f,
                        type = ParticleType.TRAIL
                    )
                )
            }
        }
    }

    fun createCelebrationBurst() {
        // Create celebration effect from center
        val centerX = width / 2f
        val centerY = height / 2f
        
        val newParticles = mutableListOf<Particle>()
        repeat(30) {
            val angle = Random.nextFloat() * 2 * Math.PI.toFloat()
            val speed = Random.nextFloat() * 5f + 3f
            val vx = cos(angle) * speed
            val vy = sin(angle) * speed
            
            newParticles.add(
                Particle(
                    x = centerX,
                    y = centerY,
                    vx = vx,
                    vy = vy,
                    size = Random.nextFloat() * 6f + 4f,
                    color = particleColors.random(),
                    life = 1f,
                    fadeRate = Random.nextFloat() * 0.015f + 0.01f,
                    type = ParticleType.CELEBRATION
                )
            )
        }
        synchronized(particles) {
            particles.addAll(newParticles)
        }
    }

    private fun initializeParticles() {
        synchronized(particles) {
            particles.clear()
            repeat(maxParticles) {
                particles.add(createAmbientParticle())
            }
        }
    }

    private fun createAmbientParticle(): Particle {
        return Particle(
            x = Random.nextFloat() * width,
            y = Random.nextFloat() * height,
            vx = Random.nextFloat() * 0.4f - 0.2f,
            vy = Random.nextFloat() * 0.6f - 0.4f,
            size = Random.nextFloat() * 4f + 1f,
            color = particleColors.random(),
            life = Random.nextFloat() * 0.5f + 0.5f,
            fadeRate = Random.nextFloat() * 0.003f + 0.002f,
            type = ParticleType.AMBIENT
        )
    }

    private fun maintainParticleLimit() {
        synchronized(particles) {
            while (particles.size > maxParticles * 2) {
                particles.removeAt(0)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (!isAnimating && particles.isEmpty()) return
        
        // Create a copy to prevent ConcurrentModificationException
        // when particles are added from touch events during iteration
        val particlesCopy = synchronized(particles) {
            particles.toList()
        }
        
        val deadParticles = mutableListOf<Particle>()
        val newParticles = mutableListOf<Particle>()
        
        // Update and draw particles
        for (particle in particlesCopy) {
            // Update particle
            particle.update(width, height, intensity)
            
            // Mark dead particles for removal
            if (particle.isDead()) {
                deadParticles.add(particle)
                // Respawn ambient particles
                if (isAnimating && particle.type == ParticleType.AMBIENT) {
                    newParticles.add(createAmbientParticle())
                }
                continue
            }
            
            // Draw particle with glow effect
            val alpha = (particle.life * 255).toInt()
            
            // Draw blur/glow
            blurPaint.color = particle.color
            blurPaint.alpha = (alpha * 0.5f).toInt()
            canvas.drawCircle(particle.x, particle.y, particle.size * 1.5f, blurPaint)
            
            // Draw solid particle
            paint.color = particle.color
            paint.alpha = alpha
            canvas.drawCircle(particle.x, particle.y, particle.size, paint)
        }
        
        // Remove dead particles and add new ones in a single synchronized block
        synchronized(particles) {
            particles.removeAll(deadParticles)
            particles.addAll(newParticles)
        }
        
        // Continue animation
        if (isAnimating || particles.isNotEmpty()) {
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && particles.isEmpty() && isAnimating) {
            initializeParticles()
        }
    }

    private data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var size: Float,
        val color: Int,
        var life: Float,
        val fadeRate: Float,
        val type: ParticleType
    ) {
        fun update(width: Int, height: Int, intensity: Float) {
            // Update position
            x += vx * intensity
            y += vy * intensity
            
            // Apply gravity for burst particles
            if (type == ParticleType.BURST || type == ParticleType.CELEBRATION) {
                vy += 0.1f
            }
            
            // Fade out
            life -= fadeRate
            
            // Wrap around for ambient particles
            if (type == ParticleType.AMBIENT) {
                if (x < 0) x = width.toFloat()
                if (x > width) x = 0f
                if (y < 0) y = height.toFloat()
                if (y > height) y = 0f
            }
        }
        
        fun isDead(): Boolean = life <= 0f
    }

    private enum class ParticleType {
        AMBIENT,
        BURST,
        TRAIL,
        CELEBRATION
    }
}
