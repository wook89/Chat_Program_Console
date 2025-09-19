package chat.server;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Map;
import java.util.Scanner;

public class ChatServer {

    final String quitCommand = "quit"; // 서버 종료 명령어
    ServerSocket serverSocket; // 서버 소켓 객체
    Map<String, ClientService> chatClientInfo = new Hashtable<>(); // 클라이언트 목록
    ArrayList<String> chatLogs = new ArrayList<>(); // 서버 로그 저장
    SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss"); // 시간 형식 지정
    PrintWriter logWriter; // 로그 파일 작성기

    // 서버 시작 메서드
    public void start(int portNo) {
        try {
            serverSocket = new ServerSocket(portNo); // 서버 소켓 생성
            System.out.println("[채팅서버] 시작 (" + InetAddress.getLocalHost() + ":" + portNo + ")");
            logWriter = new PrintWriter(new FileWriter("server_logs.txt", true), true); // 로그 파일 열기
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 클라이언트 연결 요청 처리
    public void connectClient() {
        Thread thread = new Thread(() -> {
            try {
                while (true) {
                    Socket socket = serverSocket.accept(); // 클라이언트 연결 수락
                    new ClientService(this, socket); // 새로운 클라이언트 서비스 생성
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.setDaemon(true); // 데몬 쓰레드로 설정
        thread.start();
    }

    // 클라이언트 추가
    public synchronized void addClientInfo(ClientService clientService) {
        chatClientInfo.put(clientService.chatName, clientService); // 클라이언트 등록
        String log = "[입장] " + clientService.chatName + " (현재 인원: " + chatClientInfo.size() + ")";
        chatLogs.add(log); // 로그 저장
        System.out.println(log); // 로그 출력
        writeLog(log);
        sendToAll(clientService, log);
    }

    // 클라이언트 제거
    public synchronized void removeClientInfo(ClientService clientService) {
        chatClientInfo.remove(clientService.chatName);
        String log = "[퇴장] " + clientService.chatName + " (현재 인원: " + chatClientInfo.size() + ")";
        chatLogs.add(log);
        System.out.println(log);
        writeLog(log);
        sendToAll(clientService, log);
    }

    // 모든 클라이언트에 메시지 전송 및 로그 기록
    public synchronized void sendToAll(ClientService sender, String msg) {
        String formattedMsg;
        if (msg.startsWith("[입장]") || msg.startsWith("[퇴장]") || msg.startsWith("[이미지]") || msg.startsWith("[귓속말]")) {
            formattedMsg = msg; // 포맷 유지
            chatLogs.add(formattedMsg);
            writeLog(formattedMsg);
        } else {
            formattedMsg = String.format("[%s](%s): %s",
                    sender.chatName, timeFormat.format(System.currentTimeMillis()), msg);
            chatLogs.add(formattedMsg); // 메시지를 로그에 저장
            writeLog(formattedMsg);
        }

        for (ClientService client : chatClientInfo.values()) {
            if (client != sender) {
                client.send(formattedMsg);
            }
        }
    }

    // 접속자 목록 전송
    public synchronized void sendUsers(ClientService clientService) {
        StringBuilder users = new StringBuilder("[현재 접속자 목록]\n");
        for (String name : chatClientInfo.keySet()) {
            users.append(name).append("\n");
        }
        clientService.send(users.toString());
    }

    // 로그를 기록하는 메서드
    public synchronized void logCommand(String clientName, String command, String result) {
        String logEntry = String.format("[명령어] %s -> %s : %s", clientName, command, result);
        chatLogs.add(logEntry); // 명령어 로그 저장
        System.out.println(logEntry);
        writeLog(logEntry);
    }

    // 로그를 클라이언트에게 전송
    public synchronized void showLogs(ClientService clientService) {
        StringBuilder logs = new StringBuilder("[서버 로그]\n");
        for (String log : chatLogs) {
            logs.append(log).append("\n");
        }
        clientService.send(logs.toString());
    }

    // 로그 파일에 기록
    private synchronized void writeLog(String log) {
        if (logWriter != null) {
            logWriter.println(log);
        }
    }

    // 서버 종료
    public void stop() {
        try {
            if (logWriter != null) {
                logWriter.close();
            }
            serverSocket.close();
            System.out.println("[채팅서버] 종료");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        ChatServer chatServer = new ChatServer();
        chatServer.start(18956); // 서버 시작
        chatServer.connectClient(); // 클라이언트 연결 처리

        // 서버 종료 명령 처리
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("서버를 종료하려면 quit을 입력하세요.");
            String command = scanner.nextLine();
            if (command.equalsIgnoreCase(chatServer.quitCommand)) {
                break;
            }
        }
        chatServer.stop();
        scanner.close();
    }
}
