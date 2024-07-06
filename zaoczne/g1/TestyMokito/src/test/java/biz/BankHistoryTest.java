package biz;

import biz.BankHistory;
import db.dao.DAO;
import model.Operation;
import model.User;
import model.operations.LogIn;
import model.operations.LogOut;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BankHistoryTest {

    @Mock
    DAO mockDao;

    BankHistory target;

    @BeforeEach
    void setUp() {
        target = new BankHistory(mockDao);
    }

    // Test 1: Udane logowanie
    @Test
    void logLoginSuccess() throws SQLException {
        // GIVEN
        User user = mock(User.class);

        // WHEN
        target.logLoginSuccess(user);

        // THEN
        verify(mockDao, times(1)).logOperation(any(LogIn.class), eq(true));
    }

    // Test 2: Nieudane logowanie
    @Test
    void logLoginFailure() throws SQLException {
        // GIVEN
        User user = mock(User.class);
        String info = "Invalid credentials";

        // WHEN
        target.logLoginFailure(user, info);

        // THEN
        verify(mockDao, times(1)).logOperation(any(LogIn.class), eq(false));
    }

    // Test 3: Udane wylogowanie
    @Test
    void logLogOut() throws SQLException {
        // GIVEN
        User user = mock(User.class);

        // WHEN
        target.logLogOut(user);

        // THEN
        verify(mockDao, times(1)).logOperation(any(LogOut.class), eq(true));
    }

    // Test 4: Logowanie operacji
    @Test
    void logOperation() throws SQLException {
        // GIVEN
        Operation operation = mock(Operation.class);
        boolean success = true;

        // WHEN
        target.logOperation(operation, success);

        // THEN
        verify(mockDao, times(1)).logOperation(operation, success);
    }
}
