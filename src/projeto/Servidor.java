package projeto;


import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/*
    Vitor Medeiros : 11201720112

    Observacoes:
        - Todas as requisicoes UDP fazem uso do metodo  socket.setSoTimeout(TIMEOUT_MILISECONDS), onde TIMEOUT_MILISECONDS
        é uma constante definida na classe. Em caso de timeout, o bloco catch da excecao chama novamente o metodo em questao
        para tentar novamente a comunicacao com o servidor. Secao 5.g

        - Infelizmente quando comecei o projeto eu nao tinha entendido exatamente o proposito da classe mensagem e que
        ela deveria atuar como um object de comunicacao entre servidor e cliente. Utilizei a classe mensagem como uma
        classe Singleton que mantem o estado dos arquivos que estao em determinado host, assim como os hosts que possuem
        determinado arquivo, as informacoes sao mantidas em um ConcurrentHashmap(). Apesar de nao ter utilizado a classe
        de acordo com as especificacoes, espero que seja possível considerar a execucao da aplicacao como um tod o que está
        funcionando e atende aos outros critérios de avaliacao.

 */

public class Servidor {

    public static final Integer serverPort = 10098;
    public static final Integer ALIVE_OK_TIMEOUT_MILISECONDS = 5000;

    public static void main(String[] args) {

        /*
        Instancia o server UDP que recebe requests e executa methods dependendo do tipo de request recebida,
        as request sao tratadas como strings , por isso o uso de methods que manipulam strings para obter host/ip/conteudo.
         */
        new Thread(new Runnable() {
            @Override
            public void run() {
                try (DatagramSocket socket = new DatagramSocket(serverPort)) {

                    while (true) {
                        byte[] buf = new byte[socket.getReceiveBufferSize()];
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);

                        socket.receive(packet);
                        String socketData = new String(packet.getData(), 0, packet.getLength());
                        socketData = socketData.substring(1,socketData.length()-1);
                        String operation = socketData.substring(0,socketData.indexOf(","));


                        /*

                        Secao 4.b -> Recebe uma request contendo host/numberOfFiles e lista de fileNames vindas como um
                        arraylist do client, depois adiciona a as variaveis que mantem files por host e uma lista de
                        todas as files em um host.
                         */
                        if(operation.equals("JOIN")){

                            String host = socketData.substring(nthIndexOf(socketData,",",1)+1,
                                    nthIndexOf(socketData,",",2)).trim();
                            String numberOfFiles = socketData.substring(nthIndexOf(socketData,",",2)+1,
                                    nthIndexOf(socketData,",",3)).trim();
                            List<String> fileNames = new ArrayList<>(Arrays.asList(socketData.substring(nthIndexOf(socketData,",",3)+3,
                                    socketData.length()-1).trim().split(",")));


                            System.out.println("Peer: "+  host + " adicionado com arquivos: "+ fileNames);
                            packet.setData("JOIN_OK".getBytes());

                            Mensagem msg = Mensagem.getInstance();
                            msg.setPortToFiles(host,fileNames);

                            for(String fileInList : fileNames){
                                List<String> hostsWithFiles = msg.getFilesToPort(fileInList);
                                hostsWithFiles.add(host);

                                msg.setFilesToPort(fileInList.trim(),hostsWithFiles);

                            }

                            socket.send(packet);
                        }

                        /*
                        Secao 4.c -> Requisicao leave recebe o host encapuslado em um arrayList, ao receber,
                        elimina as informacoes do host em questao e devolve um packet com data LEAVE_OK
                         */
                        if(operation.equals("LEAVE")){

                            String host = socketData.substring(nthIndexOf(socketData,",",1)+1).trim();

                            packet.setData("LEAVE_OK".getBytes());

                            Mensagem msg = Mensagem.getInstance();
                            msg.removeHost(host);

                            socket.send(packet);
                        }

                        /*
                        Secao 4.d -> Recebe o host de onde vem a requisicao e o fileName a ser procurado,
                        faz uma busca pelo arquivo no map<FileName,Lista<Hosts>> e devolve todos aqueles que
                        possuem o arquivo.
                         */
                        if(operation.equals("SEARCH")){

                            String host = socketData.substring(nthIndexOf(socketData,",",1)+1,nthIndexOf(socketData,",",2)).trim();
                            String fileName = socketData.substring(nthIndexOf(socketData,",",2)+1).trim();

                            Mensagem msg = Mensagem.getInstance();
                            List<String> hostsComArquivo = msg.getFilesToPort(fileName);

                            packet.setData(hostsComArquivo.toString().getBytes(StandardCharsets.UTF_8));
                            System.out.println("Peer: " + host+ " solicitou arquivo: " + fileName);
                            socket.send(packet);
                        }

                        /*
                        Secao 4.e -> Requisicao enviada apos o download, atualiza a lista de files por host e
                        de host por files com a nova distribuicao dos arquivos.
                         */
                        if(operation.equals("UPDATE")){

                            String host = socketData.substring(nthIndexOf(socketData,",",1)+1, nthIndexOf(socketData,",",2)).trim();
                            String fileName = socketData.substring(nthIndexOf(socketData,",",2)+1).trim();

                            packet.setData("UPDATE_OK".getBytes());

                            Mensagem msg = Mensagem.getInstance();

                            msg.updatePortToFiles(host,fileName);
                            msg.updateFilesToPort(host,fileName);

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


        /*
        Envia a requisicao de Request ALIVE para todos os hosts conectados a cada 30s
         */
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
        exec.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                Mensagem msg = Mensagem.getInstance();
                Set<String> connectedHosts = msg.getPortToFiles().keySet();
                Iterator<String> it = connectedHosts.iterator();
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
                                Integer.valueOf(hostAtual.substring(nthIndexOf(hostAtual,":",1)+1,hostAtual.length())));

                        socket.setSoTimeout(ALIVE_OK_TIMEOUT_MILISECONDS);
                        socket.send(sendPacket);

                        byte[] recBuffer = new byte[1024];
                        DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);

                        socket.receive(recPkt);
                        socket.close();

                    } catch (SocketException | UnknownHostException e) {
                        throw new RuntimeException(e);
                    }  catch (SocketTimeoutException st){
                        System.out.println("Peer: " + hostAtual + " morto. Eliminando seus arquivos");
                        msg.removeHost(hostAtual);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

            }
        }, 0, 30, TimeUnit.SECONDS);
    }


    /*
    Funcao auxiliar para manipulacao de Strings, é usado principalmente para separar listas de hosts IP:PORT
    a partir do index da virgula.
     */
    public static int nthIndexOf(String str, String subStr, int count) {
        int ind = -1;
        while(count > 0) {
            ind = str.indexOf(subStr, ind + 1);
            if(ind == -1) return -1;
            count--;
        }
        return ind;
    }

    /*
    Na hora de criar um dataGram socket, é preciso instanciar o socket em uma porta que ainda nao esteja sendo
    utilizada, para evitar problemas de concorrencia, buscamos a localPort livre no momento em que o socket é
    estabelecido.
     */
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
