package com.example.toiletfinderservice.controller;

import com.example.toiletfinderservice.dto.RequestLocationDto;
import com.example.toiletfinderservice.dto.ToiletDto;
import com.example.toiletfinderservice.service.OverpassService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendLocation;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BotController extends TelegramLongPollingBot {

    private final OverpassService overpassService;
    private final Map<Long, Integer> userRadiusMap = new HashMap<>();

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username}")
    private String botUsername;

    @PostConstruct
    public void init() {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(this);
            System.out.println("Бот успешно зарегистрирован!");
        } catch (TelegramApiException e) {
            throw new RuntimeException("Ошибка регистрации бота", e);
        }
    }

    private ReplyKeyboardMarkup createRadiusKeyboard() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(false);
        keyboard.setSelective(true);

        List<KeyboardRow> keyboardRows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("500 м");
        row1.add("1000 м");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("1500 м");
        row2.add("2000 м");

        keyboardRows.add(row1);
        keyboardRows.add(row2);
        keyboard.setKeyboard(keyboardRows);

        return keyboard;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage()) {
            log.info("Received message is empty");
            return;
        }
        if (update.getMessage().hasText()) {
            commandHandler(update);
        }
        if (update.getMessage().hasLocation()) {
            findToilet(update);
        }
    }

    private void commandHandler(Update update) {
        String command = update.getMessage().getText();
        Long chatId = update.getMessage().getChatId();
        if ("/start".equals(command)) {
            sendWelcomeMessage(chatId);
        }
        if (command.matches("\\d+ м")) {
            setRadius(chatId, command);
        }
    }

    private void sendWelcomeMessage(Long chatId) {
        int currentRadius = userRadiusMap.getOrDefault(chatId, 500);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("🚽 Выберите радиус поиска туалетов (текущий: " + currentRadius + " м)");
        message.setReplyMarkup(createRadiusKeyboard());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending welcome message", e);
        }
    }

    private void setRadius(Long chatId, String command) {
        int radius = Integer.parseInt(command.substring(0, command.indexOf(' ')));
        userRadiusMap.put(chatId, radius);
        sendWelcomeMessage(chatId);
    }

    private void findToilet(Update update) {
        int radius = userRadiusMap.get(update.getMessage().getChatId());

        RequestLocationDto requestLocationDto = RequestLocationDto.builder()
                .lat(update.getMessage().getLocation().getLatitude())
                .lon(update.getMessage().getLocation().getLongitude())
                .radius(radius)
                .build();

        List<ToiletDto> toilets = overpassService.findNearbyToilets(requestLocationDto);

        if (toilets.isEmpty()) {
            sendText(update.getMessage().getChatId(), "🚽 Туалеты не найдены в радиусе " + radius + " м!");
        } else {
            sendText(update.getMessage().getChatId(), "Найдено туалетов: " + toilets.size());
            for (ToiletDto toilet : toilets) {
                sendLocation(update.getMessage().getChatId(), toilet.getLat(), toilet.getLon());
            }
        }
    }

    private void sendText(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendLocation(Long chatId, double lat, double lon) {
        SendLocation location = new SendLocation();
        location.setChatId(chatId.toString());
        location.setLatitude(lat);
        location.setLongitude(lon);
        try {
            execute(location);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }
}
