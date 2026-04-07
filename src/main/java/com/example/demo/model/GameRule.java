package com.example.demo.model;

import java.util.Arrays;
import java.util.List;

import lombok.Data;

@Data
public class GameRule {
    private String title = "RIPPLE SHOOTER";
    private int requiredHits = 5;
    private List<String> instructions = Arrays.asList(
        "1. クリックして水色の波紋を発生させます",
        "2. 白い壁に当たると黄色の反射波が生まれます",
        "3. 巨大な黄色い壁は、反射波（黄色）だけを遮断します",
        "4. センサーに合計5回波紋を当てるとクリアです",
        "5. 波紋は最大4つまで画面に保持されます"
    );
}
