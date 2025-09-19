package chat.client;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class ChatClient {

    String chatName; // 사용자의 대화명
    Socket socket; // 서버와의 연결 소켓
    DataInputStream dis; // 서버에서 데이터 수신
    DataOutputStream dos; // 서버로 데이터 전송

    final String quitCommand = "quit"; // 채팅 종료 명령어
    final String userListCommand = "/users";
    final String helpCommand = "/help";

    // 서버 연결 메서드
    public void connect(String serverIP, int portNo, String chatName) {
        try {
            socket = new Socket(serverIP, portNo); // 서버와 연결
            dis = new DataInputStream(socket.getInputStream()); // 입력 스트림 초기화
            dos = new DataOutputStream(socket.getOutputStream()); // 출력 스트림 초기화
            this.chatName = chatName;

            send(chatName); // 서버로 대화명 전송
            System.out.println("[" + chatName + "] 서버 연결 성공 (" + serverIP + ":" + portNo + ")");
            receive(); // 메시지 수신 대기
        } catch (IOException e) {
            System.out.println("[에러] 서버 연결 실패: " + e.getMessage());
        }
    }

    // 서버로 메시지 전송
    public void send(String msg) {
        try {
            dos.writeUTF(msg); // 메시지 전송
            dos.flush(); // 스트림 비우기
        } catch (IOException e) {
            System.out.println("[에러] 메시지 전송 실패: " + e.getMessage());
        }
    }

    // 서버로부터 메시지 수신
    public void receive() {
        new Thread(() -> {
            try {
                while (true) {
                    String msg = dis.readUTF(); // 메시지 수신
                    if (msg.startsWith("/file:")) {
                        // 파일 전송 시작
                        handleIncomingFile(msg);
                    } else if (msg.startsWith("[이미지]")) {
                        System.out.println(msg);
                        // 이미지 전송 알림 후 다운로드는 사용자가 /download:파일명 명령어로 요청
                        System.out.print(">> "); // 프롬프트 재표시
                    } else {
                        System.out.println(msg); // 일반 메시지 출력
                        System.out.print(">> "); // 프롬프트 재표시
                    }
                }
            } catch (IOException e) {
                System.out.println("[서버 연결 종료]");
            } finally {
                quit(); // 종료 처리
            }
        }).start(); // 별도 쓰레드에서 실행
    }

    // 파일 전송 시작 처리
    private void handleIncomingFile(String msg) {
        try {
            // 메시지 형식: /file:파일명:파일크기
            String[] parts = msg.split(":", 3);
            if (parts.length < 3) {
                System.out.println("[에러] 파일 전송 형식 오류.");
                System.out.print(">> ");
                return;
            }
            String fileName = parts[1].trim();
            long fileSize = Long.parseLong(parts[2].trim());

            System.out.println("서버로부터 파일을 수신합니다: " + fileName + " (" + fileSize + " bytes)");

            // 파일 수신 및 저장
            FileOutputStream fos = new FileOutputStream("downloaded_" + fileName);
            byte[] buffer = new byte[4096];
            long totalRead = 0;
            int read;
            while (totalRead < fileSize && (read = dis.read(buffer, 0, buffer.length)) != -1) {
                fos.write(buffer, 0, read);
                totalRead += read;
            }
            fos.close();
            System.out.println("[다운로드 완료] 파일명: downloaded_" + fileName);
            System.out.print(">> ");
        } catch (Exception e) {
            System.out.println("[에러] 파일 수신 중 오류 발생: " + e.getMessage());
            System.out.print(">> ");
        }
    }

    // 프로그램 종료 처리
    public void quit() {
        try {
            dis.close(); // 입력 스트림 닫기
            dos.close(); // 출력 스트림 닫기
            socket.close(); // 소켓 닫기
            System.out.println("[종료] 서버와 연결이 종료되었습니다.");
            System.exit(0); // 프로그램 종료
        } catch (IOException e) {
            System.out.println("[에러] 종료 실패: " + e.getMessage());
        }
    }

    // /img 명령어 처리: 이미지 전송 요청
    public void sendImage(String filePath) {
        File file = new File(filePath); // 전송할 파일 경로
        if (!file.exists()) {
            System.out.println("[에러] 파일이 존재하지 않습니다: " + filePath);
            return;
        }
        send("/img:" + file.getName()); // 서버로 이미지 전송 요청 (파일명만 전송)
        System.out.println("[이미지 전송 요청] " + file.getName());

        // 이미지 파일 전송
        try {
            dos.writeLong(file.length()); // 파일 크기 전송
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, read);
            }
            dos.flush();
            fis.close();
            System.out.println("[이미지 전송 완료] " + file.getName());
        } catch (IOException e) {
            System.out.println("[에러] 이미지 전송 실패: " + e.getMessage());
        }
    }

    // /download 명령어 처리: 파일 다운로드 요청
    public void downloadImage(String fileName) {
        send("/download:" + fileName);
        // 파일 수신은 서버가 /file:파일명:파일크기 메시지를 보내고, handleIncomingFile에서 처리됩니다.
    }

    // 사용 가능한 명령어 표시
    private void showHelp() {
        System.out.println("사용 가능한 명령어:");
        System.out.println("/help - 사용 가능한 명령어 목록 보기");
        System.out.println("/users - 접속 중인 사용자 목록 보기");
        System.out.println("/rename:새닉네임 - 닉네임 변경");
        System.out.println("/to:닉네임/메시지 - 특정 사용자에게 귓속말 보내기");
        System.out.println("/img:파일경로 - 이미지 전송");
        System.out.println("/quit - 채팅 종료");
        System.out.println("/logs - 서버 로그 확인");
    }

    // 메인 메서드
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        ChatClient chatClient = new ChatClient();

        System.out.print("대화명을 입력하세요: ");
        String chatName = sc.nextLine();

        chatClient.connect("localhost", 18956, chatName); // 서버 연결
        System.out.println("사용자명령어목록을 확인하시려면 /help를 입력하세요.");
        // 사용자 입력 처리
        while (true) {
            System.out.print(">> "); // 사용자 입력 전에 프롬프트 표시
            String input = sc.nextLine(); // 사용자 입력 대기

            if (input.startsWith("/img:")) {
                // /img 명령어 처리
                String filePath = input.split(":", 2)[1];
                chatClient.sendImage(filePath);
            } else if (input.startsWith("/download:")) {
                // /download 명령어 처리
                String fileName = input.split(":", 2)[1];
                chatClient.downloadImage(fileName);
            } else if (input.startsWith("/help")) {
                chatClient.showHelp();
            } else if (input.startsWith("/users")) {
                chatClient.send("/users");
            } else if (input.startsWith("/logs")) {
                chatClient.send("/logs");
            } else if (input.startsWith("/rename:")) {
                chatClient.send(input); // /rename 명령어 전송
            } else if (input.startsWith("/to:")) {
                chatClient.send(input); // /to 명령어 전송
            } else if (input.equalsIgnoreCase(chatClient.quitCommand)) {
                // 종료 명령어
                chatClient.send("quit");
                break;
            } else {
                // 일반 메시지
                chatClient.send(input);
            }
        }

        chatClient.quit(); // 종료 처리
        sc.close();
    }
}
