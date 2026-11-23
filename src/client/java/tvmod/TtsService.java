package tvmod;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.mp3.Mp3Decoder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Текст -> речь (онлайн-синтез) -> PCM 48 кГц моно -> очередь кадров. */
public final class TtsService {
    // Неофициальный эндпоинт Google Translate. Может сломаться или ограничить частоту запросов.
    private static final String TTS_URL = "https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob";
    private static final int MAX_CHUNK = 180;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tv-tts");
        t.setDaemon(true);
        return t;
    });

    private static volatile int generation = 0;

    private TtsService() {
    }

    public static void speak(String text, Consumer<String> onError) {
        final int gen = generation;
        WORKER.execute(() -> {
            try {
                String lang = hasCyrillic(text) ? "ru" : "en";
                for (String part : split(text)) {
                    if (gen != generation) {
                        return;
                    }
                    short[] pcm = synthesize(part, lang);
                    if (gen != generation) {
                        return;
                    }
                    TtsQueue.addPcm48k(pcm);
                }
            } catch (Exception e) {
                onError.accept(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    public static void stop() {
        generation++;
        TtsQueue.clear();
    }

    private static short[] synthesize(String text, String lang) throws Exception {
        VoicechatApi api = TvVoicechatPlugin.api;
        if (api == null) {
            throw new IllegalStateException("Simple Voice Chat не подключён");
        }

        String url = TTS_URL + "&tl=" + lang + "&q=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
        HttpURLConnection con = (HttpURLConnection) URI.create(url).toURL().openConnection();
        con.setRequestProperty("User-Agent", "Mozilla/5.0");
        con.setConnectTimeout(8000);
        con.setReadTimeout(15000);
        if (con.getResponseCode() != 200) {
            throw new IOException("сервис озвучки ответил кодом " + con.getResponseCode());
        }

        byte[] mp3;
        try (InputStream in = con.getInputStream()) {
            mp3 = in.readAllBytes();
        }

        Mp3Decoder decoder = api.createMp3Decoder(new ByteArrayInputStream(mp3));
        if (decoder == null) {
            throw new IOException("не удалось создать MP3-декодер");
        }
        short[] samples = decoder.decode();
        var format = decoder.getAudioFormat();
        return toMono48k(samples, format.getSampleRate(), format.getChannels());
    }

    /** Сводит в моно и пересчитывает частоту в 48 кГц (линейная интерполяция). */
    private static short[] toMono48k(short[] in, float rate, int channels) {
        int frames = in.length / channels;
        short[] mono = new short[frames];
        for (int i = 0; i < frames; i++) {
            int sum = 0;
            for (int c = 0; c < channels; c++) {
                sum += in[i * channels + c];
            }
            mono[i] = (short) (sum / channels);
        }
        if (Math.abs(rate - 48000f) < 1f || frames == 0) {
            return mono;
        }

        int outLen = (int) (frames * 48000.0 / rate);
        short[] out = new short[outLen];
        double step = rate / 48000.0;
        for (int i = 0; i < outLen; i++) {
            double pos = i * step;
            int i0 = Math.min((int) pos, frames - 1);
            int i1 = Math.min(i0 + 1, frames - 1);
            double f = pos - i0;
            out[i] = (short) (mono[i0] * (1 - f) + mono[i1] * f);
        }
        return out;
    }

    private static boolean hasCyrillic(String s) {
        return s.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.CYRILLIC);
    }

    /** Режет длинный текст на куски (у сервиса лимит около 200 символов). */
    private static List<String> split(String text) {
        List<String> parts = new ArrayList<>();
        String rest = text.trim();
        while (rest.length() > MAX_CHUNK) {
            int cut = MAX_CHUNK;
            for (int i = MAX_CHUNK; i > 40; i--) {
                char c = rest.charAt(i);
                if (c == ' ' || c == '.' || c == ',' || c == '!' || c == '?') {
                    cut = i + 1;
                    break;
                }
            }
            parts.add(rest.substring(0, cut).trim());
            rest = rest.substring(cut).trim();
        }
        if (!rest.isEmpty()) {
            parts.add(rest);
        }
        return parts;
    }
}
