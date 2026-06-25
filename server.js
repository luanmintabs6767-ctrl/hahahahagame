const express = require('express');
const jwt = require('jsonwebtoken');
const bcrypt = require('bcryptjs');
const NodeRSA = require('node-rsa'); // 🛠️ 기본 불러오기 유지하되, 아래 인스턴스 생성 코드 수정 완료
const cors = require('cors');
const compression = require('compression');
const fs = require('fs').promises;
const { existsSync } = require('fs');
const path = require('path');
const dgram = require('dgram'); 

const app = express();
const PORT = 19132; 

app.use(cors());
app.use(compression());
app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ extended: true, limit: '50mb' }));

// 🔒 [E2EE 설정] 1024비트 PKCS1 (안드로이드 1.6 / 갤럭시 S1 호환)
// 🛠️ 생성자 오류 방지를 위한 안전장치 매핑 처리
const RSAConstructor = NodeRSA.default || NodeRSA;
const keyPair = new RSAConstructor({ b: 1024 });
keyPair.setOptions({ encryptionScheme: 'pkcs1' });
const PRIVATE_KEY = keyPair.exportKey('private');
const PUBLIC_KEY = keyPair.exportKey('pkcs8-public');

const JWT_SECRET = 'hahaha_game_secure_safe_key_2026';

// 📂 [로컬 스토리지 데이터 경로]
const DATA_DIR = path.join(__dirname, 'data');
const USERS_FILE = path.join(DATA_DIR, 'users.json');
const MAPS_FILE = path.join(DATA_DIR, 'maps.json');
const MAP_FILES_DIR = path.join(DATA_DIR, 'map_files');

// 🎮 [실시간 멀티플레이어 로비 및 UDP 세션 데이터]
const gameRooms = {}; 
const udpClients = {}; 

async function initDatabase() {
    try {
        if (!existsSync(DATA_DIR)) await fs.mkdir(DATA_DIR, { recursive: true });
        if (!existsSync(MAP_FILES_DIR)) await fs.mkdir(MAP_FILES_DIR, { recursive: true });
        if (!existsSync(USERS_FILE)) await fs.writeFile(USERS_FILE, '{}', 'utf8');
        if (!existsSync(MAPS_FILE)) await fs.writeFile(MAPS_FILE, '{}', 'utf8');
    } catch (err) { console.error(err); }
}
initDatabase();

// 파일 IO 헬퍼
async function loadUsers() { try { return JSON.parse(await fs.readFile(USERS_FILE, 'utf8')); } catch (e) { return {}; } }
async function saveUsers(users) { await fs.writeFile(USERS_FILE, JSON.stringify(users, null, 2), 'utf8'); }
async function loadMaps() { try { return JSON.parse(await fs.readFile(MAPS_FILE, 'utf8')); } catch (e) { return {}; } }
async function saveMaps(maps) { await fs.writeFile(MAPS_FILE, JSON.stringify(maps, null, 2), 'utf8'); }

function escapeHtml(text) {
    if (typeof text !== 'string') return text;
    return text.replace(/[&<>"']/g, (m) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' }[m]));
}

const decryptPassword = (encryptedPassword) => {
    try {
        const RSAConstructor = NodeRSA.default || NodeRSA;
        const rsaKey = new RSAConstructor(PRIVATE_KEY);
        rsaKey.setOptions({ encryptionScheme: 'pkcs1' });
        return rsaKey.decrypt(encryptedPassword, 'utf8');
    } catch (e) { return null; }
};

const authenticateToken = (req, res, next) => {
    const authHeader = req.headers['authorization'];
    let token = authHeader && authHeader.split(' ')[1];
    if (!token && req.query.token) token = req.query.token;
    if (!token) return res.status(401).json({ error: 'Token missing' });

    jwt.verify(token, JWT_SECRET, (err, user) => {
        if (err) return res.status(403).json({ error: 'Invalid token' });
        req.user = user;
        next();
    });
};

// -------------------------------------------------------------------------
// 🌐 [TCP/HTTP 웹뷰] 대시보드 렌더링 (갤럭시 S1 완벽 레이아웃)
// -------------------------------------------------------------------------
app.get('/', async (req, res) => {
    const users = await loadUsers();
    const maps = await loadMaps();
    const popularMaps = Object.values(maps).sort((a, b) => b.views - a.views).slice(0, 5);
    const ranking = Object.values(users).sort((a, b) => (b.points || 0) - (a.points || 0)).slice(0, 5);
    const activeRooms = Object.values(gameRooms);

    let html = `
    <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
    <html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <title>하하하게임 하이브리드 대시보드</title>
        <style type="text/css">
            body { font-family: sans-serif; background-color: #f4f6f9; color: #333; margin: 10px; padding: 0; }
            h1 { font-size: 20px; color: #0044cc; text-align: center; margin-bottom: 15px; font-weight:bold; }
            h2 { font-size: 15px; border-left: 4px solid #0044cc; padding-left: 8px; margin-top: 20px; color: #222; }
            .card { background-color: #ffffff; border: 1px solid #dddddd; padding: 12px; margin-bottom: 12px; }
            table { width: 100%; border-collapse: collapse; margin-top: 5px; }
            th { background-color: #0044cc; color: white; font-size: 12px; padding: 6px; text-align: left; }
            td { padding: 7px; border-bottom: 1px solid #eeeeee; font-size: 13px; }
            .status-wait { color: green; font-weight: bold; }
            .status-play { color: red; font-weight: bold; }
            .btn { display: inline-block; background-color: #0044cc; color: white; padding: 4px 10px; text-decoration: none; font-size: 11px; border-radius: 2px; }
        </style>
    </head>
    <body>
        <h1>🎮 하하하게임 하이브리드 허브 (TCP + UDP)</h1>
        <p style="font-size:11px; text-align:center; color:#666;">실시간 통신 트래픽: <b>UDP 고속 모드 활성화됨</b></p>

        <h2>🌐 온라인 실시간 방 목록 (UDP 연동)</h2>
        <div class="card" style="padding:0;">
            <table>
                <tr><th>방 이름</th><th>선택된 맵</th><th>방장</th><th>인원</th><th>상태</th></tr>
    `;
    activeRooms.forEach(room => {
        html += `
                <tr>
                    <td><b>${escapeHtml(room.roomName)}</b></td>
                    <td>${escapeHtml(room.mapTitle)}</td>
                    <td>${escapeHtml(room.host)}</td>
                    <td><b>${room.players.length} / ${room.maxPlayers} 명</b></td>
                    <td class="${room.status === '대기중' ? 'status-wait' : 'status-play'}">${room.status}</td>
                </tr>`;
    });
    if (activeRooms.length === 0) html += `<tr><td colspan="5" style="text-align:center; padding:15px; color:#999;">활성화된 방이 없습니다.</td></tr>`;
    
    html += `
            </table>
        </div>

        <h2>🔥 인기 다운로드 맵 Top 5</h2>
        <div class="card">
    `;
    popularMaps.forEach(map => {
        html += `<p style="font-size:13px;"><b>${escapeHtml(map.title)}</b> - ${escapeHtml(map.description)} (조회수: ${map.views})<br><a href="${map.downloadPage}" class="btn">다운로드 (.json)</a></p>`;
    });
    if (popularMaps.length === 0) html += `<p style="font-size:12px; color:#999;">등록된 맵이 없습니다.</p>`;

    html += `</div>
        <h2>🏆 실시간 기여 랭킹</h2>
        <div class="card" style="padding:0;">
            <table>
                <tr><th>순위</th><th>학생 번호</th><th>포인트</th></tr>
    `;
    ranking.forEach((user, index) => {
        html += `<tr><td><b>${index + 1}등</b></td><td>${escapeHtml(user.username)}</td><td>${user.points || 0} P</td></tr>`;
    });
    if (ranking.length === 0) html += `<tr><td colspan="3" style="text-align:center; padding:10px; color:#999;">가입된 유저가 없습니다.</td></tr>`;
    html += `</table></div>
    </body>
    </html>`;
    res.setHeader('Content-Type', 'text/html; charset=utf-8');
    res.status(200).send(html);
});

// -------------------------------------------------------------------------
// 🌐 [TCP/HTTP] 계정 인증 및 맵 제어 API
// -------------------------------------------------------------------------
app.get('/api/auth/public-key', (req, res) => res.json({ publicKey: PUBLIC_KEY }));

app.post('/api/auth/signin', async (req, res) => {
    const { username, encryptedPassword } = req.body;
    if (!username || !encryptedPassword) return res.status(400).json({ error: 'Missing params' });
    if (!/^\d{1,2}-\d{1,2}-\d{1,2}$/.test(username)) return res.status(400).json({ error: 'Format error' });

    const password = decryptPassword(encryptedPassword);
    if (!password) return res.status(400).json({ error: 'Decryption failed' });

    const users = await loadUsers();
    let user = users[username];

    if (!user) {
        if (password !== '1234') return res.status(400).json({ error: 'Default is 1234' });
        const salt = await bcrypt.genSalt(11);
        users[username] = { username, passwordHash: await bcrypt.hash(password, salt), points: 10 };
        await saveUsers(users);
        user = users[username];
    } else {
        const isMatch = await bcrypt.compare(password, user.passwordHash);
        if (!isMatch) return res.status(401).json({ error: 'Password mismatch' });
    }

    const token = jwt.sign({ username: user.username }, JWT_SECRET, { expiresIn: '30d' });
    res.json({ message: 'Success', token });
});

// 방 생성 API
app.post('/api/rooms', authenticateToken, (req, res) => {
    const { roomName, mapTitle, maxPlayers } = req.body;
    const roomId = 'room_' + Date.now();
    gameRooms[roomId] = {
        id: roomId, roomName, mapTitle, host: req.user.username,
        maxPlayers: parseInt(maxPlayers) || 4, players: [req.user.username], status: '대기중'
    };
    res.status(201).json({ message: 'Room created', roomId, room: gameRooms[roomId] });
});

// 방 입장 API
app.post('/api/rooms/:id/join', authenticateToken, (req, res) => {
    const room = gameRooms[req.params.id];
    if (!room) return res.status(404).json({ error: 'Not found' });
    if (room.players.length >= room.maxPlayers) return res.status(400).json({ error: 'Full' });
    if (!room.players.includes(req.user.username)) room.players.push(req.user.username);
    res.json({ message: 'Joined', room });
});

// 중간에 나가기 및 방 삭제 시스템 API
app.post('/api/rooms/:id/leave', authenticateToken, (req, res) => {
    const room = gameRooms[req.params.id];
    if (!room) return res.status(404).json({ error: 'Not found' });
    
    room.players = room.players.filter(p => p !== req.user.username);
    
    // 방장이 나가거나 생존 인원이 0명이면 방을 터트려 메모리 정리
    if (room.host === req.user.username || room.players.length === 0) {
        delete gameRooms[req.params.id];
        // 연관된 UDP 클라이언트 세션들도 같이 안전 청소
        Object.keys(udpClients).forEach(key => {
            if (udpClients[key].roomId === req.params.id) delete udpClients[key];
        });
        return res.json({ message: 'Room destroyed' });
    }
    res.json({ message: 'Left', room });
});

// 맵 파일 로컬 JSON 영구 저장 및 포인트 지급 API
app.post('/api/maps', authenticateToken, async (req, res) => {
    const { title, description, mapData } = req.body;
    const mapId = 'map_' + Date.now();
    await fs.writeFile(path.join(MAP_FILES_DIR, `${mapId}.json`), JSON.stringify(typeof mapData === 'string' ? JSON.parse(mapData) : mapData, null, 2), 'utf8');
    
    const maps = await loadMaps();
    maps[mapId] = { id: mapId, title, description, downloadPage: `http://116.47.180.199:19132/api/maps/${mapId}/download`, uploader: req.user.username, views: 0, createdAt: new Date() };
    await saveMaps(maps);

    // 맵 업로드 기여 학생에게 포인트 부여 보완
    const users = await loadUsers();
    if (users[req.user.username]) {
        users[req.user.username].points = (users[req.user.username].points || 0) + 100;
        await saveUsers(users);
    }

    res.status(201).json({ message: 'Success', mapId });
});

// 맵 다운로드 기능
app.get('/api/maps/:id/download', async (req, res) => {
    const maps = await loadMaps();
    const map = maps[req.params.id];
    if (!map) return res.status(404).send("Not found");
    map.views += 1; await saveMaps(maps);
    res.download(path.join(MAP_FILES_DIR, `${req.params.id}.json`), `${map.title}.json`);
});

// -------------------------------------------------------------------------
// 🚀 [핵심 확장] 실시간 고속 UDP 멀티플레이어 서버 엔진
// -------------------------------------------------------------------------
const udpServer = dgram.createSocket('udp4');

udpServer.on('message', (msg, rinfo) => {
    try {
        const packet = JSON.parse(msg.toString());
        const clientKey = `${rinfo.address}:${rinfo.port}`;

        switch (packet.type) {
            case 'REGISTER': 
                udpClients[clientKey] = {
                    username: packet.username,
                    roomId: packet.roomId,
                    address: rinfo.address,
                    port: rinfo.port
                };
                console.log(`[UDP 등록] 유저: ${packet.username} (${clientKey})`);
                break;

            case 'MOVE': 
            case 'ACTION':
                const currentClient = udpClients[clientKey];
                if (currentClient && currentClient.roomId) {
                    const relayData = Buffer.from(JSON.stringify({
                        type: 'UPDATE',
                        username: currentClient.username,
                        data: packet.data
                    }));

                    Object.values(udpClients).forEach(client => {
                        if (client.roomId === currentClient.roomId && client.username !== currentClient.username) {
                            udpServer.send(relayData, 0, relayData.length, client.port, client.address);
                        }
                    });
                }
                break;

            case 'LEAVE': 
                console.log(`[UDP 퇴장] ${udpClients[clientKey]?.username || clientKey}`);
                delete udpClients[clientKey];
                break;
        }
    } catch (e) {
        // 비정상 수신 예외 제어
    }
});

udpServer.on('listening', () => {
    const address = udpServer.address();
    console.log(`[UDP 멀티플레이어 엔진이 활성화되었습니다 - 포트 ${address.port}]`);
});

// -------------------------------------------------------------------------
// ⚙️ 하이브리드 통합 서버 구동
// -------------------------------------------------------------------------
app.listen(PORT, '0.0.0.0', () => {
    console.log(`[TCP 웹 인프라 포트 ${PORT} 열림 - http://116.47.180.199:${PORT}]`);
});

udpServer.bind(PORT, '0.0.0.0');