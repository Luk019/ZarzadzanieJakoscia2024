package biz;

import db.dao.DAO;
import db.dao.impl.SQLiteDB;
import model.Account;
import model.Operation;
import model.User;
import model.exceptions.OperationIsNotAllowedException;
import model.exceptions.UserUnnkownOrBadPasswordException;
import model.operations.PaymentIn;
import model.operations.Withdraw;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.verification.VerificationMode;

import java.lang.reflect.Field;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

@ExtendWith(MockitoExtension.class)
class AccountManagerTest {

    AccountManager target;
    @Mock
    DAO mockDao;
    @Mock
    BankHistory mockHistory; // = Mockito.mock(BankHistory.class);
    @Mock
    AuthenticationManager mockAuthManager;
    @Mock
    InterestOperator mockIntOperator;

    @BeforeEach
    void setUp() {
        target = new AccountManager();
        target.dao = mockDao;
        target.history=mockHistory;
        target.auth = mockAuthManager;
        target.interestOperator = mockIntOperator;
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void paymentIn() throws SQLException {
        //GIVEN
        int accId = 13;
        accId=12;
        User user = new User();
        Account a = mock(Account.class) ;
//        = new Account();
//        a.setOwner(user);
//        a.setAmmount(100);
//        a.setId(accId);
        String desc = "Wpłata";
        double amount = 123;
        when(mockDao.findAccountById(eq(accId))).thenReturn(a);
        when(mockDao.updateAccountState(eq(a))).thenReturn(true);
        //WHEN
        boolean result = target.paymentIn(user,amount,desc,accId);
        //THEN
        //System.out.println(target.dao.findAccountById(13));
        //System.out.println(target.dao.findAccountById(1));
        assertTrue(result);
        //Możemy sprawdzić stan konta po operacji
        //assertEquals(100+amount,a.getAmmount());
        verify(a, times(1)).income(amount);
        //Sprawzamy operacje na dao, find account by ID
        verify(mockDao, atMostOnce() ).findAccountById(eq(accId));
        verify(mockDao, atLeastOnce() ).findAccountById(anyInt());
        verify(mockDao, times(1) ).findAccountById(anyInt());
        verify(mockDao, atMostOnce() ).updateAccountState(eq(a));
        verify(mockDao, atLeastOnce() ).updateAccountState(any(Account.class));
        //Sprawdzamy czy zalogowano odpowiednie operacje
        verify(mockHistory, atLeastOnce()).logOperation(any(Operation.class),eq(true));
    }
    //PaymentIn przypadki testowe:
    // user == null, ammount <0, konto nie istnieje, nie udało się zupdatować bazy danych

    @Test
    void nullAccountpaymentIn() throws SQLException {
        //GIVEN
        when(mockDao.findAccountById(anyInt())).thenReturn(null);
        int accId = 13;
        User user = new User();
        String desc = "Wpłata";
        double amount = 123;
        //WHEN
        boolean result = target.paymentIn(user,amount,desc,accId);
        //THEN
        assertFalse(result);
    }

    @Test
    void userNullPaymentIn() throws SQLException {
        // GIVEN
        int accId = 13;
        Account a = mock(Account.class);
        String desc = "Wpłata";
        double amount = 123;

        // WHEN
        boolean result = target.paymentIn(null, amount, desc, accId);

        // THEN
        assertFalse(result);
        verify(mockDao,never()).findAccountById(any(Integer.class));
    }

    @Test
    void negativeAmountPaymentIn() throws SQLException {
        //GIVEN
        int accId = 13;
        User user = new User();
        Account a = mock(Account.class);
        String desc = "Wpłata";
        double amount = -123;

        //WHEN
        boolean result = target.paymentIn(user, amount, desc, accId);

        //THEN
        assertFalse(result);
        verify(mockHistory,never()).logOperation(any(Operation.class),anyBoolean());
    }

    @Test
    void updateAccountStateFailsPaymentIn() throws SQLException {
        //GIVEN
        int accId = 13;
        User user = new User();
        Account a = mock(Account.class);
        String desc = "Wpłata";
        double amount = 123;
        when(mockDao.findAccountById(eq(accId))).thenReturn(a);
        when(a.income(amount)).thenReturn(true); // ustawienie income na zawsze true
        when(mockDao.updateAccountState(eq(a))).thenReturn(false);

        //WHEN
        boolean result = target.paymentIn(user, amount, desc, accId);

        //THEN
        assertFalse(result);
        verify(a, times(1)).income(amount);
        verify(mockDao, times(1)).updateAccountState(eq(a));
        verify(mockHistory, never()).logOperation(any(Operation.class), anyBoolean());
        // Znaleziony był błąd gdy account.income zwracał false to updateAccountState i tak był uruchamiany.
        // Co prowadziło by do rozjechania danych
        verify(a, times(1)).outcome(amount);
        // Dodanie sprawdzenia cofania operacji
    }

    @Test
    void nullAccountPaymentOut() throws SQLException{
        //GIVEN
        when(mockDao.findAccountById(anyInt())).thenReturn(null);
        int accId = 13;
        User user = new User();
        String desc = "Wypłata";
        double amount = 123;

        //WHEN
        assertThrows(OperationIsNotAllowedException.class, () -> {
            target.paymentOut(user, amount, desc, accId);
        });

        //THEN
        verify(mockAuthManager, times(0)).canInvokeOperation(any(Operation.class),eq(user));
        // Oczekujemy, że metoda rzuci wyjątek, ponieważ konto jest null
    }

    @Test
    void userNullPaymentOut() throws SQLException, OperationIsNotAllowedException {
        //GIVEN
        int accId = 13;
        Account a = mock(Account.class);
        String desc = "Wypłata";
        double amount = 123;
        lenient().when(mockDao.findAccountById(eq(accId))).thenReturn(a);

        //WHEN
        assertThrows(OperationIsNotAllowedException.class, () -> {
            target.paymentOut(null, amount, desc, accId);
        });
        //THEN
        verify(mockDao,never()).findAccountById(eq(accId));
    }

    @Test
    void negativeAmountPaymentOut() throws SQLException, OperationIsNotAllowedException {
        //GIVEN
        int accId = 13;
        User user = new User();
        Account a = mock(Account.class);
        String desc = "Wypłata";
        double amount = -123;

        //WHEN
        assertThrows(OperationIsNotAllowedException.class, () -> {
            target.paymentOut(user, amount, desc, accId);
        });

        //THEN
        // Oczekujemy, że metoda rzuci wyjątek, ponieważ kwota jest ujemna
        verify(mockDao, never()).findAccountById(any(Integer.class));
        // Brak sprawdzania czy kwota jest ujemna lub 0
    }

    @Test
    void updateAccountStateFailsPaymentOut() throws SQLException, OperationIsNotAllowedException {
        //GIVEN
        int accId = 13;
        User user = new User();
        Account a = mock(Account.class);
        String desc = "Wypłata";
        double amount = 123;
        when(mockDao.findAccountById(eq(accId))).thenReturn(a);
        when(mockAuthManager.canInvokeOperation(any(Operation.class), eq(user))).thenReturn(true);
        when(a.outcome(amount)).thenReturn(true);
        when(mockDao.updateAccountState(eq(a))).thenReturn(false);

        //WHEN
        boolean result = target.paymentOut(user, amount, desc, accId);

        //THEN
        // Oczekujemy, że metoda zwróci false, ponieważ updateAccountState zwróci false
        assertFalse(result);
        verify(a, times(1)).outcome(amount);
        verify(a, times(1)).income(amount);  // Sprawdzenie czy wystepuuje cofniecie w przypadku gdy operacja na bazie sie nie powiedzie.
        verify(mockDao, times(1)).findAccountById(eq(accId));
        verify(mockDao, times(1)).updateAccountState(eq(a));
    }
    @Test
    void insufficientFundsPaymentOut() throws SQLException, OperationIsNotAllowedException {
        //GIVEN
        int accId = 13;
        User user = new User();
        Account a = mock(Account.class);
        String desc = "Wypłata";
        double amount = 123;
        when(mockDao.findAccountById(eq(accId))).thenReturn(a);
        when(mockAuthManager.canInvokeOperation(any(Operation.class), eq(user))).thenReturn(true);
        when(a.outcome(amount)).thenReturn(false); // Brak wystarczających środków na koncie

        //WHEN
        boolean result = target.paymentOut(user, amount, desc, accId);

        //THEN
        // Oczekujemy, że metoda zwróci false, ponieważ konto nie ma wystarczających środków
        assertFalse(result);
        verify(a, times(1)).outcome(amount);
        verify(a, never()).income(amount); // Cofnięcie nie jest wymagane, bo operacja outcome się nie powiodła
        verify(mockDao, never()).updateAccountState(eq(a));
        verify(mockHistory, never()).logOperation(any(Operation.class), anyBoolean());
    }

    // Sprawdza, czy metoda poprawnie obsługuje przypadek, gdy użytkownik jest null, co jest podstawowym wymogiem walidacji wejściowych.
    @Test
    void userNullInternalPayment() throws SQLException, OperationIsNotAllowedException {
        //GIVEN
        int sourceAccId = 13;
        int destAccId = 14;
        Account sourceAccount = mock(Account.class);
        Account destAccount = mock(Account.class);
        String desc = "Przelew wewnętrzny";
        double amount = 123;
        lenient().when(mockDao.findAccountById(eq(sourceAccId))).thenReturn(sourceAccount);
        lenient().when(mockDao.findAccountById(eq(destAccId))).thenReturn(destAccount);

        //WHEN
        assertThrows(OperationIsNotAllowedException.class, () -> {
            target.internalPayment(null, amount, desc, sourceAccId, destAccId);
        });

        //THEN
        verify(mockAuthManager, never()).canInvokeOperation(any(Operation.class), isNull());
        // Bład brak sprawdzania user is null
    }

    // Zapewnia, że metoda nie pozwala na operacje z ujemną kwotą, co jest ważne dla integralności danych.
    @Test
    void negativeAmountInternalPayment() throws SQLException, OperationIsNotAllowedException {
        //GIVEN
        int sourceAccId = 13;
        int destAccId = 14;
        User user = new User();
        Account sourceAccount = mock(Account.class);
        Account destAccount = mock(Account.class);
        String desc = "Przelew wewnętrzny";
        double amount = -123;
        lenient().when(mockDao.findAccountById(eq(sourceAccId))).thenReturn(sourceAccount);
        lenient().when(mockDao.findAccountById(eq(destAccId))).thenReturn(destAccount);

        //WHEN
        assertThrows(OperationIsNotAllowedException.class, () -> {
            target.internalPayment(user, amount, desc, sourceAccId, destAccId);
        });

        //THEN
        verify(mockDao, never()).updateAccountState(any(Account.class));
        verify(mockHistory, never()).logOperation(any(Operation.class), anyBoolean());
        verify(mockAuthManager, never()).canInvokeOperation(any(Operation.class), eq(user));
        //Blad brak sprawdzania w internalPayment czy kwota nie jest ujemna badz 0
    }


    // Sprawdza, czy metoda poprawnie obsługuje przypadek, gdy konto źródłowe nie istnieje, co może się zdarzyć, jeśli dane wejściowe są błędne.
    @Test
    void sourceAccountNullInternalPayment() throws SQLException, OperationIsNotAllowedException {
        //GIVEN
        int sourceAccId = 13;
        int destAccId = 14;
        User user = new User();
        Account destAccount = mock(Account.class);
        String desc = "Przelew wewnętrzny";
        double amount = 123;
        when(mockDao.findAccountById(eq(sourceAccId))).thenReturn(null);
        lenient().when(mockDao.findAccountById(eq(destAccId))).thenReturn(destAccount);

        //WHEN
        assertThrows(OperationIsNotAllowedException.class, () -> {
            target.internalPayment(user, amount, desc, sourceAccId, destAccId);
        });

        //THEN
        verify(mockDao, times(1)).findAccountById(eq(sourceAccId));
        verify(mockDao, times(1)).findAccountById(eq(destAccId));
        verify(mockDao, never()).updateAccountState(any(Account.class));
        verify(mockHistory, never()).logOperation(any(Operation.class), anyBoolean());
        verify(mockAuthManager, never()).canInvokeOperation(any(Operation.class), eq(user));

        // Błąd wynika z tego, że metoda canInvokeOperation jest wywoływana pomimo braku konta źródłowego.
        // Aby to naprawić, musimy upewnić się, że w metodzie internalPayment sprawdzamy, czy konta są null przed wywołaniem jakichkolwiek operacji, w tym autoryzacji.
    }

    // Analogicznie do poprzedniego testu, sprawdza, czy metoda poprawnie obsługuje brak konta docelowego.
    @Test
    void destAccountNullInternalPayment() throws SQLException, OperationIsNotAllowedException {
        //GIVEN
        int sourceAccId = 13;
        int destAccId = 14;
        User user = new User();
        Account sourceAccount = mock(Account.class);
        String desc = "Przelew wewnętrzny";
        double amount = 123;
        when(mockDao.findAccountById(eq(sourceAccId))).thenReturn(sourceAccount);
        when(mockDao.findAccountById(eq(destAccId))).thenReturn(null);

        //WHEN
        assertThrows(OperationIsNotAllowedException.class, () -> {
            target.internalPayment(user, amount, desc, sourceAccId, destAccId);
        });

        //THEN
        verify(mockDao, times(1)).findAccountById(eq(sourceAccId));
        verify(mockDao, times(1)).findAccountById(eq(destAccId));
        verify(mockAuthManager, never()).canInvokeOperation(any(Operation.class), eq(user));
        verify(mockDao, never()).updateAccountState(any(Account.class));
        verify(mockHistory, never()).logOperation(any(Operation.class), anyBoolean());
        // Błąd wynika z tego, że metoda canInvokeOperation jest wywoływana pomimo braku konta docelowego.
        // Aby to naprawić, musimy upewnić się, że w metodzie internalPayment sprawdzamy, czy konta są null przed wywołaniem jakichkolwiek operacji, w tym autoryzacji.
    }

    // Upewnia się, że metoda odpowiednio obsługuje przypadki, gdy operacja nie jest autoryzowana, co jest kluczowe dla bezpieczeństwa i zgodności z polityką dostępu.
    @Test
    void unauthorizedOperationInternalPayment() throws SQLException, OperationIsNotAllowedException {
        //GIVEN
        int sourceAccId = 13;
        int destAccId = 14;
        User user = new User();
        Account sourceAccount = mock(Account.class);
        Account destAccount = mock(Account.class);
        String desc = "Przelew wewnętrzny";
        double amount = 123;
        when(mockDao.findAccountById(eq(sourceAccId))).thenReturn(sourceAccount);
        when(mockDao.findAccountById(eq(destAccId))).thenReturn(destAccount);
        when(mockAuthManager.canInvokeOperation(any(Operation.class), eq(user))).thenReturn(false);

        //WHEN
        assertThrows(OperationIsNotAllowedException.class, () -> {
            target.internalPayment(user, amount, desc, sourceAccId, destAccId);
        });

        //THEN
        verify(mockAuthManager, times(1)).canInvokeOperation(any(Operation.class), eq(user));
        verify(mockHistory, times(1)).logUnauthorizedOperation(any(Operation.class), eq(false));
        verify(mockDao, never()).updateAccountState(any(Account.class));
        verify(mockHistory, never()).logOperation(any(Operation.class), anyBoolean());
    }

    // Zapewnia, że metoda odpowiednio reaguje na brak wystarczających środków na koncie źródłowym, co jest podstawowym wymogiem walidacji transakcji.
    @Test
    void insufficientFundsInternalPayment() throws SQLException, OperationIsNotAllowedException {
        //GIVEN
        int sourceAccId = 13;
        int destAccId = 14;
        User user = new User();
        Account sourceAccount = mock(Account.class);
        Account destAccount = mock(Account.class);
        String desc = "Przelew wewnętrzny";
        double amount = 123;
        when(mockDao.findAccountById(eq(sourceAccId))).thenReturn(sourceAccount);
        when(mockDao.findAccountById(eq(destAccId))).thenReturn(destAccount);
        when(mockAuthManager.canInvokeOperation(any(Operation.class), eq(user))).thenReturn(true);
        when(sourceAccount.outcome(amount)).thenReturn(false);

        //WHEN
        boolean result = target.internalPayment(user, amount, desc, sourceAccId, destAccId);

        //THEN
        assertFalse(result);
        verify(mockAuthManager, times(1)).canInvokeOperation(any(Operation.class), eq(user));
        verify(sourceAccount, times(1)).outcome(amount);
        verify(destAccount, never()).income(amount);
        verify(mockDao, never()).updateAccountState(any(Account.class));
        verify(mockHistory, times(1)).logOperation(any(Operation.class), eq(false));

        // W przypadku, gdy operacja outcome się nie powiedzie, logOperation powinna być wywoływana tylko raz, a nie dla obu operacji (withdraw i payment). Bład
    }

    // Sprawdza, czy metoda działa poprawnie w scenariuszu sukcesu, w którym wszystkie operacje są poprawnie przeprowadzane.
    @Test
    void successfulInternalPayment() throws SQLException, OperationIsNotAllowedException {
        //GIVEN
        int sourceAccId = 13;
        int destAccId = 14;
        User user = new User();
        Account sourceAccount = mock(Account.class);
        Account destAccount = mock(Account.class);
        String desc = "Przelew wewnętrzny";
        double amount = 123;
        when(mockDao.findAccountById(eq(sourceAccId))).thenReturn(sourceAccount);
        when(mockDao.findAccountById(eq(destAccId))).thenReturn(destAccount);
        when(mockAuthManager.canInvokeOperation(any(Operation.class), eq(user))).thenReturn(true);
        when(sourceAccount.outcome(amount)).thenReturn(true);
        when(destAccount.income(amount)).thenReturn(true);
        when(mockDao.updateAccountState(eq(sourceAccount))).thenReturn(true);
        when(mockDao.updateAccountState(eq(destAccount))).thenReturn(true);

        //WHEN
        boolean result = target.internalPayment(user, amount, desc, sourceAccId, destAccId);

        //THEN
        assertTrue(result);
        verify(mockAuthManager, times(1)).canInvokeOperation(any(Operation.class), eq(user));
        verify(sourceAccount, times(1)).outcome(amount);
        verify(destAccount, times(1)).income(amount);
        verify(mockDao, times(1)).updateAccountState(eq(sourceAccount));
        verify(mockDao, times(1)).updateAccountState(eq(destAccount));
        verify(mockHistory, times(1)).logOperation(argThat(op -> op instanceof Withdraw), eq(true));
        verify(mockHistory, times(1)).logOperation(argThat(op -> op instanceof PaymentIn), eq(true));
    }

    // Ten test powinien upewnić się, że metoda buildBank poprawnie tworzy instancję AccountManager i inicjalizuje wszystkie jej pola (DAO, BankHistory, AuthenticationManager, InterestOperator).
    @Test
    void successfulBuildBank() throws SQLException, ClassNotFoundException {
        // GIVEN
        DAO mockDao = mock(DAO.class);
        BankHistory mockHistory = mock(BankHistory.class);
        AuthenticationManager mockAuthManager;
        mockAuthManager = mock(AuthenticationManager.class);
        InterestOperator mockInterestOperator = mock(InterestOperator.class);

        // Stubowanie metod statycznych
        try (MockedStatic<SQLiteDB> mockedSQLiteDB = mockStatic(SQLiteDB.class)) {
            mockedSQLiteDB.when(SQLiteDB::createDAO).thenReturn(mockDao);

            // WHEN
            AccountManager accountManager = AccountManager.buildBank();

            // THEN
            // Weryfikacja, że instancja została poprawnie utworzona i pola są poprawnie przypisane
            assertNotNull(accountManager);
            assertNotNull(accountManager.dao);
            assertNotNull(accountManager.history);
            assertNotNull(accountManager.auth);
            assertNotNull(accountManager.interestOperator);

            // Weryfikacja, że pola mają odpowiednie typy
            assertEquals(mockDao, accountManager.dao);
            assertEquals(mockHistory.getClass(), accountManager.history.getClass());
            assertEquals(mockAuthManager.getClass(), accountManager.auth.getClass());
            assertEquals(mockInterestOperator.getClass(), accountManager.interestOperator.getClass());
        }
    }


    // Ten test powinien upewnić się, że metoda buildBank poprawnie obsługuje przypadek, gdy rzucane jest SQLException podczas wywołania SQLiteDB.createDAO.
    @Test
    void buildBankHandlesSQLException() throws SQLException, ClassNotFoundException {
        // GIVEN
        try (MockedStatic<SQLiteDB> mockedSQLiteDB = mockStatic(SQLiteDB.class)) {
            // Symulowanie SQLException podczas wywołania createDAO
            mockedSQLiteDB.when(SQLiteDB::createDAO).thenThrow(SQLException.class);

            // WHEN
            AccountManager accountManager = AccountManager.buildBank();

            // THEN
            // Weryfikacja, że metoda buildBank zwraca null w przypadku SQLException
            assertNull(accountManager);
        }
    }


    // Ten test powinien upewnić się, że metoda buildBank poprawnie obsługuje przypadek, gdy rzucane jest ClassNotFoundException podczas wywołania SQLiteDB.createDAO.
    @Test
    void buildBankHandlesClassNotFoundException() throws SQLException, ClassNotFoundException {
        // GIVEN
        try (MockedStatic<SQLiteDB> mockedSQLiteDB = mockStatic(SQLiteDB.class)) {
            // Symulowanie ClassNotFoundException podczas wywołania createDAO
            mockedSQLiteDB.when(SQLiteDB::createDAO).thenThrow(ClassNotFoundException.class);

            // WHEN
            AccountManager accountManager = AccountManager.buildBank();

            // THEN
            // Weryfikacja, że metoda buildBank zwraca null w przypadku ClassNotFoundException
            assertNull(accountManager);
        }
    }


    // Sprawdza, czy pole dao w AccountManager jest przypisane do obiektu zwróconego przez SQLiteDB.createDAO().
    @Test
    void buildBankInitializesDaoCorrectly() throws SQLException, ClassNotFoundException {
        // GIVEN
        DAO mockDao = mock(DAO.class);

        // Stubowanie metod statycznych
        try (MockedStatic<SQLiteDB> mockedSQLiteDB = mockStatic(SQLiteDB.class)) {
            mockedSQLiteDB.when(SQLiteDB::createDAO).thenReturn(mockDao);

            // WHEN
            AccountManager accountManager = AccountManager.buildBank();

            // THEN
            // Weryfikacja, że pole dao jest przypisane do obiektu zwróconego przez SQLiteDB.createDAO()
            assertNotNull(accountManager);
            assertEquals(mockDao, accountManager.dao);
        }
    }



    // Ten test powinien upewnić się, że pole history w AccountManager jest przypisane do nowo utworzonego obiektu BankHistory.
    @Test
    void buildBankInitializesHistoryCorrectly() throws SQLException, ClassNotFoundException {
        // GIVEN
        DAO mockDao = mock(DAO.class);

        // Stubowanie metod statycznych
        MockedStatic<SQLiteDB> mockedSQLiteDB = mockStatic(SQLiteDB.class);
        mockedSQLiteDB.when(SQLiteDB::createDAO).thenReturn(mockDao);

        // WHEN
        AccountManager accountManager = AccountManager.buildBank();

        // THEN
        // Weryfikacja, że pole history jest przypisane do nowo utworzonego obiektu BankHistory
        assertNotNull(accountManager);
        assertNotNull(accountManager.history);
        assertInstanceOf(BankHistory.class, accountManager.history);
        assertEquals(mockDao, accountManager.history.dao); // Sprawdzamy czy history ma poprawnie przypisane DAO

        // Zamykanie mockowania statycznego
        mockedSQLiteDB.close();
    }


    //Sprawdza, czy pole auth w AccountManager jest poprawnie przypisane do utworzonego AuthenticationManager.
    @Test
    void buildBankInitializesAuthCorrectly() throws SQLException, ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        // GIVEN
        DAO mockDao = mock(DAO.class);

        // Stubowanie metod statycznych
        MockedStatic<SQLiteDB> mockedSQLiteDB = mockStatic(SQLiteDB.class);
        mockedSQLiteDB.when(SQLiteDB::createDAO).thenReturn(mockDao);

        // WHEN
        AccountManager accountManager = AccountManager.buildBank();

        // THEN
        // Weryfikacja, że pole auth jest przypisane do nowo utworzonego obiektu AuthenticationManager
        assertNotNull(accountManager);
        assertNotNull(accountManager.auth);
        assertInstanceOf(AuthenticationManager.class, accountManager.auth);

        // Uzyskanie dostępu do prywatnych pól za pomocą refleksji
        Field daoField = AuthenticationManager.class.getDeclaredField("dao");
        daoField.setAccessible(true);
        DAO authDao = (DAO) daoField.get(accountManager.auth);

        Field historyField = AuthenticationManager.class.getDeclaredField("history");
        historyField.setAccessible(true);
        BankHistory authHistory = (BankHistory) historyField.get(accountManager.auth);

        // Weryfikacja, że pola są poprawnie przypisane
        assertEquals(mockDao, authDao); // Sprawdzamy czy auth ma poprawnie przypisane DAO
        assertInstanceOf(BankHistory.class, authHistory); // Sprawdzamy czy auth ma poprawnie przypisane BankHistory

        // Wyłączenie dostępu do pól po ich użyciu
        daoField.setAccessible(false);
        historyField.setAccessible(false);

        // Zamykanie mockowania statycznego
        mockedSQLiteDB.close();
    }


    // Sprawdza, czy pole interestOperator w AccountManager jest poprawnie przypisane do utworzonego InterestOperator.
    @Test
    void buildBankInitializesInterestOperatorCorrectly() throws SQLException, ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        // GIVEN
        DAO mockDao = mock(DAO.class);
        AuthenticationManager mockAuthManager = mock(AuthenticationManager.class);

        // Stubowanie metod statycznych
        MockedStatic<SQLiteDB> mockedSQLiteDB = mockStatic(SQLiteDB.class);
        mockedSQLiteDB.when(SQLiteDB::createDAO).thenReturn(mockDao);

        // WHEN
        AccountManager accountManager = AccountManager.buildBank();

        // THEN
        // Weryfikacja, że pole interestOperator jest przypisane do nowo utworzonego obiektu InterestOperator
        assertNotNull(accountManager);
        assertNotNull(accountManager.interestOperator);
        assertInstanceOf(InterestOperator.class, accountManager.interestOperator);

        // Uzyskanie dostępu do prywatnych pól za pomocą refleksji
        Field daoField = InterestOperator.class.getDeclaredField("dao");
        daoField.setAccessible(true);
        DAO interestOperatorDao = (DAO) daoField.get(accountManager.interestOperator);

        Field accountManagerField = InterestOperator.class.getDeclaredField("accountManager");
        accountManagerField.setAccessible(true);
        AccountManager interestOperatorAccountManager = (AccountManager) accountManagerField.get(accountManager.interestOperator);

        // Weryfikacja, że pola są poprawnie przypisane
        assertEquals(mockDao, interestOperatorDao); // Sprawdzamy czy interestOperator ma poprawnie przypisane DAO
        assertEquals(accountManager, interestOperatorAccountManager); // Sprawdzamy czy interestOperator ma poprawnie przypisanego AccountManagera

        // Wyłączenie dostępu do pól po ich użyciu
        daoField.setAccessible(false);
        accountManagerField.setAccessible(false);

        // Zamykanie mockowania statycznego
        mockedSQLiteDB.close();
    }


    // Sprawdza, czy metoda buildBank zwraca null, gdy rzucane jest SQLException.
    @Test
    void buildBankReturnsNullOnSQLException() throws SQLException, ClassNotFoundException {
        // Stubowanie metod statycznych
        MockedStatic<SQLiteDB> mockedSQLiteDB = mockStatic(SQLiteDB.class);
        mockedSQLiteDB.when(SQLiteDB::createDAO).thenThrow(SQLException.class);

        // WHEN
        AccountManager accountManager = AccountManager.buildBank();

        // THEN
        // Weryfikacja, że metoda buildBank zwraca null w przypadku SQLException
        assertNull(accountManager);

        // Zamykanie mockowania statycznego
        mockedSQLiteDB.close();
    }


    // Sprawdza, czy metoda buildBank zwraca null, gdy rzucane jest ClassNotFoundException
    @Test
    void buildBankReturnsNullOnClassNotFoundException() throws SQLException, ClassNotFoundException {
        // Stubowanie metod statycznych
        MockedStatic<SQLiteDB> mockedSQLiteDB = mockStatic(SQLiteDB.class);
        mockedSQLiteDB.when(SQLiteDB::createDAO).thenThrow(ClassNotFoundException.class);

        // WHEN
        AccountManager accountManager = AccountManager.buildBank();

        // THEN
        // Weryfikacja, że metoda buildBank zwraca null w przypadku ClassNotFoundException
        assertNull(accountManager);

        // Zamykanie mockowania statycznego
        mockedSQLiteDB.close();
    }


    // Lista testów dla metody logIn
    // Test 1: Udane logowanie
    @Test
    void logInSuccess() throws UserUnnkownOrBadPasswordException, SQLException {
        // GIVEN
        AuthenticationManager mockAuth = mock(AuthenticationManager.class);
        AccountManager accountManager = new AccountManager();
        accountManager.auth = mockAuth;

        String userName = "testUser";
        char[] password = "testPassword".toCharArray();
        User mockUser = mock(User.class);

        when(mockAuth.logIn(userName, password)).thenReturn(mockUser);

        // WHEN
        boolean result = accountManager.logIn(userName, password);

        // THEN
        assertTrue(result);
        assertEquals(mockUser, accountManager.loggedUser);
        verify(mockAuth, times(1)).logIn(userName, password);
    }

    // Test 2: Nieudane logowanie
    @Test
    void logInFailure() throws UserUnnkownOrBadPasswordException, SQLException {
        // GIVEN
        AuthenticationManager mockAuth = mock(AuthenticationManager.class);
        AccountManager accountManager = new AccountManager();
        accountManager.auth = mockAuth;

        String userName = "testUser";
        char[] password = "testPassword".toCharArray();

        when(mockAuth.logIn(userName, password)).thenReturn(null);

        // WHEN
        boolean result = accountManager.logIn(userName, password);

        // THEN
        assertFalse(result);
        assertNull(accountManager.loggedUser);
        verify(mockAuth, times(1)).logIn(userName, password);
    }

    // Test 3: Rzucenie UserUnnkownOrBadPasswordException
    @Test
    void logInThrowsUserUnknownOrBadPasswordException() throws UserUnnkownOrBadPasswordException, SQLException {
        // GIVEN
        AuthenticationManager mockAuth = mock(AuthenticationManager.class);
        AccountManager accountManager = new AccountManager();
        accountManager.auth = mockAuth;

        String userName = "testUser";
        char[] password = "testPassword".toCharArray();

        when(mockAuth.logIn(userName, password)).thenThrow(UserUnnkownOrBadPasswordException.class);

        // WHEN
        assertThrows(UserUnnkownOrBadPasswordException.class, () -> {
            accountManager.logIn(userName, password);
        });

        // THEN
        assertNull(accountManager.loggedUser);
    }

    // Test 4: Rzucenie SQLException
    @Test
    void logInThrowsSQLException() throws UserUnnkownOrBadPasswordException, SQLException {
        // GIVEN
        AuthenticationManager mockAuth = mock(AuthenticationManager.class);
        AccountManager accountManager = new AccountManager();
        accountManager.auth = mockAuth;

        String userName = "testUser";
        char[] password = "testPassword".toCharArray();

        when(mockAuth.logIn(userName, password)).thenThrow(SQLException.class);

        // WHEN
        assertThrows(SQLException.class, () -> {
            accountManager.logIn(userName, password);
        });

        // THEN
        assertNull(accountManager.loggedUser);
    }

    // Lista testów dla metody logOut
    // Test 1: Udane wylogowanie
    @Test
    void logOutSuccess() throws SQLException {
        // GIVEN
        AuthenticationManager mockAuth = mock(AuthenticationManager.class);
        AccountManager accountManager = new AccountManager();
        accountManager.auth = mockAuth;
        User user = new User();
        accountManager.loggedUser = user;

        when(mockAuth.logOut(user)).thenReturn(true);

        // WHEN
        boolean result = accountManager.logOut(user);

        // THEN
        assertTrue(result);
        assertNull(accountManager.loggedUser);
        verify(mockAuth, times(1)).logOut(user);
    }

    // Test 2: Nieudane wylogowanie
    @Test
    void logOutFailure() throws SQLException {
        // GIVEN
        AuthenticationManager mockAuth = mock(AuthenticationManager.class);
        AccountManager accountManager = new AccountManager();
        accountManager.auth = mockAuth;
        User user = new User();
        accountManager.loggedUser = user;

        when(mockAuth.logOut(user)).thenReturn(false);

        // WHEN
        boolean result = accountManager.logOut(user);

        // THEN
        assertFalse(result);
        assertEquals(user, accountManager.loggedUser);
        verify(mockAuth, times(1)).logOut(user);
    }

    // Test 3: Rzucenie SQLException
    @Test
    void logOutThrowsSQLException() throws SQLException {
        // GIVEN
        AuthenticationManager mockAuth = mock(AuthenticationManager.class);
        AccountManager accountManager = new AccountManager();
        accountManager.auth = mockAuth;
        User user = new User();
        accountManager.loggedUser = user;

        when(mockAuth.logOut(user)).thenThrow(SQLException.class);

        // WHEN & THEN
        assertThrows(SQLException.class, () -> {
            accountManager.logOut(user);
        });

        // Weryfikacja, że loggedUser pozostało niezmienione (user)
        assertEquals(user, accountManager.loggedUser);
    }

    // Lista testów dla metody getLoggedUser
    // Test 1: Zwracanie zalogowanego użytkownika
    @Test
    void getLoggedUserReturnsLoggedUser() {
        // GIVEN
        AccountManager accountManager = new AccountManager();
        User user = new User();
        accountManager.loggedUser = user;

        // WHEN
        User result = accountManager.getLoggedUser();

        // THEN
        assertEquals(user, result);
    }

    // Test 2: Zwracanie null, gdy brak zalogowanego użytkownika
    @Test
    void getLoggedUserReturnsNullWhenNoUserIsLogged() {
        // GIVEN
        AccountManager accountManager = new AccountManager();

        // WHEN
        User result = accountManager.getLoggedUser();

        // THEN
        assertNull(result);
    }
}