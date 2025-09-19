package chat.server;

import java.io.*;
import java.net.Socket;

public class ClientService {

    ChatServer chatServer; // 서버와의 상호작용을 위한 참조
    Socket socket; // 클라이언트와 연결된 소켓
    DataInputStream dis; // 입력 스트림
    DataOutputStream dos; // 출력 스트림
    String chatName; // 클라이언트 닉네임

    public ClientService(ChatServer chatServer, Socket socket) throws IOException {
        this.chatServer = chatServer;
        this.socket = socket;
        dis = new DataInputStream(socket.getInputStream());
        dos = new DataOutputStream(socket.getOutputStream());

        chatName = dis.readUTF(); // 닉네임 수신
        if (chatServer.chatClientInfo.containsKey(chatName)) {
            send("[서버] 닉네임이 중복됩니다. 다른 닉네임을 입력해주세요.");
            socket.close();
            return;
        }
        chatServer.addClientInfo(this); // 서버에 클라이언트 등록
        chatServer.sendToAll(this, "[입장] " + chatName);
        receive(); // 메시지 수신 처리
    }

    public void receive() {
        new Thread(() -> {
            try {
                while (true) {
                    String msg = dis.readUTF(); // 클라이언트 메시지 수신
                    if (msg.startsWith("/rename:")) {
                        handleRename(msg); // 닉네임 변경 처리
                    } else if (msg.startsWith("/to:")) {
                        handleDirectMessage(msg); // 귓속말 처리
                    } else if (msg.startsWith("/logs")) {
                        handleLogsCommand(); // 로그 요청 처리
                    } else if (msg.startsWith("/img:")) {
                        handleImageTransfer(msg); // 이미지 전송 처리
                    } else if (msg.startsWith("/download:")) {
                        handleDownload(msg); // 이미지 다운로드 처리
                    } else if (msg.startsWith("/users")) {
                        chatServer.sendUsers(this); // 현재 접속자 목록 전송
                    } else {
                        chatServer.sendToAll(this, msg); // 일반 메시지 브로드캐스트
                    }
                }
            } catch (IOException e) {
                System.out.println(chatName + " 연결 종료");
            } finally {
                quit(); // 클라이언트 종료 처리
            }
        }).start(); // 별도 쓰레드에서 실행
    }

    // 닉네임 변경 처리
    public void handleRename(String msg) {
        String[] parts = msg.split(":", 2);
        if (parts.length < 2) {
            send("[서버] 닉네임 변경 형식이 잘못되었습니다. 사용법: /rename:새닉네임");
            return;
        }
        String newName = parts[1].trim();
        if (newName.isEmpty()) {
            send("[서버] 닉네임은 공백일 수 없습니다.");
            return;
        }
        synchronized (chatServer) {
            if (!chatServer.chatClientInfo.containsKey(newName)) {
                chatServer.removeClientInfo(this);
                String oldName = chatName;
                this.chatName = newName;
                chatServer.addClientInfo(this);
                chatServer.sendToAll(this, "[서버] " + oldName + "이(가) " + newName + "으로 닉네임을 변경했습니다.");
                chatServer.logCommand(oldName, msg, "닉네임 변경 성공");
            } else {
                send("[서버] 닉네임 중복. 변경 실패.");
                chatServer.logCommand(chatName, msg, "닉네임 변경 실패 - 중복");
            }
        }
    }

    // 귓속말 처리
    public void handleDirectMessage(String msg) {
        // 형식: /to:닉네임/메시지
        try {
            if (!msg.startsWith("/to:")) {
                send("[서버] 귓속말 형식 오류. 사용법: /to:닉네임/메시지");
                return;
            }
            String commandContent = msg.substring(4); // "/to:" 이후 내용
            int slashIndex = commandContent.indexOf('/');
            if (slashIndex == -1) {
                send("[서버] 귓속말 형식 오류. 사용법: /to:닉네임/메시지");
                return;
            }
            String recipient = commandContent.substring(0, slashIndex).trim();
            String message = commandContent.substring(slashIndex + 1).trim();

            if (recipient.isEmpty()) {
                send("[서버] 수신자 닉네임이 비어있습니다.");
                return;
            }

            synchronized (chatServer) {
                if (chatServer.chatClientInfo.containsKey(recipient)) {
                    ClientService recipientService = chatServer.chatClientInfo.get(recipient);
                    String formattedMsg = String.format("[귓속말][%s](%s): %s",
                            chatName, chatServer.timeFormat.format(System.currentTimeMillis()), message);
                    recipientService.send(formattedMsg);
                    send("[귓속말] " + recipient + "에게 메시지를 보냈습니다.");
                    chatServer.logCommand(chatName, msg, "귓속말 전송 성공");
                } else {
                    send("[서버] 수신자가 존재하지 않습니다.");
                    chatServer.logCommand(chatName, msg, "귓속말 전송 실패 - 수신자 없음");
                }
            }
        } catch (Exception e) {
            send("[서버] 귓속말 처리 중 오류가 발생했습니다.");
        }
    }

    // 이미지 전송 처리
    public void handleImageTransfer(String msg) {
        // 형식: /img:파일경로
        String[] parts = msg.split(":", 2);
        if (parts.length < 2) {
            send("[서버] 이미지 전송 형식 오류. 사용법: /img:파일경로");
            return;
        }
        String filePath = parts[1].trim();
        File file = new File(filePath);
        if (!file.exists()) {
            send("[서버] 파일이 존재하지 않습니다: " + filePath);
            return;
        }

        // 이미지 데이터 수신
        try {
            long fileSize = dis.readLong(); // 파일 크기 수신
            byte[] buffer = new byte[4096];
            FileOutputStream fos = new FileOutputStream("server_" + file.getName()); // 서버에 파일 저장

            long totalRead = 0;
            while (totalRead < fileSize) {
                int bytesRead = dis.read(buffer, 0, buffer.length); // 데이터 수신
                if (bytesRead < 0) break; // EOF
                fos.write(buffer, 0, bytesRead); // 파일에 쓰기
                totalRead += bytesRead; // 진행 상황 업데이트
            }
            fos.close();
            send("[서버] 이미지 전송을 성공적으로 받았습니다: " + file.getName());
            chatServer.logCommand(chatName, msg, "이미지 전송 성공");

            // 모든 클라이언트에게 이미지 전송 알림
            String imageMsg = String.format("[이미지] %s가 이미지를 전송했습니다(다운을 원하시면 /download:%s 를 입력하세요.)", chatName, file.getName());
            chatServer.sendToAll(this, imageMsg);
        } catch (IOException e) {
            send("[서버] 이미지 전송 중 오류가 발생했습니다: " + e.getMessage());
            chatServer.logCommand(chatName, msg, "이미지 전송 실패");
        }
    }

    // 이미지 다운로드 처리 (yes/no 단계 제거)
    public void handleDownload(String msg) {
        // 형식: /download:파일명
        String[] parts = msg.split(":", 2);
        if (parts.length < 2) {
            send("[서버] 다운로드 형식 오류. 사용법: /download:파일명");
            return;
        }
        String fileName = parts[1].trim();
        // 파일명에서 경로를 제거하여 저장할 파일 이름만 추출
        File file = new File(fileName);
        String actualFileName = file.getName();
        File actualFile = new File("server_" + actualFileName); // 서버에 저장된 파일 이름

        if (!actualFile.exists()) {
            send("[서버] 파일이 존재하지 않습니다: " + actualFileName);
            chatServer.logCommand(chatName, msg, "다운로드 실패 - 파일 없음");
            return;
        }

        try {
            // 클라이언트에게 파일 정보 전송
            sendFileInfo(actualFileName, actualFile.length());

            // 클라이언트가 파일 수신을 승인한 경우 파일 전송
            // 여기서 'yes/no' 확인 단계를 제거하고 바로 파일을 전송합니다.
            sendFile(actualFile);
            send("[서버] 파일 전송 완료: " + actualFileName);
            chatServer.logCommand(chatName, msg, "파일 다운로드 완료");
        } catch (IOException e) {
            send("[서버] 파일 전송 중 오류가 발생했습니다: " + e.getMessage());
            chatServer.logCommand(chatName, msg, "파일 전송 실패");
        }
    }

    // 파일 정보 전송 메서드
    private void sendFileInfo(String fileName, long fileSize) throws IOException {
        // 특별한 메시지 형식으로 파일 전송 시작을 알림
        String fileInfoMsg = String.format("/file:%s:%d", fileName, fileSize);
        send(fileInfoMsg);
    }

    // 파일 전송 메서드
    public void sendFile(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[4096];
        int read;
        while ((read = fis.read(buffer)) != -1) {
            dos.write(buffer, 0, read);
        }
        dos.flush();
        fis.close();
    }

    // 로그 요청 처리
    public void handleLogsCommand() {
        chatServer.logCommand(chatName, "/logs", "로그 요청");
        chatServer.showLogs(this); // 채팅 로그 출력
    }

    // 메시지 전송 메서드
    public void send(String msg) {
        try {
            dos.writeUTF(msg); // 메시지 전송
            dos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 클라이언트 종료 처리
    public void quit() {
        chatServer.removeClientInfo(this);
        try {
            dis.close();
            dos.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
