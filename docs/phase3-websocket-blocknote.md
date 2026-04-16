# Phase 3 — WebSocket + Y.js 실시간 협업 에디터 (백엔드)

---

## 1. 의존성 설치

`build.gradle.kts`에 한 줄을 추가합니다.

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-websocket")
}
```

- `spring-boot-starter-websocket` — Spring WebSocket + Tomcat WebSocket 엔진을 한 번에 설치

---

## 2. WebSocket 설정 — `WebSocketConfig.java`

```java
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final EditorWebSocketHandler editorWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
            .addHandler(editorWebSocketHandler, "/ws/editor/**")
            .setAllowedOrigins("*");
    }
}
```

**`@EnableWebSocket`:**
Raw WebSocket 기능을 활성화하는 어노테이션입니다.
STOMP를 쓸 때는 `@EnableWebSocketMessageBroker`를 사용하지만, Raw WebSocket은 `@EnableWebSocket`입니다.

**STOMP vs Raw WebSocket:**

| | STOMP | Raw WebSocket |
|---|---|---|
| 메시지 형식 | 텍스트 프레임 | 바이너리 |
| Y.js 호환 | ❌ | ✅ |
| 적합한 용도 | 채팅, 알림 | 협업 편집 |

Y.js는 내부적으로 `Uint8Array` 바이너리 메시지를 사용하기 때문에 텍스트 기반의 STOMP 대신 Raw WebSocket을 사용합니다.

**와일드카드 경로 `/**`:**
`/ws/editor/my-doc`, `/ws/editor/room-1` 처럼 docId가 경로에 포함된 URL을 하나의 핸들러로 처리합니다.

**NestJS 비교:**
NestJS의 `@WebSocketGateway()` 데코레이터와 같은 역할입니다.

---

## 3. WebSocket 핸들러 — `EditorWebSocketHandler.java`

### 전체 구조

```java
@Slf4j
@Component
public class EditorWebSocketHandler extends BinaryWebSocketHandler {

    // docId → 현재 연결된 세션 목록
    private final Map<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    // docId → sync 업데이트 누적 목록 (늦은 참여자에게 전송용)
    private final Map<String, List<byte[]>> docUpdates = new ConcurrentHashMap<>();
}
```

**`BinaryWebSocketHandler`:**
Y.js 메시지가 바이너리 형식이기 때문에 `TextWebSocketHandler` 대신 `BinaryWebSocketHandler`를 상속합니다.

**`ConcurrentHashMap`:**
여러 클라이언트가 동시에 접속하므로 일반 `HashMap` 대신 사용합니다.

---

### 연결 수립 — `afterConnectionEstablished`

```java
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
```

**순서가 중요합니다.**
기존 업데이트 전송을 완료한 뒤 room에 추가합니다.
반대 순서(room 먼저 추가 → 전송)로 하면, 초기 전송 도중 다른 스레드의 릴레이도 같은 세션에 `sendMessage()`를 동시에 호출해 `BINARY_PARTIAL_WRITING` 예외가 발생합니다.

---

### 메시지 수신 — `handleBinaryMessage`

```java
@Override
protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
    String docId = extractDocId(session);
    byte[] data = message.getPayload().array();

    // sync 메시지(타입 0)만 저장. awareness(타입 1)는 릴레이만.
    if (data.length > 0 && data[0] == 0) {
        docUpdates.computeIfAbsent(docId, k -> new CopyOnWriteArrayList<>()).add(data);
    }

    // 같은 방의 다른 세션에게 릴레이
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
```

**`CopyOnWriteArrayList`:**
쓰기(`add`) 시 내부 배열을 복사해서 thread-safe하게 동작합니다.
`afterConnectionEstablished`에서 순회 중에 `add()`가 발생해도 `ConcurrentModificationException`이 없습니다.

**`synchronized (other)`:**
`WebSocketSession.sendMessage()`는 thread-safe하지 않습니다.
세션 객체를 모니터 락으로 사용해 한 번에 하나의 스레드만 해당 세션에 전송하도록 직렬화합니다.
`synchronized` 블록 안에서 `isOpen()`을 한 번 더 확인하는 이유: 락을 획득하기 전에 세션이 닫혔을 수 있기 때문입니다(TOCTOU 방지).

---

### 연결 종료 — `afterConnectionClosed`

```java
@Override
public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    String docId = extractDocId(session);
    Set<WebSocketSession> room = rooms.get(docId);
    if (room != null) room.remove(session);
}
```

---

## 4. Y.js 메시지 프로토콜

y-websocket 바이너리 메시지의 첫 번째 바이트가 메시지 타입입니다.

```
┌──────────────┬──────────────────────────────────┐
│  첫 번째 바이트 │  나머지 페이로드                     │
├──────────────┼──────────────────────────────────┤
│      0       │  sync (문서 상태 동기화)              │
│      1       │  awareness (커서/접속자 정보)          │
└──────────────┴──────────────────────────────────┘
```

**sync 메시지 (타입 0):**
Y.js 문서의 편집 내용입니다. 영속 데이터이므로 서버에 저장해두었다가 나중에 접속한 클라이언트에게 전송해 문서 상태를 복원합니다.

**awareness 메시지 (타입 1):**
커서 위치, 접속자 정보 등 일시적(ephemeral) 상태입니다. 저장하지 않고 릴레이만 합니다.
저장하면 이미 나간 사용자의 커서 데이터가 새 참여자에게 전달되는 ghost cursor 문제가 발생합니다.

---

## 5. 실행

```bash
# MySQL 컨테이너 먼저 실행
docker compose up -d

# 백엔드 실행
./gradlew bootRun
```

---

## 트러블슈팅

---

### 문제 1 — Ghost Cursor

**증상:**
사용자가 에디터를 닫고 나갔음에도 다른 참여자 화면에 그 사람의 커서가 계속 표시됩니다.

**원인:**
awareness 메시지(타입 1)를 sync 메시지(타입 0)와 구분하지 않고 `docUpdates`에 전부 저장했습니다.
나중에 접속한 클라이언트에게 이미 나간 사용자의 awareness 데이터가 그대로 전달됩니다.

```java
// 잘못된 코드
docUpdates.computeIfAbsent(docId, k -> new ArrayList<>()).add(data); // 타입 구분 없이 전부 저장
```

**수정:**
```java
// 첫 번째 바이트로 메시지 타입 확인 후 sync만 저장
if (data.length > 0 && data[0] == 0) {
    docUpdates.computeIfAbsent(docId, k -> new CopyOnWriteArrayList<>()).add(data);
}
```

---

### 문제 2 — BINARY_PARTIAL_WRITING (1차)

**증상:**
로그에 아래 에러가 반복 출력되며 WebSocket 세션이 계속 끊기고 실시간 반영이 수십 초씩 지연됩니다.

```
IllegalStateException: The remote endpoint was in state [BINARY_PARTIAL_WRITING]
which is an invalid state for called method
```

**원인:**
`WebSocketSession.sendMessage()`는 thread-safe하지 않습니다.
Y.js는 타이핑할 때마다 수십 개의 작은 메시지를 빠르게 전송하고, Tomcat은 수신 메시지마다 별도 스레드를 할당합니다.
여러 스레드가 동시에 같은 세션에 `sendMessage()`를 호출하면 예외가 터지며 세션이 강제 종료됩니다.

```
스레드1: 세션B에 sendMessage() → BINARY_PARTIAL_WRITING 상태 진입
스레드2: 세션B에 sendMessage() → 💥 IllegalStateException → 세션B 강제 종료
                                                           → y-websocket 재연결
                                                           → 누적 업데이트 재전송
                                                           → 또 충돌 → 반복
```

**수정:**
```java
synchronized (other) {
    if (other.isOpen()) {
        other.sendMessage(new BinaryMessage(data));
    }
}
```

세션 객체를 락으로 사용해 한 번에 하나의 스레드만 해당 세션에 전송하도록 직렬화합니다.

---

### 문제 3 — ConcurrentModificationException + BINARY_PARTIAL_WRITING 재발 (2차)

**증상:**
한동안 잘 동작하다가 갑자기 소켓 연결이 끊겼다 연결되기를 반복하며, 로그에 두 가지 예외가 동시에 출력됩니다.

```
java.util.ConcurrentModificationException
    at EditorWebSocketHandler.afterConnectionEstablished(EditorWebSocketHandler.java:75)

IllegalStateException: The remote endpoint was in state [BINARY_PARTIAL_WRITING]
    at EditorWebSocketHandler.handleBinaryMessage(EditorWebSocketHandler.java:121)
```

**원인 A — `ConcurrentModificationException`:**
`docUpdates`의 값이 일반 `ArrayList`였기 때문입니다.
`afterConnectionEstablished`에서 리스트를 순회하는 도중, 다른 스레드의 `handleBinaryMessage`가 같은 리스트에 `add()`를 호출하면 `ConcurrentModificationException`이 발생합니다.

```
스레드1 (afterConnectionEstablished): for (byte[] update : updates) 순회 중
스레드2 (handleBinaryMessage):        docUpdates.get(docId).add(data)   ← 💥 동시 수정
```

**원인 B — `BINARY_PARTIAL_WRITING` 재발:**
문제 2의 `synchronized` 수정이 `handleBinaryMessage` 릴레이 루프에만 적용되었습니다.
`afterConnectionEstablished`에서 room에 세션을 **먼저 추가한 뒤** 초기 업데이트를 전송하는 순서 때문에, 전송 도중 다른 스레드의 릴레이도 같은 세션을 대상으로 `sendMessage()`를 동시에 호출하는 경로가 남아 있었습니다.

```
스레드1 (afterConnectionEstablished): 세션B를 room에 추가 → 세션B에 초기 업데이트 전송 중
스레드2 (handleBinaryMessage):        room에서 세션B 발견 → 세션B에 릴레이 전송   ← 💥 동시 접근
```

**수정 A — `CopyOnWriteArrayList` 사용:**
```java
// ArrayList → CopyOnWriteArrayList
docUpdates.computeIfAbsent(docId, k -> new CopyOnWriteArrayList<>()).add(data);
```
`CopyOnWriteArrayList`는 쓰기 시 내부 배열을 복사합니다.
순회 중에 다른 스레드가 `add()`를 해도 순회 중인 배열은 건드리지 않아 `ConcurrentModificationException`이 없습니다.

**수정 B — room 추가 순서 변경:**
```java
// 수정 전: room 추가 → 초기 업데이트 전송
rooms.computeIfAbsent(docId, k -> ConcurrentHashMap.newKeySet()).add(session); // ← 먼저
for (byte[] update : updates) { session.sendMessage(...); }                   // ← 나중에

// 수정 후: 초기 업데이트 전송 → room 추가
for (byte[] update : updates) { session.sendMessage(...); }                   // ← 먼저
rooms.computeIfAbsent(docId, k -> ConcurrentHashMap.newKeySet()).add(session); // ← 나중에
```

초기 전송이 완전히 끝난 후에 room에 추가하면, 전송 중에는 어떤 스레드도 이 세션을 릴레이 대상으로 찾을 수 없습니다.

---

## 구현 파일 목록

```
src/main/java/com/study/todo_spring/
└── websocket/
    ├── config/
    │   └── WebSocketConfig.java        ← WebSocket 엔드포인트 등록
    └── handler/
        └── EditorWebSocketHandler.java  ← 릴레이 핸들러 (동시성 처리 포함)
```
