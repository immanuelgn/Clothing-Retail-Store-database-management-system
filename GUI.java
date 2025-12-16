import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.*;

public class GUI extends JFrame {
    // change the DB_USER and DB_PASSWORD
    private static final String DB_URL = "jdbc:oracle:thin:@oracle.cs.torontomu.ca:1521:orcl";
    private static final String DB_USER = "ignanase";
    private static final String DB_PASSWORD = "06255878";

    // Advanced Queries (Q1–Q5)
    private static final String SQL_Q1 = """
            SELECT c.customer_id AS "Customer ID",
                   c.name        AS "Customer",
                   c.vip_tier    AS "Tier"
            FROM   Customer c
            WHERE  EXISTS (
                     SELECT 1 FROM Orders o
                     WHERE  o.customer_id = c.customer_id
                     AND    o.order_type IN ('IN_STORE','PICKUP')
                   )
            AND    NOT EXISTS (
                     SELECT 1 FROM Orders o2
                     WHERE  o2.customer_id = c.customer_id
                     AND    o2.order_type = 'ONLINE'
                   )
            ORDER BY "Customer"
            """;

    private static final String SQL_Q2 = """
            SELECT
                RTRIM(name)  AS "Name",
                RTRIM(email) AS "Email",
                'CUSTOMER'   AS "Type"
            FROM Customer
            UNION ALL
            SELECT
                RTRIM(name)  AS "Name",
                RTRIM(email) AS "Email",
                'EMPLOYEE'   AS "Type"
            FROM Employee
            ORDER BY
                "Type", "Name"
            """;

    private static final String SQL_Q3 = """
            SELECT s.store_id                    AS "Store",
                   COUNT(DISTINCT e.employee_id) AS "Employees",
                   ROUND(SUM(o.order_total),2)   AS "Revenue ($)",
                   ROUND(AVG(o.order_total),2)   AS "Avg Order ($)",
                   ROUND(STDDEV(o.order_total),2) AS "StdDev Order ($)"
            FROM   Orders   o
            JOIN   Employee e ON e.employee_id = o.employee_id
            JOIN   Store    s ON s.store_id    = e.store_id
            WHERE  o.order_type IN ('IN_STORE','PICKUP')
            GROUP  BY s.store_id
            HAVING SUM(o.order_total) > 0
            ORDER  BY "Revenue ($)" DESC, "Store"
            """;

    private static final String SQL_Q4 = """
            WITH spends AS (
              SELECT
                  c.customer_id,
                  c.name                 AS customer_name,
                  NVL(c.vip_tier,'NONE') AS vip_tier,
                  NVL(SUM(o.order_total),0) AS total_spend
              FROM   Customer c
              LEFT JOIN Orders o
                     ON o.customer_id = c.customer_id
              GROUP  BY c.customer_id, c.name, c.vip_tier
            )
            SELECT
                customer_id                       AS "Customer ID",
                customer_name                     AS "Customer",
                vip_tier                          AS "Tier",
                ROUND(total_spend,2)              AS "Total Spend ($)",
                COUNT(*) OVER (PARTITION BY vip_tier)         AS "Tier Size",
                DENSE_RANK() OVER (ORDER BY total_spend DESC) AS "Overall Rank",
                DENSE_RANK() OVER (PARTITION BY vip_tier
                                   ORDER BY total_spend DESC) AS "Rank in Tier"
            FROM spends
            ORDER BY "Overall Rank", "Tier", "Rank in Tier", "Customer"
            """;

    private static final String SQL_Q5 = """
            SELECT s.store_id AS "Store",
                   e.employee_id AS "Emp ID",
                   e.name AS "Employee",
                   ROUND(AVG(o.order_total),2) AS "Emp Avg ($)",
                   ROUND( (SELECT AVG(o2.order_total)
                           FROM Orders o2
                           JOIN Employee e2 ON e2.employee_id = o2.employee_id
                           WHERE e2.store_id = s.store_id
                             AND o2.order_type IN ('IN_STORE','PICKUP')), 2) AS "Store Avg ($)"
            FROM   Orders   o
            JOIN   Employee e ON e.employee_id = o.employee_id
            JOIN   Store    s ON s.store_id    = e.store_id
            WHERE  o.order_type IN ('IN_STORE','PICKUP')
            GROUP  BY s.store_id, e.employee_id, e.name
            HAVING AVG(o.order_total) >= (SELECT AVG(o2.order_total)
                                          FROM Orders o2
                                          JOIN Employee e2 ON e2.employee_id = o2.employee_id
                                          WHERE e2.store_id = s.store_id
                                            AND o2.order_type IN ('IN_STORE','PICKUP'))
            ORDER  BY "Store","Emp Avg ($)" DESC,"Employee"
            """;

    private final JTextArea outputArea;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GUI gui = new GUI();
            gui.setVisible(true);
        });
    }

    public GUI() {
        setTitle("Store DB GUI");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(950, 600);
        setLocationRelativeTo(null);

        setLayout(new BorderLayout());

        // Left panel with buttons
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(0, 1, 5, 5));

        JButton btnDrop = new JButton("Drop Tables");
        JButton btnCreate = new JButton("Create Tables (BCNF)");
        JButton btnPopulate = new JButton("Populate Data");
        JButton btnShowTbls = new JButton("Show Tables");
        JButton btnSearchPK = new JButton("Search");
        JButton btnUpdatePK = new JButton("Update");
        JButton btnDeletePK = new JButton("Delete");

        JButton btnQ1 = new JButton("Q1 Customers IN_STORE/PICKUP only");
        JButton btnQ2 = new JButton("Q2 Customers + Employees");
        JButton btnQ3 = new JButton("Q3 Store Revenue Stats");
        JButton btnQ4 = new JButton("Q4 Rank Customers by Spend");
        JButton btnQ5 = new JButton("Q5 Employees >= Store Avg");

        JButton btnExit = new JButton("Exit");

        buttonPanel.add(btnDrop);
        buttonPanel.add(btnCreate);
        buttonPanel.add(btnPopulate);
        buttonPanel.add(btnShowTbls);
        buttonPanel.add(btnSearchPK);
        buttonPanel.add(btnUpdatePK);
        buttonPanel.add(btnDeletePK);
        buttonPanel.add(btnQ1);
        buttonPanel.add(btnQ2);
        buttonPanel.add(btnQ3);
        buttonPanel.add(btnQ4);
        buttonPanel.add(btnQ5);
        buttonPanel.add(btnExit);

        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(outputArea);

        add(buttonPanel, BorderLayout.WEST);
        add(scrollPane, BorderLayout.CENTER);

        // Hook up actions
        btnDrop.addActionListener(this::handleDropTables);
        btnCreate.addActionListener(this::handleCreateTables);
        btnPopulate.addActionListener(this::handlePopulate);
        btnShowTbls.addActionListener(this::handleShowTables);
        btnSearchPK.addActionListener(this::handleSearchByPK);
        btnUpdatePK.addActionListener(this::handleUpdateByPK);
        btnDeletePK.addActionListener(this::handleDeleteByPK);
        btnQ1.addActionListener(e -> withConnection(
                conn -> runQuery(conn, "Q1. Customers who placed IN_STORE/PICKUP but never ONLINE", SQL_Q1)));
        btnQ2.addActionListener(
                e -> withConnection(conn -> runQuery(conn, "Q2. Combined directory: customers + employees", SQL_Q2)));
        btnQ3.addActionListener(
                e -> withConnection(conn -> runQuery(conn, "Q3. Store revenue statistics (IN_STORE/PICKUP)", SQL_Q3)));
        btnQ4.addActionListener(e -> withConnection(
                conn -> runQuery(conn, "Q4. Rank customers within VIP tier by total spend", SQL_Q4)));
        btnQ5.addActionListener(e -> withConnection(
                conn -> runQuery(conn, "Q5. Employees whose average >= their store's average", SQL_Q5)));
        btnExit.addActionListener(e -> System.exit(0));
    }

    // Connection helper
    private void withConnection(DBAction action) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            appendLine("Connected to Oracle as " + DB_USER);
            action.run(conn);
        } catch (SQLException ex) {
            appendLine("Database error: " + ex.getMessage());
        }
    }

    private interface DBAction {
        void run(Connection conn) throws SQLException;
    }

    // Button handlers
    private void handleDropTables(ActionEvent e) {
        withConnection(conn -> {
            appendLine("\nDropping tables...");
            dropTables(conn);
            appendLine("Done dropping tables.");
        });
    }

    private void handleCreateTables(ActionEvent e) {
        withConnection(conn -> {
            appendLine("\nCreating BCNF tables...");
            createTables(conn);
            appendLine("Done creating tables.");
        });
    }

    private void handlePopulate(ActionEvent e) {
        withConnection(conn -> {
            appendLine("\nPopulating dummy data...");
            populateTables(conn);
            appendLine("Done populating data.");
        });
    }

    private void handleShowTables(ActionEvent e) {
        withConnection(conn -> {
            appendLine("\nUser tables:");
            showTables(conn);
        });
    }

    private void handleSearchByPK(ActionEvent e) {
        withConnection(conn -> {
            String table = JOptionPane.showInputDialog(this,
                    "Enter table name (Store, Customer, Product, Employee, Orders, Inventory, Order_Item, Discounts):",
                    "Search by Primary Key",
                    JOptionPane.QUESTION_MESSAGE);
            if (table == null || table.isBlank())
                return;

            searchByPrimaryKey(conn, table.trim());
        });
    }

    private void handleUpdateByPK(ActionEvent e) {
        withConnection(conn -> {
            String table = JOptionPane.showInputDialog(this,
                    "Enter table name (Store, Customer, Product, Employee, Orders, Inventory, Order_Item, Discounts):",
                    "Update by Primary Key",
                    JOptionPane.QUESTION_MESSAGE);
            if (table == null || table.isBlank())
                return;

            updateByPrimaryKey(conn, table.trim());
        });
    }

    private void handleDeleteByPK(ActionEvent e) {
        withConnection(conn -> {
            String table = JOptionPane.showInputDialog(this,
                    "Enter table name (Store, Customer, Product, Employee, Orders, Inventory, Order_Item, Discounts):",
                    "Delete by Primary Key",
                    JOptionPane.QUESTION_MESSAGE);
            if (table == null || table.isBlank())
                return;

            deleteByPrimaryKey(conn, table.trim());
        });
    }

    // Drop all tables
    private void dropTables(Connection conn) throws SQLException {
        String[] dropStatements = {
                "DROP TABLE Discounts CASCADE CONSTRAINTS",
                "DROP TABLE Order_Item CASCADE CONSTRAINTS",
                "DROP TABLE Inventory CASCADE CONSTRAINTS",
                "DROP TABLE Orders CASCADE CONSTRAINTS",
                "DROP TABLE Employee CASCADE CONSTRAINTS",
                "DROP TABLE Product CASCADE CONSTRAINTS",
                "DROP TABLE Customer CASCADE CONSTRAINTS",
                "DROP TABLE Store CASCADE CONSTRAINTS"
        };

        for (String sql : dropStatements) {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate(sql);
                appendLine("Executed: " + sql);
            } catch (SQLException ex) {
                if (!ex.getMessage().toUpperCase().contains("ORA-00942")) {
                    appendLine("Error executing: " + sql);
                    appendLine("  -> " + ex.getMessage());
                }
            }
        }
    }

    // Create BCNF tables
    private void createTables(Connection conn) throws SQLException {
        String createStore = """
                CREATE TABLE Store (
                    store_id       NUMBER               PRIMARY KEY,
                    address        VARCHAR2(200)        NOT NULL,
                    opened_date    DATE                 DEFAULT SYSDATE NOT NULL,
                    status         VARCHAR2(20)         DEFAULT 'OPEN' NOT NULL,
                    phone          VARCHAR2(20),
                    store_hours    VARCHAR2(100),
                    CONSTRAINT uq_store_phone UNIQUE (phone),
                    CONSTRAINT chk_store_status CHECK (status IN ('OPEN', 'CLOSED', 'RENOVATION'))
                )
                """;

        String createCustomer = """
                CREATE TABLE Customer (
                    customer_id    NUMBER(10)           PRIMARY KEY,
                    name           VARCHAR2(100)        NOT NULL,
                    email          VARCHAR2(200)        NOT NULL,
                    phone          VARCHAR2(20),
                    date_joined    DATE                 DEFAULT SYSDATE NOT NULL,
                    vip_tier       VARCHAR2(20)         DEFAULT 'NONE' NOT NULL,
                    CONSTRAINT uq_customer_email UNIQUE (email),
                    CONSTRAINT chk_customer_viptier CHECK (vip_tier IN ('NONE', 'SILVER', 'GOLD', 'PLATINUM'))
                )
                """;

        String createProduct = """
                CREATE TABLE Product (
                    product_id     NUMBER(10)           PRIMARY KEY,
                    category       VARCHAR2(50)         NOT NULL,
                    base_price     NUMBER(10,2)         NOT NULL,
                    CONSTRAINT chk_product_price_nonneg CHECK (base_price >= 0)
                )
                """;

        String createEmployee = """
                CREATE TABLE Employee (
                    employee_id    NUMBER               PRIMARY KEY,
                    store_id       NUMBER(10)           NOT NULL,
                    name           VARCHAR2(100)        NOT NULL,
                    email          VARCHAR2(100)        UNIQUE,
                    role           VARCHAR2(50),
                    sales_per_week NUMBER(10,2),
                    CONSTRAINT fk_employee_store
                        FOREIGN KEY (store_id)
                        REFERENCES Store(store_id)
                        ON DELETE CASCADE
                )
                """;

        String createOrders = """
                CREATE TABLE Orders (
                    order_id       NUMBER(12)           PRIMARY KEY,
                    customer_id    NUMBER(10)           NOT NULL,
                    employee_id    NUMBER               NOT NULL,
                    order_type     VARCHAR2(20)         DEFAULT 'ONLINE' NOT NULL,
                    order_total    NUMBER(10,2)         NOT NULL,
                    order_date     DATE                 DEFAULT SYSDATE NOT NULL,
                    CONSTRAINT chk_orders_type CHECK (order_type IN ('ONLINE', 'IN_STORE', 'PICKUP')),
                    CONSTRAINT chk_orders_total_nonneg CHECK (order_total >= 0),
                    CONSTRAINT fk_orders_customer
                        FOREIGN KEY (customer_id) REFERENCES Customer(customer_id) ON DELETE CASCADE,
                    CONSTRAINT fk_orders_employee
                        FOREIGN KEY (employee_id) REFERENCES Employee(employee_id) ON DELETE CASCADE
                )
                """;

        String createInventory = """
                CREATE TABLE Inventory (
                    store_id            NUMBER(10)      NOT NULL,
                    product_id          NUMBER(10)      NOT NULL,
                    quantity_available  NUMBER(10)      DEFAULT 0 NOT NULL,
                    last_restocked      DATE,
                    store_price         NUMBER(10,2)    NOT NULL,
                    CONSTRAINT pk_inventory PRIMARY KEY (store_id, product_id),
                    CONSTRAINT chk_inventory_qty_nonneg CHECK (quantity_available >= 0),
                    CONSTRAINT chk_inventory_price_nonneg CHECK (store_price >= 0),
                    CONSTRAINT fk_inventory_store
                        FOREIGN KEY (store_id) REFERENCES Store(store_id) ON DELETE CASCADE,
                    CONSTRAINT fk_inventory_product
                        FOREIGN KEY (product_id) REFERENCES Product(product_id) ON DELETE CASCADE
                )
                """;

        String createOrderItem = """
                CREATE TABLE Order_Item (
                    order_id     NUMBER(12)            NOT NULL,
                    product_id   NUMBER(10)            NOT NULL,
                    quantity     NUMBER(10)            DEFAULT 1 NOT NULL,
                    unit_price   NUMBER(10,2)          NOT NULL,
                    CONSTRAINT pk_order_item PRIMARY KEY (order_id, product_id),
                    CONSTRAINT chk_order_item_qty_pos CHECK (quantity > 0),
                    CONSTRAINT chk_order_item_price_nonneg CHECK (unit_price >= 0),
                    CONSTRAINT fk_order_item_order
                        FOREIGN KEY (order_id) REFERENCES Orders(order_id) ON DELETE CASCADE,
                    CONSTRAINT fk_order_item_product
                        FOREIGN KEY (product_id) REFERENCES Product(product_id) ON DELETE CASCADE
                )
                """;

        String createDiscounts = """
                CREATE TABLE Discounts (
                    discount_id      NUMBER(12)         PRIMARY KEY,
                    vip_tier         VARCHAR2(20)       NOT NULL,
                    start_date       DATE               NOT NULL,
                    end_date         DATE               NOT NULL,
                    discount_amount  NUMBER(5,2)        NOT NULL,
                    order_id         NUMBER(12)         NOT NULL,
                    CONSTRAINT chk_discounts_viptier CHECK (vip_tier IN ('NONE','SILVER','GOLD','PLATINUM')),
                    CONSTRAINT chk_discounts_pct CHECK (discount_amount >= 0 AND discount_amount <= 100),
                    CONSTRAINT chk_discounts_dates CHECK (end_date >= start_date),
                    CONSTRAINT fk_discounts_order
                        FOREIGN KEY (order_id) REFERENCES Orders(order_id) ON DELETE CASCADE
                )
                """;

        String[] ddl = {
                createStore,
                createCustomer,
                createProduct,
                createEmployee,
                createOrders,
                createInventory,
                createOrderItem,
                createDiscounts
        };

        for (String sql : ddl) {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate(sql);
                appendLine("Created table with statement:\n" + sql);
            } catch (SQLException e) {
                appendLine("Error executing DDL:\n" + sql);
                appendLine("  -> " + e.getMessage());
            }
        }
    }

    // Insert sample data into all tables
    private void populateTables(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {

            // STORE
            st.executeUpdate(
                    """
                            INSERT INTO Store (store_id, address, opened_date, status, phone, store_hours)
                            VALUES (1, '100 King St W, Toronto, ON', DATE '2022-03-01', 'OPEN', '416-555-1000', 'Mon-Fri 10:00-21:00')
                            """);
            st.executeUpdate(
                    """
                            INSERT INTO Store (store_id, address, opened_date, status, phone, store_hours)
                            VALUES (2, '200 Queen St W, Toronto, ON', DATE '2023-05-15', 'OPEN', '416-555-2000', 'Mon-Sun 11:00-19:00')
                            """);

            // CUSTOMER
            st.executeUpdate(
                    """
                            INSERT INTO Customer (customer_id, name, email, phone, date_joined, vip_tier)
                            VALUES (1001, 'Immanuel Gnanaseelan',  'immanuel@gmail.com',  '647-234-6228', DATE '2024-01-10', 'SILVER')
                            """);
            st.executeUpdate(
                    """
                            INSERT INTO Customer (customer_id, name, email, phone, date_joined, vip_tier)
                            VALUES (1002, 'Josh Bowler', 'josh@gmail.com', '647-555-0202', DATE '2024-02-05', 'GOLD')
                            """);
            st.executeUpdate("""
                    INSERT INTO Customer (customer_id, name, email, phone, date_joined, vip_tier)
                    VALUES (1003, 'Nehal Goel', 'nehal.goel@yahoo.com', NULL, DATE '2024-03-01', 'NONE')
                    """);

            // PRODUCT
            st.executeUpdate("""
                    INSERT INTO Product (product_id, category, base_price)
                    VALUES (100, 'Tops', 30.00)
                    """);
            st.executeUpdate("""
                    INSERT INTO Product (product_id, category, base_price)
                    VALUES (101, 'Jeans', 60.00)
                    """);
            st.executeUpdate("""
                    INSERT INTO Product (product_id, category, base_price)
                    VALUES (102, 'Accessories', 15.00)
                    """);

            // EMPLOYEE
            st.executeUpdate("""
                    INSERT INTO Employee (employee_id, store_id, name, email, role, sales_per_week)
                    VALUES (5001, 1, 'Mike Brown',  'mike.brown@corp.com',  'Manager',         15000.00)
                    """);
            st.executeUpdate(
                    """
                            INSERT INTO Employee (employee_id, store_id, name, email, role, sales_per_week)
                            VALUES (5002, 1, 'Immanuel Gnanaseelan',   'immanuel.gnanaseelan@corp.com',   'Sales Associate',  8000.00)
                            """);
            st.executeUpdate("""
                    INSERT INTO Employee (employee_id, store_id, name, email, role, sales_per_week)
                    VALUES (5003, 2, 'Mia Johnson',   'mia.johnson@crop.com',   'Sales Associate',  9000.00)
                    """);

            // ORDERS
            st.executeUpdate("""
                    INSERT INTO Orders (order_id, customer_id, employee_id, order_type, order_total, order_date)
                    VALUES (90001, 1001, 5002, 'ONLINE', 70.00, DATE '2024-11-01')
                    """);
            st.executeUpdate("""
                    INSERT INTO Orders (order_id, customer_id, employee_id, order_type, order_total, order_date)
                    VALUES (90002, 1002, 5003, 'IN_STORE', 60.00, DATE '2024-11-02')
                    """);
            st.executeUpdate("""
                    INSERT INTO Orders (order_id, customer_id, employee_id, order_type, order_total, order_date)
                    VALUES (90003, 1001, 5002, 'IN_STORE', 44.00, DATE '2024-11-03')
                    """);

            // INVENTORY
            st.executeUpdate("""
                    INSERT INTO Inventory (store_id, product_id, quantity_available, last_restocked, store_price)
                    VALUES (1, 100, 50, DATE '2024-10-01', 28.00)
                    """);
            st.executeUpdate("""
                    INSERT INTO Inventory (store_id, product_id, quantity_available, last_restocked, store_price)
                    VALUES (1, 101, 35, DATE '2024-10-05', 60.00)
                    """);
            st.executeUpdate("""
                    INSERT INTO Inventory (store_id, product_id, quantity_available, last_restocked, store_price)
                    VALUES (2, 100, 20, DATE '2024-10-03', 30.00)
                    """);
            st.executeUpdate("""
                    INSERT INTO Inventory (store_id, product_id, quantity_available, last_restocked, store_price)
                    VALUES (2, 102, 60, DATE '2024-10-06', 14.00)
                    """);

            // ORDER_ITEM
            st.executeUpdate("""
                    INSERT INTO Order_Item (order_id, product_id, quantity, unit_price)
                    VALUES (90001, 100, 2, 28.00)
                    """);
            st.executeUpdate("""
                    INSERT INTO Order_Item (order_id, product_id, quantity, unit_price)
                    VALUES (90001, 102, 1, 14.00)
                    """);
            st.executeUpdate("""
                    INSERT INTO Order_Item (order_id, product_id, quantity, unit_price)
                    VALUES (90002, 101, 1, 60.00)
                    """);
            st.executeUpdate("""
                    INSERT INTO Order_Item (order_id, product_id, quantity, unit_price)
                    VALUES (90003, 100, 1, 30.00)
                    """);
            st.executeUpdate("""
                    INSERT INTO Order_Item (order_id, product_id, quantity, unit_price)
                    VALUES (90003, 102, 1, 14.00)
                    """);

            // DISCOUNTS
            st.executeUpdate("""
                    INSERT INTO Discounts (discount_id, vip_tier, start_date, end_date, discount_amount, order_id)
                    VALUES (1, 'SILVER', DATE '2024-11-01', DATE '2024-11-30', 10.00, 90001)
                    """);
            st.executeUpdate("""
                    INSERT INTO Discounts (discount_id, vip_tier, start_date, end_date, discount_amount, order_id)
                    VALUES (2, 'GOLD',   DATE '2024-11-01', DATE '2024-11-15', 15.00, 90002)
                    """);
            st.executeUpdate("""
                    INSERT INTO Discounts (discount_id, vip_tier, start_date, end_date, discount_amount, order_id)
                    VALUES (3, 'NONE',   DATE '2024-11-03', DATE '2024-11-30',  0.00, 90003)
                    """);

            appendLine("Dummy data inserted successfully.");
        }
    }

    // Show tables
    private void showTables(Connection conn) throws SQLException {
        String sql = "SELECT table_name FROM user_tables ORDER BY table_name";
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            appendLine("User tables:");
            while (rs.next()) {
                appendLine("  " + rs.getString(1));
            }
        }
        String tableName = ask("Enter a table name to show its rows (or Cancel to skip):");
        if (tableName == null || tableName.isBlank()) {
            return; // user cancelled
        }

        tableName = tableName.trim().toUpperCase();
        // Select all rows from that table and print them
        String query = "SELECT * FROM " + tableName;
        try (Statement st2 = conn.createStatement();
                ResultSet rs2 = st2.executeQuery(query)) {

            ResultSetMetaData md = rs2.getMetaData();
            int cols = md.getColumnCount();

            appendLine("\nRows in " + tableName + ":");
            boolean any = false;

            while (rs2.next()) {
                any = true;
                StringBuilder row = new StringBuilder("  ");
                for (int i = 1; i <= cols; i++) {
                    row.append(md.getColumnName(i))
                            .append("=")
                            .append(rs2.getString(i));
                    if (i < cols)
                        row.append(" | ");
                }
                appendLine(row.toString());
            }

            if (!any) {
                appendLine("  (no rows)");
            }
        } catch (SQLException e) {
            appendLine("Error showing data for " + tableName + ": " + e.getMessage());
        }
    }

    // SEARCH by the Primary Key
    private void searchByPrimaryKey(Connection conn, String tableName) throws SQLException {
        String t = tableName.toUpperCase();
        String sql;
        PreparedStatement ps;

        switch (t) {
            case "STORE" -> {
                String id = ask("Enter store_id:");
                if (id == null)
                    return;
                sql = "SELECT * FROM Store WHERE store_id = ?";
                ps = conn.prepareStatement(sql);
                ps.setInt(1, Integer.parseInt(id));
            }
            case "CUSTOMER" -> {
                String id = ask("Enter customer_id:");
                if (id == null)
                    return;
                sql = "SELECT * FROM Customer WHERE customer_id = ?";
                ps = conn.prepareStatement(sql);
                ps.setInt(1, Integer.parseInt(id));
            }
            case "PRODUCT" -> {
                String id = ask("Enter product_id:");
                if (id == null)
                    return;
                sql = "SELECT * FROM Product WHERE product_id = ?";
                ps = conn.prepareStatement(sql);
                ps.setInt(1, Integer.parseInt(id));
            }
            case "EMPLOYEE" -> {
                String id = ask("Enter employee_id:");
                if (id == null)
                    return;
                sql = "SELECT * FROM Employee WHERE employee_id = ?";
                ps = conn.prepareStatement(sql);
                ps.setInt(1, Integer.parseInt(id));
            }
            case "ORDERS" -> {
                String id = ask("Enter order_id:");
                if (id == null)
                    return;
                sql = "SELECT * FROM Orders WHERE order_id = ?";
                ps = conn.prepareStatement(sql);
                ps.setInt(1, Integer.parseInt(id));
            }
            case "INVENTORY" -> {
                String sid = ask("Enter store_id:");
                if (sid == null)
                    return;
                String pid = ask("Enter product_id:");
                if (pid == null)
                    return;
                sql = "SELECT * FROM Inventory WHERE store_id = ? AND product_id = ?";
                ps = conn.prepareStatement(sql);
                ps.setInt(1, Integer.parseInt(sid));
                ps.setInt(2, Integer.parseInt(pid));
            }
            case "ORDER_ITEM" -> {
                String oid = ask("Enter order_id:");
                if (oid == null)
                    return;
                String pid = ask("Enter product_id:");
                if (pid == null)
                    return;
                sql = "SELECT * FROM Order_Item WHERE order_id = ? AND product_id = ?";
                ps = conn.prepareStatement(sql);
                ps.setInt(1, Integer.parseInt(oid));
                ps.setInt(2, Integer.parseInt(pid));
            }
            case "DISCOUNTS" -> {
                String id = ask("Enter discount_id:");
                if (id == null)
                    return;
                sql = "SELECT * FROM Discounts WHERE discount_id = ?";
                ps = conn.prepareStatement(sql);
                ps.setInt(1, Integer.parseInt(id));
            }
            default -> {
                appendLine("Unknown table: " + tableName);
                return;
            }
        }

        try (ps; ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData md = rs.getMetaData();
            int cols = md.getColumnCount();
            appendLine("\nResult for " + t + ":");
            if (!rs.next()) {
                appendLine("  No row found.");
                return;
            }
            do {
                StringBuilder row = new StringBuilder("  ");
                for (int i = 1; i <= cols; i++) {
                    row.append(md.getColumnName(i)).append("=").append(rs.getString(i));
                    if (i < cols)
                        row.append(" | ");
                }
                appendLine(row.toString());
            } while (rs.next());
        }
    }

    // UPDATE by Primary Key
    private void updateByPrimaryKey(Connection conn, String tableName) throws SQLException {
        String t = tableName.toUpperCase();

        String column = ask("Enter column name to update:");
        if (column == null || column.isBlank())
            return;

        String newValue = ask("Enter new value:");
        if (newValue == null)
            return;

        String whereClause;
        PreparedStatement ps;

        switch (t) {
            case "STORE" -> {
                String id = ask("Enter store_id:");
                if (id == null)
                    return;
                whereClause = "store_id = ?";
                ps = conn.prepareStatement("UPDATE Store SET " + column + " = ? WHERE " + whereClause);
                ps.setString(1, newValue);
                ps.setInt(2, Integer.parseInt(id));
            }
            case "CUSTOMER" -> {
                String id = ask("Enter customer_id:");
                if (id == null)
                    return;
                whereClause = "customer_id = ?";
                ps = conn.prepareStatement("UPDATE Customer SET " + column + " = ? WHERE " + whereClause);
                ps.setString(1, newValue);
                ps.setInt(2, Integer.parseInt(id));
            }
            case "PRODUCT" -> {
                String id = ask("Enter product_id:");
                if (id == null)
                    return;
                whereClause = "product_id = ?";
                ps = conn.prepareStatement("UPDATE Product SET " + column + " = ? WHERE " + whereClause);
                ps.setString(1, newValue);
                ps.setInt(2, Integer.parseInt(id));
            }
            case "EMPLOYEE" -> {
                String id = ask("Enter employee_id:");
                if (id == null)
                    return;
                whereClause = "employee_id = ?";
                ps = conn.prepareStatement("UPDATE Employee SET " + column + " = ? WHERE " + whereClause);
                ps.setString(1, newValue);
                ps.setInt(2, Integer.parseInt(id));
            }
            case "ORDERS" -> {
                String id = ask("Enter order_id:");
                if (id == null)
                    return;
                whereClause = "order_id = ?";
                ps = conn.prepareStatement("UPDATE Orders SET " + column + " = ? WHERE " + whereClause);
                ps.setString(1, newValue);
                ps.setInt(2, Integer.parseInt(id));
            }
            case "INVENTORY" -> {
                String sid = ask("Enter store_id:");
                if (sid == null)
                    return;
                String pid = ask("Enter product_id:");
                if (pid == null)
                    return;
                whereClause = "store_id = ? AND product_id = ?";
                ps = conn.prepareStatement("UPDATE Inventory SET " + column + " = ? WHERE " + whereClause);
                ps.setString(1, newValue);
                ps.setInt(2, Integer.parseInt(sid));
                ps.setInt(3, Integer.parseInt(pid));
            }
            case "ORDER_ITEM" -> {
                String oid = ask("Enter order_id:");
                if (oid == null)
                    return;
                String pid = ask("Enter product_id:");
                if (pid == null)
                    return;
                whereClause = "order_id = ? AND product_id = ?";
                ps = conn.prepareStatement("UPDATE Order_Item SET " + column + " = ? WHERE " + whereClause);
                ps.setString(1, newValue);
                ps.setInt(2, Integer.parseInt(oid));
                ps.setInt(3, Integer.parseInt(pid));
            }
            case "DISCOUNTS" -> {
                String id = ask("Enter discount_id:");
                if (id == null)
                    return;
                whereClause = "discount_id = ?";
                ps = conn.prepareStatement("UPDATE Discounts SET " + column + " = ? WHERE " + whereClause);
                ps.setString(1, newValue);
                ps.setInt(2, Integer.parseInt(id));
            }
            default -> {
                appendLine("Unknown table: " + tableName);
                return;
            }
        }

        try (ps) {
            int rows = ps.executeUpdate();
            appendLine("Updated " + rows + " row(s) in " + t + ".");
        }
    }

    // DELETE by Primary Key
    private void deleteByPrimaryKey(Connection conn, String tableName) throws SQLException {
        String t = tableName.toUpperCase();
        String sql;
        PreparedStatement ps;

        switch (t) {
            case "STORE" -> {
                String id = ask("Enter store_id:");
                if (id == null)
                    return;
                sql = "DELETE FROM Store WHERE store_id = ?";
                ps = conn.prepareStatement(sql);
                ps.setInt(1, Integer.parseInt(id));
            }
            case "CUSTOMER" -> {
                String id = ask("Enter customer_id:");
                if (id == null)
                    return;
                sql = "DELETE FROM Customer WHERE customer_id = ?";
                ps = conn.prepareStatement(sql);
                ps.setInt(1, Integer.parseInt(id));
            }
            case "PRODUCT" -> {
                String id = ask("Enter product_id:");
                if (id == null)
                    return;
                sql = "DELETE FROM Product WHERE product_id = ?";
                ps = conn.prepareStatement(sql);
                ps.setInt(1, Integer.parseInt(id));
            }
            case "EMPLOYEE" -> {
                String id = ask("Enter employee_id:");
                if (id == null)
                    return;
                sql = "DELETE FROM Employee WHERE employee_id = ?";
                ps = conn.prepareStatement(sql);
                ps.setInt(1, Integer.parseInt(id));
            }
            case "ORDERS" -> {
                String id = ask("Enter order_id:");
                if (id == null)
                    return;
                sql = "DELETE FROM Orders WHERE order_id = ?";
                ps = conn.prepareStatement(sql);
                ps.setInt(1, Integer.parseInt(id));
            }
            case "INVENTORY" -> {
                String sid = ask("Enter store_id:");
                if (sid == null)
                    return;
                String pid = ask("Enter product_id:");
                if (pid == null)
                    return;
                sql = "DELETE FROM Inventory WHERE store_id = ? AND product_id = ?";
                ps = conn.prepareStatement(sql);
                ps.setInt(1, Integer.parseInt(sid));
                ps.setInt(2, Integer.parseInt(pid));
            }
            case "ORDER_ITEM" -> {
                String oid = ask("Enter order_id:");
                if (oid == null)
                    return;
                String pid = ask("Enter product_id:");
                if (pid == null)
                    return;
                sql = "DELETE FROM Order_Item WHERE order_id = ? AND product_id = ?";
                ps = conn.prepareStatement(sql);
                ps.setInt(1, Integer.parseInt(oid));
                ps.setInt(2, Integer.parseInt(pid));
            }
            case "DISCOUNTS" -> {
                String id = ask("Enter discount_id:");
                if (id == null)
                    return;
                sql = "DELETE FROM Discounts WHERE discount_id = ?";
                ps = conn.prepareStatement(sql);
                ps.setInt(1, Integer.parseInt(id));
            }
            default -> {
                appendLine("Unknown table: " + tableName);
                return;
            }
        }

        try (ps) {
            int rows = ps.executeUpdate();
            appendLine("Deleted " + rows + " row(s) from " + t + ".");
        }
    }

    // Small helpers
    private void runQuery(Connection conn, String title, String sql) throws SQLException {
        appendLine("\n=== " + title + " ===");

        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(sql)) {

            ResultSetMetaData md = rs.getMetaData();
            int cols = md.getColumnCount();
            boolean any = false;

            while (rs.next()) {
                any = true;
                StringBuilder row = new StringBuilder("  ");
                for (int i = 1; i <= cols; i++) {
                    String label = md.getColumnLabel(i);
                    row.append(label)
                            .append("=")
                            .append(rs.getString(i));

                    if (i < cols)
                        row.append(" | ");
                }
                appendLine(row.toString());
            }

            if (!any) {
                appendLine("  (no rows)");
            }
        }
    }

    private String ask(String message) {
        return JOptionPane.showInputDialog(this, message);
    }

    private void appendLine(String text) {
        outputArea.append(text + "\n");
        outputArea.setCaretPosition(outputArea.getDocument().getLength());
    }
}

