package biz;

import db.dao.DAO;
import model.Account;
import model.User;
import model.operations.Interest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InterestOperatorTest {

    @Mock
    DAO mockDao;

    @Mock
    AccountManager mockAccountManager;

    @Mock
    BankHistory mockBankHistory;

    InterestOperator target;

    @BeforeEach
    void setUp() {
        target = new InterestOperator(mockDao, mockAccountManager);
        target.bankHistory = mockBankHistory;  // Bezpośrednie przypisanie do obiektu
    }

    @Test
    void countInterestForAccount_Success() throws SQLException {
        // GIVEN
        Account account = mock(Account.class);
        User user = mock(User.class);
        double accountAmount = 1000.0;
        double interestFactor = 0.2;
        double interestAmount = accountAmount * interestFactor;
        String description = "Interest ...";

        when(account.getAmmount()).thenReturn(accountAmount);
        when(mockDao.findUserByName("InterestOperator")).thenReturn(user);
        when(mockAccountManager.paymentIn(user, interestAmount, description, account.getId())).thenReturn(true);

        // WHEN
        target.countInterestForAccount(account);

        // THEN
        verify(mockAccountManager, times(1)).paymentIn(user, interestAmount, description, account.getId());
        verify(mockBankHistory, times(1)).logOperation(any(Interest.class), eq(true));
    }
}
