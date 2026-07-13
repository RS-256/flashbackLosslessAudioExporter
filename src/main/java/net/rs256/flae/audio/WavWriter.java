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
 * Streaming writer for 32bit float PCM ({@code WAVE_FORMAT_IEEE_FLOAT}) wav files.
 *
 * <p>Layout (56 byte header):
 * {@code RIFF} → {@code fmt } (16 bytes, format tag 3) → {@code fact} (4 bytes,
 * required by the spec for non-PCM formats) → {@code data}.
 *
 * <p>The total sample count is unknown while streaming, so the header is written
 * once as a placeholder and patched with the real sizes on {@link #close()}.
 * Samples are written as-is: interleaved 32bit float, no quantization,
 * no resampling.
 *
 * <p>This class is intentionally self-contained — no Flashback or Minecraft
 * dependency — and thread-safe: Flashback may call {@code close()} from a
 * different thread than the one feeding {@code encode()}.
 *
 * <p>Known limitation: data size fields are u32, so exports beyond ~4GiB of
 * audio (~3h of 48kHz stereo float) would overflow. {@link #writeSamples}
 * refuses further writes past that point instead of corrupting the header.
 */
public final class WavWriter implements AutoCloseable {
    private static final int HEADER_SIZE = 56;
    private static final int BYTES_PER_SAMPLE = 4; // 32bit float
    private static final long MAX_DATA_BYTES = 0xFFFFFFFFL - HEADER_SIZE;

    private final FileChannel channel;
    private final int channels;
    private final int sampleRate;
    private long dataBytes = 0;
    private boolean closed = false;

    public WavWriter(Path path, int channels, int sampleRate) throws IOException {
        if (channels < 1) throw new IllegalArgumentException("channels must be >= 1: " + channels);
        this.channels = channels;
        this.sampleRate = sampleRate;

        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);

        this.channel = FileChannel.open(path,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            writeHeader();
            channel.position(HEADER_SIZE); // header used positional writes; move the append cursor past it
        } catch (IOException e) {
            try { channel.close(); } catch (IOException ignored) {}
            throw e;
        }
    }

    public synchronized void writeSamples(FloatBuffer samples) throws IOException {
        if (closed) throw new IOException("WavWriter is already closed");

        FloatBuffer src = samples.duplicate();
        int sampleCount = src.remaining();
        if (sampleCount == 0) return;

        long byteCount = (long) sampleCount * BYTES_PER_SAMPLE;
        if (dataBytes + byteCount > MAX_DATA_BYTES) {
            throw new IOException("wav data chunk would exceed the 4GiB RIFF limit");
        }

        ByteBuffer bytes = ByteBuffer.allocate(sampleCount * BYTES_PER_SAMPLE).order(ByteOrder.LITTLE_ENDIAN);
        bytes.asFloatBuffer().put(src);
        while (bytes.hasRemaining()) {
            channel.write(bytes);
        }
        dataBytes += byteCount;
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
        int blockAlign = channels * BYTES_PER_SAMPLE;
        ByteBuffer h = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        h.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        h.putInt((int) (HEADER_SIZE - 8 + dataBytes));          // RIFF chunk size = file size - 8
        h.put("WAVE".getBytes(StandardCharsets.US_ASCII));

        h.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        h.putInt(16);                                           // fmt chunk size
        h.putShort((short) 3);                                  // WAVE_FORMAT_IEEE_FLOAT
        h.putShort((short) channels);
        h.putInt(sampleRate);
        h.putInt(sampleRate * blockAlign);                      // avg bytes per second
        h.putShort((short) blockAlign);
        h.putShort((short) (BYTES_PER_SAMPLE * 8));             // bits per sample

        h.put("fact".getBytes(StandardCharsets.US_ASCII));
        h.putInt(4);                                            // fact chunk size
        h.putInt((int) (dataBytes / blockAlign));               // total sample frames

        h.put("data".getBytes(StandardCharsets.US_ASCII));
        h.putInt((int) dataBytes);

        h.flip();
        int position = 0;
        while (h.hasRemaining()) {
            position += channel.write(h, position);             // positional write: does not move the append cursor
        }
    }
}
