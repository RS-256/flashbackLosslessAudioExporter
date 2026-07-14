package net.rs256.flae.audio;

import java.nio.file.Path;

public final class WavPaths {

    /**
     * Same directory and base name as the video output, extension swapped to
     * {@code .wav}. If that collided with the video file itself,
     * {@code .audio.wav} is appended instead of overwriting it.
     */
    public static Path derive(Path videoOutput) {
        String name = videoOutput.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;

        Path parent = videoOutput.getParent();
        Path wavPath = parent != null ? parent.resolve(base + ".wav") : Path.of(base + ".wav");
        if (wavPath.equals(videoOutput)) {
            wavPath = parent != null ? parent.resolve(base + ".audio.wav") : Path.of(base + ".audio.wav");
        }
        return wavPath;
    }

    private WavPaths() {}
}
