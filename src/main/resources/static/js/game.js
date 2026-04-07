const canvas = document.getElementById('gameCanvas');
const ctx = canvas.getContext('2d');
const socket = new WebSocket("ws://localhost:8080/ripple");

// サーバーから届く情報を保存する変数
let ripples = [];
let sensors = [];
let isCleared = false;

// 1. クリック座標送信
canvas.addEventListener('click', (e) => {
    const rect = canvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;

    // ★ 壁の中をクリックしたかチェック
	if (window.currentObstacles) {
	        for (let ob of window.currentObstacles) {
	            // NORMAL（白い壁）の中だけクリックをブロックする
	            // YELLOW（黄色い壁）の中はクリックできるようにする
	            if (ob.type === "NORMAL" &&
	                x >= ob.x && x <= ob.x + ob.width &&
	                y >= ob.y && y <= ob.y + ob.height) {
	                console.log("白い壁の中はクリックできません");
	                return; 
	            }
	        }
	    }

    // 壁の外側なら送信
    socket.send(`${x},${y}`);
});

// ★ 追加：リセット命令をサーバーに送る関数
// ★ 修正：リセット命令をサーバーに送る関数
function sendReset() {
    // 1. サーバーへ命令を送信
    socket.send("RESET_STAGE");

    // 2. ★ 重要：サーバーからの返信を待たずに、手元のフラグを即座に折る
    isCleared = false; 

    // 3. ボタンを隠す
    const btn = document.getElementById('resetBtn');
    if (btn) btn.style.display = 'none';
    
    // 4. ついでに古い波紋も消しておくと画面がスッキリします
    ripples = []; 
}


// 2. データの受信
socket.onmessage = (event) => {
    try {
        const data = JSON.parse(event.data);

        if (data.howToPlay) {
            document.getElementById('ruleTitle').innerText = data.howToPlay.title;
            const list = document.getElementById('instructionList');
            list.innerHTML = data.howToPlay.instructions
                .map(text => `<div class="instruction-item">${text.replace(/黄色/g, '<span class="highlight">黄色</span>')}</div>`)
                .join('');
            return;
        }

        if (data.ripples) {
            ripples = data.ripples;
            sensors = data.sensors || [];
            isCleared = data.clear || false;
            window.currentObstacles = data.obstacles || [];

            // ★ 追加：クリア状態に合わせてリセットボタンを表示
            const btn = document.getElementById('resetBtn');
            if (btn) {
                btn.style.display = isCleared ? 'block' : 'none';
            }
        }
    } catch (e) {
        console.error("Data processing error:", e);
    }
};

// 3. 描画ループ
function draw() {
    // 背景リセット（残像エフェクト）
	ctx.fillStyle = 'rgba(17, 17, 17, 0.15)'; 
	ctx.fillRect(0, 0, canvas.width, canvas.height);
	
	// 波紋の描画
	ripples.forEach((r, index) => {
		ctx.save(); 
		
		
		// 波紋本体
		ctx.beginPath();
		ctx.arc(r.x, r.y, r.radius, 0, Math.PI * 2);
		
		let opacity = 0;
		if (r.generation === 0) { // 水色の波紋
			opacity = 1.0 - (r.radius / 100);
			ctx.strokeStyle = `rgba(0, 255, 255, ${Math.max(0, opacity)})`;
			ctx.lineWidth = 2;
		} else { // 黄色の波紋
			opacity = (1.0 - (r.radius / 220)) * 0.7;
			ctx.strokeStyle = `rgba(255, 255, 0, ${Math.max(0, opacity)})`;
			ctx.lineWidth = 1.0;
			ctx.shadowBlur = 10;
			ctx.shadowColor = "yellow";
		}
		ctx.stroke();
		ctx.shadowBlur = 0;
		
		// 影の投影ロジック
		if (window.currentObstacles && window.currentObstacles.length > 0) {
//			const latestR = ripples[ripples.length - 1];
//			const isLatestGroup = (r.startX === latestR.startX && r.startY === latestR.startY);

			window.currentObstacles.forEach(ob => {
				// --- ここから条件分岐の修正 ---

				if (r.generation === 0) { 
		            // 【水色の波(Gen 0)の場合】
		            // NORMAL(黒) または CYAN(水色) の壁は通さない（影を作る）
		            // YELLOW(黄色) の壁はスルーする
		            if (ob.type === "YELLOW") return; 
		        } 
		        else if (r.generation === 1) { 
		            // 【黄色の波(Gen 1)の場合】
		            // NORMAL(黒) または YELLOW(黄色) の壁は通さない（影を作る）
		            // CYAN(水色) の壁はスルーする
		            if (ob.type === "CYAN") return; 
		        }
		        
		        			
				const corners = [
					{x: ob.x, y: ob.y},
					{x: ob.x + ob.width, y: ob.y},
					{x: ob.x + ob.width, y: ob.y + ob.height},
					{x: ob.x, y: ob.y + ob.height}
				];
				
				let cornerObjects = corners.map(c => {
					return {
						x: c.x, y: c.y,
						angle: Math.atan2(c.y - r.startY, c.x - r.startX)
					};
				});
				
				let maxDiff = -1;
				let pLeft = cornerObjects[0], pRight = cornerObjects[1];
				
				for (let i = 0; i < cornerObjects.length; i++) {
					for (let j = i + 1; j < cornerObjects.length; j++) {
						let diff = cornerObjects[j].angle - cornerObjects[i].angle;
						while (diff > Math.PI) diff -= Math.PI * 2;
						while (diff < -Math.PI) diff += Math.PI * 2;
						let absDiff = Math.abs(diff);
						if (absDiff > maxDiff) {
							maxDiff = absDiff;
							if (diff > 0) { pLeft = cornerObjects[i]; pRight = cornerObjects[j]; } 
							else { pLeft = cornerObjects[j]; pRight = cornerObjects[i]; }
						}
					}
				}

				const angleLeft = Math.atan2(pLeft.y - r.startY, pLeft.x - r.startX);
				const angleRight = Math.atan2(pRight.y - r.startY, pRight.x - r.startX);
				const sLeftX = pLeft.x + Math.cos(angleLeft) * 1500;
				const sLeftY = pLeft.y + Math.sin(angleLeft) * 1500;
				const sRightX = pRight.x + Math.cos(angleRight) * 1500;
				const sRightY = pRight.y + Math.sin(angleRight) * 1500;
			
				ctx.save();
			    ctx.beginPath();
			    ctx.globalCompositeOperation = 'destination-out';
				
				// ★ 影の縁をぼかす設定
				ctx.shadowBlur = 40; 
				ctx.shadowColor = 'black'; 
				
			    ctx.fillStyle = 'rgba(0, 0, 0, 0.8)'; // 完全に遮断
			    
			    ctx.moveTo(pLeft.x, pLeft.y);
			    ctx.lineTo(sLeftX, sLeftY);
			    ctx.lineTo(sRightX, sRightY);
			    ctx.lineTo(pRight.x, pRight.y);
			    ctx.closePath();
				ctx.fill();
			    ctx.restore();
			});
		}
		ctx.restore(); 
	});
	
	// 障害物の描画（色別に描き分け）
	if (window.currentObstacles) {
	    window.currentObstacles.forEach(ob => {
	        ctx.save();
	        ctx.lineWidth = 2;

	        if (ob.type === "CYAN") {
	            ctx.strokeStyle = "#00ffff"; // 水色の枠
	            ctx.fillStyle = "rgba(0, 255, 255, 0.1)"; // 中は薄い水色
	        } else if (ob.type === "YELLOW") {
	            ctx.strokeStyle = "#ffff00"; // 黄色の枠
	            ctx.fillStyle = "rgba(255, 255, 0, 0.1)"; // 中は薄い黄色
	        } else {
	            // NORMAL（黒い壁・白い線）
	            ctx.strokeStyle = "#ffffff"; // 白い枠線
	            ctx.fillStyle = "#000000";   // 中は真っ黒
	        }


	        ctx.fillRect(ob.x, ob.y, ob.width, ob.height);
	        ctx.strokeRect(ob.x, ob.y, ob.width, ob.height);
	        ctx.restore();
	    });
	}

	// センサーの描画
	    sensors.forEach(s => {
	        // ★ 修正：クリアに必要な数が5回になったので 5.0 で割る
	        const ratio = s.hitCount / 5.0; 
	        if (ratio > 0 || s.activated) {
	            ctx.save();
	            ctx.beginPath();
	            ctx.arc(s.x, s.y, s.radius, 0, Math.PI * 2);

	            if (s.activated) {
	                ctx.fillStyle = "rgba(255, 255, 255, 1)";
	                ctx.shadowBlur = 30; // クリア時はより光らせる
	                ctx.shadowColor = "white";
	            } else {
	                ctx.fillStyle = `rgba(0, 255, 0, ${Math.min(1, ratio)})`;
	                ctx.shadowBlur = 15 * ratio;
	                ctx.shadowColor = "#00ff00";
	            }
	            ctx.fill();
	            ctx.restore();
	        }
		});
	
		// クリアメッセージ（ボタンと重ならないよう少し上にずらすか、そのまま）
		    if (isCleared) {
		        ctx.save();
		        ctx.fillStyle = "white";
		        ctx.font = "bold 80px sans-serif";
		        ctx.textAlign = "center";
		        ctx.shadowBlur = 0;
		        ctx.shadowColor = "cyan";
		        ctx.fillText("STAGE CLEAR!", canvas.width / 2, canvas.height / 2 - 50);
		        ctx.restore();
		    }

		    requestAnimationFrame(draw);
		}

draw();