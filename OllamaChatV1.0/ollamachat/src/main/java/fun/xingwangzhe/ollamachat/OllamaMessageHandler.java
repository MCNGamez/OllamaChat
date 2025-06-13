package fun.xingwangzhe.ollamachat;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OllamaMessageHandler {
	private static final ArrayDeque<QueuedMessage> MESSAGE_QUEUE = new ArrayDeque<>();
	private static boolean isSendingMessage = false; // Prevents recursive message sending
	private static long messageLastSentAt = 0;
	private static final long MESSAGE_COOLDOWN_MS = 3000;
	private static final Pattern RECEIVED_MESSAGE_PATTERN = Pattern.compile("(?:.* )?(\\w+)(?:: | ?>> | ?» | ?- )phaze (\\S.*)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
	private static final Pattern SENT_MESSAGE_PATTERN = Pattern.compile("(?:phaze|ai) (\\S.*)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	public static void initialize() {
		ClientReceiveMessageEvents.GAME.register(OllamaMessageHandler::onReceivedMessage);
		ClientSendMessageEvents.ALLOW_CHAT.register(OllamaMessageHandler::onSentMessage);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (MinecraftClient.getInstance().player == null) return;
			if (MESSAGE_QUEUE.isEmpty()) return;

			long currentTime = Util.getMeasuringTimeMs();
			if (currentTime - messageLastSentAt < MESSAGE_COOLDOWN_MS) return; // Prevent spamming messages

			var message = MESSAGE_QUEUE.poll();
			if (message != null) {
				sendMessage(message);
			}
		});
	}

	private static void onReceivedMessage(Text text, boolean overlay) {
		if (overlay) return;
		checkReceivedMessage(text);
	}

	private static void checkReceivedMessage(Text text) {
		String string = text.getString();
		Matcher matcher = RECEIVED_MESSAGE_PATTERN.matcher(string);
		if (!matcher.matches()) return;

		String senderName = matcher.group(1);
		if (senderName.equals(MinecraftClient.getInstance().getSession().getUsername())) return;

		String messageContent = matcher.group(2);
		handleInput(senderName, messageContent, false);
	}

	private static boolean onSentMessage(String text) {
		if (isSendingMessage) {
			isSendingMessage = false; // Reset flag to prevent recursion
			return true; // Prevent recursive calls
		}
		Matcher matcher = SENT_MESSAGE_PATTERN.matcher(text);
		if (!matcher.matches()) {
			addMessageToQueue(new QueuedMessage(text, false)); // If it doesn't match, send as normal message
			return false;
		}
		String messageContent = matcher.group(1);
		String playerName = MinecraftClient.getInstance().getSession().getUsername();
		handleInput(playerName, messageContent, true);
		addMessageToQueue(new QueuedMessage(messageContent, false)); // Still queue so we have full control over spam prevention
		return false;
	}

	private static void handleInput(String playerName, String messageContent, boolean isClientMessage) {
		OllamaDebugTracker.setMessageSource(isClientMessage);
		OllamaHttpClient.handleAIRequest(playerName, messageContent, isClientMessage);
	}

	public static void addMessageToQueue(@NotNull QueuedMessage message) {
		long currentTime = Util.getMeasuringTimeMs();
		if (currentTime - messageLastSentAt < MESSAGE_COOLDOWN_MS) MESSAGE_QUEUE.add(message);
		else sendMessage(message); // Otherwise, send immediately
	}

	private static void sendMessage(@NotNull QueuedMessage message) {
		assert MinecraftClient.getInstance().player != null;
		isSendingMessage = true; // Set flag to prevent recursion
		MinecraftClient.getInstance().player.networkHandler.sendChatMessage(message.getFormattedMessage());
		messageLastSentAt = Util.getMeasuringTimeMs();
	}

	public record QueuedMessage(String message, boolean isAiMessage) {
		public String getFormattedMessage() {
			return isAiMessage ? "[AI] " + message : message;
		}
	}
}
