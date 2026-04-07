package com.example.demo.handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.example.demo.model.Obstacle;
import com.example.demo.model.Ripple;
import com.example.demo.model.Sensor;
import com.example.demo.service.GameService;

import tools.jackson.databind.ObjectMapper;

@Component
public class RippleHandler extends TextWebSocketHandler {

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final List<Ripple> activeRipples = new CopyOnWriteArrayList<>();
    private List<Sensor> sensors = new CopyOnWriteArrayList<>();
    private final List<Obstacle> obstacles = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GameService gameService = new GameService(); // サービスの読み込み2
    private int clearTimer = 0; // ★ 追加：クリア後の待ち時間カウンター
    private static final int RESET_DELAY_FRAMES = 90; // ★ 約3秒 (33ms * 90 ≒ 3秒)

   
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
     // 接続時に「遊び方」を個別に送る
        Map<String, Object> welcome = new HashMap<>();
        welcome.put("howToPlay", gameService.getHowToPlay());
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(welcome)));
    }



    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        // 1. ★【重要】リセット命令は座標分割の前に判定する
        if ("RESET_STAGE".equals(payload)) {
            System.out.println("Reset command received.");
            resetStage();
            return; // リセットしたら終了
        }

        // 2. 座標データの処理
        String[] coords = payload.split(",");
        if (coords.length >= 2) {
            try {
                double x = Double.parseDouble(coords[0]);
                double y = Double.parseDouble(coords[1]);

                // 壁の中のクリックは無視
                for (Obstacle ob : obstacles) {
                    if (x >= ob.getX() && x <= ob.getX() + ob.getWidth() &&
                        y >= ob.getY() && y <= ob.getY() + ob.getHeight()) {
                        return;
                    }
                }

                // 3. ★ 4回分保持のロジック
                List<Ripple> gen0Ripples = new ArrayList<>();
                for (Ripple r : activeRipples) {
                    if (r.getGeneration() == 0) gen0Ripples.add(r);
                }

                // 4つ以上なら一番古いものを削除
                if (gen0Ripples.size() >= 4) {
                    activeRipples.remove(gen0Ripples.get(0));
                }

                // 新しい水色の波を追加
                activeRipples.add(new Ripple(x, y, x, y, 0));

            } catch (NumberFormatException e) {
                // 数字以外のデータ（不正な通信）が来た場合は無視
            }
        }
    }


    public RippleHandler() {
        resetStage();
    }

    private void resetStage() {
        obstacles.clear();
        sensors.clear();
        activeRipples.clear();

        // --- ① 12個の白い正方形の壁 (NORMAL) をランダム配置 ---
        // (既存のロジックを維持)
        int gridSizeX = 4, gridSizeY = 3;
        int cellWidth = 800 / gridSizeX, cellHeight = 600 / gridSizeY;
        int squareSize = 60;
        List<Obstacle> whiteWalls = new ArrayList<>();
        List<Integer> cells = new ArrayList<>();
        for (int i = 0; i < gridSizeX * gridSizeY; i++) cells.add(i);
        Collections.shuffle(cells);

        for (int i = 0; i < 12; i++) {
            int idx = cells.get(i);
            int gx = idx % gridSizeX, gy = idx / gridSizeX;
            double px = (gx * cellWidth) + (cellWidth - squareSize) / 2.0 + (Math.random() * 20 - 10);
            double py = (gy * cellHeight) + (cellHeight - squareSize) / 2.0 + (Math.random() * 20 - 10);
            
            Obstacle ob = new Obstacle(px, py, squareSize, squareSize, "NORMAL");
            whiteWalls.add(ob);
            obstacles.add(ob);
        }

        // --- ② 水色の壁 (CYAN) を「橋渡し」として配置 ---
        // (既存のロジックを維持)
        for (int i = 0; i < 2; i++) {
            Obstacle w1 = whiteWalls.get(i * 2);
            Obstacle w2 = whiteWalls.get(i * 2 + 1);
            double startX = Math.min(w1.getX(), w2.getX());
            double endX = Math.max(w1.getX(), w2.getX());
            double bridgeT = 10.0;
            if (Math.abs(w1.getX() - w2.getX()) > Math.abs(w1.getY() - w2.getY())) {
                obstacles.add(new Obstacle(startX + squareSize, w1.getY() + (squareSize / 2.0) - (bridgeT / 2.0), Math.max(5, endX - (startX + squareSize)), bridgeT, "CYAN"));
            } else {
                double startY = Math.min(w1.getY(), w2.getY());
                double endY = Math.max(w1.getY(), w2.getY());
                obstacles.add(new Obstacle(w1.getX() + (squareSize / 2.0) - (bridgeT / 2.0), startY + squareSize, bridgeT, Math.max(5, endY - (startY + squareSize)), "CYAN"));
            }
        }

        // --- ③ 【変更】巨大な黄色の正方形 (YELLOW) を1つ設置 ---
        double yellowSize = 180.0; // 白い壁(60px)の3倍サイズ
        double yx = 100 + Math.random() * (800 - yellowSize - 200);
        double yy = 100 + Math.random() * (600 - yellowSize - 200);
        obstacles.add(new Obstacle(yx, yy, yellowSize, yellowSize, "YELLOW"));

        // --- ④ センサーを配置 ---
        Sensor s = new Sensor();
        boolean validPos = false;
        while (!validPos) {
            s.randomize(800, 600);
            validPos = true;
            for (Obstacle ob : obstacles) {
                // 壁に重ならないようマージンを持って配置
                if (s.getX() >= ob.getX() - 25 && s.getX() <= ob.getX() + ob.getWidth() + 25 &&
                    s.getY() >= ob.getY() - 25 && s.getY() <= ob.getY() + ob.getHeight() + 25) {
                    validPos = false; break;
                }
            }
        }
        sensors.add(s);
    }

    
    @Scheduled(fixedRate = 33)
    public void broadcastUpdate() {
        try {
            if (sessions.isEmpty()) return;

            List<Ripple> nextRipples = new ArrayList<>();
            boolean allSensorsDone = true;

         // 1. センサーの更新
            for (Sensor s : sensors) {
                s.drain();
                s.move(obstacles, 800, 600);
                // 1つでもクリアされていないセンサーがあれば false
                if (!s.isActivated()) allSensorsDone = false;
            }

            // 2. 波の更新（判定・反射含む）
            for (Ripple r : activeRipples) {
                r.update();
                for (int i = 0; i < sensors.size(); i++) {
                    sensors.get(i).checkHit(r, obstacles, i);
                }
                List<Ripple> reflections = r.checkObstacleReflection(obstacles);
                if (reflections != null) nextRipples.addAll(reflections);
                
                Ripple wallRef = r.checkReflection(800, 600);
                if (wallRef != null) nextRipples.add(wallRef);

                if (r.isAlive()) nextRipples.add(r);
            }
            activeRipples.clear();
            activeRipples.addAll(nextRipples);

         // 3. データ送信
            Map<String, Object> response = new HashMap<>();
            response.put("ripples", activeRipples);
            response.put("sensors", sensors);
            response.put("obstacles", obstacles);
            // フロントエンドに「クリア状態」を送り続けることでボタンが表示され続ける
            response.put("clear", allSensorsDone); 

            String json = objectMapper.writeValueAsString(response);
            TextMessage message = new TextMessage(json);

            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    synchronized (session) {
                        try { session.sendMessage(message); } catch (Exception e) { }
                    }
                }
            }

         // 【削除】if (clearTimer >= RESET_DELAY_FRAMES) の自動リセット処理も削除しました

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
