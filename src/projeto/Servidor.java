package projeto;


import tcpdemo.Mensagem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class Servidor {

    public static void main(String[] args) {

        // TCP
        new Thread(new Runnable() {
            @Override
            public void run() {
                ExecutorService executor = null;
                try (ServerSocket server = new ServerSocket(10098)) {
                    executor = Executors.newFixedThreadPool(5);
                    System.out.println("Listening on TCP port 10098");
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

        // UDP
        new Thread(new Runnable() {
            @Override
            public void run() {
                try (DatagramSocket socket = new DatagramSocket(10098)) {
                    System.out.println("Listening on UDP port 10098");

                    while (true) {
                        byte[] buf = new byte[socket.getReceiveBufferSize()];
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);

                        socket.receive(packet);
                        String socketData = new String(packet.getData(), 0, packet.getLength());
                        socketData = socketData.substring(1,socketData.length()-1);
                        String operation = socketData.substring(0,socketData.indexOf(","));

                        if(operation.equals("JOIN")){

                            String host = socketData.substring(nthIndexOf(socketData,",",1)+1,
                                    nthIndexOf(socketData,",",2)).trim();
                            String numberOfFiles = socketData.substring(nthIndexOf(socketData,",",2)+1,
                                    nthIndexOf(socketData,",",3)).trim();
                            List<String> fileNames = new ArrayList<>(Arrays.asList(socketData.substring(nthIndexOf(socketData,",",3)+3,
                                    socketData.length()-1).split(",")));


                            System.out.println("[JOIN_REQUEST] host:" + host + "files:"+ numberOfFiles + "names:"+ fileNames);
                            packet.setData("JOIN_OK".getBytes());

                            Mensagem msg = Mensagem.getInstance();
                            msg.setPortToFiles(host,fileNames);

                            for(String fileInList : fileNames){
                                List<String> hostsWithFiles = msg.getFilesToPort(fileInList);
                                hostsWithFiles.add(host);
                                msg.setFilesToPort(fileInList,hostsWithFiles);
                            }

                            socket.send(packet);
                        }

                        if(operation.equals("LEAVE")){

                            String host = socketData.substring(nthIndexOf(socketData,",",1)+1,
                                    socketData.length()-1).trim();


                            System.out.println("[LEAVE REQUEST] host:" + host);
                            packet.setData("LEAVE_OK".getBytes());

                            Mensagem msg = Mensagem.getInstance();
                            msg.removeHost(host);

                            socket.send(packet);
                        }

                    }
                } catch (IOException ioe) {
                    System.err.println("Cannot open the port on UDP");
                    ioe.printStackTrace();
                } finally {
                    System.out.println("Closing UDP server");
                }
            }
        }).start();

        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
        exec.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                Mensagem msg = Mensagem.getInstance();
                Set<String> connectedHosts = msg.getPortToFiles().keySet();
                Iterator<String> it = connectedHosts.iterator();
                System.out.println("LISTA DE HOSTS CONECTADOS: " + connectedHosts + " ENVIANDO ALIVE SIGNAL");
                while (it.hasNext()) {
                    String hostAtual = it.next();
                    // tenta ALIVE no host
                    try {
                        List<String> informacoes = new ArrayList<>();
                        DatagramSocket socket = new DatagramSocket(acharFreePort());

                        byte[] sendData;

                        informacoes.add("ALIVE");

                        sendData = informacoes.toString().getBytes(StandardCharsets.UTF_8);

                        DatagramPacket sendPacket = new DatagramPacket(sendData,
                                sendData.length,
                                InetAddress.getByName("127.0.0.1"),
                                3000);

                        //Integer.parseInt(hostAtual.substring(nthIndexOf(hostAtual,":",1)+1,hostAtual.length()-1)));

                        socket.setSoTimeout(5000);
                        socket.send(sendPacket);

                        byte[] recBuffer = new byte[1024];
                        DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);

                        socket.receive(recPkt);
                        socket.close();

                    } catch (SocketException | UnknownHostException e) {
                        throw new RuntimeException(e);
                    }  catch (SocketTimeoutException st){
                        System.out.println("[NAO RECEBI ALIVE_OK] PARA HOST: " + hostAtual);
                        msg.removeHost(hostAtual);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                System.out.println("[POS] LISTA DE HOSTS ===: " + connectedHosts + " SINAL ENVIADO");

            }
        }, 0, 30, TimeUnit.SECONDS);
    }


    public static int nthIndexOf(String str, String subStr, int count) {
        int ind = -1;
        while(count > 0) {
            ind = str.indexOf(subStr, ind + 1);
            if(ind == -1) return -1;
            count--;
        }
        return ind;
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