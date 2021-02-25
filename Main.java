package banking;

import org.sqlite.SQLiteDataSource;

import java.sql.*;
import java.util.Random;
import java.util.Scanner;

public class Main {
    public static boolean exited = false;
    public static String url;

    public static void main(String[] args) {
        url = "jdbc:sqlite:" + args[1];
        AccountManager.init();

        do {
            AccountManager.printMenu();
            String MenuNumber = handleInput(InputType.menu);

            if (AccountManager.isLoggedIn()) {
                switch (MenuNumber) {
                    case "1":
                        AccountManager.balance();
                        break;
                    case "2":
                        System.out.println("Enter Income:");
                        AccountManager.addIncome(Integer.parseInt(handleInput(InputType.income)));
                        break;
                    case "3":
                        System.out.println("Transfer\n" + "Enter card number:");
                        AccountManager.doTransfer(handleInput(InputType.number));
                        break;
                    case "4":
                        AccountManager.closeAccount();
                        break;
                    case "5":
                        AccountManager.logOut();
                        break;
                    case "0":
                        exited = true;
                }
            } else {
                switch (MenuNumber) {
                    case "1":
                        AccountManager.createAccount();
                        break;
                    case "2":
                        System.out.println("Enter your card number:");
                        String cdn = handleInput(InputType.number);
                        System.out.println("Enter your PIN:");
                        String curPin = handleInput(InputType.number);
                        AccountManager.logIn(cdn,curPin);
                        break;
                    case "0":
                        System.out.println("Bye!");
                        exited = true;
                }
            }
        } while (!exited);
    }

    public static String handleInput(InputType type) {
        Scanner sc = new Scanner(System.in);
        String str = sc.nextLine();
        switch (type) {
            case menu:
                while (true) {
                    if (str.matches("[123450]")){
                        return str;
                    }
                    System.out.println("Invalid Option");
                    str = sc.nextLine();
                }
            case number:
                while (true) {
                    if (str.matches("\\d+")){
                        return str;
                    }
                    System.out.println("Invalid Option");
                    str = sc.nextLine();
                }
            case income:
                while (true) {
                    if (str.matches("-?\\d+")){
                        return str;
                    }
                    System.out.println("Invalid Option");
                    str = sc.nextLine();
                }
            default:
                return str;
        }

    }
}

enum InputType {
    menu,number,income
}

class AccountManager {

    private static Account current;
    private static boolean loggedIn = false;
    private static final SQLiteDataSource dataSource = new SQLiteDataSource();

    public static void init(){
        dataSource.setUrl(Main.url);


        try (Connection con = dataSource.getConnection()) {
            try (Statement statement = con.createStatement()) {

                statement.executeUpdate("CREATE TABLE IF NOT EXISTS card(" +
                        "id INTEGER PRIMARY KEY," +
                        "number TEXT NOT NULL," +
                        "pin TEXT NOT NULL," +
                        "balance INTEGER DEFAULT 0);"
                );
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            } finally {
                if (con != null) {
                    try {
                        con.close();
                    } catch (SQLException e) {
                        System.out.println(e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public static boolean isLoggedIn() {
        return loggedIn;
    }

    public static void balance(){
        current.printBalance();
    }

    public static void logOut() {
        loggedIn = false;
        current = null;
        System.out.println("You have successfully logged out!");
    }

    public static void logIn(String cdn,String curPin) {

        try (Connection con = dataSource.getConnection()) {
            try (Statement statement = con.createStatement()) {
                try (ResultSet account = statement.executeQuery("SELECT * FROM card WHERE number = '" + cdn + "' AND pin = '" + curPin + "';")) {
                    if (account.next()) {

                        current = new Account(account.getInt("id"), account.getString("number"),account.getString("pin"),false);
                        current.setBalance(account.getInt("balance"));
                        loggedIn = true;

                        System.out.println("You have successfully logged in!");
                    } else {
                        System.out.println("Wrong card number or PIN!");
                    }
                } catch (SQLException e) {
                    System.out.println(e.getMessage());
                }
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            } finally {
                if (con != null) {
                    try {
                        con.close();
                    } catch (SQLException e) {
                        System.out.println(e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

    }

    public static void createAccount() {
        String cdn = newCardNumber();
        String cp = createPin();
        if (cdn != null) {
            current = new Account(0, cdn, cp, true);


            try (Connection con = dataSource.getConnection()) {
                try (Statement statement = con.createStatement()) {

                    statement.executeUpdate("INSERT INTO card (number,pin,balance) VALUES " +
                            current.createValuesString() + ";"
                    );

                } catch (SQLException e) {
                    System.out.println(e.getMessage());
                } finally {
                    if (con != null) {
                        try {
                            con.close();
                        } catch (SQLException e) {
                            System.out.println(e.getMessage());
                        }
                    }
                }
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        } else {
            System.out.println("Please retry, contact Administrator, or solve the Problem yourself.");
        }
        current = null;
    }

    public static void addIncome(int income) {
        current.addToBalance(income);
        saveAccountBalance(current);
        System.out.println("Income was added.");
    }

    public static void doTransfer(String transNumber) {
        if (current.getCardNumber().equals(transNumber)) {
            System.out.println("You can't transfer money to the same account!");
        } else if (lunAlgMistake(transNumber)) {
            System.out.println("Probably you made a mistake in the card number. Please try again!");
        } else {

            try (Connection con = dataSource.getConnection()) {
                String insert = "SELECT number FROM card WHERE number = ?";
                try (PreparedStatement preparedStatement = con.prepareStatement(insert)) {
                    preparedStatement.setString(1, transNumber);
                    try (ResultSet account = preparedStatement.executeQuery()) {

                        if (account.next()){
                            System.out.println("Enter how much money you want to transfer:");
                            int money = Integer.parseInt(Main.handleInput(InputType.number));
                            if (money > current.getBalance()) {
                                System.out.println("Not enough money!");
                            } else {
                                doTransaction(transNumber,money, con);
                            }
                        } else {
                            System.out.println("Such a card does not exist.");
                        }


                    } catch (SQLException e) {
                        System.out.println(e.getMessage());
                    }
                } catch (SQLException e) {
                    System.out.println(e.getMessage());
                } finally {
                    if (con != null) {
                        try {
                            con.close();
                        } catch (SQLException e) {
                            System.out.println(e.getMessage());
                        }
                    }
                }
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }


        }
    }

    private static void doTransaction(String cardN, int money, Connection con) throws SQLException {
        Account transferAccount = null;

        try (Statement statement = con.createStatement()) {
            try (ResultSet account = statement.executeQuery("SELECT * FROM card WHERE number = '" + cardN + "';")) {
                if (account.next()) {
                    transferAccount = new Account(account.getInt("id"), account.getString("number"),account.getString("pin"),false);
                    transferAccount.setBalance(account.getInt("balance"));
                } else {
                    System.out.println("Wrong card number or PIN!");
                }
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        if (transferAccount != null) {

            String updateC = "UPDATE card SET balance = ? WHERE id = ?;";
            String updateT = "UPDATE card SET balance = ? WHERE id = ?;";


            con.setAutoCommit(false);
            Savepoint savepoint = con.setSavepoint();
            try (PreparedStatement updateCurrent = con.prepareStatement(updateC);
                 PreparedStatement updateTransfer = con.prepareStatement(updateT)) {

                updateCurrent.setInt(1,current.getBalance()- money);
                updateCurrent.setInt(2,current.getId());
                updateCurrent.executeUpdate();

                updateTransfer.setInt(1,transferAccount.getBalance() + money);
                updateTransfer.setInt(2,transferAccount.getId());
                updateTransfer.executeUpdate();

                con.commit();
                current.addToBalance(- money);
                System.out.println("Success!");

            } catch (SQLException e) {
                try {
                    System.err.println("Transfer failed.");
                    System.err.print("Transaction is being rolled back.");
                    System.out.println();
                    con.rollback(savepoint);
                } catch (SQLException exception) {
                    System.out.println(e.getMessage());
                }
            }
        }
    }

    private static boolean lunAlgMistake(String number){
        StringBuilder sb = new StringBuilder(number);
        char s = sb.charAt(sb.length() - 1);
        int k = s - 48;
        sb.deleteCharAt(sb.length() - 1);
        return k != getChecksum(sb.toString());
    }

    public static void closeAccount() {
        try (Connection con = dataSource.getConnection()) {
            String delete = "DELETE FROM card WHERE id = ?";

            try (PreparedStatement preparedStatement = con.prepareStatement(delete)) {

                preparedStatement.setInt(1,current.getId());
                preparedStatement.executeUpdate();
                loggedIn = false;

            } catch (SQLException e) {
                System.out.println(e.getMessage());
            } finally {
                if (con != null) {
                    try {
                        con.close();
                    } catch (SQLException e) {
                        System.out.println(e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        System.out.println("The account has been closed!");
    }

    private static void saveAccountBalance(Account account) {
        try (Connection con = dataSource.getConnection()) {
            String update = "UPDATE card SET balance = ? WHERE id = ?";

            try (PreparedStatement preparedStatement = con.prepareStatement(update)) {

                preparedStatement.setInt(1,account.getBalance());
                preparedStatement.setInt(2,account.getId());

                preparedStatement.executeUpdate();

            } catch (SQLException e) {
                System.out.println(e.getMessage());
            } finally {
                if (con != null) {
                    try {
                        con.close();
                    } catch (SQLException e) {
                        System.out.println(e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

    }

    private static String newCardNumber() {
        while (true) {
            StringBuilder sb = new StringBuilder("400000");
            Random rnd = new Random();

            for ( int i = 0; i < 9; i++ ) {
                sb.append(rnd.nextInt(10));
            }
            sb.append(getChecksum(sb.toString()));

            try (Connection con = dataSource.getConnection()) {
                try (Statement statement = con.createStatement()) {
                    try (ResultSet account = statement.executeQuery("SELECT number FROM card WHERE number = '" + sb.toString() + "'")) {
                        if (!account.next()) {
                            return sb.toString();
                        }
                    } catch (SQLException e) {
                        System.out.println(e.getMessage());
                        return null;
                    }
                } catch (SQLException e) {
                    System.out.println(e.getMessage());
                    return null;
                } finally {
                    if (con != null) {
                        try {
                            con.close();
                        } catch (SQLException e) {
                            System.out.println(e.getMessage());
                        }
                    }
                }
            } catch (SQLException e) {
                System.out.println(e.getMessage());
                return null;
            }
        }

    }

    private static int getChecksum(String cardNumber) {
        int[] num = new int[cardNumber.length()];
        for ( int i = 0; i < cardNumber.length(); i++ ) {
            num[i] = cardNumber.charAt(i) - 48;
        }

        for ( int i = 0; i < num.length; i++ ) {
            if (i % 2 == 0) {
                num[i] *= 2;
            }
            if (num[i] > 9) {
                num[i] -= 9;
            }
        }
        int sum = 0;
        for ( int i : num ) {
            sum += i;
        }

        return 10 - sum % 10 == 10? 0 : 10 - sum % 10;
    }

    private static String createPin () {
        StringBuilder sb = new StringBuilder();
        Random rnd = new Random();

        for ( int i = 0; i < 4; i++ ) {
            sb.append(rnd.nextInt(10));
        }

        return sb.toString();
    }

    public static void printMenu() {
        System.out.println();
        if (AccountManager.isLoggedIn()) {
            System.out.println("1. Balance");
            System.out.println("2. Add income");
            System.out.println("3. Do transfer");
            System.out.println("4. Close account");
            System.out.println("5. Log out");
        } else  {
            System.out.println("1. Create an account");
            System.out.println("2. Log into account");
        }
        System.out.println("0. Exit");
        System.out.println();
    }
}

class Account {
    private final int id;
    private final String cardNumber;
    private final String pin;
    private int balance = 0;

    public Account(int id, String cardNumber, String pin, boolean newAccount) {
        this.id = id;
        this.cardNumber = cardNumber;
        this.pin = pin;
        if (newAccount) {
            System.out.printf("Your card has been created\n" + "Your card number:\n" + "%s\n" + "Your card PIN:\n" + "%s\n", cardNumber, pin);
        }
    }

    public String createValuesString() {
        return String.format("(%s,%s,%d)",cardNumber,pin,balance);
    }

    public void printBalance () {
        System.out.printf("Balance: %d\n",balance);
    }

    public void setBalance(int balance) {
        this.balance = balance;
    }

    public int getId() {
        return id;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public int getBalance() {
        return balance;
    }

    public void addToBalance(int income) {
        balance += income;
    }
}