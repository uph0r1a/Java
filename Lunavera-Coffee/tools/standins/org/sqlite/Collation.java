package org.sqlite;
/** Stand-in for sqlite-jdbc's collation class (TODO 3.84 uses it in DatabaseConfig); without it DatabaseConfig and everything that reaches it is left out of the library-free run (TODO 6.75 found this). */
public abstract class Collation { public static void create(java.sql.Connection c, String n, Collation f) throws java.sql.SQLException {} protected abstract int xCompare(String first, String second); }
