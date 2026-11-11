package org.sqlite;
public abstract class Function { public static void create(java.sql.Connection c, String n, Function f) throws java.sql.SQLException {} protected abstract void xFunc() throws java.sql.SQLException; protected String value_text(int i) { return null; } protected void result(String s) {} }
