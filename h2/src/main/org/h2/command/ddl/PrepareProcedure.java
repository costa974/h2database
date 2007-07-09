package org.h2.command.ddl;

import java.sql.SQLException;

import org.h2.command.Prepared;
import org.h2.engine.Procedure;
import org.h2.engine.Session;

public class PrepareProcedure extends DefineCommand {

    private String procedureName;
    private Prepared prepared;
    
    public PrepareProcedure(Session session) {
        super(session);
    }
    
    public void checkParameters() {
        // no not check parameters
    }
    
    public int update() throws SQLException {
        Procedure proc = new Procedure();
        proc.setName(procedureName);
        proc.setPrepared(prepared);
        prepared.setParameterList(parameters);
        prepared.setPrepareAlways(prepareAlways);
        prepared.prepare();
        session.addProcedure(proc);
        return 0;
    }

    public void setProcedureName(String name) {
        this.procedureName = name;
    }
    
    public void setPrepared(Prepared prep) {
        this.prepared = prep;
    }

}
