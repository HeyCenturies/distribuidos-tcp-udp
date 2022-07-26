package projeto;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Cliente {

    public static final Integer serverPort = 10078;
    public static String dir = null;
    //TODO UPDATE STORED FILE NAMES SE CONSEGUIU BAIXAR O ARQUIVO DE OUTRO PEER
    public static List<String> StoredfileNames = null;
    public static String ipNumber = null;
    public static String portNumber = null;
    public static Boolean connected = false;

    public static Integer TIMEOUT_MILISECONDS = 5000;

    public static void main(String[] args) throws Exception {

        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
        while(true) {
            geraMenuInterativo(connected);
            String resposta = inFromUser.readLine().toUpperCase();
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
            if(resposta.equals("SEARCH")){
                System.out.println("Insira o nome do arquivo");
                String fileName = inFromUser.readLine();
                procuraFileNoServidor(fileName);
            }
            if(resposta.equals("DOWNLOAD")){
                System.out.println("Insira o nome do host , porta e arquivo");
                List<String> parametros = new ArrayList<>(Arrays.asList(inFromUser.readLine().split(",")));
                enviaRequestDownload(parametros);
            }
            //TODO: REMOVE LATER
            if(resposta.equals("UPDATE")){
                System.out.println("Insira o nome do arquivo");
                String fileName = inFromUser.readLine();
                updateServidor(ipNumber,portNumber,fileName);
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
                        System.out.println("[TESTE1]: " + msg);

                        ThreadDownload ta = new ThreadDownload(socket,msg.get(0));
                        ta.start();

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
                                String operation = socketData;

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
                    serverPort);

            clientSocket.setSoTimeout(TIMEOUT_MILISECONDS);
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
        } catch (SocketTimeoutException st){
            System.out.println("[NAO CONSEGUIU DAR JOIN] PARA HOST: " + parametros.get(0) + "TENTANDO NOVAMENTE");
            joinServidor(parametros);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void updateServidor(String ipNumber, String portNumber, String file) {
        try {

            List<String> informacoes = new ArrayList<>();
            DatagramSocket clientSocket = new DatagramSocket(acharFreePort());

            byte[] sendData;

            informacoes.add("UPDATE");
            informacoes.add(ipNumber+":"+portNumber);
            informacoes.add(file);

            sendData = informacoes.toString().getBytes(StandardCharsets.UTF_8);

            DatagramPacket sendPacket = new DatagramPacket(sendData,
                    sendData.length,
                    InetAddress.getByName("127.0.0.1"),
                    serverPort);

            clientSocket.setSoTimeout(TIMEOUT_MILISECONDS);
            clientSocket.send(sendPacket);

            //recebe response
            byte[] recBuffer = new byte[1024];
            DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);

            clientSocket.receive(recPkt);
            String info = new String(recPkt.getData(),recPkt.getOffset(),recPkt.getLength());
            System.out.println(info);
            clientSocket.close();
        } catch (SocketTimeoutException st) {
            System.out.println("[NAO CONSEGUIU DAR UPDATE] PARA HOST: " + ipNumber + " TENTANDO NOVAMENTE");
            updateServidor(ipNumber,portNumber,file);
        }  catch (Exception e) {
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
                    serverPort);

            clientSocket.setSoTimeout(TIMEOUT_MILISECONDS);
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
        } catch (SocketTimeoutException st) {
            System.out.println("[NAO CONSEGUIU DAR LEAVE] PARA HOST: " + ipNumber+ " TENTANDO NOVAMENTE");
            leaveServidor(ipNumber,portNumber);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void procuraFileNoServidor(String fileName) {
        try {

            List<String> informacoes = new ArrayList<>();
            DatagramSocket clientSocket = new DatagramSocket(acharFreePort());

            byte[] sendData;

            informacoes.add("SEARCH");
            informacoes.add(fileName);

            sendData = informacoes.toString().getBytes(StandardCharsets.UTF_8);

            DatagramPacket sendPacket = new DatagramPacket(sendData,
                    sendData.length,
                    InetAddress.getByName("127.0.0.1"),
                    serverPort);

            clientSocket.setSoTimeout(TIMEOUT_MILISECONDS);
            clientSocket.send(sendPacket);

            //recebe response
            byte[] recBuffer = new byte[1024];
            DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
            clientSocket.receive(recPkt);

            String info = new String(recPkt.getData(),recPkt.getOffset(),recPkt.getLength());

            System.out.println("LISTA DE HOSTS COM ARQUIVO: " + fileName + " : " + info);

            clientSocket.close();
        } catch (SocketTimeoutException st) {
            System.out.println("[NAO CONSEGUIU DAR SEARCH] PARA HOST: " + ipNumber + " TENTANDO NOVAMENTE");
            procuraFileNoServidor(fileName);
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
            System.out.println("------- UPDATE -------");

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

    private static void enviaRequestDownload(List<String> parametros) {
        try {
            String fileParaBaixar  = parametros.get(2);

            Socket s = new Socket(parametros.get(0),Integer.valueOf(parametros.get(1)));

            OutputStream os = s.getOutputStream();
            DataOutputStream writer = new DataOutputStream(os);


            writer.writeBytes(fileParaBaixar + "\n");


            InputStream is = s.getInputStream();
            DataInputStream clientData = new DataInputStream(is);
            Integer bufferSize = Integer.valueOf(clientData.readUTF());
            OutputStream output = new FileOutputStream(dir + "/" + fileParaBaixar);
            BufferedOutputStream bos = new BufferedOutputStream(output);

            byte[] buffer = new byte[bufferSize];

            is.read(buffer,0,bufferSize);
            output.write(buffer,0,bufferSize);
            output.flush();

            //TimeUnit.SECONDS.sleep(5);
            System.out.println("[DOWNLOAD] completo");

    } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }


static class ThreadDownload extends Thread{

    private Socket no = null;
    private String file = null;
    public ThreadDownload(Socket node, String file){
        this.no = node;
        this.file = file;
    }

    public void run() {
        try{

            OutputStream os = no.getOutputStream();
            DataOutputStream writer = new DataOutputStream(os);

            File myFile = new File(dir + "/" + file);
            byte[] mybytearray = new byte[(int) myFile.length()];
            FileInputStream fis = new FileInputStream(myFile);
            BufferedInputStream bis = new BufferedInputStream(fis);

            fis.read(mybytearray,0,mybytearray.length);
            writer.writeUTF(String.valueOf(mybytearray.length));
            writer.write(mybytearray, 0, mybytearray.length);

            no.shutdownOutput();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
}



