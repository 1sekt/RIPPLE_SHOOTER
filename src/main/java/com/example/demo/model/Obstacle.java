package com.example.demo.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
// @AllArgsConstructor  ← これを削除（手動のコンストラクタと重複するため）
public class Obstacle {
    private double x, y, width, height;
    private String type; // "NORMAL", "CYAN", "YELLOW"

    
    // 手動で定義したコンストラクタ（こちらを残す）
    public Obstacle(double x, double y, double w, double h, String type) {
        this.x = x; 
        this.y = y; 
        this.width = w; 
        this.height = h;
        this.type = type;
    }

    // 波紋がこの四角形に当たったか判定
    public boolean isHit(double rippleX, double rippleY, double radius) {
        double closestX = Math.max(x, Math.min(rippleX, x + width));
        double closestY = Math.max(y, Math.min(rippleY, y + height));
        double distance = Math.hypot(rippleX - closestX, rippleY - closestY);
        

        return Math.abs(distance - radius) < 10.0;
    }
}
