package io.github.michalmszczepanski.project22_card_game_1.web;

import io.github.michalmszczepanski.project22_card_game_1.security.CurrentUserResolver;
import io.github.michalmszczepanski.project22_card_game_1.user.AppUser;
import io.github.michalmszczepanski.project22_card_game_1.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** "Moje konto" (polityka prywatności: prawo do usunięcia) — {@code /me/**}, requires login. */
@Controller
@RequiredArgsConstructor
@Slf4j
public class AccountController {

    private final UserService userService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/me/account")
    public String myAccount(Authentication authentication, Model model) {
        model.addAttribute("username", currentUserResolver.resolve(authentication).getUsername());
        return "my-account";
    }

    @PostMapping("/me/account/delete")
    public String deleteAccount(Authentication authentication, HttpServletRequest request, HttpServletResponse response,
                                 RedirectAttributes redirectAttributes) {
        AppUser user = currentUserResolver.resolve(authentication);
        userService.deleteAccount(user);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        redirectAttributes.addFlashAttribute("info", "Konto usunięte.");
        return "redirect:/";
    }
}
