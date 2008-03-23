/*
 * Copyright 2004-2008 H2 Group. Licensed under the H2 License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.command.dml;

import java.sql.SQLException;

import org.h2.command.ddl.SchemaCommand;
import org.h2.constant.ErrorCode;
import org.h2.engine.Database;
import org.h2.engine.Right;
import org.h2.engine.Session;
import org.h2.expression.Expression;
import org.h2.message.Message;
import org.h2.schema.Schema;
import org.h2.schema.Sequence;
import org.h2.table.Column;
import org.h2.table.Table;

/**
 * This class represents the statement
 * ALTER SEQUENCE
 */
public class AlterSequence extends SchemaCommand {

    private Table table;
    private Sequence sequence;
    private Expression start;
    private Expression increment;

    public AlterSequence(Session session, Schema schema) {
        super(session, schema);
    }    

    public void setSequence(Sequence sequence) {
        this.sequence = sequence;
    }
    
    public boolean isTransactional() {
        return true;
    }
    
    public void setColumn(Column column) throws SQLException {
        table = column.getTable();
        sequence = column.getSequence();
        if (sequence == null) {
            throw Message.getSQLException(ErrorCode.SEQUENCE_NOT_FOUND_1, column.getSQL());
        }
    }

    public void setStartWith(Expression start) {
        this.start = start;
    }

    public void setIncrement(Expression increment) throws SQLException {
        this.increment = increment;
    }

    public int update() throws SQLException {
        // TODO rights: what are the rights required for a sequence?
        Database db = session.getDatabase();
        if (table != null) {
            session.getUser().checkRight(table, Right.ALL);
        }
        if (start != null) {
            long startValue = start.optimize(session).getValue(session).getLong();
            sequence.setStartValue(startValue);
        }
        if (increment != null) {
            long incrementValue = increment.optimize(session).getValue(session).getLong();
            if (incrementValue == 0) {
                throw Message.getSQLException(ErrorCode.INVALID_VALUE_2, new String[] { "0", "INCREMENT" });
            }
            sequence.setIncrement(incrementValue);
        }
        // need to use the system session, so that the update
        // can be committed immediately - not committing it
        // would keep other transactions from using the sequence
        Session sysSession = db.getSystemSession();
        synchronized (sysSession) {
            db.update(sysSession, sequence);
            sysSession.commit(true);
        }
        return 0;
    }
    
}
