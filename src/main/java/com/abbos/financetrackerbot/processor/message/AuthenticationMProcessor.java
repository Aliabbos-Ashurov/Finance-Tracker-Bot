package com.abbos.financetrackerbot.processor.message;


import com.abbos.financetrackerbot.domain.entity.User;
import com.abbos.financetrackerbot.enums.Language;
import com.abbos.financetrackerbot.enums.Role;
import com.abbos.financetrackerbot.processor.Processor;
import com.abbos.financetrackerbot.service.UserService;
import com.abbos.financetrackerbot.util.factory.SendMessageFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import static com.abbos.financetrackerbot.state.AdminBaseMenuState.ADMIN_ON_BASE_MENU;
import static com.abbos.financetrackerbot.state.UserBaseMenuState.USER_ON_BASE_MENU;

/**
 * @author Aliabbos Ashurov
 * @since 09/January/2025  15:46
 **/
@Component
@RequiredArgsConstructor
public class AuthenticationMProcessor implements Processor {

    private final UserService userService;
    private final SendMessageFactory sendMessageFactory;
    private final PasswordEncoder passwordEncoder;

    @Override
    public BotApiMethod<?> process(Update update, String state) {
        final var message = update.getMessage();
        final var chatId = message.getFrom().getId();
        final var password = message.getText();
        final var user = userService.findByChatId(chatId)
                .orElseThrow(() -> new IllegalStateException("User not found for chat ID: " + chatId));
        final var language = user.getLanguage();

        if (isValidPassword(password, user))
            return sendMenuBasedOnRole(chatId, user, language);
        return sendMessageFactory.sendMessagePassWrong(chatId.toString(), language);
    }

    private BotApiMethod<?> sendMenuBasedOnRole(Long chatId, User user, Language language) {
        final var role = user.getRole();
        return switch (role) {
            case USER -> {
                updateUserState(user, USER_ON_BASE_MENU);
                yield sendMessageFactory.sendMessageUserMenu(chatId.toString(), language);
            }
            case ADMIN -> {
                updateUserState(user, ADMIN_ON_BASE_MENU);
                yield sendMessageFactory.sendMessageAdminMenu(chatId.toString(), language);
            }
            default -> unsupported(chatId, language);
        };
    }

    private boolean isValidPassword(String password, User user) {
        return passwordEncoder.matches(password, user.getPassword());
    }

    private SendMessage unsupported(Long chatID, Language language) {
        User user = getUserByChatId(chatID);
        Role role = user.getRole();
        final var state = role.equals(Role.USER) ? USER_ON_BASE_MENU : ADMIN_ON_BASE_MENU;
        updateUserState(user, state);
        return role.equals(Role.USER)
                ? sendMessageFactory.sendMessageUserMenu(String.valueOf(chatID), language)
                : sendMessageFactory.sendMessageAdminMenu(String.valueOf(chatID), language);
    }

    private void updateUserState(User user, String state) {
        user.setState(state);
        userService.updateUser(user);
    }

    private User getUserByChatId(Long chatId) {
        return userService.findByChatId(chatId)
                .orElseThrow(() -> new IllegalStateException("User not found for chat ID: " + chatId));
    }
}
