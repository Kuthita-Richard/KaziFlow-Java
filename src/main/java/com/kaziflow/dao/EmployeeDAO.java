package com.kaziflow.dao;

import com.kaziflow.database.DatabaseManager;
import com.kaziflow.models.Employee;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class EmployeeDAO {

    private Connection getConn() throws SQLException {
        return DatabaseManager.getInstance().getConnection();
    }

    public List<Employee> findAll() {
        List<Employee> list = new ArrayList<>();
        String sql = """
            SELECT e.*, d.name as department_name FROM employees e
            LEFT JOIN departments d ON e.department_id = d.id
            ORDER BY e.name
        """;
        try (Connection conn = getConn(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public List<Employee> search(String query) {
        List<Employee> list = new ArrayList<>();
        String sql = """
            SELECT e.*, d.name as department_name FROM employees e
            LEFT JOIN departments d ON e.department_id = d.id
            WHERE e.name LIKE ? OR e.employee_number LIKE ? OR e.position LIKE ?
        """;
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            String q = "%" + query + "%";
            ps.setString(1, q); ps.setString(2, q); ps.setString(3, q);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public boolean save(Employee e) {
        String empNum = generateEmployeeNumber();
        String sql = "INSERT INTO employees (employee_number, name, email, phone, department_id, position, employment_type, salary, hire_date, status) VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, empNum);
            ps.setString(2, e.getName());
            ps.setString(3, e.getEmail());
            ps.setString(4, e.getPhone());
            ps.setInt(5, e.getDepartmentId());
            ps.setString(6, e.getPosition());
            ps.setString(7, e.getEmploymentType() != null ? e.getEmploymentType() : "full-time");
            ps.setDouble(8, e.getSalary());
            ps.setString(9, e.getHireDate() != null ? e.getHireDate().toString() : LocalDate.now().toString());
            ps.setString(10, "active");
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) { ex.printStackTrace(); return false; }
    }

    public boolean update(Employee e) {
        String sql = "UPDATE employees SET name=?, email=?, phone=?, department_id=?, position=?, employment_type=?, salary=?, status=? WHERE id=?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, e.getName());
            ps.setString(2, e.getEmail());
            ps.setString(3, e.getPhone());
            ps.setInt(4, e.getDepartmentId());
            ps.setString(5, e.getPosition());
            ps.setString(6, e.getEmploymentType());
            ps.setDouble(7, e.getSalary());
            ps.setString(8, e.getStatus());
            ps.setInt(9, e.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) { ex.printStackTrace(); return false; }
    }

    public int getTotalCount() {
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM employees WHERE status='active'");
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public double getTotalMonthlySalary() {
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COALESCE(SUM(salary),0) FROM employees WHERE status='active'");
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public List<String[]> getDepartments() {
        List<String[]> list = new ArrayList<>();
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT id, name FROM departments ORDER BY name");
            while (rs.next()) list.add(new String[]{rs.getString("id"), rs.getString("name")});
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    private String generateEmployeeNumber() {
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM employees");
            if (rs.next()) return String.format("EMP-%03d", rs.getInt(1) + 1);
        } catch (SQLException e) { e.printStackTrace(); }
        return "EMP-001";
    }

    private Employee mapRow(ResultSet rs) throws SQLException {
        Employee e = new Employee();
        e.setId(rs.getInt("id"));
        e.setEmployeeNumber(rs.getString("employee_number"));
        e.setName(rs.getString("name"));
        e.setEmail(rs.getString("email"));
        e.setPhone(rs.getString("phone"));
        e.setDepartmentId(rs.getInt("department_id"));
        e.setDepartmentName(rs.getString("department_name"));
        e.setPosition(rs.getString("position"));
        e.setEmploymentType(rs.getString("employment_type"));
        e.setSalary(rs.getDouble("salary"));
        e.setStatus(rs.getString("status"));
        try { e.setHireDate(LocalDate.parse(rs.getString("hire_date"))); } catch (Exception ignored) {}
        return e;
    }
}
