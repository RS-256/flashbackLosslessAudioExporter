package net.rs256.flae.audio;

public enum WavFormat {
    PCM_F32("32-bit float (lossless)", 3, 32),
    PCM_I32("32-bit int PCM", 1, 32),
    PCM_I24("24-bit int PCM", 1, 24),
    PCM_I16("16-bit int PCM", 1, 16),
    PCM_U8("8-bit PCM (unsigned)", 1, 8);

    public static final WavFormat[] VALUES = values();

    private final String label;
    private final int formatTag;
    private final int bitsPerSample;

    WavFormat(String label, int formatTag, int bitsPerSample) {
        this.label = label;
        this.formatTag = formatTag;
        this.bitsPerSample = bitsPerSample;
    }

    public String label() {
        return this.label;
    }

    /** {@code wFormatTag}: 1 = WAVE_FORMAT_PCM, 3 = WAVE_FORMAT_IEEE_FLOAT. */
    public int formatTag() {
        return this.formatTag;
    }

    public int bitsPerSample() {
        return this.bitsPerSample;
    }

    public int bytesPerSample() {
        return this.bitsPerSample / 8;
    }

    public boolean isFloat() {
        return this.formatTag == 3;
    }

    public static String[] labels() {
        String[] labels = new String[VALUES.length];
        for (int i = 0; i < VALUES.length; i++) {
            labels[i] = VALUES[i].label;
        }
        return labels;
    }
}
