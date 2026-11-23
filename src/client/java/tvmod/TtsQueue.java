package tvmod;

import java.util.concurrent.ConcurrentLinkedQueue;

/** Очередь кадров озвучки. Один кадр = 960 сэмплов = 20 мс при 48 кГц, моно. */
public final class TtsQueue {
    public static final int FRAME_SIZE = 960;

    private static final ConcurrentLinkedQueue<short[]> FRAMES = new ConcurrentLinkedQueue<>();

    private TtsQueue() {
    }

    /** Режет готовый звук (48 кГц, моно) на кадры и кладёт в очередь. */
    public static void addPcm48k(short[] pcm) {
        for (int i = 0; i < pcm.length; i += FRAME_SIZE) {
            short[] frame = new short[FRAME_SIZE];
            System.arraycopy(pcm, i, frame, 0, Math.min(FRAME_SIZE, pcm.length - i));
            FRAMES.add(frame);
        }
    }

    /** Следующий кадр или null, если говорить нечего. */
    public static short[] next() {
        return FRAMES.poll();
    }

    public static void clear() {
        FRAMES.clear();
    }
}
