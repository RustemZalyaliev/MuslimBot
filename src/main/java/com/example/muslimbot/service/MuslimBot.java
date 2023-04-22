package com.example.muslimbot.service;

import com.example.muslimbot.config.BotConfig;
import com.example.muslimbot.entities.DayH2;
import com.example.muslimbot.entities.Muslim;
import com.example.muslimbot.pojo.Day;
import com.example.muslimbot.repo.DayRepository;
import com.example.muslimbot.repo.MuslimRepository;
import com.vdurmont.emoji.EmojiParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Component
public class MuslimBot extends TelegramLongPollingBot {

    final BotConfig botConfig;

    @Autowired
    private MuslimRepository muslimRepo;
    @Autowired
    private DayRepository dayRepo;
    @Autowired
    private Day today;

    static Map<String, LocalTime> todayPrayerTimes = new HashMap<>();
    static String muslimToday;
    final static Map<Integer, String> MUSLIM_MONTHS = new HashMap<>();
    final static Map<Integer, String> PRAYERS = new LinkedHashMap<>();
    final static String FAJR = "ФАДЖР";
    final static String SHURUK = "ШУРУК";
    final static String ZUHR = "ЗУХР";
    final static String ASR = "АСР";
    final static String MAGHREB = "МАГРИБ";
    final static String ISHA = "ИША";

    final static String NO_TODAY_DATA = EmojiParser.parseToUnicode("""
            Сервис по техническим причинам
            сегодня недоступен. :hammer_and_wrench:
            Приносим извинения за
            доставленные неудобства. :pensive:""");

    final static Map<LocalDate, LocalTime> SHUTDOWN_TIME = new HashMap<>();
    static LocalDate turn_onDate;
    static LocalTime turn_onTime;
    static boolean botIsOff = true;
    
    public MuslimBot(BotConfig botConfig) {

        this.botConfig = botConfig;

        List<BotCommand> listOfCommands = new ArrayList<>();

        listOfCommands.add(new BotCommand("/my_prayers", " Мое расписание на сегодня"));
        listOfCommands.add(new BotCommand("/my_settings", " Текущие настройки"));
        listOfCommands.add(new BotCommand("/choose_prayers", " Выбрать намазы"));
        listOfCommands.add(new BotCommand("/minutes", " За сколько минут уведомлять"));
        listOfCommands.add(new BotCommand("/today_prayers", " Все намазы сегодня"));

        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            System.out.println("\nError settings bot's command list: " + e.getMessage());
        }

    }

    @Scheduled(cron = "* * * * * *")
    private void sendMessageAboutTurningOnToAll() throws InterruptedException {

        if (botIsOff) {
            sendToAllMuslims(EmojiParser.parseToUnicode("Бот включен. :muscle:\nПриятной эксплуатации. "));
            botIsOff = false;
        }
    }

    @Override
    public void onUpdateReceived(Update update) {

        if (update.hasMessage() && update.getMessage().hasText()) {

            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            SendMessage message = new SendMessage();

            if ("/start".equals(messageText)) {

                if (!muslimRepo.existsById(chatId)) {

                    Muslim muslim = new Muslim();
                    muslim.setChatId(chatId);
                    muslim.setFirstName(update.getMessage().getChat().getFirstName());
                    muslim.setLastName(update.getMessage().getChat().getLastName());
                    muslim.setUserName(update.getMessage().getChat().getUserName());
                    muslim.setRegisteredAt(String.valueOf(LocalDateTime.now()));
                    muslim.setPrayers(FAJR + "&" + SHURUK + "&" + ZUHR + "&" + ASR + "&" + MAGHREB + "&" + ISHA);
                    muslim.setDelta(15);

                    muslimRepo.save(muslim);

                    message.setChatId(chatId);
                    String welcome = EmojiParser.parseToUnicode("Ас-саляму алейкум, "
                            + muslim.getFirstName() + "! " + ":wave:");
                    message.setText(welcome);
                    sendMessage(message);
                    String advice = """
                            На данный момент действуют настройки
                            по умолчанию. Применяемое в боте
                            время намазов действительно для
                            Москвы и Московской области.""";
                    message.setText(advice);
                    sendMessage(message);
                    showSettings(chatId);
                    advice = EmojiParser.parseToUnicode(":point_down: Выберите нужные намазы и настройте\n" +
                            "время уведомлений ниже в меню.");
                    message.setText(advice);
                    sendMessage(message);
                    newChoosePrayersMenu(chatId);

                } else {

                    Muslim muslim = muslimRepo.findById(chatId).get();
                    LocalDateTime stamp = LocalDateTime.parse(muslim.getRegisteredAt());
                    LocalDate date = LocalDate.from(stamp);
                    LocalTime time = LocalTime.of(stamp.getHour(), stamp.getMinute());
                    String welcome = EmojiParser.parseToUnicode("Ас-саляму алейкум, " + muslim.getFirstName()
                            + "! " + ":wave:" + "\nВы уже зарегистрировались\n" + date.getDayOfMonth() + " "
                            + date.getMonth() + " " + date.getYear() + " в " + time + ". " + ":point_up:");
                    message.setChatId(chatId);
                    message.setText(welcome);
                    sendMessage(message);

                }

            } else if(messageText.contains("/shutdown")) {

                String adminMessage = messageText.substring(messageText.indexOf(" ") + 1);
                prepareShutDownMessage(adminMessage);

            } else if (messageText.contains("/send")) {

                String messageToAll = messageText.substring(messageText.indexOf(" ") + 1);
                try {
                    sendToAllMuslims(messageToAll);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

            } else {

                switch (messageText) {
                    case "/my_prayers" -> showMyPrayers(chatId);
                    case "/my_settings" -> showSettings(chatId);
                    case "/choose_prayers" -> newChoosePrayersMenu(chatId);
                    case "/minutes" -> chooseMinutes(chatId);
                    case "/today_prayers" -> showTodayPrayers(chatId);
                    case "hecntv1979" -> {
                        if (botConfig.getOwnerId() == chatId) {
                            System.exit(130);
                        }
                    }
                }
            }

        } else if (update.hasCallbackQuery()) {

            Message messageIn = update.getCallbackQuery().getMessage();
            int messageId = messageIn.getMessageId();
            long chatId = messageIn.getChatId();
            String callbackData = update.getCallbackQuery().getData();
            Muslim muslim = muslimRepo.findById(chatId).get();
            String text;

            try {

                int delta = Integer.parseInt(callbackData);

                muslim.setDelta(delta);

                text = EmojiParser.parseToUnicode("Установлено " + callbackData + " минут. :ok_hand:");
                executeEditMessage(chatId, text, messageId);
                muslimRepo.save(muslim);
                showSettings(chatId);

            } catch (Exception e) {

                if (!"DONE".equals(callbackData)) {

                    List<String> prayersList = strToList(muslim.getPrayers());
                    int index = callbackData.lastIndexOf("_");
                    String tumbler = callbackData.substring(index + 1);
                    String prayer = callbackData.substring(0, index);

                    if ("ON".equals(tumbler)) {

                        prayersList.add(prayer);
                        muslim.setPrayers(listToString(prayersList));
                        muslimRepo.save(muslim);

                        try {
                            editChoosePrayersMenu(messageIn);
                        } catch (TelegramApiException ex) {
                            throw new RuntimeException(ex);
                        }

                    } else if ("OFF".equals(tumbler)) {

                        if (prayersList.size() > 1) {
                            prayersList.remove(prayer);
                            muslim.setPrayers(listToString(prayersList));
                            muslimRepo.save(muslim);

                            try {
                                editChoosePrayersMenu(messageIn);
                            } catch (TelegramApiException ex) {
                                throw new RuntimeException(ex);
                            }

                        } else {

                            text = EmojiParser.parseToUnicode("""
                        Вы отключаете последнее уведомление,
                        и вместе с ним по сути всего бота. :cry:
                        Пожалуйста, подключите сначала
                        второе уведомление на другой намаз
                        и затем отключите это. :face_with_monocle:""");
                            executeEditMessage(chatId, text, messageId);
                            newChoosePrayersMenu(chatId);
                        }

                    }

                } else {

                    text = EmojiParser.parseToUnicode("Ваше расписание настроено. :ok_hand:");
                    executeEditMessage(chatId, text, messageId);
                    showSettings(chatId);

                }

            }

        }
    }

    private void executeEditMessage(long chatId, String text, Integer messageId) {

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId);
        message.setText(text);
        message.setMessageId(messageId);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.getStackTrace();
        }
    }

    private void showSettings(long chatId) {

        if (muslimRepo.existsById(chatId)) {

            Muslim muslim = muslimRepo.findById(chatId).get();
            List<String> prayersDraft = strToList(muslim.getPrayers());
            StringBuilder text = new StringBuilder();

            List<String> prayers = new ArrayList<>();

            for (String i : PRAYERS.values()) {
                if (prayersDraft.contains(i)) {
                    prayers.add(i);
                }
            }

            if (prayers.size() < 6) {
                text.append("У Вас подключены уведомления на следующие намазы:\n\n");
            } else {
                text.append("У Вас подключены уведомления на все намазы:\n\n");
            }

            for (String i : prayers) {
                text.append(":palms_up_together: ").append(i).append("\n");
            }
            text.append("\n- уведомления приходят за ").append(muslim.getDelta()).append(" минут. :alarm_clock:");
            String textToSend = EmojiParser.parseToUnicode(String.valueOf(text));
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText(textToSend);
            sendMessage(message);

        }

    }

    private void showMyPrayers(long chatId) {

        SendMessage message = new SendMessage();
        message.setChatId(chatId);

        if (muslimRepo.existsById(chatId)) {

            if (muslimToday != null) {

                Muslim muslim = muslimRepo.findById(chatId).get();
                List<String> prayersDraft = strToList(muslim.getPrayers());
                StringBuilder text = new StringBuilder();
                text.append("Мое расписание на сегодня:\n\n");

                for (String i : PRAYERS.values()) {
                    if (prayersDraft.contains(i)) {
                        text.append(":palms_up_together: ").append(todayPrayerTimes.get(i)).append(" - ").append(i).append("\n");
                    }
                }

                text.append("\n(сегодня ").append(muslimToday).append(").");
                message.setText(EmojiParser.parseToUnicode(String.valueOf(text)));

            } else {

                message.setText(NO_TODAY_DATA);

            }

            sendMessage(message);

        }

    }

    private void newChoosePrayersMenu(long chatId) {

        if (muslimRepo.existsById(chatId)) {

            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText(EmojiParser.parseToUnicode("""
                    Подключите или отключите уведомления к нужным
                    Вам намазам ( :white_check_mark: - уведомления подключены)."""));

            message.setReplyMarkup(createKeyboard(chatId));

            sendMessage(message);

        }

    }

    private InlineKeyboardMarkup createKeyboard(long chatId) {

        Muslim muslim = muslimRepo.findById(chatId).get();

        List<String> prayers = strToList(muslim.getPrayers());

        String text, backData, prayer;

        InlineKeyboardMarkup markupInLine = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInLine = new ArrayList<>();
        List<InlineKeyboardButton> rowInLine = new ArrayList<>();

        for (int i = 0; i < 6; i++) {
            prayer = PRAYERS.get(i);
            rowInLine.add(new InlineKeyboardButton());
            text = prayers.contains(prayer) ? prayer + " :white_check_mark:" : prayer;
            backData = prayers.contains(prayer) ? prayer + "_OFF" : prayer + "_ON";
            rowInLine.get(rowInLine.size() - 1).setText(EmojiParser.parseToUnicode(text));
            rowInLine.get(rowInLine.size() - 1).setCallbackData(backData);
            if (i == 2 || i == 5) {
                rowsInLine.add(rowInLine);
                rowInLine = new ArrayList<>();
            }
        }

        InlineKeyboardButton done = new InlineKeyboardButton();
        done.setText("Готово");
        done.setCallbackData("DONE");
        rowInLine.add(done);
        rowsInLine.add(rowInLine);

        markupInLine.setKeyboard(rowsInLine);

        return markupInLine;
    }

    private void editChoosePrayersMenu(Message message) throws TelegramApiException {

        long chatId = message.getChatId();
        EditMessageReplyMarkup editMessageReplyMarkup = new EditMessageReplyMarkup();

        editMessageReplyMarkup.setChatId(chatId);
        editMessageReplyMarkup.setMessageId(message.getMessageId());
        editMessageReplyMarkup.setReplyMarkup(createKeyboard(chatId));

        execute(editMessageReplyMarkup);
    }

    private void showTodayPrayers(long chatId) {

        SendMessage message = new SendMessage();
        message.setChatId(chatId);

        if (muslimToday != null) {

            StringBuilder text = new StringBuilder();
            text.append("Расписание всех намазов на сегодня:\n\n");

            for (String i : PRAYERS.values()) {
                text.append(":palms_up_together: ").append(todayPrayerTimes.get(i)).append(" - ").append(i).append("\n");
            }

            text.append("\nВремя применимо к Москве\nи Московской области\n\n(сегодня ").append(muslimToday).append(").");
            message.setText(EmojiParser.parseToUnicode(String.valueOf(text)));

        } else {

            message.setText(NO_TODAY_DATA);

        }

        sendMessage(message);
    }

    private void chooseMinutes(long chatId) {

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(EmojiParser.parseToUnicode("""
                Выберите нужное кол-во минут
                (за сколько до начала намаза Вам
                будут приходить уведомления :alarm_clock: )"""));

        InlineKeyboardMarkup markupInLine = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInLine = new ArrayList<>();
        List<InlineKeyboardButton> rowInLine = new ArrayList<>();

        for (int i = 5; i < 61; i += 5) {
            rowInLine.add(new InlineKeyboardButton());
            rowInLine.get(rowInLine.size() - 1).setText(String.valueOf(i));
            rowInLine.get(rowInLine.size() - 1).setCallbackData(String.valueOf(i));
            if (i % 20 == 0) {
                rowsInLine.add(rowInLine);
                rowInLine = new ArrayList<>();
            }
        }

        markupInLine.setKeyboard(rowsInLine);

        message.setReplyMarkup(markupInLine);

        sendMessage(message);

    }

    private String listToString(List<String> list) {

        if (!list.isEmpty()) {
            StringBuilder str = new StringBuilder();
            for (String i : list) {
                str.append(i).append("&");
            }
            return String.valueOf(str);
        } else {
            return "";
        }

    }

    private List<String> strToList(String str) {

        if (!str.isEmpty()) {
            String[] arr = str.split("&");
            return new ArrayList<>(List.of(arr));
        } else {
            return new ArrayList<>();
        }
    }

    @Scheduled(cron = "${day.scheduler}")
    private void newDayIsComing() throws InterruptedException {

        assignDaySchedule();

    }

    @Scheduled(cron = "${prayer.scheduler}")
    private void prepareReminder() {

        LocalTime testTime;
        LocalTime currentTime = LocalTime.now();
        LocalDate currentDate = LocalDate.now();

        if (!SHUTDOWN_TIME.isEmpty() && SHUTDOWN_TIME.containsKey(currentDate)
                && currentTime.getHour() == SHUTDOWN_TIME.get(currentDate).getHour()
                && currentTime.getMinute() == SHUTDOWN_TIME.get(currentDate).getMinute()) {

            SendMessage message = new SendMessage();
            message.setChatId(botConfig.getOwnerId());
            message.setText("Бот отключен.\nЗаявленное пользователям\nвремя включения:\n\n" + turn_onTime + " "
                    + turn_onDate.getDayOfMonth() + " " + turn_onDate.getMonth().toString().toLowerCase() + " "
                    + turn_onDate.getYear() + ".");
            sendMessage(message);

            System.exit(130);

        }

        for (LocalTime checkTime : todayPrayerTimes.values()) {

            testTime = checkTime.minusMinutes(63);

            if (currentTime.isAfter(testTime) && currentTime.isBefore(checkTime.minusMinutes(4))) {

                LocalTime prayerTime;
                LocalTime futureTime;
                String prayer;
                StringBuilder time = new StringBuilder();

                if (muslimRepo.count() != 0) {

                    List<Muslim> allMuslims = (List<Muslim>) muslimRepo.findAll();

                    for (var elem : todayPrayerTimes.entrySet()) {

                        prayerTime = elem.getValue();
                        List<String> muslimPrayers;

                        for (Muslim muslim : allMuslims) {

                            futureTime = LocalTime.now().plusMinutes(muslim.getDelta());
                            muslimPrayers = strToList(muslim.getPrayers());

                            if ((futureTime.equals(prayerTime) ||
                                    (futureTime.isAfter(prayerTime) && futureTime.isBefore(prayerTime.plusSeconds(30))))
                                    && muslimPrayers.contains(elem.getKey())) {
                                prayer = elem.getKey();
                                String strTime = String.valueOf(prayerTime);
                                for (int i = 0; i < strTime.length(); i++) {
                                    if (strTime.charAt(i) == '.') {
                                        break;
                                    } else {
                                        time.append(strTime.charAt(i));
                                    }
                                }

                                sendReminder(muslim.getChatId(), prayer, time, muslim.getDelta());

                            }

                        }

                    }

                }

            }

        }

    }

    private void sendReminder(long chatId, String prayer, StringBuilder time, int delta) {

        String textToSend = EmojiParser.parseToUnicode(":alarm_clock: Следующий намаз " + prayer + " :palms_up_together:" +
                "\n\nначнется через " + delta + " минут в " + time + ".\n\n(сегодня " + muslimToday + ").");

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(textToSend);
        sendMessage(message);

    }

    private void prepareShutDownMessage(String adminMessage) {

        SendMessage message = new SendMessage();
        String textForAdmin = "Сообщение об отключении\nсоставлено некорректно.\nПожалуйста, повторите.";

        try {

            String[] times = adminMessage.split(" ");
            LocalTime timeFrom, timeTo;
            LocalDate date;

            if (times.length == 2 || times.length == 3) {

                if (times.length == 3) {
                    date = LocalDate.parse(times[0]);
                    timeFrom = LocalTime.parse(times[1]);
                    timeTo = LocalTime.parse(times[2]);
                } else {
                    date = LocalDate.now();
                    timeFrom = LocalTime.parse(times[0]);
                    timeTo = LocalTime.parse(times[1]);
                }

                if (date.isBefore(LocalDate.now())) {

                    textForAdmin = """
                            Сообщение об отключении
                            составлено некорректно.
                            Дата отключения должно быть
                            позже или равна текущей дате.
                            Пожалуйста, повторите.""";

                } else if (date.equals(LocalDate.now()) && (timeTo.equals(timeFrom) || timeTo.isBefore(timeFrom))) {

                    textForAdmin = """
                            Сообщение об отключении
                            составлено некорректно.
                            Время включения должно быть
                            позже времени отключения.
                            Пожалуйста, повторите.""";

                } else if (date.equals(LocalDate.now()) && timeFrom.isBefore(LocalTime.now())) {

                    textForAdmin = """
                            Сообщение об отключении
                            составлено некорректно.
                            Время отключения должно быть
                            позже текущего времени.
                            Пожалуйста, повторите.""";

                }  else {

                    String textToSend = EmojiParser.parseToUnicode(date.getDayOfMonth() + " " + date.getMonth().toString().toLowerCase() + " "
                            + date.getYear() + " с " + timeFrom + " до " + timeTo + "\nForNamazBot будет\nотключен для проведения"
                            + "\nпрофилактических работ. :hammer_and_wrench:" + "\nПриносим извинения за\nдоставленные неудобства. :pensive:");

                    textForAdmin = EmojiParser.parseToUnicode("Сообщение разослано.\nБот будет отключен\n"
                            + date.getDayOfMonth() + " " + date.getMonth().toString().toLowerCase() + " " + date.getYear() + " в " + timeFrom + "."
                            + "\nПожалуйста, позаботьтесь\nо его включении в " + timeTo + ". :point_up:");

                    sendToAllMuslims(textToSend);
                    SHUTDOWN_TIME.put(date, timeFrom);
                    turn_onDate = date;
                    turn_onTime = timeTo;

                }

            } else if (times.length == 4) {

                LocalDate dateFrom = LocalDate.parse(times[0]);
                LocalDate dateTo = LocalDate.parse(times[1]);
                timeFrom = LocalTime.parse(times[2]);
                timeTo = LocalTime.parse(times[3]);

                if (dateTo.isBefore(dateFrom)) {

                    textForAdmin = """
                            Сообщение об отключении
                            составлено некорректно.
                            Дата включения должна быть
                            позже даты отключения.
                            Пожалуйста, повторите.""";

                } else if (dateFrom.equals(dateTo) && (timeTo.equals(timeFrom) || timeTo.isBefore(timeFrom))) {

                    textForAdmin = """
                            Сообщение об отключении
                            составлено некорректно.
                            Время включения должно быть
                            позже времени отключения.
                            Пожалуйста, повторите.""";

                } else if (dateFrom.isBefore(LocalDate.now())) {

                    textForAdmin = """
                            Сообщение об отключении
                            составлено некорректно.
                            Дата отключения должно быть
                            позже или равна текущей дате.
                            Пожалуйста, повторите.""";

                } else if (dateFrom.equals(LocalDate.now()) && timeFrom.isBefore(LocalTime.now())) {

                    textForAdmin = """
                            Сообщение об отключении
                            составлено некорректно.
                            Время отключения должно быть
                            позже текущего времени.
                            Пожалуйста, повторите.""";

                }  else {

                    String textToSend = EmojiParser.parseToUnicode("С " + timeFrom + " " + dateFrom.getDayOfMonth() + " "
                            + dateFrom.getMonth().toString().toLowerCase() + " " + dateFrom.getYear()
                            + "\nдо " + timeTo + " " + dateTo.getDayOfMonth() + " "
                            + dateTo.getMonth().toString().toLowerCase() + " " + dateTo.getYear()
                            + "\nForNamazBot будет\nотключен для проведения"
                            + "\nпрофилактических работ. :hammer_and_wrench:"
                            + "\nПриносим извинения за\nдоставленные неудобства. :pensive:");

                    textForAdmin = EmojiParser.parseToUnicode("Сообщение разослано.\nБот будет отключен\n"
                            + dateFrom.getDayOfMonth() + " " + dateFrom.getMonth().toString().toLowerCase() + " " + dateFrom.getYear() + " в "
                            + timeFrom + "." + "\nПожалуйста, позаботьтесь\nо его включении в "
                            + timeTo + "\n" + dateTo.getDayOfMonth() + " " + dateTo.getMonth().toString().toLowerCase() + " " + dateTo.getYear()
                            + ". :point_up:");

                    sendToAllMuslims(textToSend);
                    SHUTDOWN_TIME.put(dateFrom, timeFrom);
                    turn_onDate = dateTo;
                    turn_onTime = timeTo;

                }

            }

        } catch (Exception e) {

            textForAdmin = "Сообщение об отключении\nсоставлено некорректно.\nПожалуйста, повторите.";
            
        }

        message.setChatId(botConfig.getOwnerId());
        message.setText(textForAdmin);
        sendMessage(message);

    }

    private void sendToAllMuslims(String textToSend) throws InterruptedException {

        if (muslimRepo.count() != 0) {
            List<Muslim> allMuslims = (List<Muslim>) muslimRepo.findAll();
            SendMessage message;
            for (int i = 0; i < allMuslims.size(); i++) {
                if (i % 20 == 0) {
                    Thread.sleep(100);
                }
                message = new SendMessage();
                message.setChatId(String.valueOf(allMuslims.get(i).getChatId()));
                message.setText(textToSend);
                sendMessage(message);
            }
        }

    }

    private void sendMessage(SendMessage message) {

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.getStackTrace();
        }
    }

    private void assignDaySchedule() throws InterruptedException {

        String todayDate = LocalDate.now().toString();

        if (dayRepo.existsById(todayDate)) {

            DayH2 todayH2 = dayRepo.findById(todayDate).get();
            today = dayH2ToDay(todayH2);
            todayPrayerTimes.putAll(today.getPrayerTimes());
            muslimToday = EmojiParser.parseToUnicode(today.getMuslimDate() + " число, месяц " + today.getMuslimMonth() + ",\n" +
                    today.getHijraYear() + " год по Хиджре :mosque: ");

        } else {

            todayPrayerTimes.clear();
            muslimToday = "";
            sendToAllMuslims(NO_TODAY_DATA);

        }

    }

    @Override
    public String getBotUsername() {

        MUSLIM_MONTHS.put(1, "Мухаррам");
        MUSLIM_MONTHS.put(2, "Сафар");
        MUSLIM_MONTHS.put(3, "Раби Аль-Авваль");
        MUSLIM_MONTHS.put(4, "Рабиу Ас-Сани");
        MUSLIM_MONTHS.put(5, "Джумад Аль-Уля");
        MUSLIM_MONTHS.put(6, "Джумад Ас-Сани");
        MUSLIM_MONTHS.put(7, "Раджаб");
        MUSLIM_MONTHS.put(8, "Шабан");
        MUSLIM_MONTHS.put(9, "Рамадан");
        MUSLIM_MONTHS.put(10, "Шавваль");
        MUSLIM_MONTHS.put(11, "Зу-ль-Када");
        MUSLIM_MONTHS.put(12, "Зу-ль-Хиджа");

        PRAYERS.put(0, FAJR);
        PRAYERS.put(1, SHURUK);
        PRAYERS.put(2, ZUHR);
        PRAYERS.put(3, ASR);
        PRAYERS.put(4, MAGHREB);
        PRAYERS.put(5, ISHA);

        try {
            assignDaySchedule();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return botConfig.getBotName();
    }

    @Override
    public String getBotToken() {
        return botConfig.getToken();
    }

    static Day dayH2ToDay(DayH2 dayH2) {

        Day day = new Day();
        day.setDate(LocalDate.parse(dayH2.getDate()));
        day.setMuslimDate(dayH2.getMuslimDate());
        day.setMuslimMonth(MUSLIM_MONTHS.get(dayH2.getMuslimMonth()));
        day.setHijraYear(dayH2.getHijraYear());
        String[] strTimes = dayH2.getPrayerTimes().split("&");
        Map<String, LocalTime> times = new LinkedHashMap<>();
        for (int i = 0; i < strTimes.length; i++) {
            times.put(PRAYERS.get(i), LocalTime.parse(strTimes[i]));
        }
        day.setPrayerTimes(times);
        return day;
    }

    /*static List<DayH2> readFile() throws FileNotFoundException {

        List<DayH2> dayH2List = new ArrayList<>();

        File textFile = new File("C:/Users/zalya/Telegram Bots/aprilData.txt");
        Scanner textReader = new Scanner(textFile);
        String textIn = "";
        while (textReader.hasNextLine()) {
            textIn += " " + textReader.nextLine();
        }

        String[] textArr = textIn.split("@");

        System.out.println("\ntextArr = " + Arrays.toString(textArr));

        DayH2 dayH2;

        for (int i = 0; i < textArr.length; i += 5) {

            dayH2 = new DayH2();
            dayH2.setDate(textArr[i]);
            dayH2.setMuslimDate(Integer.parseInt(textArr[i + 2]));
            for (var elem : MUSLIM_MONTHS.entrySet()) {
                if (textArr[i + 3].equals(elem.getValue())) {
                    dayH2.setMuslimMonth(elem.getKey());
                    break;
                }
            }
            dayH2.setHijraYear(Integer.parseInt(textArr[i + 4]));
            dayH2.setPrayerTimes(textArr[i + 1]);

            dayH2List.add(dayH2);
        }

        return dayH2List;
    }*/

}
