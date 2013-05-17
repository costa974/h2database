/*
 * Copyright 2004-2013 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import org.h2.constant.ErrorCode;
import org.h2.constant.SysProperties;
import org.h2.jdbc.JdbcStatement;
import org.h2.store.fs.FileUtils;
import org.h2.test.TestBase;

/**
 * Tests for the Statement implementation.
 */
public class TestStatement extends TestBase {

    private Connection conn;

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String... a) throws Exception {
        TestBase.createCaller().init().test();
    }

    @Override
    public void test() throws Exception {
        deleteDb("statement");
        conn = getConnection("statement");
        testTraceError();
        if (config.jdk14) {
            testSavepoint();
        }
        testConnectionRollback();
        testStatement();
        if (config.jdk14) {
            testIdentityMerge();
            testIdentity();
        }
        conn.close();
        deleteDb("statement");
    }

    private void testTraceError() throws Exception {
        if (config.memory || config.networked || config.traceLevelFile != 0) {
            return;
        }
        Statement stat = conn.createStatement();
        String fileName = getBaseDir() + "/statement.trace.db";
        stat.execute("DROP TABLE TEST IF EXISTS");
        stat.execute("CREATE TABLE TEST(ID INT PRIMARY KEY)");
        stat.execute("INSERT INTO TEST VALUES(1)");
        try {
            stat.execute("ERROR");
        } catch (SQLException e) {
            // ignore
        }
        long lengthBefore = FileUtils.size(fileName);
        try {
            stat.execute("ERROR");
        } catch (SQLException e) {
            // ignore
        }
        long error = FileUtils.size(fileName);
        assertSmaller(lengthBefore, error);
        lengthBefore = error;
        try {
            stat.execute("INSERT INTO TEST VALUES(1)");
        } catch (SQLException e) {
            // ignore
        }
        error = FileUtils.size(fileName);
        assertEquals(lengthBefore, error);
        stat.execute("DROP TABLE TEST IF EXISTS");
    }

    private void testConnectionRollback() throws SQLException {
        Statement stat = conn.createStatement();
        conn.setAutoCommit(false);
        stat.execute("CREATE TABLE TEST(ID INT PRIMARY KEY, NAME VARCHAR(255))");
        stat.execute("INSERT INTO TEST VALUES(1, 'Hello')");
        conn.rollback();
        ResultSet rs = stat.executeQuery("SELECT * FROM TEST");
        assertFalse(rs.next());
        stat.execute("DROP TABLE TEST");
        conn.setAutoCommit(true);
    }

    private void testSavepoint() throws SQLException {
        Statement stat = conn.createStatement();
        stat.execute("CREATE TABLE TEST(ID INT PRIMARY KEY, NAME VARCHAR(255))");
        conn.setAutoCommit(false);
        stat.execute("INSERT INTO TEST VALUES(0, 'Hi')");
        Savepoint savepoint1 = conn.setSavepoint();
        int id1 = savepoint1.getSavepointId();
        assertThrows(ErrorCode.SAVEPOINT_IS_UNNAMED, savepoint1).
                getSavepointName();
        stat.execute("DELETE FROM TEST");
        conn.rollback(savepoint1);
        stat.execute("UPDATE TEST SET NAME='Hello'");
        Savepoint savepoint2a = conn.setSavepoint();
        Savepoint savepoint2 = conn.setSavepoint();
        conn.releaseSavepoint(savepoint2a);
        assertThrows(ErrorCode.SAVEPOINT_IS_INVALID_1, savepoint2a).
                getSavepointId();
        int id2 = savepoint2.getSavepointId();
        assertTrue(id1 != id2);
        stat.execute("UPDATE TEST SET NAME='Hallo' WHERE NAME='Hello'");
        Savepoint savepointTest = conn.setSavepoint("Joe's");
        stat.execute("DELETE FROM TEST");
        assertEquals(savepointTest.getSavepointName(), "Joe's");
        assertThrows(ErrorCode.SAVEPOINT_IS_NAMED, savepointTest).
                getSavepointId();
        conn.rollback(savepointTest);
        conn.commit();
        ResultSet rs = stat.executeQuery("SELECT NAME FROM TEST");
        rs.next();
        String name = rs.getString(1);
        assertEquals(name, "Hallo");
        assertFalse(rs.next());
        assertThrows(ErrorCode.SAVEPOINT_IS_INVALID_1, conn).
                rollback(savepoint2);
        stat.execute("DROP TABLE TEST");
        conn.setAutoCommit(true);
    }

    private void testStatement() throws SQLException {

        Statement stat = conn.createStatement();

        assertEquals(ResultSet.HOLD_CURSORS_OVER_COMMIT, conn.getHoldability());
        conn.setHoldability(ResultSet.CLOSE_CURSORS_AT_COMMIT);
        assertEquals(ResultSet.CLOSE_CURSORS_AT_COMMIT, conn.getHoldability());

        // ignored
        stat.setCursorName("x");
        // fixed return value
        assertEquals(stat.getFetchDirection(), ResultSet.FETCH_FORWARD);
        // ignored
        stat.setFetchDirection(ResultSet.FETCH_REVERSE);
        // ignored
        stat.setMaxFieldSize(100);

        assertEquals(SysProperties.SERVER_RESULT_SET_FETCH_SIZE, stat.getFetchSize());
        stat.setFetchSize(10);
        assertEquals(10, stat.getFetchSize());
        stat.setFetchSize(0);
        assertEquals(SysProperties.SERVER_RESULT_SET_FETCH_SIZE, stat.getFetchSize());
        assertEquals(ResultSet.TYPE_FORWARD_ONLY, stat.getResultSetType());
        Statement stat2 = conn.createStatement(
                ResultSet.TYPE_SCROLL_SENSITIVE,
                ResultSet.CONCUR_READ_ONLY,
                ResultSet.HOLD_CURSORS_OVER_COMMIT);
        assertEquals(ResultSet.TYPE_SCROLL_SENSITIVE, stat2.getResultSetType());
        assertEquals(ResultSet.HOLD_CURSORS_OVER_COMMIT, stat2.getResultSetHoldability());
        assertEquals(ResultSet.CONCUR_READ_ONLY, stat2.getResultSetConcurrency());
        assertEquals(0, stat.getMaxFieldSize());
        assertTrue(!((JdbcStatement) stat2).isClosed());
        stat2.close();
        assertTrue(((JdbcStatement) stat2).isClosed());


        ResultSet rs;
        int count;
        boolean result;

        stat.execute("CREATE TABLE TEST(ID INT)");
        stat.execute("SELECT * FROM TEST");
        stat.execute("DROP TABLE TEST");

        conn.getTypeMap();

        // this method should not throw an exception - if not supported, this
        // calls are ignored

        if (config.jdk14) {
            assertEquals(ResultSet.HOLD_CURSORS_OVER_COMMIT, stat.getResultSetHoldability());
        }
        assertEquals(ResultSet.CONCUR_READ_ONLY, stat.getResultSetConcurrency());

        stat.cancel();
        stat.setQueryTimeout(10);
        assertTrue(stat.getQueryTimeout() == 10);
        stat.setQueryTimeout(0);
        assertTrue(stat.getQueryTimeout() == 0);
        assertThrows(ErrorCode.INVALID_VALUE_2, stat).setQueryTimeout(-1);
        assertTrue(stat.getQueryTimeout() == 0);
        trace("executeUpdate");
        count = stat.executeUpdate("CREATE TABLE TEST(ID INT PRIMARY KEY,VALUE VARCHAR(255))");
        assertEquals(0, count);
        count = stat.executeUpdate("INSERT INTO TEST VALUES(1,'Hello')");
        assertEquals(1, count);
        count = stat.executeUpdate("INSERT INTO TEST(VALUE,ID) VALUES('JDBC',2)");
        assertEquals(1, count);
        count = stat.executeUpdate("UPDATE TEST SET VALUE='LDBC' WHERE ID=2 OR ID=1");
        assertEquals(2, count);
        count = stat.executeUpdate("UPDATE TEST SET VALUE='\\LDBC\\' WHERE VALUE LIKE 'LDBC' ");
        assertEquals(2, count);
        count = stat.executeUpdate("UPDATE TEST SET VALUE='LDBC' WHERE VALUE LIKE '\\\\LDBC\\\\'");
        trace("count:" + count);
        assertEquals(2, count);
        count = stat.executeUpdate("DELETE FROM TEST WHERE ID=-1");
        assertEquals(0, count);
        count = stat.executeUpdate("DELETE FROM TEST WHERE ID=2");
        assertEquals(1, count);

        assertThrows(ErrorCode.METHOD_NOT_ALLOWED_FOR_QUERY, stat).
                executeUpdate("SELECT * FROM TEST");

        count = stat.executeUpdate("DROP TABLE TEST");
        assertTrue(count == 0);

        trace("execute");
        result = stat.execute("CREATE TABLE TEST(ID INT PRIMARY KEY,VALUE VARCHAR(255))");
        assertTrue(!result);
        result = stat.execute("INSERT INTO TEST VALUES(1,'Hello')");
        assertTrue(!result);
        result = stat.execute("INSERT INTO TEST(VALUE,ID) VALUES('JDBC',2)");
        assertTrue(!result);
        result = stat.execute("UPDATE TEST SET VALUE='LDBC' WHERE ID=2");
        assertTrue(!result);
        result = stat.execute("DELETE FROM TEST WHERE ID=3");
        assertTrue(!result);
        result = stat.execute("SELECT * FROM TEST");
        assertTrue(result);
        result = stat.execute("DROP TABLE TEST");
        assertTrue(!result);

        assertThrows(ErrorCode.METHOD_ONLY_ALLOWED_FOR_QUERY, stat).
                executeQuery("CREATE TABLE TEST(ID INT PRIMARY KEY,VALUE VARCHAR(255))");

        stat.execute("CREATE TABLE TEST(ID INT PRIMARY KEY,VALUE VARCHAR(255))");

        assertThrows(ErrorCode.METHOD_ONLY_ALLOWED_FOR_QUERY, stat).
                executeQuery("INSERT INTO TEST VALUES(1,'Hello')");

        assertThrows(ErrorCode.METHOD_ONLY_ALLOWED_FOR_QUERY, stat).
                executeQuery("UPDATE TEST SET VALUE='LDBC' WHERE ID=2");

        assertThrows(ErrorCode.METHOD_ONLY_ALLOWED_FOR_QUERY, stat).
                executeQuery("DELETE FROM TEST WHERE ID=3");

        stat.executeQuery("SELECT * FROM TEST");

        assertThrows(ErrorCode.METHOD_ONLY_ALLOWED_FOR_QUERY, stat).
                executeQuery("DROP TABLE TEST");

        // getMoreResults
        rs = stat.executeQuery("SELECT * FROM TEST");
        assertFalse(stat.getMoreResults());
        assertThrows(ErrorCode.OBJECT_CLOSED, rs).next();
        assertTrue(stat.getUpdateCount() == -1);
        count = stat.executeUpdate("DELETE FROM TEST");
        assertFalse(stat.getMoreResults());
        assertTrue(stat.getUpdateCount() == -1);

        stat.execute("DROP TABLE TEST");
        stat.executeUpdate("DROP TABLE IF EXISTS TEST");

        assertTrue(stat.getWarnings() == null);
        stat.clearWarnings();
        assertTrue(stat.getWarnings() == null);
        assertTrue(conn == stat.getConnection());

        stat.close();
    }

    private void testIdentityMerge() throws SQLException {
        Statement stat = conn.createStatement();
        stat.execute("drop table if exists test1");
        stat.execute("create table test1(id identity, x int)");
        stat.execute("drop table if exists test2");
        stat.execute("create table test2(id identity, x int)");
        stat.execute("merge into test1(x) key(x) values(5)");
        ResultSet keys;
        keys = stat.getGeneratedKeys();
        keys.next();
        assertEquals(1, keys.getInt(1));
        stat.execute("insert into test2(x) values(10), (11), (12)");
        stat.execute("merge into test1(x) key(x) values(5)");
        keys = stat.getGeneratedKeys();
        assertFalse(keys.next());
        stat.execute("merge into test1(x) key(x) values(6)");
        keys = stat.getGeneratedKeys();
        keys.next();
        assertEquals(2, keys.getInt(1));
        stat.execute("drop table test1, test2");
    }

    private void testIdentity() throws SQLException {
        Statement stat = conn.createStatement();
        stat.execute("CREATE SEQUENCE SEQ");
        stat.execute("CREATE TABLE TEST(ID INT)");
        stat.execute("INSERT INTO TEST VALUES(NEXT VALUE FOR SEQ)");
        ResultSet rs = stat.getGeneratedKeys();
        rs.next();
        assertEquals(1, rs.getInt(1));
        assertFalse(rs.next());
        stat.execute("INSERT INTO TEST VALUES(NEXT VALUE FOR SEQ)", Statement.RETURN_GENERATED_KEYS);
        rs = stat.getGeneratedKeys();
        rs.next();
        assertEquals(2, rs.getInt(1));
        assertFalse(rs.next());
        stat.execute("INSERT INTO TEST VALUES(NEXT VALUE FOR SEQ)", new int[] { 1 });
        rs = stat.getGeneratedKeys();
        rs.next();
        assertEquals(3, rs.getInt(1));
        assertFalse(rs.next());
        stat.execute("INSERT INTO TEST VALUES(NEXT VALUE FOR SEQ)", new String[] { "ID" });
        rs = stat.getGeneratedKeys();
        rs.next();
        assertEquals(4, rs.getInt(1));
        assertFalse(rs.next());
        stat.executeUpdate("INSERT INTO TEST VALUES(NEXT VALUE FOR SEQ)", Statement.RETURN_GENERATED_KEYS);
        rs = stat.getGeneratedKeys();
        rs.next();
        assertEquals(5, rs.getInt(1));
        assertFalse(rs.next());
        stat.executeUpdate("INSERT INTO TEST VALUES(NEXT VALUE FOR SEQ)", new int[] { 1 });
        rs = stat.getGeneratedKeys();
        rs.next();
        assertEquals(6, rs.getInt(1));
        assertFalse(rs.next());
        stat.executeUpdate("INSERT INTO TEST VALUES(NEXT VALUE FOR SEQ)", new String[] { "ID" });
        rs = stat.getGeneratedKeys();
        rs.next();
        assertEquals(7, rs.getInt(1));
        assertFalse(rs.next());

        stat.execute("CREATE TABLE TEST2(ID identity primary key)");
        stat.execute("INSERT INTO TEST2 VALUES()");
        stat.execute("SET @X = IDENTITY()");
        rs = stat.executeQuery("SELECT @X");
        rs.next();
        assertEquals(1, rs.getInt(1));

        stat.execute("DROP TABLE TEST");
        stat.execute("DROP TABLE TEST2");
    }

}
