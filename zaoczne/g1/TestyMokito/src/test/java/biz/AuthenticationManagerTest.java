package biz;

import db.dao.DAO;
import model.Operation;
import model.Password;
import model.Role;
import model.User;
import model.exceptions.UserUnnkownOrBadPasswordException;
import model.operations.OperationType;
import model.operations.Withdraw;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthenticationManagerTest {

    AuthenticationManager target;

    @Mock
    DAO mockDao;

    @Mock
    BankHistory mockHistory;

    @BeforeEach
    void setUp() {
        target = new AuthenticationManager(mockDao, mockHistory);
    }

    // Test 1: Udane logowanie
    @Test
    void logIn() throws UserUnnkownOrBadPasswordException, SQLException {
        // GIVEN
        int userId = 13;
        User user = mock(User.class);
        Password passwd = new Password();
        passwd.setUserId(userId);
        String login = "testUser";
        char[] password = "testPassword".toCharArray();
        char[] passwordCopy = Arrays.copyOf(password, password.length);
        passwd.setPasswd(AuthenticationManager.hashPassword(password));

        when(mockDao.findUserByName(login)).thenReturn(user);
        when(mockDao.findPasswordForUser(user)).thenReturn(passwd);

        // WHEN
        User result = target.logIn(login, passwordCopy);

        // THEN
        assertEquals(user, result); // Sprawdzenie, czy wynik logowania to zamockowany użytkownik
        verify(mockHistory, times(1)).logLoginSuccess(user); // Sprawdzenie, czy zalogowanie sukcesu zostało wywołane
        verify(mockHistory, never()).logLoginFailure(any(User.class), anyString()); // Sprawdzenie, czy logowanie porażki nie zostało wywołane
    }

    // Test 2: Nieznana nazwa użytkownika
    @Test
    void logInUnknownUser() throws UserUnnkownOrBadPasswordException, SQLException {
        // GIVEN
        String userName = "unknownUser";
        char[] password = "testPassword".toCharArray();

        when(mockDao.findUserByName(userName)).thenReturn(null);

        // WHEN & THEN
        UserUnnkownOrBadPasswordException exception = assertThrows(UserUnnkownOrBadPasswordException.class, () -> {
            target.logIn(userName, password);
        });

        assertEquals("Zła nazwa użytkownika", exception.getMessage());
        verify(mockHistory, times(1)).logLoginFailure(null, "Zła nazwa użytkownika " + userName);
        verify(mockHistory, never()).logLoginSuccess(any(User.class));
        // Bład
        // Expected :Zła nazwa użytkownika
        // Actual   :Bad Password
    }

    // Test 3: Nieprawidłowe hasło
    @Test
    void logInWithBadPassword() throws SQLException {
        // GIVEN
        int userId = 13;
        User user = mock(User.class);
        Password passwd = new Password();
        passwd.setUserId(userId);
        String login = "testUser";
        char[] correctPassword = "testPassword".toCharArray();
        char[] incorrectPassword = "anotherPassword".toCharArray();
        char[] incorrectPasswordCopy = Arrays.copyOf(incorrectPassword, incorrectPassword.length);
        passwd.setPasswd(AuthenticationManager.hashPassword(correctPassword));

        when(mockDao.findUserByName(login)).thenReturn(user);
        when(mockDao.findPasswordForUser(user)).thenReturn(passwd);

        // WHEN
        UserUnnkownOrBadPasswordException thrown = assertThrows(UserUnnkownOrBadPasswordException.class, () -> {
            target.logIn(login, incorrectPasswordCopy);
        });

        // THEN
        assertEquals("Bad Password", thrown.getMessage()); // Sprawdzenie poprawności wiadomości wyjątku
        verify(mockHistory, times(1)).logLoginFailure(user, "Bad Password"); // Sprawdzenie, czy zalogowanie nieudanego logowania zostało wywołane
        verify(mockHistory, never()).logLoginSuccess(any(User.class)); // Sprawdzenie, czy logowanie sukcesu nie zostało wywołane
    }

    // Test 4: Rzucenie SQLException
    @Test
    void logInThrowsSQLException() throws UserUnnkownOrBadPasswordException, SQLException {
        // GIVEN
        String userName = "testUser";
        char[] password = "testPassword".toCharArray();

        when(mockDao.findUserByName(userName)).thenThrow(SQLException.class);

        // WHEN & THEN
        assertThrows(SQLException.class, () -> {
            target.logIn(userName, password);
        });

        // Weryfikacja, że żadna operacja logowania nie jest logowana
        verify(mockHistory, never()).logLoginSuccess(any(User.class));
        verify(mockHistory, never()).logLoginFailure(any(User.class), anyString());
    }

    // Metoda logOut
    // Test 1: Udane wylogowanie
    @Test
    void logOutSuccess() throws SQLException {
        // GIVEN
        User user = mock(User.class);
        doNothing().when(mockHistory).logLogOut(user);

        // WHEN
        boolean result = target.logOut(user);

        // THEN
        assertTrue(result); // Sprawdzamy, czy metoda zwraca true
        verify(mockHistory, times(1)).logLogOut(user); // Sprawdzamy, czy logowanie wylogowania zostało wywołane raz
    }

    // Test 2: Wyjątek SQLException podczas wylogowania
    @Test
    void logOutThrowsSQLException() throws SQLException {
        // GIVEN
        User user = mock(User.class);
        doThrow(new SQLException()).when(mockHistory).logLogOut(user);

        // WHEN & THEN
        assertThrows(SQLException.class, () -> {
            target.logOut(user);
        });

        verify(mockHistory, times(1)).logLogOut(user); // Sprawdzamy, czy logowanie wylogowania zostało wywołane raz mimo wyjątku
    }

    // Metoda canInvokeOperation
    @Test
    void canInvokeOperation_AdminRole() {
        // GIVEN
        // Przygotowujemy użytkownika z rolą "Admin" i operację dowolnego typu.
        User user = mock(User.class);
        Role role = mock(Role.class);
        Operation operation = mock(Operation.class);

        when(user.getRole()).thenReturn(role);
        when(role.getName()).thenReturn("Admin");

        // WHEN
        // Wywołujemy metodę canInvokeOperation.
        boolean result = target.canInvokeOperation(operation, user);

        // THEN
        // Metoda powinna zwrócić true.
        assertTrue(result);
    }

    @Test
    void canInvokeOperation_PaymentIn() {
        // GIVEN
        // Przygotowujemy użytkownika bez roli "Admin" i operację typu PAYMENT_IN.
        User user = mock(User.class);
        Role role = mock(Role.class);
        Operation operation = mock(Operation.class);

        when(user.getRole()).thenReturn(role);
        when(role.getName()).thenReturn("User");
        when(operation.getType()).thenReturn(OperationType.PAYMENT_IN);

        // WHEN
        // Wywołujemy metodę canInvokeOperation.
        boolean result = target.canInvokeOperation(operation, user);

        // THEN
        // Metoda powinna zwrócić true.
        assertTrue(result);
    }

    @Test
    void canInvokeOperation_Withdraw_UserIsOwner() {
        // GIVEN
        // Przygotowujemy użytkownika bez roli "Admin" i operację typu WITHDRAW, gdzie użytkownik jest właścicielem operacji.
        User user = mock(User.class);
        Role role = mock(Role.class);
        Withdraw withdrawOperation = mock(Withdraw.class);

        when(user.getRole()).thenReturn(role);
        when(role.getName()).thenReturn("User");
        when(user.getId()).thenReturn(1);
        when(withdrawOperation.getType()).thenReturn(OperationType.WITHDRAW);
        when(withdrawOperation.getUser()).thenReturn(user);

        // WHEN
        // Wywołujemy metodę canInvokeOperation.
        boolean result = target.canInvokeOperation(withdrawOperation, user);

        // THEN
        // Metoda powinna zwrócić true.
        assertTrue(result);
    }

    @Test
    void canInvokeOperation_Withdraw_UserIsNotOwner() {
        // GIVEN
        // Przygotowujemy użytkownika bez roli "Admin" i operację typu WITHDRAW, gdzie użytkownik nie jest właścicielem operacji.
        User user = mock(User.class);
        Role role = mock(Role.class);
        User otherUser = mock(User.class);
        Withdraw withdrawOperation = mock(Withdraw.class);

        when(user.getRole()).thenReturn(role);
        when(role.getName()).thenReturn("User");
        when(user.getId()).thenReturn(1);
        when(otherUser.getId()).thenReturn(2);
        when(withdrawOperation.getType()).thenReturn(OperationType.WITHDRAW);
        when(withdrawOperation.getUser()).thenReturn(otherUser);

        // WHEN
        // Wywołujemy metodę canInvokeOperation.
        boolean result = target.canInvokeOperation(withdrawOperation, user);

        // THEN
        // Metoda powinna zwrócić false.
        assertFalse(result);
    }

    @Test
    void canInvokeOperation_OtherOperation() {
        // GIVEN
        // Przygotowujemy użytkownika bez roli "Admin" i operację innego typu.
        User user = mock(User.class);
        Role role = mock(Role.class);
        Operation operation = mock(Operation.class);

        when(user.getRole()).thenReturn(role);
        when(role.getName()).thenReturn("User");
        when(operation.getType()).thenReturn(OperationType.INTEREST); // Any other operation type

        // WHEN
        // Wywołujemy metodę canInvokeOperation.
        boolean result = target.canInvokeOperation(operation, user);

        // THEN
        // Metoda powinna zwrócić false.
        assertFalse(result);
    }
}
