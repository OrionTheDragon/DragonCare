package com.dragoncare.client.sound;

import com.dragoncare.DragonCare;
import com.mojang.logging.LogUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.Optional;

/**
 * Custom OpenAL player for the ash sensor's geiger track.
 *
 * <p>Bypasses Minecraft's {@code SoundInstance} pipeline because there is no
 * vanilla API to seek inside a playing sound. We own two OpenAL sources both
 * bound to a fully decoded PCM buffer of {@code ash_geiger.ogg} and drive
 * playback position from the sensor's ash level: at {@code level=0} the
 * window starts at the beginning of the track, at {@code level=1} at the end.
 * Playback speed simultaneously ramps from {@code 1.0x} at {@code level=0} to
 * {@code 1.5x} at {@code level=1}.
 *
 * <p>Smooth looping is achieved by alternating between two voices that
 * crossfade around the window boundary: as the active voice approaches the
 * end of its window the inactive voice is restarted at the window start at
 * zero gain and ramped up, while the previous voice ramps down. Same trick
 * is used when the level changes enough to move the window. Because both
 * voices share a single AL buffer, no extra memory is used.
 *
 * <p>Loudness is normalized at load time so the track sits at roughly the
 * same perceived loudness as Minecraft sound instances regardless of how
 * the source OGG was mastered. We target an RMS at about {@code -15 dBFS}
 * but cap the scale so the peak never exceeds {@code 95%} of full-scale.
 *
 * <p>Lifecycle: lazily initialized on the first call to {@link #update},
 * kept alive for the lifetime of the game. Category gain (master * players)
 * is applied manually so the sound respects the player's volume sliders
 * even though it doesn't go through Minecraft's mixer.
 *
 * <p>Thread-safety: all methods must be invoked from the client tick thread.
 */
public final class GeigerSoundPlayer {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Resource path of the underlying OGG file. */
    private static final Identifier SOUND_LOCATION =
            Identifier.of(DragonCare.MOD_ID, "sounds/ash_geiger.ogg");

    /**
     * Length of the looping window in seconds. Anything in this range plays
     * before we crossfade back to the window start (or to a new window if
     * the level changed). Larger windows mean fewer crossfades; smaller
     * windows respond to level changes faster.
     */
    private static final float WINDOW_SECONDS = 0.8F;

    /**
     * Distance from the window end at which we begin the crossfade to the
     * idle voice. With a {@value #CROSSFADE_PER_TICK} per tick fade, the
     * crossfade completes in about {@code TARGET_VOLUME / CROSSFADE_PER_TICK}
     * ticks ≈ {@code 0.5 s}, so this should give the new voice time to ramp
     * up before the old one runs out of window.
     */
    private static final float LOOP_LOOKAHEAD_SECONDS = 0.45F;

    /**
     * If the playhead drifts further than this from the current window
     * (e.g. because the level changed sharply), trigger an immediate
     * crossfade to the new window rather than waiting for the lookahead.
     */
    private static final float WINDOW_DRIFT_TOLERANCE = 0.08F;

    /** Playback speed at {@code level = 0}. */
    private static final float MIN_PITCH = 1.0F;
    /** Playback speed at {@code level = 1}. */
    private static final float MAX_PITCH = 1.5F;

    /** Steady-state volume of the primary voice (before category gain and normalization). */
    private static final float TARGET_VOLUME = 0.7F;
    /** Volume change per tick for both fade-in and fade-out — controls crossfade smoothness. */
    private static final float CROSSFADE_PER_TICK = 0.07F;
    /** Below this gain a voice is stopped to avoid holding inaudible AL voices. */
    private static final float MIN_AUDIBLE_VOLUME = 0.005F;

    /** Target RMS amplitude (≈ -15 dBFS) used by the loudness normalizer. */
    private static final float TARGET_RMS = 0.18F * Short.MAX_VALUE;
    /** Maximum peak amplitude (95% of full scale) allowed after normalization. */
    private static final float MAX_PEAK = 0.95F * Short.MAX_VALUE;
    /** Upper safety cap on the normalization factor so a near-silent file doesn't blow up. */
    private static final float MAX_NORMALIZATION = 4.0F;

    private static boolean initialized;
    private static boolean loadFailed;
    private static int buffer;
    private static float totalSeconds;
    /** RMS+peak based scale applied on top of currentVolume*categoryGain. */
    private static float loudnessNormalization = 1.0F;

    /** Two AL sources sharing the same buffer; one is primary, the other fades. */
    private static final int[] voices = new int[2];
    private static final float[] voiceVolumes = new float[2];
    private static final boolean[] voicePlaying = new boolean[2];
    /** Index in {@link #voices} of the primary (fading-in) voice. */
    private static int primaryIdx;

    private GeigerSoundPlayer() {}

    /**
     * Per-tick driver. Pass the sensor's current ash level and whether the
     * geiger track should be audible right now. The player handles fading
     * in and out, looping, position seeking, and pitch ramp internally.
     */
    public static void update(float level, boolean active) {
        if (loadFailed) return;
        if (!isOpenALReady()) return;
        if (!initialized) {
            initialized = true;
            try {
                load();
            } catch (Exception e) {
                LOGGER.error("Failed to load ash geiger sound", e);
                loadFailed = true;
                return;
            }
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        float categoryGain = mc.options.getSoundVolume(SoundCategory.PLAYERS)
                           * mc.options.getSoundVolume(SoundCategory.MASTER);
        float gainScale = categoryGain * loudnessNormalization;

        if (!active) {
            // Fade both voices out and stop them once silent.
            for (int i = 0; i < voices.length; i++) {
                voiceVolumes[i] = Math.max(0.0F, voiceVolumes[i] - CROSSFADE_PER_TICK);
                AL10.alSourcef(voices[i], AL10.AL_GAIN, voiceVolumes[i] * gainScale);
                if (voiceVolumes[i] <= MIN_AUDIBLE_VOLUME && voicePlaying[i]) {
                    AL10.alSourceStop(voices[i]);
                    voicePlaying[i] = false;
                }
            }
            return;
        }

        float clamped = Math.max(0.0F, Math.min(1.0F, level));
        float windowStart = windowStartFor(clamped);
        float windowEnd = windowStart + WINDOW_SECONDS;
        float pitch = MIN_PITCH + clamped * (MAX_PITCH - MIN_PITCH);

        int p = primaryIdx;
        int s = 1 - p;

        // If the primary isn't playing yet (first activation or post-reset),
        // start it at the level-appropriate window with zero gain.
        if (!voicePlaying[p]) {
            startVoice(p, windowStart, pitch);
        }

        // Keep both voices in sync pitch-wise: the still-fading old voice
        // would otherwise sound at a stale speed during the crossfade.
        AL10.alSourcef(voices[p], AL10.AL_PITCH, pitch);
        AL10.alSourcef(voices[s], AL10.AL_PITCH, pitch);

        float position = AL10.alGetSourcef(voices[p], AL11.AL_SEC_OFFSET);
        boolean nearLoopEnd = position >= windowEnd - LOOP_LOOKAHEAD_SECONDS;
        boolean outOfWindow = position < windowStart - WINDOW_DRIFT_TOLERANCE
                           || position > windowEnd + WINDOW_DRIFT_TOLERANCE;

        if (nearLoopEnd || outOfWindow) {
            // Hand off to the secondary voice at the (possibly new) window start.
            // The old primary keeps playing and will be ramped down by the fade
            // logic below; it shares the same buffer so it's just a separate AL
            // source — no extra memory.
            primaryIdx = s;
            p = primaryIdx;
            s = 1 - p;
            if (!voicePlaying[p]) {
                startVoice(p, windowStart, pitch);
            } else {
                // Already playing (left over from a previous fade); seek it to
                // the right spot and let it ramp up from where it left off.
                AL10.alSourcef(voices[p], AL11.AL_SEC_OFFSET, windowStart);
            }
        }

        // Crossfade: primary up toward TARGET, secondary down toward 0.
        voiceVolumes[p] = Math.min(TARGET_VOLUME, voiceVolumes[p] + CROSSFADE_PER_TICK);
        voiceVolumes[s] = Math.max(0.0F, voiceVolumes[s] - CROSSFADE_PER_TICK);

        AL10.alSourcef(voices[p], AL10.AL_GAIN, voiceVolumes[p] * gainScale);
        AL10.alSourcef(voices[s], AL10.AL_GAIN, voiceVolumes[s] * gainScale);

        if (voiceVolumes[s] <= MIN_AUDIBLE_VOLUME && voicePlaying[s]) {
            AL10.alSourceStop(voices[s]);
            voicePlaying[s] = false;
        }
    }

    private static void startVoice(int idx, float windowStart, float pitch) {
        AL10.alSourcef(voices[idx], AL11.AL_SEC_OFFSET, windowStart);
        AL10.alSourcef(voices[idx], AL10.AL_PITCH, pitch);
        AL10.alSourcef(voices[idx], AL10.AL_GAIN, 0.0F);
        AL10.alSourcePlay(voices[idx]);
        voicePlaying[idx] = true;
        voiceVolumes[idx] = 0.0F;
    }

    private static float windowStartFor(float level) {
        float maxStart = Math.max(0.0F, totalSeconds - WINDOW_SECONDS);
        return level * maxStart;
    }

    public static boolean isOpenALReady() {
        try {
            return AL.getCapabilities() != null;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public static void reset() {
        if (initialized) {
            for (int i = 0; i < voices.length; i++) {
                if (voices[i] != 0) {
                    AL10.alSourceStop(voices[i]);
                    AL10.alDeleteSources(voices[i]);
                }
            }
            if (buffer != 0) {
                AL10.alDeleteBuffers(buffer);
            }
        }
        initialized = false;
        loadFailed = false;
        buffer = 0;
        totalSeconds = 0.0F;
        loudnessNormalization = 1.0F;
        primaryIdx = 0;
        for (int i = 0; i < voices.length; i++) {
            voices[i] = 0;
            voiceVolumes[i] = 0.0F;
            voicePlaying[i] = false;
        }
    }

    /**
     * Loads the OGG file from the resource manager, decodes it to PCM via
     * STB Vorbis, computes a loudness normalization factor (RMS to
     * {@code -15 dBFS}, peak-limited), uploads PCM into an OpenAL buffer,
     * and configures two relative (headphone-style) looping sources sharing
     * that buffer. Called once on first activation.
     */
    private static void load() throws IOException {
        MinecraftClient mc = MinecraftClient.getInstance();
        Optional<Resource> opt = mc.getResourceManager().getResource(SOUND_LOCATION);
        if (opt.isEmpty()) {
            throw new IOException("Resource not found: " + SOUND_LOCATION);
        }

        ByteBuffer ogg;
        try (InputStream in = opt.get().getInputStream()) {
            byte[] bytes = in.readAllBytes();
            ogg = MemoryUtil.memAlloc(bytes.length);
            ogg.put(bytes).flip();
        }

        ShortBuffer pcm = null;
        long handle = 0L;
        int channels;
        int sampleRate;
        int totalSamplesPerChannel;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer error = stack.mallocInt(1);
            handle = STBVorbis.stb_vorbis_open_memory(ogg, error, null);
            if (handle == 0L) {
                throw new IOException("STB Vorbis open failed (error " + error.get(0) + ")");
            }

            STBVorbisInfo info = STBVorbisInfo.malloc(stack);
            STBVorbis.stb_vorbis_get_info(handle, info);
            channels = info.channels();
            sampleRate = info.sample_rate();
            totalSamplesPerChannel = STBVorbis.stb_vorbis_stream_length_in_samples(handle);

            pcm = MemoryUtil.memAllocShort(totalSamplesPerChannel * channels);
            STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, pcm);
        } finally {
            if (handle != 0L) STBVorbis.stb_vorbis_close(handle);
            MemoryUtil.memFree(ogg);
        }

        loudnessNormalization = computeLoudnessNormalization(pcm);

        try {
            int format = channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
            buffer = AL10.alGenBuffers();
            AL10.alBufferData(buffer, format, pcm, sampleRate);
        } finally {
            MemoryUtil.memFree(pcm);
        }

        for (int i = 0; i < voices.length; i++) {
            voices[i] = AL10.alGenSources();
            AL10.alSourcei(voices[i], AL10.AL_BUFFER, buffer);
            AL10.alSourcei(voices[i], AL10.AL_LOOPING, AL10.AL_TRUE);
            // Relative + (0,0,0): constant-volume headphone behaviour, no
            // spatial attenuation as the player moves.
            AL10.alSourcei(voices[i], AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(voices[i], AL10.AL_POSITION, 0.0F, 0.0F, 0.0F);
            AL10.alSourcef(voices[i], AL10.AL_GAIN, 0.0F);
            AL10.alSourcef(voices[i], AL10.AL_PITCH, MIN_PITCH);
        }

        totalSeconds = (float) totalSamplesPerChannel / (float) sampleRate;
    }

    /**
     * Scans the decoded PCM buffer to compute a gain factor that brings the
     * RMS toward {@link #TARGET_RMS} while keeping the peak under
     * {@link #MAX_PEAK}. The lower of the two implied factors wins.
     */
    private static float computeLoudnessNormalization(ShortBuffer pcm) {
        int limit = pcm.limit();
        if (limit == 0) return 1.0F;

        double sumSquares = 0.0;
        int peak = 0;
        for (int i = 0; i < limit; i++) {
            int sample = pcm.get(i);
            int abs = sample == Short.MIN_VALUE ? Short.MAX_VALUE : Math.abs(sample);
            if (abs > peak) peak = abs;
            sumSquares += (double) sample * (double) sample;
        }

        if (peak == 0) return 1.0F;

        double rms = Math.sqrt(sumSquares / limit);
        float rmsFactor = rms > 0.0 ? (float) (TARGET_RMS / rms) : 1.0F;
        float peakFactor = MAX_PEAK / (float) peak;
        return Math.min(MAX_NORMALIZATION, Math.min(rmsFactor, peakFactor));
    }
}


