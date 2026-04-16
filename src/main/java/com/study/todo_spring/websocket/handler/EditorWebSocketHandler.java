package com.study.todo_spring.websocket.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * EditorWebSocketHandler - Y.js 실시간 편집 WebSocket 핸들러
 *
 * [역할]
 * Y.js는 CRDT 기반으로 클라이언트끼리 문서 상태를 동기화한다.
 * 이 서버는 Y.js 프로토콜을 직접 해석하지 않고,
 * 클라이언트가 보낸 바이너리 업데이트를 같은 방의 다른 클라이언트에게 그대로 전달(릴레이)한다.
 *
 * [BinaryWebSocketHandler]
 * Y.js 메시지는 Uint8Array 바이너리 형식이다.
 * 텍스트 메시지를 다루는 TextWebSocketHandler 대신 BinaryWebSocketHandler를 상속한다.
 *
 * [NestJS 비교]
 * NestJS의 @WebSocketGateway + @SubscribeMessage() 조합과 같은 역할이다.
 * 차이점: NestJS는 이벤트 이름 기반으로 메시지를 구분하지만,
 *        여기서는 Y.js가 알아서 처리하므로 서버는 그냥 전달만 한다.
 *
 * [Thread Safety]
 * 여러 클라이언트가 동시에 접속하므로 ConcurrentHashMap을 사용한다.
 * 일반 HashMap은 동시 접근 시 데이터 손상이 발생할 수 있다.
 */
@Slf4j
@Component
public class EditorWebSocketHandler extends BinaryWebSocketHandler {

    /**
     * docId → 현재 연결된 세션 목록
     * 예: { "my-doc": [SessionA, SessionB], "other-doc": [SessionC] }
     */
    private final Map<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    /**
     * docId → 지금까지 수신된 Y.js 업데이트 목록
     * 나중에 접속한 클라이언트에게 기존 문서 상태를 전달하기 위해 저장한다.
     * (Y.js 업데이트는 누적 적용되므로 전부 보내야 현재 상태가 됨)
     */
    private final Map<String, List<byte[]>> docUpdates = new ConcurrentHashMap<>();

    // ── 연결 수립 ──────────────────────────────────────────────────────────────

    /**
     * 새 클라이언트가 WebSocket 연결을 맺었을 때 호출된다.
     *
     * [순서가 중요하다]
     * 1. 먼저 기존 업데이트를 새 클라이언트에게 전송한다.
     * 2. 전송이 끝난 후 room에 추가한다.
     *
     * room에 먼저 추가하면, 다른 스레드의 릴레이가 초기 동기화 전송과 동시에
     * 같은 세션에 sendMessage()를 호출해 BINARY_PARTIAL_WRITING이 발생한다.
     * room 추가를 나중에 함으로써 초기 전송 중에는 이 세션이 릴레이 대상에서 제외된다.
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String docId = extractDocId(session);

        // 기존 업데이트를 새 클라이언트에게 순서대로 전송 (room 추가 전)
        List<byte[]> updates = docUpdates.getOrDefault(docId, Collections.emptyList());
        for (byte[] update : updates) {
            session.sendMessage(new BinaryMessage(update));
        }

        // 초기 동기화 완료 후 room에 추가 → 이제부터 릴레이 대상이 됨
        rooms.computeIfAbsent(docId, k -> ConcurrentHashMap.newKeySet()).add(session);

        log.info("[WS] 연결됨 - docId: {}, sessionId: {}, 현재 접속자: {}명",
                docId, session.getId(), rooms.get(docId).size());
    }

    // ── 메시지 수신 ────────────────────────────────────────────────────────────

    /**
     * 클라이언트로부터 바이너리 메시지(Y.js 업데이트)를 수신했을 때 호출된다.
     *
     * 1. 메시지 타입을 확인한다.
     * 2. sync 메시지(타입 0)만 저장한다. (나중에 접속할 클라이언트를 위해)
     * 3. 같은 방의 다른 클라이언트에게 그대로 전달한다.
     *
     * [y-websocket 프로토콜 — 메시지 첫 번째 바이트]
     * 0 = sync  (문서 상태, 영속) → 저장 + 릴레이
     * 1 = awareness (커서/프레즌스, 일시적) → 릴레이만
     *
     * awareness 메시지를 저장하면 나중에 접속한 클라이언트에게
     * 이미 나간 사용자의 커서 데이터가 전송되는 ghost cursor 문제가 발생한다.
     */
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        String docId = extractDocId(session);
        byte[] data = message.getPayload().array();

        // sync 메시지(타입 0)만 누적 저장, awareness(타입 1)는 저장하지 않음
        // CopyOnWriteArrayList: 쓰기 시 내부 배열을 복사해 thread-safe하게 add()를 처리한다.
        // afterConnectionEstablished에서 순회 중에 add()가 발생해도 ConcurrentModificationException이 없다.
        if (data.length > 0 && data[0] == 0) {
            docUpdates.computeIfAbsent(docId, k -> new CopyOnWriteArrayList<>()).add(data);
        }

        // 같은 방의 다른 세션에게 릴레이 (모든 메시지 타입 전달)
        //
        // [동시성 문제 — BINARY_PARTIAL_WRITING]
        // WebSocketSession.sendMessage()는 thread-safe하지 않다.
        // Y.js는 타이핑할 때마다 수십 개의 작은 메시지를 빠르게 전송하고,
        // Tomcat은 수신 메시지마다 별도 스레드를 할당한다.
        // 결과적으로 여러 스레드가 동시에 같은 세션에 sendMessage()를 호출하면
        // "BINARY_PARTIAL_WRITING" IllegalStateException이 발생하며 세션이 강제 종료된다.
        //
        // 해결: 세션 객체를 모니터 락으로 사용해 한 번에 하나의 스레드만 전송하게 직렬화한다.
        Set<WebSocketSession> room = rooms.getOrDefault(docId, Collections.emptySet());
        for (WebSocketSession other : room) {
            if (other.isOpen() && !other.getId().equals(session.getId())) {
                synchronized (other) {
                    if (other.isOpen()) {
                        other.sendMessage(new BinaryMessage(data));
                    }
                }
            }
        }
    }

    // ── 연결 종료 ──────────────────────────────────────────────────────────────

    /**
     * 클라이언트 연결이 끊겼을 때 호출된다.
     * 방에서 해당 세션을 제거한다.
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String docId = extractDocId(session);
        Set<WebSocketSession> room = rooms.get(docId);

        if (room != null) {
            room.remove(session);
            log.info("[WS] 연결 종료 - docId: {}, sessionId: {}, 남은 접속자: {}명",
                    docId, session.getId(), room.size());
        }
    }

    // ── private 헬퍼 ──────────────────────────────────────────────────────────

    /**
     * WebSocket 세션의 URL 경로에서 docId를 추출한다.
     * /ws/editor/my-doc → "my-doc"
     */
    private String extractDocId(WebSocketSession session) {
        String path = session.getUri().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
