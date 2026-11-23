package tvmod;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Клиентская команда: /tv <текст> озвучивает текст в голосовом чате, /tv stop прерывает. */
public class TvModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("tv")
                        .then(ClientCommands.literal("stop").executes(context -> {
                            TtsService.stop();
                            context.getSource().sendFeedback(Component.literal("[tv] Остановлено"));
                            return 1;
                        }))
                        .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                                .executes(context -> {
                                    String text = StringArgumentType.getString(context, "text");
                                    var source = context.getSource();
                                    source.sendFeedback(Component.literal("[tv] Озвучиваю..."));
                                    TtsService.speak(text, error ->
                                            Minecraft.getInstance().execute(() ->
                                                    source.sendFeedback(Component.literal("[tv] Ошибка: " + error))));
                                    return 1;
                                }))));
    }
}
