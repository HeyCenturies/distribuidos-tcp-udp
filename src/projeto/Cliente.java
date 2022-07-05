package projeto;

import tcpdemo.Mensagem;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.Thread.interrupted;

public class Cliente {

    public static String dir = null;
    public static List<String> StoredfileNames = null;
    public static String ipNumber = null;
    public static String portNumber = null;

    public static Boolean connected = false;

    public static void main(String[] args) throws Exception {

        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
        while(true) {
            geraMenuInterativo(connected);
            String resposta = inFromUser.readLine();
            if(resposta.equals("JOIN")){
                System.out.println("Digite IP,Porta,Path de Arquivos");
                List<String> parametros = new ArrayList<>(Arrays.asList(inFromUser.readLine().split(",")));
                joinServidor(parametros);
                iniciaTCP(Integer.valueOf(portNumber));
                iniciaUDP(Integer.valueOf(portNumber));
            }
            if(resposta.equals("LEAVE")){
                leaveServidor(ipNumber,portNumber);
                System.exit(1);
            }
        }
    }

    private static void iniciaTCP(int porta){
        new Thread(new Runnable() {
            @Override
            public void run() {
                ExecutorService executor = null;
                try (ServerSocket server = new ServerSocket(porta)) {
                    executor = Executors.newFixedThreadPool(5);
                    System.out.println("Listening on TCP port " + porta);
                    while (true) {
                        final Socket socket = server.accept();
                        InputStreamReader isr = new InputStreamReader(socket.getInputStream());
                        BufferedReader reader = new BufferedReader(isr);
                        List<String> msg = Arrays.asList(reader.readLine().toString().split("\\s+"));

                        if(msg.get(0).equals("JOIN")){
                            ThreadAtendimento ta = new ThreadAtendimento(socket);
                            ta.start();
                        }

                    }
                } catch (IOException ioe) {
                    System.err.println("Cannot open the port on TCP");
                    ioe.printStackTrace();
                } finally {
                    System.out.println("Closing TCP server");
                    if (executor != null) {
                        executor.shutdown();
                    }
                }
            }
        }).start();
    }

    private static void iniciaUDP(int porta){
        new Thread(new Runnable() {
            @Override
            public void run() {
                        try {
                            DatagramSocket socket = new DatagramSocket(porta);
                            System.out.println("Listening on UDP port" + porta);

                            while (true) {
                                byte[] buf = new byte[socket.getReceiveBufferSize()];
                                DatagramPacket packet = new DatagramPacket(buf, buf.length);

                                socket.receive(packet);
                                String socketData = new String(packet.getData(), 0, packet.getLength());
                                socketData = socketData.substring(1,socketData.length()-1);
                                String operation = socketData.substring(0,socketData.length()-1);

                                System.out.println("CLIENT RECEBEU REQUEST: " + operation);

                                packet.setData("ALIVE_OK".getBytes());
                                socket.send(packet);
                            }
                        } catch (IOException ioe) {
                            System.err.println("Cannot open the port on UDP");
                            ioe.printStackTrace();
                        } finally {
                            System.out.println("Closing UDP server");
                        }
                    }
        }).start();
    }

    private static void joinServidor(List<String> parametros) {
        try {
            // 69,420,src/projeto/peer1

            //pega nome dos arquivos
            List<String> fileNames = getFileNames(parametros.get(2));
            List<String> informacoes = new ArrayList<>();

            //transforma lista de nomes em bytes e envia
            DatagramSocket clientSocket = new DatagramSocket();

            byte[] sendData;

            informacoes.add("JOIN");
            informacoes.add(parametros.get(0) + ":" + parametros.get(1));
            informacoes.add(String.valueOf(fileNames.size()));
            informacoes.add(fileNames.toString());

            sendData = informacoes.toString().getBytes(StandardCharsets.UTF_8);
            DatagramPacket sendPacket = new DatagramPacket(sendData,
                    sendData.length,
                    InetAddress.getByName("127.0.0.1"),
                    10098);

            clientSocket.send(sendPacket);

            StoredfileNames = fileNames;
            ipNumber = parametros.get(0);
            dir = parametros.get(2);
            portNumber = parametros.get(1);
            connected = true;

            //recebe response
            byte[] recBuffer = new byte[1024];
            DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);

            clientSocket.receive(recPkt);
            String info = new String(recPkt.getData(),recPkt.getOffset(),recPkt.getLength());
            System.out.println(info);
            System.out.println("Sou peer " + ipNumber + ":" + portNumber + " com arquivos" + fileNames.toString());
            clientSocket.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private static void leaveServidor(String ipNumber, String portNumber) {
        try {

            List<String> informacoes = new ArrayList<>();
            DatagramSocket clientSocket = new DatagramSocket(acharFreePort());

            byte[] sendData;

            informacoes.add("LEAVE");
            informacoes.add(ipNumber+":"+portNumber);
            sendData = informacoes.toString().getBytes(StandardCharsets.UTF_8);


            DatagramPacket sendPacket = new DatagramPacket(sendData,
                    sendData.length,
                    InetAddress.getByName("127.0.0.1"),
                    10098);


            clientSocket.send(sendPacket);

            StoredfileNames = null;
            connected = false;

            //recebe response
            byte[] recBuffer = new byte[1024];
            DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);

            clientSocket.receive(recPkt);
            String info = new String(recPkt.getData(),recPkt.getOffset(),recPkt.getLength());
            System.out.println(info);
            clientSocket.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private static List<String> getFileNames(String folderPath) {
        Path dir = Paths.get(folderPath);
        List<String> files = new ArrayList<>();
        try {
            Files.walk(dir).forEach(path -> files.add(showFile(path.toFile())));
            files.removeAll(Collections.singleton(null));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return files;
    }

    private static void geraMenuInterativo(Boolean connected) {
        if(connected){
            System.out.println("--- ESCOLHA UMA ACAO ---");
            System.out.println("------ SEARCH -------");
            System.out.println("----- DOWNLOAD ------");
            System.out.println("------- LEAVE -------");
        } else{
            System.out.println("--- ESCOLHA UMA ACAO ---");
            System.out.println("------- JOIN --------");
            System.out.println("------ SEARCH -------");
            System.out.println("----- DOWNLOAD ------");
        }

    }

    public static String showFile(File file) {
        if (!file.isDirectory()) {
            return file.getName();
        }
        return null;
    }

    public static int acharFreePort(){
        try {
            ServerSocket socket = new ServerSocket(0);
            int port = socket.getLocalPort();
            socket.close();
            return port;
        }
        catch (IOException ioe) {}
        return 0;
    }

}
