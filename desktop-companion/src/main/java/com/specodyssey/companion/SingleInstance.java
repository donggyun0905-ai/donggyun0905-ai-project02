package com.specodyssey.companion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * 캐릭터는 한 번에 하나만 — 이미 켜져 있으면 받은 값(연결 코드 등)을 켜진 캐릭터에게 넘기고 새 프로그램은 바로 끝낸다.
 * 이 PC 안(127.0.0.1)에서만 받는 작은 소켓을 쓴다.
 */
public final class SingleInstance {

    private static final int PORT = 47831;
    private static final String HELLO = "SPEC-ODYSSEY-COMPANION";

    private SingleInstance() {
    }

    /**
     * 이 프로그램이 첫 번째면 true를 돌려주고 이후 넘어오는 값을 onArgs로 받는다.
     * 이미 켜진 캐릭터가 있으면 line을 넘기고 false.
     */
    public static boolean claim(String line, Consumer<String> onArgs, int attempts) {
        ServerSocket server = null;
        for (int i = 0; i < attempts && server == null; i++) {
            try {
                server = new ServerSocket(PORT, 5, InetAddress.getLoopbackAddress());
            } catch (IOException taken) {
                if (i + 1 < attempts) {
                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        if (server == null) {
            sendToRunning(line);
            return false;
        }
        ServerSocket bound = server;
        Thread t = new Thread(() -> {
            while (!bound.isClosed()) {
                try (Socket s = bound.accept();
                     BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
                    s.setSoTimeout(3000);
                    if (HELLO.equals(in.readLine())) {
                        String received = in.readLine();
                        onArgs.accept(received == null ? "" : received);
                    }
                } catch (IOException ignored) {
                    // 잘못 붙은 연결은 무시
                }
            }
        }, "single-instance");
        t.setDaemon(true);
        t.start();
        return true;
    }

    private static void sendToRunning(String line) {
        try (Socket s = new Socket(InetAddress.getLoopbackAddress(), PORT);
             PrintWriter out = new PrintWriter(s.getOutputStream(), true, StandardCharsets.UTF_8)) {
            out.println(HELLO);
            out.println(line == null ? "" : line);
        } catch (IOException ignored) {
            // 켜진 캐릭터가 막 꺼지는 중이면 넘기지 못해도 그만
        }
    }
}
