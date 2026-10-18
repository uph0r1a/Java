module com.coffeeshop.coffeeshopmanagement {
    requires javafx.controls;
    requires javafx.fxml;

    // Added for the persistence layer.
    requires java.sql;
    // Automatic module name published by sqlite-jdbc's own manifest. If a future
    // version of the driver ever renames/removes that manifest entry, Maven's error
    // message will show the derived name it expects (typically "sqlite.jdbc") -
    // swap this line for that name if the build ever fails to resolve it.
    requires org.xerial.sqlitejdbc;
    // MariaDB / MySQL driver, found by DriverManager when db.type=mariadb
    requires org.mariadb.jdbc;
    // sqlite-jdbc's module-info requires this internally; slf4j-api's own module is
    // named "org.slf4j" (it ships a real module-info, not just an automatic-module name).
    requires org.slf4j;

    exports com.coffeeshop.coffeeshopmanagement;
    exports com.coffeeshop.coffeeshopmanagement.controller;
    exports com.coffeeshop.coffeeshopmanagement.model;

    opens com.coffeeshop.coffeeshopmanagement to javafx.fxml;
    opens com.coffeeshop.coffeeshopmanagement.controller to javafx.fxml;
}