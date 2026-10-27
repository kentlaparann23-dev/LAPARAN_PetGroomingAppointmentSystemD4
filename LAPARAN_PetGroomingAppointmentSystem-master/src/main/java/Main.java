import dao.UserDao;
import ui.Menu;

import java.sql.SQLException;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        UserDao userDao = new UserDao();

        try {
            userDao.createTable();
        } catch (SQLException e) {
            System.out.println("Could not initialize database: " + e.getMessage());
            return;
        }

        try (Scanner scanner = new Scanner(System.in)) {
            new Menu(scanner, userDao).start();
        }
    }
}
