package com.kaziflow.dao;
import com.kaziflow.database.DatabaseManager;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
public class SchoolCanteenDAO {
    private Connection conn() throws SQLException { return DatabaseManager.getInstance().getConnection(); }
    public void ensureTables() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS canteen_accounts (id INTEGER PRIMARY KEY AUTOINCREMENT, student_no TEXT NOT NULL UNIQUE, student_name TEXT NOT NULL, class_name TEXT, phone TEXT, balance REAL DEFAULT 0, status TEXT DEFAULT 'active', created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            s.execute("CREATE TABLE IF NOT EXISTS canteen_transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, account_id INTEGER REFERENCES canteen_accounts(id), type TEXT CHECK(type IN ('topup','purchase','refund')), amount REAL NOT NULL, description TEXT, balance_after REAL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP, created_by INTEGER REFERENCES users(id))");
        } catch (Exception e) { e.printStackTrace(); }
    }
    public int createAccount(String studentNo, String studentName, String className, String phone, double initialBalance) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("INSERT INTO canteen_accounts (student_no,student_name,class_name,phone,balance) VALUES (?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1,studentNo); ps.setString(2,studentName); ps.setString(3,className); ps.setString(4,phone); ps.setDouble(5,initialBalance);
            ps.executeUpdate(); ResultSet keys=ps.getGeneratedKeys(); return keys.next()?keys.getInt(1):-1;
        } catch (Exception e) { e.printStackTrace(); return -1; }
    }
    public boolean topUp(int accountId, double amount, String description, int createdBy) {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            double newBalance=0;
            try (PreparedStatement ps=c.prepareStatement("UPDATE canteen_accounts SET balance=balance+? WHERE id=?")) { ps.setDouble(1,amount); ps.setInt(2,accountId); ps.executeUpdate(); }
            try (PreparedStatement ps=c.prepareStatement("SELECT balance FROM canteen_accounts WHERE id=?")) { ps.setInt(1,accountId); ResultSet rs=ps.executeQuery(); if(rs.next()) newBalance=rs.getDouble(1); }
            try (PreparedStatement ps=c.prepareStatement("INSERT INTO canteen_transactions (account_id,type,amount,description,balance_after,created_by) VALUES (?,?,?,?,?,?)")) { ps.setInt(1,accountId); ps.setString(2,"topup"); ps.setDouble(3,amount); ps.setString(4,description); ps.setDouble(5,newBalance); ps.setInt(6,createdBy); ps.executeUpdate(); }
            c.commit(); return true;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }
    public boolean purchase(int accountId, double amount, String description, int createdBy) {
        try (Connection c = conn()) {
            double balance=0;
            try (PreparedStatement ps=c.prepareStatement("SELECT balance FROM canteen_accounts WHERE id=?")) { ps.setInt(1,accountId); ResultSet rs=ps.executeQuery(); if(rs.next()) balance=rs.getDouble(1); }
            if (balance < amount) return false;
            c.setAutoCommit(false);
            try (PreparedStatement ps=c.prepareStatement("UPDATE canteen_accounts SET balance=balance-? WHERE id=?")) { ps.setDouble(1,amount); ps.setInt(2,accountId); ps.executeUpdate(); }
            double newBal=balance-amount;
            try (PreparedStatement ps=c.prepareStatement("INSERT INTO canteen_transactions (account_id,type,amount,description,balance_after,created_by) VALUES (?,?,?,?,?,?)")) { ps.setInt(1,accountId); ps.setString(2,"purchase"); ps.setDouble(3,amount); ps.setString(4,description); ps.setDouble(5,newBal); ps.setInt(6,createdBy); ps.executeUpdate(); }
            c.commit(); return true;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }
    public List<String[]> findAll(String filter) {
        List<String[]> list=new ArrayList<>();
        String where=filter!=null&&!filter.equals("all")?"WHERE status='"+filter+"'":"";
        try (Connection c=conn(); Statement s=c.createStatement()) {
            ResultSet rs=s.executeQuery("SELECT id,student_no,student_name,class_name,phone,balance,status FROM canteen_accounts "+where+" ORDER BY student_name LIMIT 200");
            while(rs.next()) list.add(new String[]{String.valueOf(rs.getInt("id")),rs.getString("student_no"),rs.getString("student_name"),rs.getString("class_name")!=null?rs.getString("class_name"):"—",rs.getString("phone")!=null?rs.getString("phone"):"—",String.format("%.2f",rs.getDouble("balance")),rs.getString("status")});
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }
    public List<String[]> getTransactions(int accountId) {
        List<String[]> list=new ArrayList<>();
        try (Connection c=conn(); PreparedStatement ps=c.prepareStatement("SELECT type,amount,description,balance_after,created_at FROM canteen_transactions WHERE account_id=? ORDER BY created_at DESC LIMIT 20")) {
            ps.setInt(1,accountId); ResultSet rs=ps.executeQuery();
            while(rs.next()) list.add(new String[]{rs.getString("type"),String.format("%.2f",rs.getDouble("amount")),rs.getString("description")!=null?rs.getString("description"):"—",String.format("%.2f",rs.getDouble("balance_after")),rs.getString("created_at")});
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }
    public int getTotalAccounts() { try(Connection c=conn();Statement s=c.createStatement()){ResultSet rs=s.executeQuery("SELECT COUNT(*) FROM canteen_accounts WHERE status='active'");return rs.next()?rs.getInt(1):0;}catch(Exception e){return 0;} }
    public double getTotalBalance() { try(Connection c=conn();Statement s=c.createStatement()){ResultSet rs=s.executeQuery("SELECT COALESCE(SUM(balance),0) FROM canteen_accounts WHERE status='active'");return rs.next()?rs.getDouble(1):0;}catch(Exception e){return 0;} }
    public double getTodaySales() { try(Connection c=conn();Statement s=c.createStatement()){ResultSet rs=s.executeQuery("SELECT COALESCE(SUM(amount),0) FROM canteen_transactions WHERE type='purchase' AND DATE(created_at)=DATE('now')");return rs.next()?rs.getDouble(1):0;}catch(Exception e){return 0;} }
}
