package securemailbox.security;

import securemailbox.entity.User;
import securemailbox.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomUserDetailsServiceTest {

    private UserRepository userRepository;
    private CustomUserDetailsService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new CustomUserDetailsService(userRepository);
    }

    @Test
    void loadUserByUsername_mapsUserToSpringSecurityUserDetails() {
        User user = new User();
        user.setUsername("alice");
        user.setPasswordHash("bcrypt-hash");
        user.setRole(User.Role.ADMIN);
        user.setEnabled(true);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("alice");

        assertThat(details.getUsername()).isEqualTo("alice");
        assertThat(details.getPassword()).isEqualTo("bcrypt-hash");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void loadUserByUsername_mapsDisabledFlagCorrectly() {
        User user = new User();
        user.setUsername("bob");
        user.setPasswordHash("hash");
        user.setRole(User.Role.USER);
        user.setEnabled(false);

        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("bob");

        // Wichtig fuer z.B. deaktivierte/gesperrte Konten: Spring Security
        // verweigert damit automatisch jede weitere Authentifizierung.
        assertThat(details.isEnabled()).isFalse();
    }

    @Test
    void loadUserByUsername_throwsForUnknownUsername() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
