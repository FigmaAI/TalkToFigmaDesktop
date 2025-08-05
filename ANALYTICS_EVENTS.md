# Google Analytics 이벤트 목록

## 📊 개요

이 문서는 TalkToFigma Desktop 앱에서 Google Analytics 4로 전송되는 모든 이벤트를 정리한 것입니다.

**측정 ID**: 환경변수 `GOOGLE_ANALYTICS_ID`에서 로드  
**API Secret**: 환경변수 `GOOGLE_ANALYTICS_API_SECRET`에서 로드

---

## 🚀 앱 라이프사이클 이벤트

### 1. 앱 시작 (app_start)
**전송 시점**: 앱 시작 시 자동 전송  
**이벤트 타입**: 자동 수집

```json
{
  "event_name": "app_start",
  "parameters": {
    "app_version": "1.0.5",
    "os_info": "macOS 14.6.0 (arm64)"
  }
}
```

### 2. 앱 종료 (user_action)
**전송 시점**: 사용자가 Exit 버튼 클릭 시  
**이벤트 타입**: 사용자 액션

```json
{
  "event_name": "user_action",
  "parameters": {
    "action": "app_exit",
    "category": "app_lifecycle",
    "label": "user_initiated"
  }
}
```

---

## 🖥️ 사용자 인터페이스 이벤트

### 3. MCP 설정 열기 (user_action)
**전송 시점**: MCP Configuration 메뉴 클릭 시

```json
{
  "event_name": "user_action",
  "parameters": {
    "action": "open_mcp_config",
    "category": "ui_interaction",
    "label": "tray_menu"
  }
}
```

### 4. MCP 설정 복사 (user_action)
**전송 시점**: MCP 설정 복사 버튼 클릭 시

```json
{
  "event_name": "user_action",
  "parameters": {
    "action": "copy_mcp_config",
    "category": "ui_interaction",
    "label": "config_dialog"
  }
}
```

### 5. 로그 보기 (user_action)
**전송 시점**: View Logs 메뉴 클릭 시

```json
{
  "event_name": "user_action",
  "parameters": {
    "action": "view_logs",
    "category": "ui_interaction",
    "label": "tray_menu"
  }
}
```

### 6. 로그 새로고침 (user_action)
**전송 시점**: 로그 뷰어에서 새로고침 버튼 클릭 시

```json
{
  "event_name": "user_action",
  "parameters": {
    "action": "refresh_logs",
    "category": "ui_interaction",
    "label": "log_viewer"
  }
}
```

### 7. 로그 지우기 (user_action)
**전송 시점**: 로그 뷰어에서 지우기 버튼 클릭 시

```json
{
  "event_name": "user_action",
  "parameters": {
    "action": "clear_logs",
    "category": "ui_interaction",
    "label": "log_viewer"
  }
}
```

### 8. 로그 복사 (user_action)
**전송 시점**: 로그 뷰어에서 복사 버튼 클릭 시

```json
{
  "event_name": "user_action",
  "parameters": {
    "action": "copy_logs",
    "category": "ui_interaction",
    "label": "log_viewer"
  }
}
```

### 9. 튜토리얼 보기 (user_action)
**전송 시점**: Tutorial 메뉴 클릭 시

```json
{
  "event_name": "user_action",
  "parameters": {
    "action": "view_tutorial",
    "category": "ui_interaction",
    "label": "tray_menu"
  }
}
```

---

## 🖥️ 서버 관리 이벤트

### 10. WebSocket 서버 시작 (user_action)
**전송 시점**: WebSocket 서버 시작 시  
**성능 데이터 포함**

```json
{
  "event_name": "user_action",
  "parameters": {
    "action": "start_websocket_server",
    "category": "server_management",
    "label": "port_3055",
    "value": 150  // 시작 시간 (밀리초)
  }
}
```

### 11. WebSocket 서버 중지 (user_action)
**전송 시점**: WebSocket 서버 수동 중지 시

```json
{
  "event_name": "user_action",
  "parameters": {
    "action": "stop_websocket_server",
    "category": "server_management",
    "label": "manual_stop"
  }
}
```

### 12. MCP 서버 시작 (user_action)
**전송 시점**: MCP 서버 시작 시  
**성능 데이터 포함**

```json
{
  "event_name": "user_action",
  "parameters": {
    "action": "start_mcp_server",
    "category": "server_management",
    "label": "port_3056",
    "value": 200  // 시작 시간 (밀리초)
  }
}
```

### 13. MCP 서버 중지 (user_action)
**전송 시점**: MCP 서버 수동 중지 시

```json
{
  "event_name": "user_action",
  "parameters": {
    "action": "stop_mcp_server",
    "category": "server_management",
    "label": "manual_stop"
  }
}
```

### 14. 모든 서버 강제 종료 (user_action)
**전송 시점**: Kill All Servers 버튼 클릭 시

```json
{
  "event_name": "user_action",
  "parameters": {
    "action": "kill_all_servers",
    "category": "emergency_action",
    "label": "force_stop"
  }
}
```

---

## 💥 크래시 및 오류 이벤트

### 15. 앱 크래시 (app_crash)
**전송 시점**: 예외 발생 시 자동 전송  
**이벤트 타입**: 자동 수집

```json
{
  "event_name": "app_crash",
  "parameters": {
    "exception_type": "NullPointerException",
    "exception_message": "Cannot invoke method on null object",
    "stack_trace": "전체 스택 트레이스",
    "thread_name": "main",
    "thread_id": 1,
    "app_version": "1.0.5",
    "os_name": "macOS",
    "os_version": "14.6.0",
    "java_version": "21.0.9",
    "total_memory_mb": 512,
    "free_memory_mb": 256,
    "max_memory_mb": 1024,
    "available_processors": 8,
    "timestamp": 1703123456789
  }
}
```

### 16. 일반 오류 (app_error)
**전송 시점**: 크래시가 아닌 일반 오류 발생 시  
**이벤트 타입**: 수동 전송

```json
{
  "event_name": "app_error",
  "parameters": {
    "error_message": "오류 메시지",
    "error_code": "ERROR_001",  // 선택사항
    "context": "오류 발생 컨텍스트"
  }
}
```

---

## 🔗 Figma 연결 이벤트 (준비됨)

### 17. Figma 연결 (figma_connection)
**전송 시점**: Figma 플러그인 연결/해제 시  
**이벤트 타입**: 수동 전송 (아직 구현되지 않음)

```json
{
  "event_name": "figma_connection",
  "parameters": {
    "connection_type": "connect",  // "connect" | "disconnect"
    "success": true,  // true | false
    "error_message": "연결 실패 이유"  // 선택사항
  }
}
```

---

## 🔧 MCP 요청 이벤트 (준비됨)

### 18. MCP 요청 (mcp_request)
**전송 시점**: MCP 도구 호출 시  
**이벤트 타입**: 수동 전송 (아직 구현되지 않음)

```json
{
  "event_name": "mcp_request",
  "parameters": {
    "request_type": "get_document_info",  // "get_document_info" | "create_rectangle" | "set_text_content"
    "success": true,  // true | false
    "duration_ms": 150,  // 응답 시간 (밀리초)
    "error_message": "요청 실패 이유"  // 선택사항
  }
}
```

---

## 📈 Google Analytics에서 확인 방법

### 실시간 보고서
1. [Google Analytics](https://analytics.google.com) 접속
2. 속성: `G-C17YYSSXS8` 선택
3. `보고서` → `실시간` → `이벤트` 확인

### 이벤트 분석
1. `보고서` → `참여도` → `이벤트` 확인
2. 이벤트별 발생 빈도 및 패턴 분석

### 사용자 행동 분석
1. `탐색` → `자유 형식` 선택
2. 차원: `이벤트 이름`, `사용자` 추가
3. 지표: `이벤트 수` 추가

---

## 🛠️ 설정 정보

### analytics.properties
```properties
# Use environment variables for sensitive information
analytics.measurement.id=${GOOGLE_ANALYTICS_ID}
analytics.api.secret=${GOOGLE_ANALYTICS_API_SECRET}
analytics.debug.mode=false
analytics.crash.reporting.enabled=true
analytics.user.tracking.enabled=true
analytics.figma.tracking.enabled=true
analytics.mcp.tracking.enabled=true
```

### 환경 변수 (.envrc)
```bash
# Set your Google Analytics credentials here
export GOOGLE_ANALYTICS_ID="your_measurement_id_here"
export GOOGLE_ANALYTICS_API_SECRET="your_api_secret_here"
```

---

## 📊 이벤트 카테고리별 분류

| 카테고리 | 이벤트 수 | 설명 |
|---------|----------|------|
| `app_lifecycle` | 2 | 앱 시작/종료 |
| `ui_interaction` | 7 | 사용자 인터페이스 상호작용 |
| `server_management` | 4 | 서버 시작/중지 |
| `emergency_action` | 1 | 긴급 액션 |
| `app_crash` | 1 | 크래시 리포트 |
| `app_error` | 1 | 일반 오류 |
| `figma_connection` | 1 | Figma 연결 (준비됨) |
| `mcp_request` | 1 | MCP 요청 (준비됨) |

**총 이벤트 수**: 18개 (구현됨: 16개, 준비됨: 2개)

---

## 🔄 업데이트 히스토리

- **v1.0.5**: 기본 이벤트 구현 (app_start, server_management)
- **v1.0.6**: UI 인터랙션 이벤트 추가
- **v1.0.7**: 성능 데이터 포함 서버 시작 이벤트
- **v1.0.8**: 크래시 리포트 및 오류 이벤트 (예정)
- **v1.0.9**: Figma 연결 및 MCP 요청 이벤트 (예정) 