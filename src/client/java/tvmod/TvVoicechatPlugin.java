package tvmod;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MergeClientSoundEvent;

/** Плагин Simple Voice Chat: подмешивает озвучку в звук микрофона. */
public class TvVoicechatPlugin implements VoicechatPlugin {

    public static volatile VoicechatApi api;

    @Override
    public String getPluginId() {
        return "tvmod";
    }

    @Override
    public void initialize(VoicechatApi voicechatApi) {
        api = voicechatApi;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        // Событие приходит каждые 20 мс, даже если микрофон молчит
        registration.registerEvent(MergeClientSoundEvent.class, event -> {
            short[] frame = TtsQueue.next();
            if (frame != null) {
                event.mergeAudio(frame);
            }
        });
    }
}
