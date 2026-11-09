package io.github.michalmszczepanski.project22_card_game_1.web;

import io.github.michalmszczepanski.project22_card_game_1.user.InvalidRegistrationException;
import io.github.michalmszczepanski.project22_card_game_1.user.UserService;
import io.github.michalmszczepanski.project22_card_game_1.user.UsernameTakenException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final UserService userService;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;

    public AuthController(UserService userService, ObjectProvider<ClientRegistrationRepository> clientRegistrations) {
        this.userService = userService;
        this.clientRegistrations = clientRegistrations;
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("googleEnabled", clientRegistrations.getIfAvailable() != null);
        return "login";
    }

    @GetMapping("/register")
    public String registerForm() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username,
                            @RequestParam String password,
                            RedirectAttributes redirectAttributes) {
        try {
            userService.register(username, password);
            redirectAttributes.addFlashAttribute("info", "Konto utworzone, możesz się zalogować.");
            return "redirect:/login";
        } catch (UsernameTakenException | InvalidRegistrationException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/register";
        }
    }
}
