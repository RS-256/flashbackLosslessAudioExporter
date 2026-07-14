package net.rs256.flae.audio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Streaming wav writer fed with the interleaved 32bit float buffers Flashback
 * renders. The on-disk sample format is selected via {@link WavFormat}:
 * {@code PCM_FLOAT32} writes the input verbatim (lossless), the integer
 * formats quantize with plain rounding + clamp (no dither).
 *
 * <p>Header layout (SPEC.md §4.4): {@code RIFF} → {@code fmt } (16 bytes) →
 * [{@code fact} (4 bytes, required by spec for non-PCM formats — written for
 * float only)] → {@code data}. The total sample count is unknown while
 * streaming, so the header is written once as a placeholder and patched with
 * the real sizes on {@link #close()}.
 *
 * <p>This class is intentionally self-contained — no Flashback or Minecraft
 * dependency — and thread-safe: Flashback may call {@code close()} from a
 * different thread than the one feeding {@code encode()}.
 *
 * <p>Known limitation: data size fields are u32, so files beyond ~4GiB of
 * audio would overflow. {@link #writeSamples} refuses further writes past
 * that point instead of corrupting the header.
 */
public final class WavWriter implements AutoCloseable {

    private final FileChannel channel;
    private final WavFormat format;
    private final int headerSize; // 56 with fact chunk (float), 44 without (int PCM)
    private final int channels;
    private final int sampleRate;
    private long dataBytes = 0;
    private boolean closed = false;

    public WavWriter(Path path, int channels, int sampleRate, WavFormat format) throws IOException {
        if (channels < 1) throw new IllegalArgumentException("channels must be >= 1: " + channels);
        this.channels = channels;
        this.sampleRate = sampleRate;
        this.format = format;
        this.headerSize = format.isFloat() ? 56 : 44;

        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);

        this.channel = FileChannel.open(path,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            writeHeader();
            channel.position(this.headerSize); // header used positional writes; move the append cursor past it
        } catch (IOException e) {
            try { channel.close(); } catch (IOException ignored) {}
            throw e;
        }
    }

    /**
     * Converts and appends all samples between {@code position()} and
     * {@code limit()} of the given buffer to the data chunk. The buffer itself
     * is not modified (a duplicate is consumed), so the caller's position
     * survives untouched — important because Flashback still hands the same
     * buffer to FFmpeg afterward.
     */
    public synchronized void writeSamples(FloatBuffer samples) throws IOException {
        if (closed) throw new IOException("WavWriter is already closed");

        FloatBuffer src = samples.duplicate();
        int sampleCount = src.remaining();
        if (sampleCount == 0) return;

        long byteCount = (long) sampleCount * format.bytesPerSample();
        if (dataBytes + byteCount > 0xFFFFFFFFL - this.headerSize) {
            throw new IOException("wav data chunk would exceed the 4GiB RIFF limit");
        }

        ByteBuffer bytes = ByteBuffer.allocate((int) byteCount).order(ByteOrder.LITTLE_ENDIAN);
        convert(src, bytes);
        bytes.flip();
        while (bytes.hasRemaining()) {
            channel.write(bytes);
        }
        dataBytes += byteCount;
    }

    private void convert(FloatBuffer src, ByteBuffer out) {
        switch (this.format) {
            case PCM_F32 -> {
                out.asFloatBuffer().put(src);
                out.position(out.capacity()); // the float view does not advance the byte position
            }
            case PCM_I32 -> {
                while (src.hasRemaining()) out.putInt((int) quantize(src.get(), 2147483647.0));
            }
            case PCM_I24 -> {
                while (src.hasRemaining()) {
                    int v = (int) quantize(src.get(), 8388607.0);
                    out.put((byte) v).put((byte) (v >> 8)).put((byte) (v >> 16));
                }
            }
            case PCM_I16 -> {
                while (src.hasRemaining()) out.putShort((short) quantize(src.get(), 32767.0));
            }
            case PCM_U8 -> {
                // 8-bit wav is unsigned with the zero line at 128
                while (src.hasRemaining()) out.put((byte) (quantize(src.get(), 127.0) + 128));
            }
        }
    }

    /** Rounds {@code sample * scale} and clamps to [-scale-1, scale]. */
    private static long quantize(float sample, double scale) {
        double v = Math.rint(sample * scale);
        return (long) Math.clamp(v, -scale - 1.0, scale);
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        try {
            writeHeader();
        } finally {
            channel.close();
        }
    }

    private void writeHeader() throws IOException {
        int blockAlign = channels * format.bytesPerSample();
        ByteBuffer h = ByteBuffer.allocate(this.headerSize).order(ByteOrder.LITTLE_ENDIAN);

        h.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        h.putInt((int) (this.headerSize - 8 + dataBytes));      // RIFF chunk size = file size - 8
        h.put("WAVE".getBytes(StandardCharsets.US_ASCII));

        h.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        h.putInt(16);                                           // fmt chunk size
        h.putShort((short) format.formatTag());
        h.putShort((short) channels);
        h.putInt(sampleRate);
        h.putInt(sampleRate * blockAlign);                      // avg bytes per second
        h.putShort((short) blockAlign);
        h.putShort((short) format.bitsPerSample());

        if (format.isFloat()) {
            h.put("fact".getBytes(StandardCharsets.US_ASCII));  // required for non-PCM format tags
            h.putInt(4);                                        // fact chunk size
            h.putInt((int) (dataBytes / blockAlign));           // total sample frames
        }

        h.put("data".getBytes(StandardCharsets.US_ASCII));
        h.putInt((int) dataBytes);

        h.flip();
        int position = 0;
        while (h.hasRemaining()) {
            position += channel.write(h, position);             // positional write: does not move the append cursor
        }
    }
}
