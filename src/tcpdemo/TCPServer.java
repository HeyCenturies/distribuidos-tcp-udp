package tcpdemo;

import java.net.ServerSocket;
import java.net.Socket;

public class TCPServer {
    public static void main(String[] args) throws Exception {

        ServerSocket serverSocket = new ServerSocket(9000);
        Mensagem mensagem = Mensagem.getInstance();
        while(true){

            // aguarda conexao, quando recebe cria uma thread especifica para a requisicao
            System.out.println("Esperando Conexao");
            Socket no =  serverSocket.accept();
            System.out.println("Conn Aceita ");


            ThreadAtendimento thread = new ThreadAtendimento(no);
            thread.start();

        }

    }
}
