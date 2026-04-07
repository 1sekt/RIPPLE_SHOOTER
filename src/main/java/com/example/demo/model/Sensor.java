package com.example.demo.model;

import java.awt.geom.Line2D; // 交差判定に便利な標準ライブラリ
import java.util.List;

import lombok.Data;

@Data
public class Sensor {
    private double x, y;
    private double radius = 20.0;
    private double vx = 2.0; // 横方向の速度
    private double vy = 2.0; // 縦方向の速度
    private final double baseSpeed = 1.5; // ★ 基本速度
    private double hitCount = 0.0;      // ★ 0.0 ～ 3.0 で管理（3.0でクリア）
    private final double maxHits = 5.0; // ★ クリアに必要な量
    private boolean activated = false;
    
    //移動と壁反射のロジック
    public void move(List<Obstacle> obstacles, int width, int height) {
        if (activated) return;

        // 1. スピード計算
        double multiplierPerHit = 6.0;
        double speedMultiplier = 1.0 + (hitCount * multiplierPerHit); 
        double currentAngle = Math.atan2(vy, vx);
        this.vx = Math.cos(currentAngle) * baseSpeed * speedMultiplier;
        this.vy = Math.sin(currentAngle) * baseSpeed * speedMultiplier;
        
        // 2. 次の予想位置
        double nextX = x + vx;
        double nextY = y + vy;

        // 3. 画面端判定（跳ね返り＋座標を完全に端に固定）
        if (nextX < radius) {
            nextX = radius;
            vx = Math.abs(vx); // 右向きへ強制
        } else if (nextX > width - radius) {
            nextX = width - radius;
            vx = -Math.abs(vx); // 左向きへ強制
        }
        if (nextY < radius) {
            nextY = radius;
            vy = Math.abs(vy); // 下向きへ強制
        } else if (nextY > height - radius) {
            nextY = height - radius;
            vy = -Math.abs(vy); // 上向きへ強制
        }

        // 4. 障害物（黒い壁）との反射判定と「強力な押し出し」
        for (Obstacle ob : obstacles) {
        	// ★【追加】NORMAL（白い壁）以外は衝突判定を行わずスルーする
            if (!"NORMAL".equals(ob.getType())) {
                continue;
            }
            double ox = ob.getX(), oy = ob.getY(), ow = ob.getWidth(), oh = ob.getHeight();
            
            // 矩形との最短距離にある点(closestX, closestY)を算出
            double closestX = Math.max(ox, Math.min(nextX, ox + ow));
            double closestY = Math.max(oy, Math.min(nextY, oy + oh));
            
            double distX = nextX - closestX;
            double distY = nextY - closestY;
            double distance = Math.sqrt(distX * distX + distY * distY);

            // 衝突判定（半径以内に入ったか）
            if (distance < radius) {
                // 当たった方向を判定して速度を反転
                if (Math.abs(distX) > Math.abs(distY)) {
                    vx *= -1;
                    // ★ 強力な押し出し：壁の厚みを考慮して完全に外側へ飛ばす
                    if (distX > 0) nextX = ox + ow + radius + 1; // 右側へ
                    else nextX = ox - radius - 1;                // 左側へ
                } else {
                    vy *= -1;
                    if (distY > 0) nextY = oy + oh + radius + 1; // 下側へ
                    else nextY = oy - radius - 1;                // 上側へ
                }
                // 1つの壁で補正したら、その座標で確定させる（多重衝突によるスタック防止）
                break; 
            }
        }
        
        // 5. 最終位置の確定
        this.x = nextX;
        this.y = nextY;
    }


    
    // randomize時に速度もランダムに設定する
    public void randomize(int width, int height) {
        this.radius = 20.0;
        this.x = radius + Math.random() * (width - 2 * radius);
        this.y = radius + Math.random() * (height - 2 * radius);
        this.vx = (Math.random() > 0.5 ? baseSpeed : -baseSpeed);
        this.vy = (Math.random() > 0.5 ? baseSpeed : -baseSpeed);
        this.hitCount = 0.0;
        this.activated = false;
    }

    // ★ 毎フレーム呼ばれる減少ロジック
    public void drain() {
        if (activated) return;
        if (this.hitCount > 0) {
            // 0.01 から 0.03 に変更（約3倍の速さで消える）
            // さらに難しくしたければ 0.05 程度がおすすめです
            this.hitCount = Math.max(0, this.hitCount - 0.03);
        }
    }


    public boolean checkHit(Ripple r, List<Obstacle> obstacles, int sensorId) {
        // すでにこの波で判定済み、またはセンサーがクリア済みならスキップ
        if (this.activated || r.getHitSensorIds().contains(sensorId)) return this.activated;

        // 距離の判定（波の縁がセンサーに触れているか）
        double dist = Math.hypot(r.getX() - this.x, r.getY() - this.y);
        
        if (Math.abs(dist - r.getRadius()) < 20.0) {
            
            // ★【シンプル化】波の種類（Gen 0 / Gen 1）に関わらず共通で判定
            // 始点は元のクリック位置(startX, startY)に戻すことで、反射壁との誤判定を回避
            Line2D ray = new Line2D.Double(r.getStartX(), r.getStartY(), this.x, this.y);
            
            for (Obstacle ob : obstacles) {
                // 波の世代による透過設定
                if (r.getGeneration() == 0) {
                    if ("YELLOW".equals(ob.getType())) continue; // 水色は黄色を透過
                } else if (r.getGeneration() == 1) {
                    if ("CYAN".equals(ob.getType())) continue;   // 黄色は水色を透過
                }
                
                // 壁に遮られていたらカウントしない
                if (ray.intersects(ob.getX(), ob.getY(), ob.getWidth(), ob.getHeight())) {
                    return false; 
                }
            }

            // 遮蔽されていなければカウントアップ
            r.getHitSensorIds().add(sensorId);
            this.hitCount = Math.min(maxHits, this.hitCount + 1.0);
            
            if (this.hitCount >= 4.9) {
                this.hitCount = maxHits;
                this.activated = true;
                return true;
            }
        }
        return this.activated;
    }





}


