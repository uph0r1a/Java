package com.example.demo;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        java.net.URL url = getClass().getResource("/demo/javafx_connect_mysql/book-view.fxml");
        System.out.println("FXML URL = " + url);

        if (url == null) {
            throw new IOException("Không tìm thấy book-view.fxml. Kiểm tra thư mục resources!");
        }

        FXMLLoader loader = new FXMLLoader(url);
        Scene scene = new Scene(loader.load(), 800, 600);
        stage.setTitle("Quản lý Sách - BookDB");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
