# 💬 Java 멀티 클라이언트 콘솔 채팅 프로그램

이 프로젝트는 **Java 네트워크 프로그래밍**을 활용하여 구현한 **콘솔 기반 채팅 애플리케이션**입니다.  
중앙 서버를 중심으로 여러 사용자가 동시에 접속해 대화를 주고받을 수 있으며, 기본적인 메시징을 넘어 **닉네임 관리, 멘션, 파일 전송, 로그 관리** 등 다양한 기능을 지원합니다.  

---

## ✨ 주요 기능

- **멀티 사용자 실시간 채팅**  
  - 여러 클라이언트가 서버에 접속하여 동일한 채팅룸에서 대화 가능  

- **사용자 관리**  
  - 닉네임 변경 (중복 방지 포함)  
  - 접속자 목록 조회  

- **메시징**  
  - 발신자와 타임스탬프 자동 표시  
  - `@닉네임` 멘션 기능  
  - 오프라인 사용자 멘션 시 알림 처리  

- **파일 전송**  
  - 이미지 전송 및 다운로드 기능  
  - 안전한 수신 절차  

- **로그 관리**  
  - 서버에서 접속/종료/메시지/명령어를 파일로 기록  
  - 클라이언트가 `/logs` 명령어로 요청 시 확인 가능  

---

## 🔧 프로젝트 구조

```mermaid
sequenceDiagram
    participant Client
    participant ChatServer
    participant ClientService

    Client->>ChatServer: Connect
    ChatServer->>ClientService: Create thread
    Client->>ClientService: Send message/command
    ClientService->>ChatServer: Process & broadcast
    ChatServer->>Client: Broadcast messages
