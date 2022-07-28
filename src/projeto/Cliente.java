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
import java.util.concurrent.TimeUnit;

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
public class Cliente {

    public static final Integer serverPort = 10098;
    public static String dir = null;
    public static List<String> StoredfileNames = null;
    public static String ipNumber = null;
    public static String portNumber = null;
    public static Boolean connected = false;
    public static List<String> lastSearchResults = null;
    public static List<String> retryHosts = new ArrayList<>();

    public static Integer TIMEOUT_MILISECONDS = 5000;

    public static Integer DOWNLOAD_ATTEMPTS = 1;

    public static void main(String[] args) throws Exception {

        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));

        /*
            loop do menu interativo, para cada input do usuario, aciona determinado metodo responsavel por processar
            a requisicao e enviar as requests UDP e TCP para server/peer.

         */
        while(true) {
            geraMenuInterativo(connected);
            String resposta = inFromUser.readLine().toUpperCase();

            /*
            Metodo JOIN UDP (Secao 5.a / 5.b)

             */
            if(resposta.equals("JOIN")){
                System.out.println("Digite IP,Porta,Path de Arquivos");
                List<String> parametros = new ArrayList<>(Arrays.asList(inFromUser.readLine().split(",")));
                joinServidor(parametros);

                /*
                 INICIA AS THREADS DOS SERVIDORES UDP E TCP
                 */
                iniciaTCP(Integer.valueOf(portNumber));
                iniciaUDP(Integer.valueOf(portNumber));
            }

            /*
            Metodo leaveServidor UDP (Secao 5.c)
             */
            if(resposta.equals("LEAVE")){
                leaveServidor(ipNumber,portNumber);
                System.exit(1);
            }
            /*
            Metodo Search UDP (Secao 5.f)
             */
            if(resposta.equals("SEARCH")){
                System.out.println("Insira o nome do arquivo");
                String fileName = inFromUser.readLine();
                procuraFileNoServidor(fileName);
            }
            /*
            Metodo Download
             */
            if(resposta.equals("DOWNLOAD")){
                System.out.println("Insira o nome do host , porta e arquivo");
                List<String> parametros = new ArrayList<>(Arrays.asList(inFromUser.readLine().split(",")));
                enviaRequestDownload(parametros);
            }
        }
    }


    /*
    Metodo TCP abre a conexao TCP com a porta definida pelo usuario no input do menuInterativo. O
    Server mantem um socket de conexao nessa porta e, ao chegar uma requisicao, inicia o "Atendimento"
    atraves da ThreadDownload (inner class)

    Secao 5.i
     */
    private static void iniciaTCP(int porta){
        new Thread(new Runnable() {
            @Override
            public void run() {
                ExecutorService executor = null;
                try (ServerSocket server = new ServerSocket(porta)) {
                    executor = Executors.newFixedThreadPool(5);
                    while (true) {
                        final Socket socket = server.accept();

                        InputStreamReader isr = new InputStreamReader(socket.getInputStream());
                        BufferedReader reader = new BufferedReader(isr);

                        List<String> msg = Arrays.asList(reader.readLine().toString().split("\\s+"));

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

    /*
    Inicia Servidor UDP tambem na porta de input do menuInterativo apos selecionar a opcao JOIN. O Server responde
    a requisicoes nessa porta e basicamente serve para responder as requisicoes de ALIVE vindas do servidor.

    Secao 5.e
     */
    private static void iniciaUDP(int porta){
        new Thread(new Runnable() {
            @Override
            public void run() {
                        try {
                            DatagramSocket socket = new DatagramSocket(porta);

                            while (true) {
                                byte[] buf = new byte[socket.getReceiveBufferSize()];
                                DatagramPacket packet = new DatagramPacket(buf, buf.length);

                                socket.receive(packet);
                                String socketData = new String(packet.getData(), 0, packet.getLength());
                                socketData = socketData.substring(1,socketData.length()-1);

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
            System.out.println("Sou peer " + ipNumber + ":" + portNumber + " com arquivos" + fileNames.toString());
            clientSocket.close();
        } catch (SocketTimeoutException st){
            // nao conseguiu dar join, tentar novamente até dar certo.
            joinServidor(parametros);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /*
    Metodo que envia uma request UDP para o servidor atualizar a lista de arquivos de determinado host
    assim como a lista de hosts para determinado arquivo.
    Secao 5.d
     */
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
            clientSocket.close();
        } catch (SocketTimeoutException st) {
            // nao conseguiu dar update, tentar novamente até dar certo.
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
            clientSocket.close();
        } catch (SocketTimeoutException st) {
            // nao conseguiu dar leave, tentar novamente até dar certo.
            leaveServidor(ipNumber,portNumber);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /*
    Metodo que envia UDP Request de Search para o servidor e retorna a lista de hosts que contem aquela file
    com o resultado, atualiza a variable lastSearchResults que será usada no retry de um download_negado, buscando
    hosts dessa lista que ainda nao tenha sido tentados (lista retryHosts)
     */
    private static void procuraFileNoServidor(String fileName) {
        try {

            List<String> informacoes = new ArrayList<>();
            DatagramSocket clientSocket = new DatagramSocket(acharFreePort());

            byte[] sendData;

            informacoes.add("SEARCH");
            informacoes.add(ipNumber+":"+portNumber);
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
            info = info.substring(1,info.length()-1);
            lastSearchResults = new ArrayList<>();
            for(String searchResult: info.split(",")){
                lastSearchResults.add(searchResult);
            }

            System.out.println("peers com arquivo solicitado: " + info);

            clientSocket.close();
        } catch (SocketTimeoutException st) {
            // nao conseguiu dar search, tentar novamente até dar certo.
            procuraFileNoServidor(fileName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /*
    Metodo usado durante o JOIN do cliente. A partir do path informado no menu interativo, percorre o diretorio
    e salva o nome e extensao de todas as files.
     */
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

    /*
    Gera o menuInterativo Secao 6.5
     */
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

    /*
    Metodo auxiliar de getFileNames, para obter o nome das files no diretorio.
     */
    public static String showFile(File file) {
        if (!file.isDirectory()) {
            return file.getName();
        }
        return null;
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

    /*
    Recebe uma lista de parametros contendo (nome do arquivo, endereco e porta do peer) e a partir dai envia uma
    request TCP de download, se o "header" (UTF) da request conter download_negado, busca outro host atraves do method
    achaOutroPeer() e chama novamente o metodo até um maximo de 2 retry attempts. se o download der certo, transfere o
    conteudo em bytes para o arquivo no diretorio do peer atraves do FileOutputStream

    Secao 5.h , 5.i , 5.j e 5.k
     */
    private static void enviaRequestDownload(List<String> parametros) {
        try {
            String fileParaBaixar  = parametros.get(2);
            Socket s = new Socket(parametros.get(0),Integer.parseInt(parametros.get(1)));

            OutputStream os = s.getOutputStream();
            DataOutputStream writer = new DataOutputStream(os);


            writer.writeBytes(fileParaBaixar + "\n");


            InputStream is = s.getInputStream();
            DataInputStream clientData = new DataInputStream(is);
            String UTF = clientData.readUTF();
            if(!UTF.equals("DOWNLOAD_NEGADO")){
                Integer bufferSize = Integer.valueOf(UTF);
                OutputStream output = new FileOutputStream(dir + "/" + fileParaBaixar);

                byte[] buffer = new byte[bufferSize];

                is.read(buffer,0,bufferSize);
                output.write(buffer,0,bufferSize);
                output.flush();

                System.out.println("Arquivo: " + fileParaBaixar + " baixado com sucesso na pasta: " + (dir + "/" + fileParaBaixar));
                retryHosts = new ArrayList<>();
                updateServidor(ipNumber,portNumber,fileParaBaixar);
                DOWNLOAD_ATTEMPTS = 1;

            } else{
                if(DOWNLOAD_ATTEMPTS < 3){
                    DOWNLOAD_ATTEMPTS++;
                    List<String> novosParametros = new ArrayList<>();
                    String novoHost = achaOutroPeer((parametros.get(0)+":"+Integer.parseInt(parametros.get(1))),fileParaBaixar);
                    novosParametros.add(novoHost.split(":")[0].trim());
                    novosParametros.add(novoHost.split(":")[1].trim());
                    novosParametros.add(fileParaBaixar.trim());
                    System.out.println("peer: " + (parametros.get(0) + ":" + Integer.parseInt(parametros.get(1)))
                            + " negou o download," + "pedindo agora para o peer: " + novoHost);
                    enviaRequestDownload(novosParametros);
                    }
                DOWNLOAD_ATTEMPTS = 1;
            }


    } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    /*
    Se ocorrer um download negado, busca outro host dentro daqueles obtidos atraves da requisicao SEARCH que ainda
    nao tenham sido tentador (nao estejam em retryHosts), se falhar adiciona o host atual a retryHosts e busca outro
     */
    private static String achaOutroPeer(String host, String file) {

        try{
            List<String> hostsWithFile = lastSearchResults;

            if(hostsWithFile != null){
                String newHost = hostsWithFile.stream().filter(h -> (!h.equals(host.trim()) && !retryHosts.contains(host.trim()))).findFirst().orElse(null);
                if(newHost != null){
                    retryHosts.add(newHost);
                    return newHost;
                }
            }
            return host;
        } catch (Exception e){
            System.out.println(e);
            return host;
        }

    }



    /*
    Quando o client recebe uma request de Download, essa classe é responsavel por obter a request,
    verificar se ela existe no diretorio do peer e, com 50% de chance, recusar o download ou (nos outros 50%) de
    aceitar o download, entao escrevendo os bytes do arquivo em buffer e enviando de volta como response da request.
     */
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
            if(myFile.exists() && Math.random() > 0.5){
                byte[] mybytearray = new byte[(int) myFile.length()];
                FileInputStream fis = new FileInputStream(myFile);

                fis.read(mybytearray,0,mybytearray.length);
                writer.writeUTF(String.valueOf(mybytearray.length));
                writer.write(mybytearray, 0, mybytearray.length);
            } else{
                writer.writeUTF("DOWNLOAD_NEGADO");
            }

            no.shutdownOutput();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
}



