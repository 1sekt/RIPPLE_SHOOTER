package com.example.demo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class GameController {

    @GetMapping("/play") // ブラウザで http://localhost:8080/play と入力してアクセス
    public String index() {
        // "index" と返すと、templates/index.html を探しに行きます
        // (.html は自動で補完されるので不要です)
        return "index";
    }
}