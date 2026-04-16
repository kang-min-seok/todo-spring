package com.study.todo_spring.websocket.config;

import com.study.todo_spring.websocket.handler.EditorWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocketConfig - WebSocket 엔드포인트 등록
 *
 * [STOMP vs Raw WebSocket]
 * Phase 3는 STOMP 대신 Raw WebSocket을 사용한다.
 * Y.js가 내부적으로 바이너리(Uint8Array)로 메시지를 전송하기 때문에
 * 텍스트 프레임 기반의 STOMP와 호환되지 않는다.
 *
 * [@EnableWebSocket]
 * Raw WebSocket 기능을 활성화한다.
 * STOMP를 쓸 때는 @EnableWebSocketMessageBroker를 사용하지만,
 * Raw WebSocket은 @EnableWebSocket이다.
 *
 * [NestJS 비교]
 * NestJS의 @WebSocketGateway()가 하는 역할과 같다.
 * 어떤 경로로 WebSocket 연결을 받을지 등록하는 설정이다.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final EditorWebSocketHandler editorWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
            .addHandler(editorWebSocketHandler, "/ws/editor/**")
            // Y.js WebsocketProvider는 ws://host/ws/editor/{docId} 형태로 연결한다.
            // /** 로 와일드카드를 잡아야 /ws/editor/my-doc, /ws/editor/other-doc 모두 처리 가능.
            .setAllowedOrigins("*");
            // 개발 환경에서는 * 허용. 운영에서는 특정 도메인만 허용할 것.
    }
}
