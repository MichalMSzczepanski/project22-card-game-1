package io.github.michalmszczepanski.project22_card_game_1.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Backs only the {@code /admin/**} filter chain — never mixed with player accounts. */
@Service
@RequiredArgsConstructor
public class OperatorUserDetailsService implements UserDetailsService {

    private final OperatorRepository operatorRepository;

    @Override
    public UserDetails loadUserByUsername(String username) {
        Operator operator = operatorRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));
        return User.builder()
                .username(operator.getUsername())
                .password(operator.getPasswordHash())
                .roles("OPERATOR")
                .build();
    }
}
