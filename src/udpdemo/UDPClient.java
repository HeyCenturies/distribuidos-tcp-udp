package udpdemo;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class UDPClient {
    public static void main(String[] args) throws Exception {

        DatagramSocket clientSocket = new DatagramSocket();
        InetAddress IP = InetAddress.getByName("127.0.0.1");

        byte[] sendData = new byte[1024];
        sendData = "Sou Cliente".getBytes(StandardCharsets.UTF_8);

        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,IP,9876);
        clientSocket.send(sendPacket);


        byte[] recBuffer = new byte[1024];
        DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);

        clientSocket.receive(recPkt);
        String info = new String(recPkt.getData(),recPkt.getOffset(),recPkt.getLength());
        System.out.println(info);

        //clientSocket.close();
    }
}
