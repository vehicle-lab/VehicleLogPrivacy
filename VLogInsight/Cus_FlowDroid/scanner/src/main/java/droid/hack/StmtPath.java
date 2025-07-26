package droid.hack;

import soot.Unit;
import soot.jimple.Stmt;

import java.util.List;

public interface StmtPath {

    public Unit getStmtPathTail();

    public List<Stmt> getStmtPath();
}
