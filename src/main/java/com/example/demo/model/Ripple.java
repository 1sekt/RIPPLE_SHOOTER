package com.example.demo.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.Data;

@Data
public class Ripple {
    private double x, y; // 現在の中心
    private double startX, startY; // 光源（常にクリック地点を保持）
    private double radius;
    private int generation; 
    private boolean alive = true;

    private boolean hitLeft, hitRight, hitTop, hitBottom;
    private final Set<Integer> hitSensorIds = new HashSet<>();
    private final Set<Integer> reflectedObstacleIds = new HashSet<>();

    public Ripple(double x, double y, double startX, double startY, int generation) {
        this.x = x;
        this.y = y;
        this.startX = startX; 
        this.startY = startY;
        this.radius = 0;
        this.generation = generation;
    }

    public void update() {
        this.radius += 6.0;
        if (this.generation == 0) {
            if (this.radius > 100) this.alive = false;
        } else {
            if (this.radius > 200) this.alive = false;
        }
    }

    // 外枠への反射
    public Ripple checkReflection(int width, int height) {
        if (!alive || generation >= 1) return null;

        // ★ 第3, 4引数に this.startX, this.startY を渡すように戻しました
        if (!hitLeft && (x - radius) <= 0) {
            hitLeft = true;
            return new Ripple(0, y, this.startX, this.startY, generation + 1);
        }
        if (!hitRight && (x + radius) >= width) {
            hitRight = true;
            return new Ripple(width, y, this.startX, this.startY, generation + 1);
        }
        if (!hitTop && (y - radius) <= 0) {
            hitTop = true;
            return new Ripple(x, 0, this.startX, this.startY, generation + 1);
        }
        if (!hitBottom && (y + radius) >= height) {
            hitBottom = true;
            return new Ripple(x, height, this.startX, this.startY, generation + 1);
        }
        
        return null;
    }
    
    	
    // 黒い壁への反射
    public List<Ripple> checkObstacleReflection(List<Obstacle> obstacles) {
        if (!alive || generation != 0) return null; // 反射を生むのは水色の波(Gen 0)のみ
        List<Ripple> newRipples = new ArrayList<>();

        for (int i = 0; i < obstacles.size(); i++) {
            if (reflectedObstacleIds.contains(i)) continue;

            Obstacle ob = obstacles.get(i);
            double ox = ob.getX(), oy = ob.getY(), ow = ob.getWidth(), oh = ob.getHeight();
            
            double margin = ("NORMAL".equals(ob.getType())) ? 15.0 : 8.0; // CYANは細いので遊びを小さく
            boolean isHit = false;
            double hitX = x, hitY = y;

            // --- 改良版：円と長方形の当たり判定 ---
            // 波の外周（radius）が壁の矩形範囲に入っているかチェック
            boolean horizontally = (x + radius >= ox && x - radius <= ox + ow);
            boolean vertically   = (y + radius >= oy && y - radius <= oy + oh);
            
            // ★ 追加：黄色の壁(YELLOW)なら、水色の波は何もしない（スルーする）
            if ("YELLOW".equals(ob.getType())) {
                continue; 
            }
            
         // 1. 左右の辺（x方向の接触）
            if (y >= oy && y <= oy + oh) {
                if (Math.abs((x + radius) - ox) < margin) { 
                    isHit = true; hitX = ox; hitY = y; // 左壁表面
                } else if (Math.abs((x - radius) - (ox + ow)) < margin) { 
                    isHit = true; hitX = ox + ow; hitY = y; // 右壁表面
                }
            }
            // 2. 上下の辺（y方向の接触）
            if (!isHit && x >= ox && x <= ox + ow) {
                if (Math.abs((y + radius) - oy) < margin) { 
                    isHit = true; hitX = x; hitY = oy; // 上壁表面
                } else if (Math.abs((y - radius) - (oy + oh)) < margin) { 
                    isHit = true; hitX = x; hitY = oy + oh; // 下壁表面
                }
            }
                // 角の判定
                if (!isHit) {
                    double[][] corners = {{ox, oy}, {ox + ow, oy}, {ox, oy + oh}, {ox + ow, oy + oh}};
                    for (double[] c : corners) {
                        if (Math.abs(Math.hypot(x - c[0], y - c[1]) - radius) < 15) {
                            isHit = true; hitX = c[0]; hitY = c[1];
                            break;
                        }
                    }
                }

             // 判定後の処理
                if (isHit) {
                    reflectedObstacleIds.add(i);
                    // NORMAL（白い壁）の時だけ黄色い波を作る
                    if ("NORMAL".equals(ob.getType())) {
                        newRipples.add(new Ripple(hitX, hitY, this.startX, this.startY, 1));
                    }
                    // CYAN（水色の壁）は ID登録(reflectedObstacleIds) だけで波は作らない
                }
        }
        return newRipples.isEmpty() ? null : newRipples;
    }

}
