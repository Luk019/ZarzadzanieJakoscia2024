package biz;

import db.dao.DAO;
import db.dao.impl.DAOImpl;
import db.dao.impl.SQLiteDB;
import model.Account;
import model.Operation;
import model.User;
import model.exceptions.OperationIsNotAllowedException;
import model.exceptions.UserUnnkownOrBadPasswordException;
import model.operations.PaymentIn;
import model.operations.Withdraw;

import java.sql.SQLException;

/**
 * Created by Krzysztof Podlaski on 04.03.2018.
 */
public class AccountManager {
    DAO dao;
    BankHistory history;
    AuthenticationManager auth;
    InterestOperator interestOperator;
    User loggedUser=null;

    public boolean paymentIn(User user, double ammount, String description, int accountId) throws SQLException {
        if ((user == null) || (ammount <= 0)){
            return false;
        }
        Account account = dao.findAccountById(accountId);
        if (account == null) {return false;}
        Operation operation = new PaymentIn(user, ammount,description, account);
        boolean success = account.income(ammount);;
        if (success) {
            success = dao.updateAccountState(account);
            if(!success){
                account.outcome(ammount);
                return false;
            }
        }
        history.logOperation(operation, success);
        return success;
    }

    public boolean paymentOut(User user, double ammount, String description, int accountId) throws OperationIsNotAllowedException, SQLException {
        if(user == null){
            throw new OperationIsNotAllowedException("User is null");
        }
        if(ammount <= 0){
            throw new OperationIsNotAllowedException("Ammount should be greater than 0");
        }
        Account account = dao.findAccountById(accountId);
        if (account == null) {throw new OperationIsNotAllowedException("Account in null");}
        Operation operation = new Withdraw(user, ammount,description, account);
        boolean success = auth.canInvokeOperation(operation,user );
        if (!success){
            history.logUnauthorizedOperation(operation, success);
            throw new OperationIsNotAllowedException("Unauthorized operation");
        }
        success = account.outcome(ammount);
        if(success){
            success = dao.updateAccountState(account);
            if(!success){
                account.income(ammount);
                return false;
            }
        }
        history.logOperation(operation, success);
        return success;
    }

    public boolean internalPayment(User user, double ammount, String description, int sourceAccountId, int destAccountId) throws OperationIsNotAllowedException, SQLException {
        // Dodanie sprawdzenia czy user nie jest null
        // Aktualizacja o kwotę wieksza od zera
        if ( ammount <= 0) {
            throw new OperationIsNotAllowedException("Invalid user or amount");
        }
        Account sourceAccount = dao.findAccountById(sourceAccountId);
        Account destAccount = dao.findAccountById(destAccountId);
        //Dodanie sprawdzenia czy konto zrodłowe nie jest null
        //Dodanie sprawdzenia czy konto docelowe nie jest null
        if (sourceAccount == null || destAccount == null) {
            throw new OperationIsNotAllowedException("Account not found");
        }
        Operation withdraw = new Withdraw(user, ammount,description, sourceAccount);
        Operation payment = new PaymentIn(user, ammount,description, destAccount);
        boolean success = auth.canInvokeOperation(withdraw,user );
        if (!success){
            history.logUnauthorizedOperation(withdraw, success);
            throw new OperationIsNotAllowedException("Unauthorized operation");
        }
        // Zmienimy logikę, aby logOperation była wywoływana tylko raz w przypadku niepowodzenia operacji outcome.
        success = sourceAccount.outcome(ammount);
        if (success) {
            success = destAccount.income(ammount);
            if (success) {
                success = dao.updateAccountState(sourceAccount);
                if (success) {
                    success = dao.updateAccountState(destAccount);
                }
            }
        }
        history.logOperation(withdraw, success);
        if (success) {
            history.logOperation(payment, success);
        }
        return success;
    }

    public static AccountManager buildBank() {
        try {
            DAO dao = SQLiteDB.createDAO();
            BankHistory history = new BankHistory(dao);
            AuthenticationManager am = new AuthenticationManager(dao, history);
            AccountManager aManager = new AccountManager();
            InterestOperator io = new InterestOperator(dao, aManager);
            aManager.dao = dao;
            aManager.auth = am;
            aManager.history = history;
            aManager.interestOperator = io;
            return aManager;
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean logIn(String userName, char[] password) throws UserUnnkownOrBadPasswordException, SQLException {
        loggedUser =  auth.logIn(userName, password);
        return loggedUser!=null;
    }

    public boolean logOut(User user) throws SQLException {
        if (auth.logOut(user)) {
            loggedUser = null;
            return true;
        }
        return false;
    }

    public User getLoggedUser() {
        return loggedUser;
    }
}
