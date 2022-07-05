package udpdemo;

import tcpdemo.ThreadAtendimento;

import java.net.*;
import java.nio.charset.StandardCharsets;

public class UDPServer {
    public static void main(String[] args) throws Exception {

        DatagramSocket serverSocket = new DatagramSocket(9876);

        while(true){

            byte[] recBuffer = new byte[1024];
            DatagramPacket dp = new DatagramPacket(recBuffer, recBuffer.length);
            serverSocket.receive(dp);

            byte[] sendBuf = new byte[1024];
            sendBuf = "Sou o Servidor".getBytes(StandardCharsets.UTF_8);

            InetAddress sendIp = dp.getAddress();
            int port = dp.getPort();

            DatagramPacket sendPacket = new DatagramPacket(sendBuf, sendBuf.length,sendIp,port);

            serverSocket.send(sendPacket);
        }

    }
}
