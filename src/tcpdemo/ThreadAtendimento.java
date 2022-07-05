package tcpdemo;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class ThreadAtendimento extends Thread{
    
    private Socket no = null;
    
    public ThreadAtendimento(Socket node){
        this.no = node;
    }
    
    public void run() {
        try{
            InputStreamReader isr = new InputStreamReader(no.getInputStream());
            BufferedReader reader = new BufferedReader(isr);

            OutputStream os = no.getOutputStream();
            DataOutputStream writer = new DataOutputStream(os);

            String texto = reader.readLine();

            writer.writeBytes(texto.toUpperCase() + "\n");

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
