package model.dao;

import model.entity.ReportSummary;

import java.sql.Connection;
import java.sql.SQLException;

public interface ReportDao {
    ReportSummary loadSummary(Connection connection) throws SQLException;
}
